package com.jobtracker.mcp;

import io.modelcontextprotocol.spec.McpSchema.CompleteRequest.CompleteArgument;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.springaicommunity.mcp.annotation.McpComplete;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Registers MCP prompt templates.
 */
@Service
@SuppressWarnings("unused")
public class McpPromptsConfig {

    /**
     * Full autonomous vacancy-intake workflow.
     */
    @McpPrompt(
            name = McpPromptNames.INTAKE_VACANCY,
            title = "Intake Vacancy",
            description = "Execute the autonomous application intake workflow from a pasted vacancy")
    public GetPromptResult intakeVacancyPrompt(
            @McpArg(name = "vacancyContent", description = "Job description, link, recruiter message, or LinkedIn post", required = true)
            String vacancyContent) {
        String text = """
                You are my software engineering application assistant. Always communicate with me in PT-BR.
                Content generated for the CV must be written in the vacancy language.

                Execute the complete workflow autonomously, without waiting for intermediate confirmations.
                Stop only for genuinely necessary questions (see Step 7).

                Before executing steps 8 and 9, read the resources:
                  %s  (application field rules)
                  %s  (mandatory CV generation sequence)
                  %s  (allowed status values)

                === VACANCY ===
                %s
                === END VACANCY ===

                MANDATORY REGISTRATION WORKFLOW (applies to this and every other application-related
                request — generating/adapting a resume, drafting an email/WhatsApp/LinkedIn message,
                contacting a recruiter, evaluating job fit, or preparing application materials):
                List-Statuses → Search by exact vacancy URL → Create-Application when absent → Continue requested workflow

                Follow this order exactly:

                STEP 1 - Analyze the vacancy
                Extract: vacancyName (title), organization (company), vacancyLink (URL if present),
                required stack, seniority, vacancy language, recruiter name/email if present.

                STEP 2 - Check for existing application (mandatory, do not skip)
                Call List-Statuses to get the valid status values. Then call List-Applications/Search-Applications
                and search for an existing application using the exact vacancy URL (vacancyLink).
                Similar vacancy names, recruiters, organizations, salaries, or technology stacks are NOT sufficient
                evidence of a duplicate. Treat the vacancy as a duplicate only when the vacancyLink is identical to
                an existing application, or when I explicitly confirm it is the same vacancy.
                If an exact-URL duplicate exists, return a message indicating that an application already exists
                and provide its UUID. Do not proceed with the workflow if a duplicate application is found.
                If the exact URL is not registered (including reposts of an otherwise similar vacancy under a new
                URL), you MUST call Create-Application before performing the requested resume, message,
                evaluation, or outreach action — never silently skip vacancy registration. This applies whether
                the requested action is generating a resume, message, evaluation, or outreach.

                STEP 3 - Read my BASE INFORMATION (TOP PRIORITY, mandatory before generating any content)
                - Call List-Base-Information and read EVERY document via Get-Base-Information-Content
                  (or resource://job-apply-tracker/base-information/{infoId}).
                - This is the AUTHORITATIVE, highest-priority source of truth about me. You MUST read it before
                  generating any CV content.
                Extract: experience, real stack, projects, education, certifications, achievements, languages.
                NEVER invent experience, technologies, projects, or certifications — ground everything in my base information.

                STEP 4 - Read my real resume (secondary/supplementary source)
                - Call List-Applications and select the most recent application with driveResumeFileId populated.
                - If such an application exists, read resource://job-apply-tracker/generated-resume/{applicationId}
                  and use it to supplement (never override) my base information.
                - If neither base information nor a prior CV exists, ask for the current CV text because this MCP does
                  not expose generic Drive search or file reading.

                STEP 5 - List and select a CV template
                Call List-Base-Resumes. Select by language field (PT→PT-BR template; EN→EN-US template).
                Do not ask if there is only one template per language.

                STEP 6 - Detect placeholders
                Call Detect-Resume-Placeholders with the baseResumeId obtained in STEP 5 (see resume-workflow-rules).
                The baseResumeId is the UUID from List-Base-Resumes — it is NOT a Google Drive fileId.

                STEP 7 - Questions
                Ask ONLY if a piece of information is missing from my base information, the real CV, AND the vacancy.
                Be it stack, seniority, projects, or even the vacancy name or organization. Always cross-check first before asking.

                STEP 8 - Generate placeholder values
                Cross-check my base information (step 3) and real CV (step 4) with the vacancy requirements (step 1).
                ATS-friendly, without inventing anything. Follow resume-workflow-rules for completeness and key formatting.

                STEP 9 - Create the application
                Follow application-creation-rules. Call Create-Application with the extracted data.
                Do NOT fill nextStepDateTime.
                Note: ATS-focused summary (stack, seniority, fit, gaps).

                STEP 10 - Generate the filled CV
                Only after Create-Application returns a valid UUID AND all placeholders are generated.
                Follow resume-workflow-rules. Call Generate-Resume and return the Google Doc link.

                STEP 11 - Final delivery (in PT-BR)
                1. Link to the generated CV
                2. Detected placeholders + generated value for each one
                3. UUID and status of the created application
               \s""".formatted(
                McpResourcesConfig.URI_APPLICATION_CREATION_RULES,
                McpResourcesConfig.URI_RESUME_WORKFLOW_RULES,
                McpResourcesConfig.URI_APPLICATION_STATUSES,
                vacancyContent);

        return new GetPromptResult(
                "Intake Vacancy",
                List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }

    @McpPrompt(
            name = McpPromptNames.PREPARE_NEW_APPLICATION,
            title = "Prepare New Application",
            description = "Guide the user through logging a new job application step-by-step")
    public GetPromptResult prepareNewApplicationPrompt(
            @McpArg(name = "vacancyName", description = "The job title or vacancy name, e.g. 'Backend Engineer'", required = false)
            String vacancyName,
            @McpArg(name = "recruiterName", description = "Recruiter's full name if known", required = false)
            String recruiterName,
            @McpArg(name = "organization", description = "Company or organization name", required = false)
            String organization) {
        String text = """
                You are helping me log a new job application in my tracker.

                Known details so far:
                - Vacancy: %s
                - Recruiter: %s
                - Organization: %s

                Read %s for field defaults before calling Create-Application. Ask me for any missing
                required fields (rhAcceptedConnection, interviewScheduled, recruiterDmReminderEnabled),
                then call Create-Application with the complete data. Use status "RH" for a standard
                LinkedIn/HR cold outreach.
                """.formatted(
                valueOrDefault(vacancyName),
                valueOrDefault(recruiterName),
                valueOrDefault(organization),
                McpResourcesConfig.URI_APPLICATION_CREATION_RULES);

        return new GetPromptResult(
                "Prepare-New-Application: " + valueOrDefault(vacancyName),
                List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }

    @McpComplete(prompt = McpPromptNames.INTAKE_VACANCY)
    public List<String> completeIntakeVacancy(CompleteArgument argument) {
        return List.of();
    }

    @McpComplete(prompt = McpPromptNames.PREPARE_NEW_APPLICATION)
    public List<String> completePrepareNewApplication(CompleteArgument argument) {
        if (argument == null || argument.name() == null) {
            return List.of();
        }
        if (!"vacancyName".equals(argument.name())) {
            return List.of();
        }
        String prefix = argument.value() == null ? "" : argument.value().toLowerCase();
        return List.of("Backend Engineer", "Full Stack Engineer", "Software Engineer", "Data Engineer")
                .stream()
                .filter(candidate -> candidate.toLowerCase().startsWith(prefix))
                .toList();
    }

    @McpComplete(prompt = McpPromptNames.TAILOR_RESUME)
    public List<String> completeTailorResume(CompleteArgument argument) {
        return List.of();
    }

    @McpComplete(prompt = McpPromptNames.SUMMARIZE_PIPELINE)
    public List<String> completeSummarizePipeline(CompleteArgument argument) {
        return List.of();
    }

    @McpComplete(prompt = McpPromptNames.ANALYZE_JOB_SEARCH)
    public List<String> completeAnalyzeJobSearch(CompleteArgument argument) {
        return List.of();
    }

    @McpPrompt(
            name = McpPromptNames.TAILOR_RESUME,
            title = "Tailor Resume",
            description = "Generate a tailored resume for a specific application using Google Drive")
    public GetPromptResult tailorResumePrompt(
            @McpArg(name = "applicationId", description = "UUID of the target job application", required = true)
            String applicationId) {
        String text = """
                I want to tailor a resume for job application ID: %s

                1. Call `Get-Application` with id="%s" to see the vacancy name, organization, and language context.
                   Before writing any tailored content, read my base information (`List-Base-Information` →
                   `Get-Base-Information-Content`) as the authoritative source of truth about me.
                2. Call `List-Base-Resumes` to see all available resume templates with their UUIDs and language codes.
                3. Select the template matching the vacancy language (PT → PT-BR, EN → EN-US).
                   Ask me which template to use only if the choice is ambiguous.
                4. Call `Copy-Resume-To-Application` with the applicationId and the chosen baseResumeId (UUID from List-Base-Resumes).
                5. Return the Google Docs link from the response so I can start editing.
                """.formatted(applicationId, applicationId);

        return new GetPromptResult(
                "Tailor-Resume for Application " + applicationId,
                List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }

    @McpPrompt(
            name = McpPromptNames.SUMMARIZE_PIPELINE,
            title = "Summarize Pipeline",
            description = "Produce a human-readable summary of the current job search pipeline")
    public GetPromptResult summarizePipelinePrompt() {
        String text = """
                Please summarize my current job search pipeline:

                1. Read `resource://job-apply-tracker/pipeline-summary` for aggregate statistics.
                2. Call `List-Applications` (page=0, size=10, sort=createdAt,desc) for recent applications.
                3. Call `Get-Overdue-Applications` to identify follow-ups needing immediate action.
                4. Read `resource://job-apply-tracker/gamification-profile` to include level and XP.

                Report: total applications, status breakdown, interview count, overdue follow-ups,
                daily/weekly rate, gamification level, XP, and streak.
                """;

        return new GetPromptResult(
                "Summarize-Pipeline",
                List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }

    @McpPrompt(
            name = McpPromptNames.ANALYZE_JOB_SEARCH,
            title = "Analyze Job Search",
            description = "Provides a comprehensive analysis of the user's job search performance with actionable insights")
    public GetPromptResult analyzeJobSearchPrompt() {
        String text = """
                Please perform a comprehensive analysis of my job search performance. Communicate in PT-BR.

                Execute these steps in order:
                1. Call `Get-Analytics` (no date filters) to retrieve overall statistics.
                2. Call `Get-Weekly-Summary` to get week-over-week trends.
                3. Call `Get-Applications-By-Organization` to see pipeline distribution across companies.

                Based on the collected data, produce a structured report with the following sections:

                **Taxa de entrevista geral**
                Report the overall interview conversion rate (interviewRate). Compare it to a healthy benchmark
                of 15–25%. Flag if significantly below or above.

                **Comparação semanal**
                Compare thisWeekApplications vs lastWeekApplications, including weekOverWeekDelta.
                Highlight whether application volume is growing, declining, or stable.
                Include thisWeekInterviews and overdueCount with recommended follow-up actions.

                **Empresas sem resposta**
                From the organization breakdown, list companies with more than 1 application where
                hasInterview is false and the latest status suggests an early stage (e.g., RH or
                Aguardando Atualização). These warrant follow-up or deprioritization.

                **Plataformas com melhor/pior conversão**
                Only if platformBreakdown contains data: identify which platforms appear most frequently
                and correlate with interview outcomes. Recommend which platforms deserve more focus.
                Skip this section if no platform data is available.

                **Recomendações acionáveis**
                Based on the data above, provide 2–3 concrete and specific recommendations, such as:
                follow up with named companies, increase weekly application volume, diversify platforms,
                or adjust target role criteria. Be direct and data-driven.
                """;

        return new GetPromptResult(
                "Analyze-Job-Search",
                List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }

    private static String valueOrDefault(String value) {
        return value == null || value.isBlank() ? "(not yet informed)" : value;
    }
}
