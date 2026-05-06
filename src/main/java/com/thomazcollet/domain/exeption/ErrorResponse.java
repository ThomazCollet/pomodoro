package com.thomazcollet.domain.exeption;

import java.time.LocalDateTime;

/**
 * DTO para padronizar o envio de erros para a interface ou logs.
 */
public record ErrorResponse(
    LocalDateTime timestamp,
    String message,
    String details
) {}