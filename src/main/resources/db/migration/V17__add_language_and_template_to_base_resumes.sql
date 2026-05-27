ALTER TABLE google_drive_base_resumes
    ADD COLUMN language VARCHAR(10) NULL AFTER document_name,
    ADD COLUMN is_template TINYINT(1) NOT NULL DEFAULT 0 AFTER language;
