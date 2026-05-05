package com.thomazcollet.domain.model;

public enum TimerState {
    // 1. Definimos as constantes e passamos o texto para o construtor
    RUNNING("Rodando"),
    PAUSED("Pausado"),
    STOPPED("Parado");

    // 2. Criamos o atributo que vai guardar o texto
    private final String description;

    // 3. O construtor do Enum (ele é privado por padrão)
    TimerState(String description) {
        this.description = description;
    }

    // 4. O método que você perguntou: ele apenas devolve o texto guardado
    public String getDescription() {
        return description;
    }
}
