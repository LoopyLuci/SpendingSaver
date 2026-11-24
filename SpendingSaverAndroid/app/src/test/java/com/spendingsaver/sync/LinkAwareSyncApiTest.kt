package com.spendingsaver.sync

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class LinkAwareSyncApiTest {
    private class Recorder : SyncApi {
        val urls = mutableListOf<String>()
        override suspend fun pair(hubUrl: String, key: String, deviceId: String, deviceName: String): PairResponse {
            urls += hubUrl
            return PairResponse()
        }
        override suspend fun exchange(hubUrl: String, token: String, request: ExchangeRequest): ExchangeResponse {
            urls += hubUrl
            return ExchangeResponse()
        }
    }

    private val id = "ab".repeat(32)

    @Test fun `link hubs are resolved to the local endpoint`() = runBlocking {
        val rec = Recorder()
        val api = LinkAwareSyncApi(rec) { node -> assertEquals(id, node); "http://127.0.0.1:4444" }
        api.pair("$LINK_SCHEME$id", "K", "d", "n")
        api.exchange("$LINK_SCHEME$id", "t", ExchangeRequest(0, emptyList()))
        assertEquals(listOf("http://127.0.0.1:4444", "http://127.0.0.1:4444"), rec.urls)
    }

    @Test fun `lan hubs are used as they are and never touch the link`() = runBlocking {
        val rec = Recorder()
        val api = LinkAwareSyncApi(rec) { error("link must not be used") }
        api.exchange("http://192.168.1.20:8125", "t", ExchangeRequest(0, emptyList()))
        assertEquals(listOf("http://192.168.1.20:8125"), rec.urls)
    }

    @Test fun `link ids are validated`() {
        assertEquals("$LINK_SCHEME$id", (HubUrl.parse("$LINK_SCHEME${id.uppercase()}") as HubUrl.Result.Ok).url)
        assertTrue(HubUrl.parse("${LINK_SCHEME}xyz") is HubUrl.Result.Invalid)
        assertTrue(HubUrl.parse(LINK_SCHEME) is HubUrl.Result.Invalid)
    }
}
