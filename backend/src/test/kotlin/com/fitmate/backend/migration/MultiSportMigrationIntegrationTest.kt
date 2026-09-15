package com.fitmate.backend.migration

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.sql.DriverManager
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "FITMATE_TEST_DB_URL", matches = ".+")
class MultiSportMigrationIntegrationTest {
    @Test fun `V20 upgrades to V27 and empty database reaches V27`() {
        withDatabase { url, user, password ->
            assertEquals(20, flyway(url, user, password, "20").migrate().migrationsExecuted)
            val latest = flyway(url, user, password, "27")
            assertEquals(7, latest.migrate().migrationsExecuted)
            assertEquals(1, exists(url, user, password, "tables", "table_name", "fitness_sport_records"))
            assertEquals(0, latest.migrate().migrationsExecuted)
        }
        withDatabase { url, user, password ->
            val latest = flyway(url, user, password, "27")
            assertEquals(27, latest.migrate().migrationsExecuted)
            assertEquals(4, activeSports(url, user, password))
            assertEquals(0, latest.migrate().migrationsExecuted)
        }
    }
    private fun withDatabase(block: (String, String, String) -> Unit) {
        val source = requireNotNull(System.getenv("FITMATE_TEST_DB_URL")); val db = "fitmate_sport_${UUID.randomUUID().toString().replace("-", "").take(12)}"; val server = source.substringBeforeLast("/") + "/mysql"
        val user = System.getenv("FITMATE_TEST_DB_USERNAME") ?: "root"; val password = System.getenv("FITMATE_TEST_DB_PASSWORD") ?: ""
        DriverManager.getConnection(server, user, password).use { it.createStatement().use { s -> s.execute("create database `$db` character set utf8mb4 collate utf8mb4_0900_ai_ci") } }
        val url = source.substringBeforeLast("/").substringBefore("?") + "/$db" + source.substringAfter("?", "").let { if (it.isEmpty()) "" else "?$it" }
        try { block(url, user, password) } finally { DriverManager.getConnection(server, user, password).use { it.createStatement().use { s -> s.execute("drop database if exists `$db`") } } }
    }
    private fun flyway(url: String, user: String, password: String, target: String) = Flyway.configure().dataSource(url, user, password).locations("filesystem:../db/migration").target(target).load()
    private fun exists(url: String, user: String, password: String, collection: String, key: String, value: String) = DriverManager.getConnection(url, user, password).use { c -> c.prepareStatement("select count(*) from information_schema.$collection where table_schema = database() and $key = ?").use { s -> s.setString(1, value); s.executeQuery().use { r -> r.next(); r.getInt(1) } } }
    private fun activeSports(url: String, user: String, password: String) = DriverManager.getConnection(url, user, password).use { c -> c.createStatement().use { s -> s.executeQuery("select count(*) from fitness_sports where is_active = true and code in ('BADMINTON','CLIMBING','HIKING','GOLF')").use { r -> r.next(); r.getInt(1) } } }
}
