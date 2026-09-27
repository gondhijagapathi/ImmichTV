package com.jagapathi.immichtv.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlTest {

    @Test
    fun `keeps valid urls and trims whitespace and trailing slashes`() {
        assertEquals("https://photos.example.com", normalizeServerUrl("  https://photos.example.com/ "))
        assertEquals("http://192.168.1.20:2283/api", normalizeServerUrl("http://192.168.1.20:2283/api/"))
    }

    @Test
    fun `rejects input without an http scheme`() {
        assertNull(normalizeServerUrl("192.168.1.20:2283"))
        assertNull(normalizeServerUrl("photos.example.com"))
        assertNull(normalizeServerUrl("ftp://photos.example.com"))
        assertNull(normalizeServerUrl(""))
    }
}
