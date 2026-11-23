package com.spendingsaver.sync

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class SyncEngineTest {

    private class MemoryState(var state: SyncState = SyncState()) : SyncStateStore {
        override fun load() = state
        override fun save(state: SyncState) { this.state = state }
    }

    private class FakeStore(var pending: List<SyncRecord> = emptyList()) : SyncStore {
        val applied = mutableListOf<SyncRecord>()
        val askedSince = mutableListOf<Long>()
        override suspend fun modifiedSince(sinceMs: Long): List<SyncRecord> {
            askedSince += sinceMs
            return pending.filter { (SyncMapper.fromIso(it.updatedAt) ?: 0) >= sinceMs }
        }
        override suspend fun apply(records: List<SyncRecord>): Int { applied += records; return records.size }
    }

    private class FakeApi : SyncApi {
        var pairResponse = PairResponse(SYNC_PROTOCOL_VERSION, "dev", "token-123", "hub")
        val responses = ArrayDeque<ExchangeResponse>()
        val requests = mutableListOf<ExchangeRequest>()
        var pairCalls = 0
        override suspend fun pair(hubUrl: String, key: String, deviceId: String, deviceName: String): PairResponse {
            pairCalls++
            return pairResponse
        }
        override suspend fun exchange(hubUrl: String, token: String, request: ExchangeRequest): ExchangeResponse {
            requests += request
            return responses.removeFirstOrNull() ?: ExchangeResponse(SYNC_PROTOCOL_VERSION, request.cursor)
        }
    }

    private fun rec(id: String, ms: Long, entity: String = SyncEntity.ACCOUNT) = SyncRecord(
        entity, id, SyncMapper.toIso(ms), false,
        mapOf("name" to id, "type" to "checking", "balance" to 1.0, "currency" to "USD"),
    )

    private val paired = SyncState(hubUrl = "http://192.168.1.2:8125", deviceId = "d", token = "t")

    @Test
    fun `sync without pairing fails cleanly`() = runTest {
        val engine = SyncEngine(FakeStore(), FakeApi(), MemoryState())
        val e = assertThrows<SyncException> { engine.sync() }
        assertEquals(SyncException.Kind.NOT_PAIRED, e.kind)
    }

    @Test
    fun `pairing stores the token and starts from a clean cursor`() = runTest {
        val state = MemoryState(SyncState(deviceId = "keep-me", cursor = 99, lastPushMs = 99))
        val engine = SyncEngine(FakeStore(), FakeApi(), state)
        engine.pair("192.168.1.2:8125", "ab12cd", "Pixel")
        assertEquals("token-123", state.state.token)
        assertEquals("http://192.168.1.2:8125", state.state.hubUrl)
        assertEquals("keep-me", state.state.deviceId)
        assertEquals(0, state.state.cursor)
        assertEquals(0, state.state.lastPushMs)
    }

    @Test
    fun `pairing rejects bad addresses without contacting the hub`() = runTest {
        val api = FakeApi()
        val engine = SyncEngine(FakeStore(), api, MemoryState())
        assertThrows<SyncException> { engine.pair("http://example.com", "KEY", "x") }
        assertThrows<SyncException> { engine.pair("192.168.1.2", "  ", "x") }
        assertEquals(0, api.pairCalls)
    }

    @Test
    fun `pairing rejects a hub with another protocol or no token`() = runTest {
        val api = FakeApi().apply { pairResponse = PairResponse(99, "d", "tok", null) }
        val engine = SyncEngine(FakeStore(), api, MemoryState())
        assertEquals(SyncException.Kind.PROTOCOL, assertThrows<SyncException> { engine.pair("10.0.0.2", "K", "x") }.kind)
        api.pairResponse = PairResponse(SYNC_PROTOCOL_VERSION, "d", null, null)
        assertThrows<SyncException> { engine.pair("10.0.0.2", "K", "x") }
    }

    @Test
    fun `sync pushes pending rows, applies pulled rows and advances both marks`() = runTest {
        val store = FakeStore(listOf(rec("a", 1_000), rec("b", 2_000)))
        val api = FakeApi().apply {
            responses += ExchangeResponse(SYNC_PROTOCOL_VERSION, cursor = 7, changes = listOf(rec("remote", 5_000)))
        }
        val state = MemoryState(paired)
        val result = SyncEngine(store, api, state, clock = { 42L }).sync()

        assertEquals(listOf("a", "b"), api.requests.single().changes.map { it.id })
        assertEquals(0L, api.requests.single().cursor)
        assertEquals(listOf("remote"), store.applied.map { it.id })
        assertEquals(2, result.pushed)
        assertEquals(1, result.pulled)
        assertEquals(7, state.state.cursor)
        assertEquals(2_000, state.state.lastPushMs)
        assertEquals(42L, state.state.lastSyncAtMs)
    }

    @Test
    fun `second sync only sends rows changed since the watermark`() = runTest {
        val store = FakeStore(listOf(rec("a", 1_000), rec("b", 2_000)))
        val api = FakeApi()
        val state = MemoryState(paired)
        val engine = SyncEngine(store, api, state, clock = { 5_000 })
        engine.sync()
        store.pending = store.pending + rec("c", 3_000)
        api.requests.clear()
        engine.sync()
        // The watermark row itself (>=) is re-sent; the hub ignores equal timestamps.
        assertEquals(listOf("b", "c"), api.requests.single().changes.map { it.id })
    }

    @Test
    fun `rows pulled from the hub are not echoed back on the next sync`() = runTest {
        val now = 10_000_000L
        val store = FakeStore()
        val api = FakeApi().apply {
            responses += ExchangeResponse(SYNC_PROTOCOL_VERSION, cursor = 5, changes = listOf(rec("remote", now - 3_600_000)))
        }
        val state = MemoryState(paired)
        SyncEngine(store, api, state, clock = { now }).sync()
        // A clean sync moves the watermark up to the 60 s look-back even with nothing pushed.
        assertEquals(now - 60_000, state.state.lastPushMs)

        // The pulled row now exists locally with its (old) remote timestamp: not re-sent.
        store.pending = listOf(rec("remote", now - 3_600_000))
        api.requests.clear()
        SyncEngine(store, api, state, clock = { now + 1_000 }).sync()
        assertTrue(api.requests.single().changes.isEmpty())
    }

    @Test
    fun `rejected rows hold the watermark back so they are retried`() = runTest {
        val store = FakeStore(listOf(rec("a", 1_000), rec("bad", 2_000), rec("c", 3_000)))
        val api = FakeApi().apply {
            responses += ExchangeResponse(
                SYNC_PROTOCOL_VERSION, cursor = 1,
                rejected = listOf(Rejection(SyncEntity.ACCOUNT, "bad", "missing_reference")),
            )
        }
        val state = MemoryState(paired)
        val result = SyncEngine(store, api, state).sync()
        assertEquals(1, result.rejected.size)
        assertEquals(2, result.pushed)
        assertEquals(2_000, state.state.lastPushMs)
    }

    @Test
    fun `keeps pulling while the hub reports more`() = runTest {
        val api = FakeApi().apply {
            responses += ExchangeResponse(SYNC_PROTOCOL_VERSION, cursor = 10, hasMore = true, changes = listOf(rec("r1", 1)))
            responses += ExchangeResponse(SYNC_PROTOCOL_VERSION, cursor = 20, hasMore = true, changes = listOf(rec("r2", 2)))
            responses += ExchangeResponse(SYNC_PROTOCOL_VERSION, cursor = 30, hasMore = false, changes = listOf(rec("r3", 3)))
        }
        val store = FakeStore()
        val state = MemoryState(paired)
        SyncEngine(store, api, state).sync()
        assertEquals(listOf(0L, 10L, 20L), api.requests.map { it.cursor })
        assertTrue(api.requests.drop(1).all { it.changes.isEmpty() })
        assertEquals(listOf("r1", "r2", "r3"), store.applied.map { it.id })
        assertEquals(30, state.state.cursor)
    }

    @Test
    fun `large pushes are split into batches`() = runTest {
        val store = FakeStore((1..5).map { rec("id$it", it * 1_000L) })
        val api = FakeApi()
        SyncEngine(store, api, MemoryState(paired), batchSize = 2).sync()
        assertEquals(listOf(2, 2, 1), api.requests.map { it.changes.size })
    }

    @Test
    fun `a failed exchange leaves the watermark untouched`() = runTest {
        val store = FakeStore(listOf(rec("a", 1_000)))
        val api = object : SyncApi {
            override suspend fun pair(hubUrl: String, key: String, deviceId: String, deviceName: String) = PairResponse()
            override suspend fun exchange(hubUrl: String, token: String, request: ExchangeRequest): ExchangeResponse =
                throw SyncException("down", SyncException.Kind.NETWORK)
        }
        val state = MemoryState(paired)
        assertThrows<SyncException> { SyncEngine(store, api, state).sync() }
        assertEquals(0, state.state.lastPushMs)
        assertNull(state.state.lastSyncAtMs)
    }

    @Test
    fun `unpair forgets the hub but keeps the device id and local data`() = runTest {
        val store = FakeStore(listOf(rec("a", 1_000)))
        val state = MemoryState(paired.copy(cursor = 5, lastPushMs = 5))
        SyncEngine(store, FakeApi(), state).unpair()
        assertFalse(state.state.isPaired)
        assertEquals("d", state.state.deviceId)
        assertEquals(0, state.state.cursor)
        assertEquals(1, store.pending.size)
    }

    @Test
    fun `a hub with a different protocol is rejected during sync`() = runTest {
        val api = FakeApi().apply { responses += ExchangeResponse(protocol = 2, cursor = 1) }
        val e = assertThrows<SyncException> { SyncEngine(FakeStore(), api, MemoryState(paired)).sync() }
        assertEquals(SyncException.Kind.PROTOCOL, e.kind)
    }
}
