-- Add Firebase UID column to sys_user table
-- This column stores the Firebase Authentication User UID for users who authenticate via Firebase

ALTER TABLE sys_user ADD COLUMN firebase_uid VARCHAR(128) NULL COMMENT 'Firebase Authentication User UID';

-- Create unique index for Firebase UID lookups
CREATE UNIQUE INDEX idx_sys_user_firebase_uid ON sys_user (firebase_uid);

-- Note: Run this migration on your database before enabling Firebase authentication
