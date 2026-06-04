package com.jobtracker.mcp;

import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
import io.modelcontextprotocol.spec.McpSchema.PromptMessage;
import io.modelcontextprotocol.spec.McpSchema.Role;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.springaicommunity.mcp.annotation.McpArg;
import org.springaicommunity.mcp.annotation.McpPrompt;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Registers MCP prompt templates.
 */
@Service
public class McpPromptsConfig {

    /**
     * Full autonomous vacancy-intake workflow.
     */
    @McpPrompt(name = "intake_vacancy", description = "Executa o workflow autonomo completo de candidatura a partir de uma vaga colada")
    public GetPromptResult intakeVacancyPrompt(
            @McpArg(name = "vacancyContent", description = "Descricao da vaga, link, mensagem do recrutador ou post do LinkedIn", required = true)
            String vacancyContent) {
        String text = """
                Voce e meu assistente de candidaturas para engenharia de software, operando via os MCPs
                "Job Apply Tracker - MCP", "Google Drive" e "Gmail". Fale SEMPRE comigo em PT-BR.
                Conteudo gerado para o CV deve estar no IDIOMA DA VAGA.

                Execute o workflow completo de forma AUTONOMA, sem esperar confirmacoes intermediarias.
                Pare apenas para perguntas genuinamente necessarias (ver Passo 5).

                Antes de executar os passos 7 e 8, leia os recursos:
                  %s  (regras de campo da candidatura)
                  %s  (sequencia obrigatoria de geracao de CV)

                === VAGA ===
                %s
                === FIM DA VAGA ===

                Siga rigorosamente, nesta ordem:

                PASSO 1 - Analisar a vaga
                Extrair: vacancyName (cargo), organization (empresa), vacancyLink (URL se houver),
                stack exigida, senioridade, idioma da vaga, nome/email do recrutador se presentes.

                PASSO 2 - Ler meu curriculo REAL (obrigatorio antes de gerar qualquer conteudo)
                - Chamar listApplications e selecionar o fileId de um CV JA GERADO no MESMO idioma da vaga.
                  NUNCA usar um template como fonte de dados.
                - Ler com Google Drive:read_file_content.
                - Se nao houver CV anterior: Google Drive:search_files com
                  "title contains 'curriculo' or title contains 'resume' or title contains 'cv'".
                - Se ainda nao encontrar: pedir o nome/link do arquivo de CV no Drive.
                Extrair: experiencias, stack real, projetos, formacao, certificacoes, conquistas, idiomas.
                NUNCA inventar experiencias, tecnologias, projetos ou certificacoes.

                PASSO 3 - Listar e selecionar template de CV
                Chamar listBaseResumes. Selecionar por idioma (PT→PT-BR; EN→EN-US). Nao perguntar se houver so um por idioma.

                PASSO 4 - Detectar placeholders
                Chamar detectResumePlaceholders com o baseResumeId selecionado (ver resume-workflow-rules).

                PASSO 5 - Perguntas minimas (so se necessario)
                Perguntar SOMENTE se uma informacao estiver ausente no CV real E na vaga, e for necessaria
                para um placeholder ou o registro. Nunca perguntar sobre tecnologias ou background.

                PASSO 6 - Gerar valores dos placeholders
                Cruzar CV real (passo 2) com requisitos da vaga (passo 1). ATS-friendly, sem inventar.
                Seguir resume-workflow-rules para completude e formatacao de chaves.

                PASSO 7 - Criar a candidatura
                Seguir application-creation-rules. Chamar createApplication com os dados extraidos.
                NAO preencher nextStepDateTime.
                note: resumo ATS-focused (stack, senioridade, fit, gaps).

                PASSO 8 - Gerar o CV preenchido
                Somente apos createApplication retornar um UUID valido E todos os placeholders gerados.
                Seguir resume-workflow-rules. Chamar generateResume e retornar o link do Google Doc.

                PASSO 9 - Rascunho de email (se houver email de contato)
                Email profissional conciso (max 150 palavras), baseado no CV real.
                Chamar Gmail:create_draft automaticamente, sem perguntar.

                PASSO 10 - Entrega final (em PT-BR)
                1. Link do CV gerado
                2. Placeholders detectados + valor gerado para cada um
                3. Confirmacao do rascunho de email (se gerado)
                4. UUID e status da candidatura criada
                """.formatted(
                McpResourcesConfig.URI_APPLICATION_CREATION_RULES,
                McpResourcesConfig.URI_RESUME_WORKFLOW_RULES,
                vacancyContent);

        return new GetPromptResult(
                "Intake de vaga (workflow autonomo completo)",
                List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }

    @McpPrompt(name = "prepare_new_application", description = "Guides the user through logging a new job application step-by-step")
    public GetPromptResult prepareNewApplicationPrompt(
            @McpArg(name = "vacancyName", description = "The job title or vacancy name, e.g. 'Backend Engineer'", required = false)
            String vacancyName,
            @McpArg(name = "recruiterName", description = "Recruiter's full name if known", required = false)
            String recruiterName,
            @McpArg(name = "organization", description = "Company or organisation name", required = false)
            String organization) {
        String text = """
                You are helping me log a new job application in my tracker.

                Known details so far:
                - Vacancy: %s
                - Recruiter: %s
                - Organisation: %s

                Read resource://job-tracker/application-creation-rules for field defaults before calling
                createApplication. Ask me for any missing required fields (rhAcceptedConnection,
                interviewScheduled, recruiterDmReminderEnabled), then call createApplication with the
                complete data. Use status "RH" for a standard LinkedIn/HR cold outreach.
                """.formatted(
                valueOrDefault(vacancyName),
                valueOrDefault(recruiterName),
                valueOrDefault(organization));

        return new GetPromptResult(
                "Prepare new application: " + valueOrDefault(vacancyName),
                List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }

    @McpPrompt(name = "tailor_resume", description = "Generates a tailored resume for a specific application using Google Drive")
    public GetPromptResult tailorResumePrompt(
            @McpArg(name = "applicationId", description = "UUID of the target job application", required = true)
            String applicationId) {
        String text = """
                I want to tailor a resume for job application ID: %s

                1. Call `getApplication` with id="%s" to see the vacancy name and organisation.
                2. Call `listBaseResumes` to see available resume templates.
                3. Ask me which base resume template to use if more than one exists.
                4. Call `copyResumeToApplication` with the applicationId and the chosen baseResumeId.
                5. Return the Google Docs link from the response so I can start editing.
                """.formatted(applicationId, applicationId);

        return new GetPromptResult(
                "Tailor resume for application " + applicationId,
                List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }

    @McpPrompt(name = "summarize_pipeline", description = "Produces a human-readable summary of the current job search pipeline")
    public GetPromptResult summarizePipelinePrompt() {
        String text = """
                Please summarise my current job search pipeline:

                1. Call `getPipelineSummary` for aggregate statistics.
                2. Call `listApplications` (page=0, size=10, sort=createdAt,desc) for recent applications.
                3. Call `getOverdueApplications` to identify follow-ups needing immediate action.
                4. Call `getGamificationProfile` to include level and XP.

                Report: total applications, status breakdown, interview count, overdue follow-ups,
                daily/weekly rate, gamification level, XP, and streak.
                """;

        return new GetPromptResult(
                "Pipeline summary",
                List.of(new PromptMessage(Role.USER, new TextContent(text))));
    }

    private static String valueOrDefault(String value) {
        return value == null || value.isBlank() ? "(not yet informed)" : value;
    }
}
