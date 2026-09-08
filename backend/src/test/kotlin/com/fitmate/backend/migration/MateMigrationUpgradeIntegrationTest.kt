package com.fitmate.backend.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.sql.DriverManager
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "FITMATE_TEST_DB_URL", matches = ".+")
class MateMigrationUpgradeIntegrationTest {
    @Test
    fun `V13 database upgrades through mate migrations without losing existing seed data`() {
        val sourceUrl = System.getenv("FITMATE_TEST_DB_URL")
        val database = "fitmate_upgrade_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val serverUrl = sourceUrl.substringBeforeLast("/") + "/mysql"
        val user = System.getenv("FITMATE_TEST_DB_USERNAME") ?: "root"
        val password = System.getenv("FITMATE_TEST_DB_PASSWORD") ?: ""
        DriverManager.getConnection(serverUrl, user, password).use { connection ->
            connection.createStatement().use { it.execute("create database `$database` character set utf8mb4 collate utf8mb4_0900_ai_ci") }
        }
        val url = sourceUrl.substringBeforeLast("/") + "/$database" + sourceUrl.substringAfter("?", "").let { if (it.isEmpty()) "" else "?$it" }
        try {
            val v13 = flyway(url, user, password, "13")
            assertEquals(13, v13.migrate().migrationsExecuted)
            val sportsBefore = count(url, user, password, "fitness_sports")
            assertTrue(sportsBefore > 0)

            val latest = flyway(url, user, password)
            assertEquals(4, latest.migrate().migrationsExecuted)
            assertEquals(sportsBefore, count(url, user, password, "fitness_sports"))
            listOf("fitness_mate_posts", "fitness_mate_participations", "user_notifications", "fitness_mate_chat_rooms", "fitness_mate_chat_messages").forEach {
                assertEquals(1, tableExists(url, user, password, it))
            }
            assertEquals(0, latest.migrate().migrationsExecuted)
        } finally {
            DriverManager.getConnection(serverUrl, user, password).use { connection ->
                connection.createStatement().use { it.execute("drop database if exists `$database`") }
            }
        }
    }

    private fun flyway(url: String, user: String, password: String, target: String? = null) =
        Flyway.configure().dataSource(url, user, password).locations("filesystem:../db/migration").apply { target?.let { target(it) } }.load()

    private fun count(url: String, user: String, password: String, table: String): Long =
        DriverManager.getConnection(url, user, password).use { connection -> connection.createStatement().use { statement -> statement.executeQuery("select count(*) from `$table`").use { result -> result.next(); result.getLong(1) } } }

    private fun tableExists(url: String, user: String, password: String, table: String): Int =
        DriverManager.getConnection(url, user, password).use { connection -> connection.prepareStatement("select count(*) from information_schema.tables where table_schema = database() and table_name = ?").use { statement -> statement.setString(1, table); statement.executeQuery().use { result -> result.next(); result.getInt(1) } } }
}
