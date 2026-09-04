package com.fitmate.backend.common.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.util.UUID

class UuidBinaryConverterTest {

    private val converter = UuidBinaryConverter()

    @Test
    fun `uuid can round trip through binary converter`() {
        val source = UUID.fromString("6c070701-88ca-434f-99b1-3c330d0a6f12")

        val bytes = converter.convertToDatabaseColumn(source)
        val restored = converter.convertToEntityAttribute(bytes)

        assertNotNull(bytes)
        assertEquals(source, restored)
    }

    @Test
    fun `converter matches mysql uuid_to_bin swap flag one ordering`() {
        val source = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff")

        val bytes = converter.convertToDatabaseColumn(source)
        val hex = bytes!!.joinToString("") { "%02x".format(it) }

        assertEquals("66774455001122338899aabbccddeeff", hex)
    }
}
