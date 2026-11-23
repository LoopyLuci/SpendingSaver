package com.spendingsaver.sync

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class HubUrlTest {
    private fun ok(input: String) = (HubUrl.parse(input) as HubUrl.Result.Ok).url
    private fun invalid(input: String) = assertTrue(HubUrl.parse(input) is HubUrl.Result.Invalid, "expected invalid: $input")

    @Test fun `bare lan address gets http and keeps the port`() = assertEquals("http://192.168.1.20:8125", ok("192.168.1.20:8125"))
    @Test fun `trailing slash and path are dropped`() = assertEquals("http://10.0.0.5:8125", ok(" http://10.0.0.5:8125/api/ "))
    @Test fun `https is accepted for any host`() = assertEquals("https://hub.example.com", ok("https://hub.example.com"))
    @Test fun `mdns and single label hosts count as local`() {
        assertEquals("http://desktop.local:8125", ok("desktop.local:8125"))
        assertEquals("http://mypc:8125", ok("mypc:8125"))
    }
    @Test fun `emulator and loopback hosts are local`() {
        ok("10.0.2.2:8125"); ok("localhost:8125"); ok("127.0.0.1:8125")
    }
    @Test fun `plain http to a public host is refused`() {
        invalid("http://example.com:8125"); invalid("8.8.8.8:8125"); invalid("172.32.0.1"); invalid("192.169.1.1")
    }
    @Test fun `private 172 range is local`() = assertEquals("http://172.20.1.1", ok("172.20.1.1"))
    @Test fun `garbage and credentials are refused`() {
        invalid(""); invalid("   "); invalid("ftp://192.168.1.1"); invalid("http://user:pw@192.168.1.1"); invalid("http://")
    }
}
