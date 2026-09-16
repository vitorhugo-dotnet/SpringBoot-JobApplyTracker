package com.jobtracker.dto.auth;

import jakarta.validation.constraints.Email;

public record PasskeyLoginOptionsRequest(
        @Email String email
) {
}
