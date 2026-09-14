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
    fun `V19 upgrades to latest and empty database reaches latest`() {
        withDatabase { url, user, password ->
            assertEquals(19, flyway(url, user, password, "19").migrate().migrationsExecuted)
            val latest = flyway(url, user, password)
            assertEquals(2, latest.migrate().migrationsExecuted)
            assertEquals(1, objectExists(url, user, password, "TABLES", "table_name", "health_external_workout_summaries"))
            assertEquals(1, objectExists(url, user, password, "COLUMNS", "column_name", "source_app_key", "table_name", "health_external_workout_summaries"))
            assertEquals(4, objectExists(url, user, password, "STATISTICS", "index_name", "uk_health_external_source", "table_name", "health_external_workout_summaries"))
            assertEquals(0, latest.migrate().migrationsExecuted)
        }
        withDatabase { url, user, password ->
            val latest = flyway(url, user, password)
            assertEquals(21, latest.migrate().migrationsExecuted)
            assertEquals(0, latest.migrate().migrationsExecuted)
        }
    }

    @Test
    fun `V21 migrates Gemini analyses to legacy records`() {
        withDatabase { url, user, password ->
            flyway(url, user, password, "20").migrate()
            DriverManager.getConnection(url, user, password).use { connection ->
                connection.createStatement().use { statement ->
                    // Fixtures live only in this disposable database.
                    statement.execute("SET FOREIGN_KEY_CHECKS = 0")
                    for (status in listOf("COMPLETED", "PROCESSING")) {
                        statement.execute("""
                            INSERT INTO health_inbody_analyses
                            (id, user_id, media_id, status, provider, result_json, created_at, updated_at)
                            VALUES (UUID_TO_BIN(UUID()), UUID_TO_BIN(UUID()), UUID_TO_BIN(UUID()),
                                    '$status', 'GEMINI', '{"preserved":true}', NOW(3), NOW(3))
                        """.trimIndent())
                    }
                    statement.execute("SET FOREIGN_KEY_CHECKS = 1")
                }
            }
            val latest = flyway(url, user, password)
            assertEquals(1, latest.migrate().migrationsExecuted)
            latest.validate()
            DriverManager.getConnection(url, user, password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT status, provider, result_json, failure_code FROM health_inbody_analyses ORDER BY status").use { result ->
                        org.junit.jupiter.api.Assertions.assertTrue(result.next())
                        assertEquals("COMPLETED", result.getString("status"))
                        assertEquals("LEGACY", result.getString("provider"))
                        assertEquals("{\"preserved\":true}", result.getString("result_json"))
                        org.junit.jupiter.api.Assertions.assertTrue(result.next())
                        assertEquals("PROCESSING", result.getString("status"))
                        assertEquals("LEGACY", result.getString("provider"))
                        assertEquals("{\"preserved\":true}", result.getString("result_json"))
                        assertEquals(null, result.getString("failure_code"))
                        org.junit.jupiter.api.Assertions.assertFalse(result.next())
                    }
                    org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException::class.java) {
                        statement.executeUpdate("UPDATE health_inbody_analyses SET provider = 'GEMINI' WHERE provider = 'LEGACY'")
                    }
                }
            }
            assertEquals(0, latest.migrate().migrationsExecuted)
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
