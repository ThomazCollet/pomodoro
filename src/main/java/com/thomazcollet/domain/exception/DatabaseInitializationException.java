package com.thomazcollet.domain.exception;

/**
 * Lançada quando ocorre uma falha crítica na inicialização 
 * ou configuração do banco de dados SQLite.
 */
public class DatabaseInitializationException extends PomodoroException {
    
    public DatabaseInitializationException(String message) {
        super(message);
    }

    public DatabaseInitializationException(String message, Throwable cause) {
        super(message, cause);
    }
}