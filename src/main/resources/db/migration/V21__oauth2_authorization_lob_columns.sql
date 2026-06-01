ALTER TABLE oauth2_authorization
    MODIFY attributes LONGBLOB DEFAULT NULL,
    MODIFY authorization_code_value LONGBLOB DEFAULT NULL,
    MODIFY authorization_code_metadata LONGBLOB DEFAULT NULL,
    MODIFY access_token_value LONGBLOB DEFAULT NULL,
    MODIFY access_token_metadata LONGBLOB DEFAULT NULL,
    MODIFY oidc_id_token_value LONGBLOB DEFAULT NULL,
    MODIFY oidc_id_token_metadata LONGBLOB DEFAULT NULL,
    MODIFY refresh_token_value LONGBLOB DEFAULT NULL,
    MODIFY refresh_token_metadata LONGBLOB DEFAULT NULL,
    MODIFY user_code_value LONGBLOB DEFAULT NULL,
    MODIFY user_code_metadata LONGBLOB DEFAULT NULL,
    MODIFY device_code_value LONGBLOB DEFAULT NULL,
    MODIFY device_code_metadata LONGBLOB DEFAULT NULL;
