package com.thomazcollet.domain.exception;

/**
 * Lançada quando uma operação é solicitada em um estado que não a permite.
 */
public class TimerStateException extends PomodoroException {

    // Permite: throw new TimerStateException();
    public TimerStateException() {
        super();
    }

    // Permite: throw new TimerStateException("Mensagem customizada");
    public TimerStateException(String message) {
        super(message);
    }

    // Permite: throw new TimerStateException("Mensagem", causaRaiz);
    public TimerStateException(String message, Throwable cause) {
        super(message, cause);
    }
}