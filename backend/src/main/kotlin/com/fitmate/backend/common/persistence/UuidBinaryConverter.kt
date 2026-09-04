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
        return swapForMysqlOrderedUuid(buffer.array())
    }

    override fun convertToEntityAttribute(dbData: ByteArray?): UUID? {
        if (dbData == null) {
            return null
        }

        val buffer = ByteBuffer.wrap(restoreFromMysqlOrderedUuid(dbData))
        return UUID(buffer.long, buffer.long)
    }

    private fun swapForMysqlOrderedUuid(bytes: ByteArray): ByteArray {
        return byteArrayOf(
            bytes[6], bytes[7],
            bytes[4], bytes[5],
            bytes[0], bytes[1], bytes[2], bytes[3],
            bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15],
        )
    }

    private fun restoreFromMysqlOrderedUuid(bytes: ByteArray): ByteArray {
        return byteArrayOf(
            bytes[4], bytes[5], bytes[6], bytes[7],
            bytes[2], bytes[3],
            bytes[0], bytes[1],
            bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15],
        )
    }
}
