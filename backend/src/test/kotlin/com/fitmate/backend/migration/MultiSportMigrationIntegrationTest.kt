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
            seedLegacyHealthAndRunning(url, user, password)
            val latest = flyway(url, user, password, "27")
            assertEquals(7, latest.migrate().migrationsExecuted)
            assertEquals(1, exists(url, user, password, "tables", "table_name", "fitness_sport_records"))
            assertEquals("HEALTH|COMPLETED|2026-09-14 01:00:00.123", session(url, user, password, "10000000-0000-0000-0000-000000000001"))
            assertEquals("RUNNING|COMPLETED|120|0", running(url, user, password, "10000000-0000-0000-0000-000000000002"))
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
    private fun seedLegacyHealthAndRunning(url: String, user: String, password: String) = DriverManager.getConnection(url, user, password).use { c -> c.createStatement().use { s ->
        s.executeUpdate("insert into core_users (id,email,email_normalized,password_hash,account_status,auth_version,created_at,updated_at) values (uuid_to_bin('10000000-0000-0000-0000-000000000000', 1),'legacy@example.com','legacy@example.com','hash','ACTIVE',0,'2026-09-14 00:00:00.000','2026-09-14 00:00:00.000')")
        s.executeUpdate("insert into fitness_workout_sessions (id,user_id,sport_code,status,started_at,ended_at,client_updated_at,version,created_at,updated_at) values (uuid_to_bin('10000000-0000-0000-0000-000000000001',1),uuid_to_bin('10000000-0000-0000-0000-000000000000',1),'HEALTH','COMPLETED','2026-09-14 01:00:00.123','2026-09-14 01:02:00.123','2026-09-14 01:02:00.123',0,'2026-09-14 01:02:00.123','2026-09-14 01:02:00.123')")
        s.executeUpdate("insert into fitness_workout_sessions (id,user_id,sport_code,status,started_at,ended_at,client_updated_at,version,created_at,updated_at) values (uuid_to_bin('10000000-0000-0000-0000-000000000002',1),uuid_to_bin('10000000-0000-0000-0000-000000000000',1),'RUNNING','COMPLETED','2026-09-14 02:00:00.123','2026-09-14 02:02:00.123','2026-09-14 02:02:00.123',0,'2026-09-14 02:02:00.123','2026-09-14 02:02:00.123')")
        s.executeUpdate("insert into fitness_running_records (workout_session_id,run_type,route_visibility,auto_pause_enabled,active_duration_seconds,total_distance_meters,version,created_at,updated_at) values (uuid_to_bin('10000000-0000-0000-0000-000000000002',1),'FREE','PRIVATE',false,120,0,0,'2026-09-14 02:02:00.123','2026-09-14 02:02:00.123')")
    } }
    private fun session(url: String, user: String, password: String, id: String) = DriverManager.getConnection(url, user, password).use { c -> c.createStatement().use { s -> s.executeQuery("select concat(sport_code, '|', status, '|', date_format(started_at, '%Y-%m-%d %H:%i:%s.%f')) from fitness_workout_sessions where id = uuid_to_bin('$id', 1)").use { r -> r.next(); r.getString(1).dropLast(3) } } }
    private fun running(url: String, user: String, password: String, id: String) = DriverManager.getConnection(url, user, password).use { c -> c.createStatement().use { s -> s.executeQuery("select concat(w.sport_code, '|', w.status, '|', r.active_duration_seconds, '|', r.total_distance_meters) from fitness_workout_sessions w join fitness_running_records r on r.workout_session_id = w.id where w.id = uuid_to_bin('$id', 1)").use { r -> r.next(); r.getString(1) } } }
}
