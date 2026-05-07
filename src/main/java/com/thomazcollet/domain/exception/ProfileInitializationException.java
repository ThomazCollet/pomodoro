package com.thomazcollet.domain.exception;

/**
 * Lançada quando ocorre uma falha crítica na criação ou 
 * inicialização automática do perfil de usuário.
 */
public class ProfileInitializationException extends PomodoroException {

    public ProfileInitializationException() {
        super();
    }

    public ProfileInitializationException(String message) {
        super(message);
    }

    public ProfileInitializationException(String message, Throwable cause) {
        super(message, cause);
    }

    public ProfileInitializationException(Throwable cause) {
        super(cause);
    }
}