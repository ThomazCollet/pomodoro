package com.thomazcollet.domain.exception;

/**
 * Lançada quando ocorre uma falha no cálculo, processamento ou 
 * recuperação de métricas e estatísticas de foco.
 */
public class StatisticsComputationException extends PomodoroException {

    public StatisticsComputationException() {
        super();
    }

    public StatisticsComputationException(String message) {
        super(message);
    }

    public StatisticsComputationException(String message, Throwable cause) {
        super(message, cause);
    }

    public StatisticsComputationException(Throwable cause) {
        super(cause);
    }
}