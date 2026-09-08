ALTER TABLE core_user_profiles
    ADD COLUMN profile_image_media_id BINARY(16) NULL,
    ADD CONSTRAINT fk_core_user_profiles_profile_image
        FOREIGN KEY (profile_image_media_id) REFERENCES media_assets (id);
