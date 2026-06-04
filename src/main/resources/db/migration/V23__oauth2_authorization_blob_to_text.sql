ALTER TABLE oauth2_authorization
    MODIFY authorization_code_value      LONGTEXT NULL,
    MODIFY authorization_code_metadata   LONGTEXT NULL,
    MODIFY access_token_value            LONGTEXT NULL,
    MODIFY access_token_metadata         LONGTEXT NULL,
    MODIFY oidc_id_token_value           LONGTEXT NULL,
    MODIFY oidc_id_token_metadata        LONGTEXT NULL,
    MODIFY refresh_token_value           LONGTEXT NULL,
    MODIFY refresh_token_metadata        LONGTEXT NULL,
    MODIFY user_code_value               LONGTEXT NULL,
    MODIFY user_code_metadata            LONGTEXT NULL,
    MODIFY device_code_value             LONGTEXT NULL,
    MODIFY device_code_metadata          LONGTEXT NULL;