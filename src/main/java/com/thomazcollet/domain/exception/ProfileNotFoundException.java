package com.thomazcollet.domain.exception;

/**
 * Lançada quando um perfil específico não é encontrado no sistema.
 */
public class ProfileNotFoundException extends PomodoroException {

    public ProfileNotFoundException() {
        super();
    }

    public ProfileNotFoundException(String message) {
        super(message);
    }

    public ProfileNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProfileNotFoundException(Throwable cause) {
        super(cause);
    }
}