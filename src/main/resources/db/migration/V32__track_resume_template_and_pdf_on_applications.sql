ALTER TABLE job_applications
    ADD COLUMN drive_resume_template_id BINARY(16) NULL AFTER drive_resume_generated_at,
    ADD COLUMN drive_resume_pdf_file_id VARCHAR(255) NULL AFTER drive_resume_template_id,
    ADD COLUMN drive_resume_pdf_url VARCHAR(2048) NULL AFTER drive_resume_pdf_file_id;
