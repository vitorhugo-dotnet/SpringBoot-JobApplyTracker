ALTER TABLE oauth2_authorization
    MODIFY COLUMN attributes LONGTEXT,
    MODIFY COLUMN authorization_code_metadata LONGTEXT,
    MODIFY COLUMN access_token_metadata LONGTEXT,
    MODIFY COLUMN oidc_id_token_metadata LONGTEXT,
    MODIFY COLUMN refresh_token_metadata LONGTEXT;
