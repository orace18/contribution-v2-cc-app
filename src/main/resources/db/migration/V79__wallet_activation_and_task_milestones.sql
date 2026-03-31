ALTER TABLE mobile_onboarding_journeys
    DROP CONSTRAINT IF EXISTS ck_mobile_onboarding_journeys_status;

ALTER TABLE mobile_onboarding_journeys
    ADD CONSTRAINT ck_mobile_onboarding_journeys_status
        CHECK (status IN (
            'REGISTERED',
            'AUTHENTICATED',
            'WALLET_ACTIVATED',
            'COUNTRY_SELECTED',
            'SPONSOR_SET',
            'PLAN_SELECTED',
            'PAYMENT_PENDING',
            'PAYMENT_CONFIRMED',
            'ACTIVE_MEMBER',
            'NETWORK_LINKED',
            'ONBOARDING_COMPLETED'
        ));

ALTER TABLE user_progress
    DROP CONSTRAINT IF EXISTS ck_user_progress_current_step;

ALTER TABLE user_progress
    DROP CONSTRAINT IF EXISTS ck_user_progress_next_task;

ALTER TABLE user_progress
    ADD CONSTRAINT ck_user_progress_current_step CHECK (
        current_step IS NULL OR current_step IN (
            'COMPLETE_KYC',
            'FUND_WALLET',
            'JOIN_CIRCLE',
            'MAKE_FIRST_CONTRIBUTION',
            'INVITE_FRIENDS',
            'INVITE_MEMBER',
            'APPLY_LOAN',
            'REPAY_INSTALLMENT',
            'COMPLETE_PROFILE',
            'VERIFY_PHONE',
            'PHONE_VERIFIED',
            'KYC_COMPLETED',
            'CIRCLE_JOINED',
            'TRAINING_COMPLETED',
            'PROJECT_SUBMITTED',
            'PERSONAL_CONTRIBUTION_PAID'
        )
    );

ALTER TABLE user_progress
    ADD CONSTRAINT ck_user_progress_next_task CHECK (
        next_task IS NULL OR next_task IN (
            'COMPLETE_KYC',
            'FUND_WALLET',
            'JOIN_CIRCLE',
            'MAKE_FIRST_CONTRIBUTION',
            'INVITE_FRIENDS',
            'INVITE_MEMBER',
            'APPLY_LOAN',
            'REPAY_INSTALLMENT',
            'COMPLETE_PROFILE',
            'VERIFY_PHONE',
            'PHONE_VERIFIED',
            'KYC_COMPLETED',
            'CIRCLE_JOINED',
            'TRAINING_COMPLETED',
            'PROJECT_SUBMITTED',
            'PERSONAL_CONTRIBUTION_PAID'
        )
    );

ALTER TABLE user_tasks
    DROP CONSTRAINT IF EXISTS ck_user_tasks_type;

ALTER TABLE user_tasks
    ADD CONSTRAINT ck_user_tasks_type CHECK (
        task_type IN (
            'COMPLETE_KYC',
            'FUND_WALLET',
            'JOIN_CIRCLE',
            'MAKE_FIRST_CONTRIBUTION',
            'INVITE_FRIENDS',
            'INVITE_MEMBER',
            'APPLY_LOAN',
            'REPAY_INSTALLMENT',
            'COMPLETE_PROFILE',
            'VERIFY_PHONE',
            'PHONE_VERIFIED',
            'KYC_COMPLETED',
            'CIRCLE_JOINED',
            'TRAINING_COMPLETED',
            'PROJECT_SUBMITTED',
            'PERSONAL_CONTRIBUTION_PAID'
        )
    );

INSERT INTO task_definitions (id, task_type, title_template, description_template, trigger_event, action_json, reward_points, is_active, metadata_json)
VALUES
    ('c1f6d8b8-b5ce-4cd8-829a-5f02a4f91777', 'FUND_WALLET', 'Approvisionne ton wallet', 'Ajoute des fonds pour debloquer les prochaines actions.', 'WALLET_FUNDED', '{"type":"FLOW","flow":"WALLET_FUNDING_FLOW"}', 45, TRUE, '{"basePriority":625}'),
    ('5ab2e8e0-16a4-4ed8-a425-fd0d6d412b61', 'INVITE_FRIENDS', 'Invite un ami', 'Partage ton cercle avec un proche pour avancer ensemble.', 'FRIEND_INVITED', '{"type":"FLOW","flow":"INVITE_FLOW"}', 25, TRUE, '{"basePriority":360}')
ON CONFLICT (task_type) DO NOTHING;
