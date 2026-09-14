package com.fitmate.backend.recommendation.application

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.sql.DriverManager
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "FITMATE_TEST_DB_URL", matches = ".+")
class WorkoutPlanMigrationIntegrationTest {
    @Test fun `V22 routines survive additive V23 and migration is repeatable`() {
        val source = requireNotNull(System.getenv("FITMATE_TEST_DB_URL"))
        val name = "fitmate_plan_upgrade_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val server = source.substringBefore('?').substringBeforeLast('/') + "/mysql?useSSL=false&allowPublicKeyRetrieval=true"
        val url = source.substringBefore('?').substringBeforeLast('/') + "/$name?useSSL=false&allowPublicKeyRetrieval=true"
        val user = System.getenv("FITMATE_TEST_DB_USERNAME") ?: "root"
        val password = System.getenv("FITMATE_TEST_DB_PASSWORD") ?: ""
        DriverManager.getConnection(server, user, password).use { it.createStatement().use { statement -> statement.execute("CREATE DATABASE `$name` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci") } }
        try {
            fun flyway(target: String) = Flyway.configure().dataSource(url, user, password).locations("filesystem:../db/migration").target(target).load()
            assertEquals(22, flyway("22").migrate().migrationsExecuted)
            fun snapshot(): List<String> = DriverManager.getConnection(url, user, password).use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("SELECT HEX(id), name, split_count, version FROM fitness_routines ORDER BY id").use { rows ->
                        buildList { while (rows.next()) add((1..4).joinToString("|") { rows.getString(it) }) }
                    }
                }
            }
            val before = snapshot()
            assertFalse(before.isEmpty())
            assertEquals(1, flyway("23").migrate().migrationsExecuted)
            assertEquals(before, snapshot())
            assertEquals(0, flyway("23").migrate().migrationsExecuted)
            flyway("23").validate()
            DriverManager.getConnection(url, user, password).use { connection -> connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM fitness_routines WHERE recommendation_json IS NOT NULL").use { rows -> rows.next(); assertEquals(0, rows.getInt(1)) }
                statement.executeQuery("SELECT COUNT(*) FROM fitness_workout_plans").use { rows -> rows.next(); assertEquals(0, rows.getInt(1)) }
            } }
        } finally {
            // Only the random schema created by this test is removed; the configured source schema is untouched.
            DriverManager.getConnection(server, user, password).use { it.createStatement().use { statement -> statement.execute("DROP DATABASE `$name`") } }
        }
    }
}
