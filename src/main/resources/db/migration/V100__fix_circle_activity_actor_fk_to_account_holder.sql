-- circle_activity.actor_user_id has always stored an account_holder_id.
-- The original FK pointing to users(id) was a schema error introduced in V6.
-- Since the removal of mandatory app_user prerequisites for circle creation,
-- the mismatch now causes a 500 on every circle creation or join for members
-- who have no entry in the users table.
--
-- This migration fixes the FK anchor to account_holders(id), which is the
-- canonical business identity for the circle domain.
-- No data migration needed: the stored values are already account_holder_ids.

ALTER TABLE circle_activity
    DROP CONSTRAINT IF EXISTS fk_circle_activity_actor;

ALTER TABLE circle_activity
    ADD CONSTRAINT fk_circle_activity_actor
        FOREIGN KEY (actor_user_id) REFERENCES account_holders(id);
