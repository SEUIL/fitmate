# P0 MySQL DDL

`migration/` contains Flyway-compatible MySQL migrations for the implemented P0 scope and P1 mate MVP.

1. Run `V1__create_p0_schema.sql` to create the schema.
2. Run `V2__seed_p0_reference_data.sql` to insert only reference data.
3. Run `V3__create_notification_settings.sql` to create user notification preferences.
4. Run `V4__create_user_custom_sports.sql` to store profile-only custom interests, unique by user and normalized name. These do not modify the managed sport catalog or proficiency selections.
5. Run `V5__activate_running_sport.sql` to expose `HEALTH` and `RUNNING` as the two base solo-workout sports. Running record persistence is added separately.
6. Run `V6__create_mate_posts_and_participations.sql` to create mate posts and participation history.
7. Run `V7__create_in_app_notifications.sql` to create in-app notification storage.
8. Run `V8__create_mate_inquiry_chat.sql` to create one-to-one mate inquiry rooms and messages.
9. Run `V9__add_mate_post_list_index.sql` to add the status, creation-time, and ID list index used by cursor pagination.

The files require MySQL 8.0.16 or later (MySQL 8.4 LTS recommended), InnoDB, and `utf8mb4`. UUIDs are stored as `BINARY(16)` and seed values use `UUID_TO_BIN(uuid, 1)`.

The seed creates no users, passwords, tokens, facilities, or operational data. It contains required terms v1, 17 metropolitan-city/province activity regions, the sport-code catalog, 24 health exercises, substitutions, and five routine templates.

The current migrations intentionally exclude media, running persistence, health synchronization, and withdrawal/erasure jobs. Mate inquiry chat is distinct from the excluded general DM and group-chat scopes.
