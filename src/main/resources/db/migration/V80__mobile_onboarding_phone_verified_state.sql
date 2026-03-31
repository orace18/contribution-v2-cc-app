ALTER TABLE mobile_onboarding_journeys
    DROP CONSTRAINT IF EXISTS ck_mobile_onboarding_journeys_status;

ALTER TABLE mobile_onboarding_journeys
    ADD CONSTRAINT ck_mobile_onboarding_journeys_status
        CHECK (status IN (
            'REGISTERED',
            'AUTHENTICATED',
            'COUNTRY_SELECTED',
            'SPONSOR_SET',
            'PLAN_SELECTED',
            'PAYMENT_PENDING',
            'PAYMENT_CONFIRMED',
            'ACTIVE_MEMBER',
            'PHONE_VERIFIED',
            'WALLET_ACTIVATED',
            'ONBOARDING_COMPLETED',
            'NETWORK_LINKED'
        ));
