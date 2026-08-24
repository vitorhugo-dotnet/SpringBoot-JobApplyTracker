package com.jobtracker.controller;

import com.jobtracker.dto.github.GitHubProfileResponse;
import com.jobtracker.service.GitHubProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "GitHub", description = "GitHub account reference used by the Developer Tools screen")
@RestController
@RequestMapping("/api/v1/github")
public class GitHubController {

    private final GitHubProfileService gitHubProfileService;

    public GitHubController(GitHubProfileService gitHubProfileService) {
        this.gitHubProfileService = gitHubProfileService;
    }

    @Operation(
            summary = "Get the linked GitHub profile",
            description = """
                    Resolves the configured GitHub account from its stable numeric user ID and returns the
                    login and profile URL currently reported by GitHub, so the reference keeps working after
                    a username change.""",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Resolved GitHub profile",
                            content = @Content(schema = @Schema(implementation = GitHubProfileResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Not authenticated"),
                    @ApiResponse(responseCode = "404", description = "No GitHub account configured on the server"),
                    @ApiResponse(responseCode = "503", description = "GitHub could not be reached and no cached profile exists")
            }
    )
    @GetMapping("/profile")
    public ResponseEntity<GitHubProfileResponse> getProfile() {
        return ResponseEntity.ok(gitHubProfileService.getProfile());
    }
}
