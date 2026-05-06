package com.thomazcollet.domain.exeption;

/**
 * Exceção base para o domínio do Pomodoro.
 * Segue as boas práticas de fornecer construtores para diferentes contextos.
 */
public class PomodoroException extends RuntimeException {

    // Construtor vazio: permite lançar sem mensagem
    public PomodoroException() {
        super();
    }

    // Construtor com mensagem: o que você já criou
    public PomodoroException(String message) {
        super(message);
    }

    // Construtor com mensagem e causa: essencial para "Exception Wrapping"
    public PomodoroException(String message, Throwable cause) {
        super(message, cause);
    }

    // Construtor apenas com a causa
    public PomodoroException(Throwable cause) {
        super(cause);
    }
}