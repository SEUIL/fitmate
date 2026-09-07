# P0 MySQL DDL

`migration/` contains Flyway-compatible MySQL migrations for the current P0 scope.

1. Run `V1__create_p0_schema.sql` to create the schema.
2. Run `V2__seed_p0_reference_data.sql` to insert only reference data.
3. Run `V3__create_notification_settings.sql` to create user notification preferences.
4. Run `V4__create_user_custom_sports.sql` to store profile-only custom interests, unique by user and normalized name. These do not modify the managed sport catalog or proficiency selections.
5. Run `V5__activate_running_sport.sql` to expose `HEALTH` and `RUNNING` as the two base solo-workout sports. Running record persistence is added separately.
6. Run `V6__seed_legal_dong_regions.sql` to add active legal-dong hierarchy (province, city/county/district, town) while retaining existing IDs. The migration uses the official code.go.kr active code export downloaded on 2026-09-07, not an invented regional catalog. Abolished provinces are deactivated, not deleted.
7. Run `V7__add_routine_split_cycle.sql` to add user-defined 1-10 day split routines and snapshot each routine session's cumulative workout day and cyclic split day.

Regenerate V6 only before it is deployed: `./generate-region-migration.ps1 -Archive <official-code-export.zip> -Output migration/V6__seed_legal_dong_regions.sql`. Its header records the source checksum. After deployment, use a new migration for code changes. The current source contains the new 전남광주통합특별시 entry; do not assume the old fixed list of 17 provinces.

The files require MySQL 8.0.16 or later (MySQL 8.4 LTS recommended), InnoDB, and `utf8mb4`. UUIDs are stored as `BINARY(16)` and seed values use `UUID_TO_BIN(uuid, 1)`.

The seed creates no users, passwords, tokens, facilities, or operational data. It contains required terms v1, 17 metropolitan-city/province activity regions, the sport-code catalog, 24 health exercises, substitutions, and five routine templates.

The initial DDL intentionally excludes media, notifications, mate, running, health synchronization, and withdrawal/erasure jobs. When a Spring Boot project is added, configure Flyway to load this directory or move these unchanged files to its standard migration directory.
