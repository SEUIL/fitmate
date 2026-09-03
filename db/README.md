# P0 MySQL DDL

`migration/` contains Flyway-compatible MySQL migrations for the current P0 scope.

1. Run `V1__create_p0_schema.sql` to create the schema.
2. Run `V2__seed_p0_reference_data.sql` to insert only reference data.

The files require MySQL 8.0.16 or later (MySQL 8.4 LTS recommended), InnoDB, and `utf8mb4`. UUIDs are stored as `BINARY(16)` and seed values use `UUID_TO_BIN(uuid, 1)`.

The seed creates no users, passwords, tokens, facilities, or operational data. It contains required terms v1, 17 metropolitan-city/province activity regions, the sport-code catalog, 24 health exercises, substitutions, and five routine templates.

The initial DDL intentionally excludes media, notifications, mate, running, health synchronization, and withdrawal/erasure jobs. When a Spring Boot project is added, configure Flyway to load this directory or move these unchanged files to its standard migration directory.
