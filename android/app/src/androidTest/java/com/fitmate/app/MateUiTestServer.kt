package com.fitmate.app.ui

import com.fitmate.app.data.remote.FitMateApiClient
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException
import java.util.UUID
import kotlin.concurrent.thread

/** In-device HTTP fixture. No requests or messages leave the test process. */
internal class MateUiTestServer(private val automaticLogin: Boolean = true, private val respond: (String, String, JsonObject) -> Pair<Int, Any>) : AutoCloseable {
    private val socket = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))
    private val gson = Gson()
    @Volatile private var failure: Throwable? = null
    private val worker = thread(name = "mate-ui-fixture", isDaemon = true) {
        try {
            while (!socket.isClosed) socket.accept().use { client ->
                client.soTimeout = 5_000
                val input = client.getInputStream()
                fun line(): String {
                    val bytes = ByteArrayOutputStream()
                    while (true) {
                        val next = input.read()
                        if (next < 0 || next == 10) break
                        if (next != 13) bytes.write(next)
                    }
                    return bytes.toString("UTF-8")
                }
                val request = line().split(' ')
                var length = 0
                while (true) {
                    val header = line()
                    if (header.isEmpty()) break
                    if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                }
                val bytes = ByteArray(length)
                var offset = 0
                while (offset < length) {
                    val read = input.read(bytes, offset, length - offset)
                    check(read > 0)
                    offset += read
                }
                val body = if (length == 0) JsonObject() else JsonParser.parseString(String(bytes, Charsets.UTF_8)).asJsonObject
                val result = if (automaticLogin && request[1] == "/auth/login") 200 to mapOf("tokens" to mapOf("accessToken" to UUID.randomUUID().toString(), "refreshToken" to UUID.randomUUID().toString()))
                    else respond(request[0], request[1], body)
                val response = gson.toJson(if (result.first < 400) mapOf("data" to result.second) else mapOf("error" to mapOf("message" to result.second))).toByteArray(Charsets.UTF_8)
                client.getOutputStream().apply {
                    write("HTTP/1.1 ${result.first} Test\r\nContent-Type: application/json\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(response); flush()
                }
            }
        } catch (error: Exception) {
            if (error !is SocketException || !socket.isClosed) failure = error
        }
    }

    val baseUrl: String get() = "http://127.0.0.1:${socket.localPort}"
    fun api() = FitMateApiClient(baseUrl).also { it.authenticate("auth/login", emptyMap<String, String>()) }

    override fun close() {
        socket.close()
        worker.join(2_000)
        failure?.let { throw AssertionError("Fixture HTTP failure", it) }
    }
}
