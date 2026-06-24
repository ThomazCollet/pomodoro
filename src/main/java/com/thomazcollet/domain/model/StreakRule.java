package com.thomazcollet.domain.model;

/**
 * Define as regras de contagem de streak disponíveis para o usuário.
 * Persistida como TEXT na coluna streak_rule da tabela profiles.
 *
 * <ul>
 * <li>{@link #ALL_DAYS} — todos os dias contam; sem folga automática.</li>
 * <li>{@link #EXCEPT_SUNDAY} — segunda a sábado; domingo não quebra a
 * streak.</li>
 * <li>{@link #WEEKDAYS_ONLY} — segunda a sexta; fim de semana não quebra a
 * streak.</li>
 * </ul>
 *
 * Implementação efetiva da regra é prevista para uma fase posterior —
 * aqui apenas o enum e a persistência são estabelecidos.
 */
public enum StreakRule {
    ALL_DAYS("Todos os dias"),
    EXCEPT_SUNDAY("Segunda a Sábado"),
    WEEKDAYS_ONLY("Apenas dias úteis (Seg–Sex)");

    private final String displayName;

    StreakRule(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}