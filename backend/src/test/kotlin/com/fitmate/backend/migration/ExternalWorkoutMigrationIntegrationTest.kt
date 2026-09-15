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
