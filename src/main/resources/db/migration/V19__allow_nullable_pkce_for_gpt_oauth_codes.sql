ALTER TABLE gpt_oauth_authorization_codes
    MODIFY code_challenge VARCHAR(255) NULL,
    MODIFY code_challenge_method VARCHAR(20) NULL;
