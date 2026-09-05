package com.fitmate.backend.common.persistence

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.nio.ByteBuffer
import java.util.UUID

@Converter(autoApply = false)
class UuidBinaryConverter : AttributeConverter<UUID, ByteArray> {

    override fun convertToDatabaseColumn(attribute: UUID?): ByteArray? {
        if (attribute == null) {
            return null
        }

        val buffer = ByteBuffer.wrap(ByteArray(16))
        buffer.putLong(attribute.mostSignificantBits)
        buffer.putLong(attribute.leastSignificantBits)
        return toMysqlOrderedBytes(buffer.array())
    }

    override fun convertToEntityAttribute(dbData: ByteArray?): UUID? {
        if (dbData == null) {
            return null
        }

        val buffer = ByteBuffer.wrap(fromMysqlOrderedBytes(dbData))
        return UUID(buffer.long, buffer.long)
    }

    companion object {
        /** Converts the logical UUID used by the API to MySQL UUID_TO_BIN(uuid, 1) byte order. */
        fun toMysqlOrderedUuid(value: UUID): UUID = fromBytes(toMysqlOrderedBytes(toBytes(value)))

        /** Converts a UUID read as a raw BINARY(16) value back to its logical API UUID. */
        fun fromMysqlOrderedUuid(value: UUID): UUID = fromBytes(fromMysqlOrderedBytes(toBytes(value)))

        private fun toBytes(value: UUID): ByteArray = ByteBuffer.allocate(16)
            .putLong(value.mostSignificantBits)
            .putLong(value.leastSignificantBits)
            .array()

        private fun fromBytes(value: ByteArray): UUID = ByteBuffer.wrap(value).let { UUID(it.long, it.long) }

        private fun toMysqlOrderedBytes(bytes: ByteArray): ByteArray = byteArrayOf(
            bytes[6], bytes[7],
            bytes[4], bytes[5],
            bytes[0], bytes[1], bytes[2], bytes[3],
            bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15],
        )

        private fun fromMysqlOrderedBytes(bytes: ByteArray): ByteArray = byteArrayOf(
            bytes[4], bytes[5], bytes[6], bytes[7],
            bytes[2], bytes[3],
            bytes[0], bytes[1],
            bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15],
        )
    }
}
