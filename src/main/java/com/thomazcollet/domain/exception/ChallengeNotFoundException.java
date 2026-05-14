package com.thomazcollet.domain.exception;

/**
 * Exceção lançada quando um desafio específico não é encontrado no repositório.
 * Herda de PomodoroException para manter a hierarquia de erros do domínio.
 */
public class ChallengeNotFoundException extends PomodoroException {

    // Construtor vazio
    public ChallengeNotFoundException() {
        super();
    }

    // Construtor com mensagem: mais comum para buscas falhas
    public ChallengeNotFoundException(String message) {
        super(message);
    }

    // Construtor com mensagem e causa: útil se houver erro de parsing ou BD no meio
    public ChallengeNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    // Construtor apenas com a causa
    public ChallengeNotFoundException(Throwable cause) {
        super(cause);
    }
}