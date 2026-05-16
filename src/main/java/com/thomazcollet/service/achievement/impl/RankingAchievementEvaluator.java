package com.thomazcollet.service.achievement.impl;

import com.thomazcollet.domain.model.AchievementCategory;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.repository.ProfileRepository;
import com.thomazcollet.service.achievement.AchievementEvaluator;

import java.util.Optional;

/**
 * Avaliador responsável por validar o desbloqueio de insígnias baseadas
 * no progresso de Ranking e XP acumulado do usuário.
 */
public class RankingAchievementEvaluator implements AchievementEvaluator {

    private final ProfileRepository profileRepository;

    public RankingAchievementEvaluator(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    public boolean evaluate(Long profileId, String achievementKey, int conditionValue) {
        // Fail-Fast: Se a chave não pertencer ao contexto de rankings, ignora imediatamente
        if (achievementKey == null || !achievementKey.startsWith("ranking_tier_")) {
            return false;
        }

        // Busca o perfil do usuário para analisar o XP atual
        Optional<Profile> profileOptional = profileRepository.findById(profileId);
        if (profileOptional.isEmpty()) {
            return false;
        }

        Profile profile = profileOptional.get();
        int currentXp = profile.getXp();

        // Avalia o ganho com base na chave mapeada na planilha
        return switch (achievementKey) {
            case "ranking_tier_c" -> currentXp >= 2000;  // Alcançar o rank C
            case "ranking_tier_a" -> currentXp >= 15000; // Alcançar o rank A
            case "ranking_tier_s" -> currentXp >= 40000; // Alcançar o rank S
            case "ranking_tier_ss" -> currentXp >= 100000; // Alcançar o rank SS
            default -> false;
        };
    }

    @Override
    public AchievementCategory getCategory() {
        return AchievementCategory.RANKING;
    }
}