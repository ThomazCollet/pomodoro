package com.thomazcollet.ui.model;

/**
 * Representa os três estados visuais possíveis de um sub-card de conquista na
 * interface.
 */
public enum CardState {
    /**
     * O desafio anterior ainda não foi concluído. Card cinza com cadeado.
     */
    LOCKED,

    /**
     * O desafio atual que o usuário está tentando completar. Card colorido com
     * barra ativa.
     */
    ACTIVE,

    /**
     * Desafio finalizado. Card com borda de sucesso e ícone de check.
     */
    COMPLETED
}