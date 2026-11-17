-- Phase 6: onboarding state and consent for health data.

-- Allergies are health data (GDPR art. 9): stored only after explicit consent, given on the site
-- when the person authorizes an AI. Withdrawing consent clears them.
alter table person_profile
    add column health_data_consent_at timestamptz;

-- The database itself refuses allergies without consent, whatever the Java code does.
alter table person_profile
    add constraint person_profile_allergies_need_consent
        check (jsonb_array_length(allergies) = 0 or health_data_consent_at is not null);

-- An empty list cannot tell "not asked yet" from "nothing to report": the household interview
-- is marked done explicitly, like the personal one (person_profile.onboarding_completed_at).
alter table household_profile
    add column onboarding_completed_at timestamptz;
