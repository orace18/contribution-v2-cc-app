-- V99: Fix FK on operations.user_id
--
-- Root cause: new provisioning users (UserProvisioningService.provisionNew) are created
-- in app_users only, never in the legacy users table. The FK fk_operations_user
-- (user_id → users.id) therefore fails for every membership payment made by a
-- new-provisioning account holder.
--
-- Safe migration: V9 already seeded the entire users table into app_users, so every
-- existing operations.user_id value is present in app_users. The FK target can be
-- switched from users to app_users without touching any row data.

ALTER TABLE operations DROP CONSTRAINT fk_operations_user;

ALTER TABLE operations
    ADD CONSTRAINT fk_operations_user FOREIGN KEY (user_id) REFERENCES app_users(id);
