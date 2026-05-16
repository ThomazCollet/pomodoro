package com.thomazcollet.service.achievement;

import com.thomazcollet.domain.model.AchievementCategory;

public interface AchievementEvaluator {

    /**
     * Executa a regra de negócio específica para verificar se o perfil atingiu os
     * requisitos.
     * * @param profileId Id do perfil do usuário.
     * 
     * @param conditionValue O valor numérico que define a meta da conquista (ex: 5
     *                       streaks, 120 minutos).
     * @return true se o usuário atingiu ou ultrapassou a meta.
     */
    boolean evaluate(Long profileId, String achievementKey, int conditionValue);

    /**
     * Retorna qual categoria de conquista este validador sabe processar.
     */
    AchievementCategory getCategory();
}