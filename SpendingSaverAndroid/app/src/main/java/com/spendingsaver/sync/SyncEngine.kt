package com.spendingsaver.sync

import java.util.UUID

/**
 * Pairs with, and exchanges changes with, an optional desktop sync hub.
 *
 * The app never depends on this: local data is the source of truth for every
 * screen, and unpairing leaves all local data untouched.
 *
 * Algorithm per [sync]:
 *  1. push every local row modified since the last successful push (including
 *     soft-deleted rows, which are tombstones) in batches,
 *  2. apply what the hub returns (last-write-wins by `updatedAt`),
 *  3. keep pulling pages until the hub reports nothing more,
 *  4. only then advance the push watermark. Rows the hub rejected keep the
 *     watermark at their timestamp so they are retried next time.
 */
class SyncEngine(
    private val store: SyncStore,
    private val api: SyncApi,
    private val state: SyncStateStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val batchSize: Int = 500,
) {
    private companion object {
        const val PUSH_LOOKBACK_MS = 60_000L
    }

    suspend fun pair(rawHubUrl: String, pairingKey: String, deviceName: String) {
        val url = when (val parsed = HubUrl.parse(rawHubUrl)) {
            is HubUrl.Result.Ok -> parsed.url
            is HubUrl.Result.Invalid -> throw SyncException(parsed.reason, SyncException.Kind.PROTOCOL)
        }
        if (pairingKey.isBlank()) throw SyncException("Enter the pairing key", SyncException.Kind.REJECTED_KEY)
        val current = state.load()
        val deviceId = current.deviceId.ifBlank { UUID.randomUUID().toString() }
        val response = api.pair(url, pairingKey.trim(), deviceId, deviceName)
        val token = response.token
        if (response.protocol != SYNC_PROTOCOL_VERSION || token.isNullOrBlank()) {
            throw SyncException("The hub speaks a different sync protocol", SyncException.Kind.PROTOCOL)
        }
        // A new hub means a new change log: start from scratch and push everything.
        state.save(
            SyncState(hubUrl = url, deviceId = deviceId, token = token, cursor = 0, lastPushMs = 0, lastSyncAtMs = null),
        )
    }

    /** Forgets the hub. Local finance data is not touched. */
    fun unpair() {
        val s = state.load()
        state.save(SyncState(deviceId = s.deviceId))
    }

    suspend fun sync(): SyncResult {
        val syncStartMs = clock()
        val start = state.load()
        val token = start.token
        if (!start.isPaired || token == null) {
            throw SyncException("Not paired with a hub", SyncException.Kind.NOT_PAIRED)
        }

        val pending = store.modifiedSince(start.lastPushMs).sortedBy { SyncMapper.fromIso(it.updatedAt) ?: 0L }
        val batches = pending.chunked(batchSize).ifEmpty { listOf(emptyList()) }

        var cursor = start.cursor
        var pushed = 0
        var pulled = 0
        val rejected = mutableListOf<Rejection>()
        var maxAccepted = start.lastPushMs
        var minRejected: Long? = null

        suspend fun round(changes: List<SyncRecord>): ExchangeResponse {
            val resp = api.exchange(start.hubUrl, token, ExchangeRequest(cursor, changes))
            if (resp.protocol != SYNC_PROTOCOL_VERSION) {
                throw SyncException("The hub speaks a different sync protocol", SyncException.Kind.PROTOCOL)
            }
            pulled += store.apply(resp.changes)
            cursor = resp.cursor
            // Persist progress after every page so an interruption never re-pulls everything.
            state.save(state.load().copy(cursor = cursor))
            return resp
        }

        var last: ExchangeResponse? = null
        for (batch in batches) {
            val resp = round(batch)
            last = resp
            val rejectedKeys = resp.rejected.mapNotNull { r -> r.id?.let { r.entity to it } }.toSet()
            for (rec in batch) {
                val ms = SyncMapper.fromIso(rec.updatedAt) ?: 0L
                if ((rec.entity to rec.id) in rejectedKeys) {
                    minRejected = minOf(minRejected ?: ms, ms)
                } else {
                    pushed++
                    maxAccepted = maxOf(maxAccepted, ms)
                }
            }
            rejected += resp.rejected
        }
        while (last?.hasMore == true) {
            last = round(emptyList())
        }

        // Everything older than the look-back was in `pending` (or is a pulled row that
        // needs no push), so a clean sync can move the watermark up to it. The look-back
        // covers a local write that committed just after `pending` was read.
        val lookbackMark = syncStartMs - PUSH_LOOKBACK_MS
        val newPushMark = (minRejected?.coerceAtMost(lookbackMark) ?: maxOf(maxAccepted, lookbackMark))
            .coerceAtLeast(start.lastPushMs)
        state.save(state.load().copy(lastPushMs = newPushMark, lastSyncAtMs = clock()))
        return SyncResult(pushed = pushed, pulled = pulled, rejected = rejected)
    }
}
