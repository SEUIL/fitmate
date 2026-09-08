# P0 MySQL DDL

`migration/` contains Flyway-compatible MySQL migrations for the implemented P0 scope and P1 mate MVP.

1. Run `V1__create_p0_schema.sql` to create the schema.
2. Run `V2__seed_p0_reference_data.sql` to insert only reference data.
3. Run `V3__create_notification_settings.sql` to create user notification preferences.
4. Run `V4__create_user_custom_sports.sql` to store profile-only custom interests, unique by user and normalized name.
5. Run `V5__activate_running_sport.sql` to expose `HEALTH` and `RUNNING` as the two base solo-workout sports.
6. Run `V6__seed_legal_dong_regions.sql` to add active legal-dong hierarchy while retaining existing IDs.
7. Run `V7__add_routine_split_cycle.sql` to add user-defined 1-10 day split routines and session split snapshots.
8. Run `V8__create_running_records.sql` to store completed running summaries, laps, optional route points, goals, condition, and memo.
9. Run `V9` through `V13` for social relationships, media, community, direct messages, notifications, reports, and profile images.
10. Run `V14__create_mate_posts_and_participations.sql` to create mate posts and participation history.
11. Run `V15__extend_user_notifications_for_mate.sql` to add idempotent mate events to the existing in-app notification table.
12. Run `V16__create_mate_inquiry_chat.sql` to create one-to-one mate inquiry rooms and messages.
13. Run `V17__add_mate_post_list_index.sql` to add the status, creation-time, and ID list index used by cursor pagination.

Regenerate V6 only before it is deployed: `./generate-region-migration.ps1 -Archive <official-code-export.zip> -Output migration/V6__seed_legal_dong_regions.sql`. Its header records the source checksum. After deployment, use a new migration for code changes.

The files require MySQL 8.0.16 or later (MySQL 8.4 LTS recommended), InnoDB, and `utf8mb4`. UUIDs are stored as `BINARY(16)` and seed values use `UUID_TO_BIN(uuid, 1)`.

The seed creates no users, passwords, tokens, facilities, or operational data. It contains required terms v1, activity regions, the sport-code catalog, health exercises, substitutions, and routine templates.

The current migrations intentionally exclude health synchronization, withdrawal/erasure jobs, Push delivery, and group chat. Running record persistence is introduced by V8; mate inquiry chat is distinct from general DM and group-chat scopes. When a Spring Boot project is added, configure Flyway to load this directory or move these unchanged files to its standard migration directory.
