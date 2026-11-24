package com.spendingsaver.link

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class LinkReplyTest {
    @Test fun `ok reply carries the result object`() {
        val r = LinkReply.parse("""{"ok":true,"result":{"local_addr":"127.0.0.1:5555"}}""") as LinkReply.Ok
        assertEquals("127.0.0.1:5555", r.result.get("local_addr").asString)
    }
    @Test fun `error reply carries the message`() =
        assertEquals("nope", (LinkReply.parse("""{"ok":false,"error":"nope"}""") as LinkReply.Error).message)
    @Test fun `garbage never throws`() {
        assertTrue(LinkReply.parse("not json") is LinkReply.Error)
        assertTrue(LinkReply.parse("") is LinkReply.Error)
    }
}
