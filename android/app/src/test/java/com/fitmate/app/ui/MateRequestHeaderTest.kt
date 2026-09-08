package com.fitmate.app.ui

import com.fitmate.app.data.remote.FitMateApiClient
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetSocketAddress
import java.util.UUID

class MateRequestHeaderTest {
    @Test fun `idempotency header survives access token refresh`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val keys = mutableListOf<String?>()
        var attempts = 0
        val session = """{"accessToken":"${UUID.randomUUID()}","refreshToken":"${UUID.randomUUID()}"}"""
        server.createContext("/") { exchange ->
            exchange.requestBody.use { it.readBytes() }
            val (code, body) = when (exchange.requestURI.path) {
                "/auth/login" -> 200 to """{"data":{"tokens":$session}}"""
                "/auth/refresh" -> 200 to """{"data":$session}"""
                else -> {
                    keys += exchange.requestHeaders.getFirst("Idempotency-Key")
                    attempts++
                    if (attempts == 1) 401 to """{"error":{"message":"expired"}}"""
                    else 201 to """{"data":{"id":"created"}}"""
                }
            }
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val client = FitMateApiClient("http://127.0.0.1:${server.address.port}")
            client.authenticate("auth/login", emptyMap<String, String>())
            val key = UUID.randomUUID().toString()
            client.request("POST", "mate-posts", mapOf("title" to "test"), headers = mapOf("Idempotency-Key" to key))
            assertEquals(listOf(key, key), keys)
        } finally { server.stop(0) }
    }
}
