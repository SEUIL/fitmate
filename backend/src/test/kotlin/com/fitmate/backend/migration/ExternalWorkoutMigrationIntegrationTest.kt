package com.fitmate.backend.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.sql.DriverManager
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "FITMATE_TEST_DB_URL", matches = ".+")
class ExternalWorkoutMigrationIntegrationTest {
    @Test
    fun `V19 upgrades to V20 and empty database reaches V20`() {
        withDatabase { url, user, password ->
            assertEquals(19, flyway(url, user, password, "19").migrate().migrationsExecuted)
            val latest = flyway(url, user, password, "20")
            assertEquals(1, latest.migrate().migrationsExecuted)
            assertEquals(1, objectExists(url, user, password, "TABLES", "table_name", "health_external_workout_summaries"))
            assertEquals(1, objectExists(url, user, password, "COLUMNS", "column_name", "source_app_key", "table_name", "health_external_workout_summaries"))
            assertEquals(4, objectExists(url, user, password, "STATISTICS", "index_name", "uk_health_external_source", "table_name", "health_external_workout_summaries"))
            assertEquals(0, latest.migrate().migrationsExecuted)
        }
        withDatabase { url, user, password ->
            val latest = flyway(url, user, password, "20")
            assertEquals(20, latest.migrate().migrationsExecuted)
            assertEquals(0, latest.migrate().migrationsExecuted)
        }
    }

    @Test
    fun `V21 migrates Gemini analyses to legacy records`() {
        withDatabase { url, user, password ->
            flyway(url, user, password, "20").migrate()
            DriverManager.getConnection(url, user, password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("SET FOREIGN_KEY_CHECKS = 0")
                    for (status in listOf("COMPLETED", "PROCESSING")) statement.execute("""
                        INSERT INTO health_inbody_analyses (id, user_id, media_id, status, provider, result_json, created_at, updated_at)
                        VALUES (UUID_TO_BIN(UUID()), UUID_TO_BIN(UUID()), UUID_TO_BIN(UUID()), '$status', 'GEMINI', '{"preserved":true}', NOW(3), NOW(3))
                    """.trimIndent())
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1")
                }
            }
            val migrationsThroughV26 = flyway(url, user, password, "26")
            assertEquals(6, migrationsThroughV26.migrate().migrationsExecuted)
            migrationsThroughV26.validate()
            DriverManager.getConnection(url, user, password).use { connection -> connection.createStatement().use { statement ->
                statement.executeQuery("SELECT status, provider, result_json, failure_code FROM health_inbody_analyses ORDER BY status").use { result ->
                    org.junit.jupiter.api.Assertions.assertTrue(result.next()); assertEquals("COMPLETED", result.getString("status")); assertEquals("LEGACY", result.getString("provider")); assertEquals("{\"preserved\":true}", result.getString("result_json"))
                    org.junit.jupiter.api.Assertions.assertTrue(result.next()); assertEquals("PROCESSING", result.getString("status")); assertEquals("LEGACY", result.getString("provider")); assertEquals("{\"preserved\":true}", result.getString("result_json")); assertEquals(null, result.getString("failure_code")); org.junit.jupiter.api.Assertions.assertFalse(result.next())
                }
                org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException::class.java) { statement.executeUpdate("UPDATE health_inbody_analyses SET provider = 'GEMINI' WHERE provider = 'LEGACY'") }
            } }
            assertEquals(0, migrationsThroughV26.migrate().migrationsExecuted)
        }
    }

    @Test
    fun `V27 database upgrades to V28 without changing existing external summaries`() {
        withDatabase { url, user, password ->
            flyway(url, user, password, "27").migrate()
            DriverManager.getConnection(url, user, password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("SET FOREIGN_KEY_CHECKS = 0")
                    statement.execute("""
                        INSERT INTO health_external_workout_summaries (
                            workout_session_id, user_id, provider, source_record_id, exercise_type,
                            started_at, ended_at, active_duration_seconds, payload_hash, version, saved_at, updated_at
                        ) VALUES (
                            UUID_TO_BIN('10000000-0000-0000-0000-000000000001'),
                            UUID_TO_BIN('10000000-0000-0000-0000-000000000002'),
                            'SAMSUNG_HEALTH_DATA_SDK', 'preserved-source', 'RUNNING',
                            '2026-01-01 00:00:00.000', '2026-01-01 00:01:00.000', 60,
                            REPEAT('a', 64), 3, '2026-01-01 00:02:00.000', '2026-01-01 00:02:00.000'
                        )
                    """.trimIndent())
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1")
                }
            }

            val v28 = flyway(url, user, password, "28")
            assertEquals(1, v28.migrate().migrationsExecuted)
            assertEquals(1, objectExists(url, user, password, "TABLES", "table_name", "health_external_workout_samples"))
            assertEquals(1, objectExists(url, user, password, "COLUMNS", "column_name", "timeseries_hash", "table_name", "health_external_workout_summaries"))
            DriverManager.getConnection(url, user, password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT source_record_id, version, timeseries_hash, timeseries_version, timeseries_sample_count FROM health_external_workout_summaries").use { result ->
                        org.junit.jupiter.api.Assertions.assertTrue(result.next())
                        assertEquals("preserved-source", result.getString("source_record_id"))
                        assertEquals(3, result.getLong("version"))
                        assertEquals("", result.getString("timeseries_hash"))
                        assertEquals(1, result.getInt("timeseries_version"))
                        assertEquals(0, result.getInt("timeseries_sample_count"))
                    }
                }
            }
            assertEquals(0, v28.migrate().migrationsExecuted)
        }
    }

    private fun withDatabase(block: (String, String, String) -> Unit) {
        val source = requireNotNull(System.getenv("FITMATE_TEST_DB_URL"))
        val database = "fitmate_external_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val server = source.substringBeforeLast("/") + "/mysql"
        val user = System.getenv("FITMATE_TEST_DB_USERNAME") ?: "root"
        val password = System.getenv("FITMATE_TEST_DB_PASSWORD") ?: ""
        DriverManager.getConnection(server, user, password).use { it.createStatement().use { statement -> statement.execute("create database `$database` character set utf8mb4 collate utf8mb4_0900_ai_ci") } }
        val query = source.substringAfter("?", "").let { if (it.isEmpty()) "" else "?$it" }
        val url = source.substringBeforeLast("/").substringBefore("?") + "/$database$query"
        try { block(url, user, password) }
        finally { DriverManager.getConnection(server, user, password).use { it.createStatement().use { statement -> statement.execute("drop database if exists `$database`") } } }
    }

    private fun flyway(url: String, user: String, password: String, target: String? = null) =
        Flyway.configure().dataSource(url, user, password).locations("filesystem:../db/migration").apply { target?.let { target(it) } }.load()

    private fun objectExists(
        url: String, user: String, password: String, collection: String, key: String, value: String,
        parentKey: String? = null, parentValue: String? = null,
    ): Int = DriverManager.getConnection(url, user, password).use { connection ->
        val parentClause = if (parentKey == null) "" else " and $parentKey = ?"
        connection.prepareStatement("select count(*) from information_schema.$collection where table_schema = database() and $key = ?$parentClause").use { statement ->
            statement.setString(1, value)
            if (parentValue != null) statement.setString(2, parentValue)
            statement.executeQuery().use { result -> result.next(); result.getInt(1) }
        }
    }
}
