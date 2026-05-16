package com.thomazcollet.service;

import com.thomazcollet.domain.model.Achievement;
import com.thomazcollet.domain.model.AchievementCategory;
import com.thomazcollet.domain.model.AchievementTier;
import com.thomazcollet.domain.repository.AchievementRepository;
import com.thomazcollet.service.achievement.AchievementEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AchievementServiceTest {

    private MockAchievementRepository mockRepository;
    private MockEvaluator mockDailyFocusEvaluator;
    private MockEvaluator mockStreakEvaluator;
    private MockAchievementsEvaluator mockAchievementsEvaluator; // Tipo específico corrigido para suportar o count
    private MockEvaluator mockChallengeEvaluator;
    private MockEvaluator mockRankingEvaluator;
    private AchievementService achievementService;
    private final Long profileId = 1L;

    @BeforeEach
    void setUp() {
        mockRepository = new MockAchievementRepository();
        mockDailyFocusEvaluator = new MockEvaluator(AchievementCategory.DAILY_FOCUS);
        mockStreakEvaluator = new MockEvaluator(AchievementCategory.STREAK);
        mockAchievementsEvaluator = new MockAchievementsEvaluator();
        mockChallengeEvaluator = new MockEvaluator(AchievementCategory.CHALLENGE);
        mockRankingEvaluator = new MockEvaluator(AchievementCategory.RANKING);

        achievementService = new AchievementService(mockRepository,
                List.of(mockDailyFocusEvaluator, mockStreakEvaluator, mockAchievementsEvaluator, mockChallengeEvaluator,
                        mockRankingEvaluator));
    }

    @Test
    @DisplayName("Deve lançar NullPointerException se o repositório fornecido for nulo")
    void shouldThrowExceptionWhenRepositoryIsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            new AchievementService(null,
                    List.of(mockDailyFocusEvaluator, mockStreakEvaluator, mockAchievementsEvaluator,
                            mockChallengeEvaluator, mockRankingEvaluator));
        });
        assertEquals("AchievementRepository não pode ser nulo", exception.getMessage());
    }

    @Test
    @DisplayName("Deve pular a validação se a conquista já foi desbloqueada previamente (Fail-Fast)")
    void shouldSkipValidationWhenAchievementIsAlreadyUnlocked() {
        mockRepository.simulateAlreadyUnlocked("focus_daily_1h_hours");

        // Configuramos para disparar true apenas na chave alvo para isolar o
        // comportamento do fail-fast
        mockDailyFocusEvaluator.setTargetKeyAndValue("focus_daily_1h_hours", 60);

        achievementService.checkAndUnlockNewAchievements(profileId);

        assertTrue(mockRepository.getSavedAchievements().isEmpty(),
                "Não deveria salvar uma nova conquista se a atual já está desbloqueada.");
    }

    @Test
    @DisplayName("Deve desbloquear e salvar a conquista de Bronze se o critério for atingido")
    void shouldUnlockAndSaveAchievementWhenCriteriaIsMet() {
        mockDailyFocusEvaluator.setTargetKeyAndValue("focus_daily_1h_hours", 60);

        achievementService.checkAndUnlockNewAchievements(profileId);

        List<Achievement> saved = mockRepository.getSavedAchievements();
        assertEquals(1, saved.size(), "Deveria ter salvo exatamente 1 conquista");

        Achievement bronze = saved.get(0);
        assertEquals("focus_daily_1h_hours", bronze.getAchievementKey());
        assertEquals(AchievementCategory.DAILY_FOCUS, bronze.getCategory());
        assertEquals(AchievementTier.BRONZE, bronze.getTier());
        assertEquals(profileId, bronze.getProfileId());
    }

    @Test
    @DisplayName("Deve desbloquear múltiplas conquistas em cadeia se o critério atingir níveis mais altos")
    void shouldUnlockMultipleAchievementsInChain() {
        // Ativamos as chaves progressivas de horas diárias simulando que o usuário
        // bateu a meta de 120 minutos
        mockDailyFocusEvaluator.setTargetKeyAndValue("focus_daily_1h_hours", 60);
        mockDailyFocusEvaluator.setTargetKeyAndValue("focus_daily_2h_hours", 120);

        achievementService.checkAndUnlockNewAchievements(profileId);

        List<Achievement> saved = mockRepository.getSavedAchievements();
        assertEquals(2, saved.size(), "Deveria ter salvo 2 conquistas (Bronze e Prata)");

        assertEquals("focus_daily_1h_hours", saved.get(0).getAchievementKey());
        assertEquals("focus_daily_2h_hours", saved.get(1).getAchievementKey());
    }

    @Test
    @DisplayName("Não deve salvar nenhuma conquista se o critério do avaliador não for atingido")
    void shouldNotSaveAnyAchievementWhenCriteriaIsNotMet() {
        // Sem nenhuma chave configurada nos alvos, todos retornam falso implicitamente
        achievementService.checkAndUnlockNewAchievements(profileId);

        assertTrue(mockRepository.getSavedAchievements().isEmpty(),
                "Nenhuma conquista deveria ser salva se os critérios falharem.");
    }

    // ==========================================
    // SCENARIOS DE TESTE: STREAK
    // ==========================================

    @Test
    @DisplayName("Deve desbloquear conquista de Streak de Bronze quando a ofensiva atual atingir 5 dias")
    void shouldUnlockStreakBronzeWhenCurrentStreakHitsFiveDays() {
        mockStreakEvaluator.setTargetKeyAndValue("streak_current_5", 5);

        mockRepository.simulateAlreadyUnlocked("streak_count_5_x3");
        mockRepository.simulateAlreadyUnlocked("streak_count_5_x5");

        achievementService.checkAndUnlockNewAchievements(profileId);

        List<Achievement> saved = mockRepository.getSavedAchievements();
        assertEquals(1, saved.size(), "Deveria ter salvo exatamente 1 conquista de streak atual");

        Achievement streakBronze = saved.get(0);
        assertEquals("streak_current_5", streakBronze.getAchievementKey());
        assertEquals(AchievementCategory.STREAK, streakBronze.getCategory());
        assertEquals(AchievementTier.BRONZE, streakBronze.getTier());
    }

    @Test
    @DisplayName("Deve desbloquear conquista cumulativa de Streak quando bater a meta x3 vezes")
    void shouldUnlockStreakAccumulatedWhenTargetIsReachedThreeTimes() {
        mockStreakEvaluator.setTargetKeyAndValue("streak_count_5_x3", 5);

        mockRepository.simulateAlreadyUnlocked("streak_current_5");
        mockRepository.simulateAlreadyUnlocked("streak_count_5_x5");

        achievementService.checkAndUnlockNewAchievements(profileId);

        List<Achievement> saved = mockRepository.getSavedAchievements();
        assertEquals(1, saved.size(), "Deveria salvar apenas a conquista de repetição pendente");

        assertEquals("streak_count_5_x3", saved.get(0).getAchievementKey());
    }
    // ==========================================
    // SCENARIOS DE TESTE: CHALLENGE
    // ==========================================

    @Test
    @DisplayName("Deve desbloquear conquista de Desafio de Bronze ao completar um desafio de constância de 7 dias")
    void shouldUnlockChallengeConstancyWhenSevenDaysCompleted() {
        mockChallengeEvaluator.setTargetKeyAndValue("challenge_constancy_days_7", 7);

        // Fail-fast para outras chaves que não queremos avaliar neste cenário
        mockRepository.simulateAlreadyUnlocked("challenge_perfect_days_5");
        mockRepository.simulateAlreadyUnlocked("challenge_count_constancy_min_7_3");

        achievementService.checkAndUnlockNewAchievements(profileId);

        List<Achievement> saved = mockRepository.getSavedAchievements();
        assertFalse(saved.isEmpty(), "Deveria ter salvo a conquista de desafio pendente");
        assertEquals("challenge_constancy_days_7", saved.get(0).getAchievementKey());
    }

    @Test
    @DisplayName("Deve desbloquear conquista de Desafio Perfeito de Prata ao terminar 7 dias com vidas intactas")
    void shouldUnlockChallengePerfectWhenCompletedWithAllLives() {
        // Configuramos a chave exata que o Evaluator vai processar
        mockChallengeEvaluator.setTargetKeyAndValue("challenge_perfect_days_7", 7);

        // Simula que as outras conquistas de régua de 7 dias já foram obtidas para
        // isolar o teste
        mockRepository.simulateAlreadyUnlocked("challenge_constancy_days_7");
        mockRepository.simulateAlreadyUnlocked("challenge_count_constancy_min_7_3");
        mockRepository.simulateAlreadyUnlocked("challenge_count_constancy_min_7_6");

        achievementService.checkAndUnlockNewAchievements(profileId);

        List<Achievement> saved = mockRepository.getSavedAchievements();
        boolean hasPerfectKey = saved.stream().anyMatch(a -> "challenge_perfect_days_7".equals(a.getAchievementKey()));
        assertTrue(hasPerfectKey, "Deveria liberar a chave 'challenge_perfect_days_7'");
    }
    
    // ==========================================
    // SCENARIOS DE TESTE: META-CONQUISTAS (ACHIEVEMENTS)
    // ==========================================

    @Test
    @DisplayName("Deve desbloquear meta-conquista de Bronze ao atingir o total de 5 conquests normais")
    void shouldUnlockMetaTotalAchievementsWhenCountHitsFive() {
        mockAchievementsEvaluator.setTargetKeyAndValue("meta_total_5", 5);

        mockRepository.simulateAlreadyUnlocked("meta_first_gold");
        mockRepository.simulateAlreadyUnlocked("meta_total_15");
        mockRepository.simulateAlreadyUnlocked("meta_total_30");

        achievementService.checkAndUnlockNewAchievements(profileId);

        List<Achievement> saved = mockRepository.getSavedAchievements();
        boolean hasTotal5Key = saved.stream().anyMatch(a -> "meta_total_5".equals(a.getAchievementKey()));
        assertTrue(hasTotal5Key, "A chave salva deveria ser 'meta_total_5'");
    }

    // ==========================================
    // SCENARIOS DE TESTE: RANKING
    // ==========================================

    @Test
    @DisplayName("Deve desbloquear a insígnia de Ouro ao atingir a condição do Rank S da planilha")
    void shouldUnlockRankingGoldWhenUserHitsRankS() {
        mockRankingEvaluator.setTargetKeyAndValue("ranking_tier_s", 3);

        achievementService.checkAndUnlockNewAchievements(profileId);

        List<Achievement> saved = mockRepository.getSavedAchievements();
        assertEquals(1, saved.size(), "Deveria registrar apenas o Rank alvo configurado");
        assertEquals("ranking_tier_s", saved.get(0).getAchievementKey());
        assertEquals(AchievementTier.GOLD, saved.get(0).getTier());
    }

    // ==========================================
    // IMPLEMENTAÇÕES DE MOCKS MANUAIS CORRIGIDOS
    // ==========================================

    private static class MockAchievementRepository implements AchievementRepository {
        private final List<Achievement> savedAchievements = new ArrayList<>();
        private final Set<String> unlockedKeys = new HashSet<>();

        public void simulateAlreadyUnlocked(String key) {
            unlockedKeys.add(key);
            if (key.contains("platinum") || key.endsWith("_30") || key.contains("365_triple")) {
                Achievement fakePlat = new Achievement();
                fakePlat.setTier(AchievementTier.PLATINUM);
                savedAchievements.add(fakePlat);
            }
        }

        public List<Achievement> getSavedAchievements() {
            return savedAchievements;
        }

        @Override
        public void save(Achievement achievement) {
            savedAchievements.add(achievement);
        }

        @Override
        public boolean isUnlocked(Long profileId, String achievementKey) {
            return unlockedKeys.contains(achievementKey);
        }

        @Override
        public List<Achievement> findByProfileId(Long profileId) {
            return savedAchievements;
        }

        @Override
        public int countByProfileAndTier(Long profileId, String tier) {
            return (int) savedAchievements.stream()
                    .filter(a -> a.getTier() != null && a.getTier().name().equalsIgnoreCase(tier))
                    .count();
        }
    }

    private static class MockEvaluator implements AchievementEvaluator {
        private final AchievementCategory category;
        private final Set<String> activeKeys = new HashSet<>();

        public MockEvaluator(AchievementCategory category) {
            this.category = category;
        }

        // CORREÇÃO ESTRUTURAL: Vincula explicitamente quais chaves devem responder true
        // baseado no valor exato
        public void setTargetKeyAndValue(String key, int val) {
            activeKeys.add(key + "_" + val);
        }

        @Override
        public boolean evaluate(Long profileId, String achievementKey, int conditionValue) {
            if (!achievementKey.startsWith(this.category.name().toLowerCase().split("_")[0])) {
                // Tratamento especial para DAILY_FOCUS cujo prefixo é focus_
                if (this.category == AchievementCategory.DAILY_FOCUS && !achievementKey.startsWith("focus_")) {
                    return false;
                }
                // Tratamento para RANKING
                if (this.category == AchievementCategory.RANKING && !achievementKey.startsWith("ranking_")) {
                    return false;
                }
            }
            return activeKeys.contains(achievementKey + "_" + conditionValue);
        }

        @Override
        public AchievementCategory getCategory() {
            return this.category;
        }
    }

    // Mock especializado herdando a mecânica para evitar problemas de casting em
    // Meta-Conquistas
    private static class MockAchievementsEvaluator extends MockEvaluator {
        public MockAchievementsEvaluator() {
            super(AchievementCategory.ACHIEVEMENTS);
        }

        @Override
        public boolean evaluate(Long profileId, String achievementKey, int conditionValue) {
            if (!achievementKey.startsWith("meta_")) {
                return false;
            }
            return super.activeKeys.contains(achievementKey + "_" + conditionValue);
        }
    }
}