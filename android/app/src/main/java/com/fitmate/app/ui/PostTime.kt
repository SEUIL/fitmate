package com.fitmate.app.ui

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

private val postTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)

/** API timestamps are instants; display them in the device's local time zone. */
internal fun formatPostTime(value: String, zone: ZoneId = ZoneId.systemDefault()): String =
    try {
        postTimeFormat.format(Instant.parse(value).atZone(zone))
    } catch (_: DateTimeParseException) {
        // Preserve legacy mock labels or already formatted dates without inventing a time.
        value
    }
