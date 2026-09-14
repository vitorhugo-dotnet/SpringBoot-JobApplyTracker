-- Ask ApplyWell lexical retrieval. Platform remains a structured filter.
ALTER TABLE job_applications
    ADD FULLTEXT INDEX ft_job_applications_assistant
        (vacancy_name, organization, recruiter_name, note);
