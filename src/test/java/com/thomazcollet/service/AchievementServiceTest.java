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
    private MockEvaluator mockStreakEvaluator; // Novo Evaluator plugado no ciclo de vida
    private AchievementService achievementService;
    private final Long profileId = 1L;

    @BeforeEach
    void setUp() {
        // Inicializa os dublês de testes limpos antes de cada execução
        mockRepository = new MockAchievementRepository();
        mockDailyFocusEvaluator = new MockEvaluator(AchievementCategory.DAILY_FOCUS);
        mockStreakEvaluator = new MockEvaluator(AchievementCategory.STREAK); // Instanciando a dependência de Streak

        // Instancia o serviço injetando manualmente as duas dependências (Garante que
        // as chaves de Streak não retornem null)
        achievementService = new AchievementService(mockRepository,
                List.of(mockDailyFocusEvaluator, mockStreakEvaluator));
    }

    @Test
    @DisplayName("Deve lançar NullPointerException se o repositório fornecido for nulo")
    void shouldThrowExceptionWhenRepositoryIsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            new AchievementService(null, List.of(mockDailyFocusEvaluator, mockStreakEvaluator));
        });
        assertEquals("AchievementRepository não pode ser nulo", exception.getMessage());
    }

    @Test
    @DisplayName("Deve pular a validação se a conquista já foi desbloqueada previamente (Fail-Fast)")
    void shouldSkipValidationWhenAchievementIsAlreadyUnlocked() {
        mockRepository.simulateAlreadyUnlocked("focus_daily_1h_hours");

        mockDailyFocusEvaluator.setEvaluateResult(true);
        mockDailyFocusEvaluator.setMaxSimulatedLimit(60);

        achievementService.checkAndUnlockNewAchievements(profileId);

        assertTrue(mockRepository.getSavedAchievements().isEmpty(),
                "Não deveria salvar uma nova conquista se a atual já está desbloqueada e as metas superiores não foram batidas.");
    }

    @Test
    @DisplayName("Deve desbloquear e salvar a conquista de Bronze se o critério for atingido")
    void shouldUnlockAndSaveAchievementWhenCriteriaIsMet() {
        mockDailyFocusEvaluator.setEvaluateResult(true);
        mockDailyFocusEvaluator.setMaxSimulatedLimit(60);

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
        mockDailyFocusEvaluator.setEvaluateResult(true);
        mockDailyFocusEvaluator.setMaxSimulatedLimit(120);

        achievementService.checkAndUnlockNewAchievements(profileId);

        List<Achievement> saved = mockRepository.getSavedAchievements();
        assertEquals(2, saved.size(), "Deveria ter salvo 2 conquistas (Bronze e Prata)");

        assertEquals("focus_daily_1h_hours", saved.get(0).getAchievementKey());
        assertEquals("focus_daily_2h_hours", saved.get(1).getAchievementKey());
    }

    @Test
    @DisplayName("Não deve salvar nenhuma conquista se o critério do avaliador não for atingido")
    void shouldNotSaveAnyAchievementWhenCriteriaIsNotMet() {
        mockDailyFocusEvaluator.setEvaluateResult(false);
        mockStreakEvaluator.setEvaluateResult(false);

        achievementService.checkAndUnlockNewAchievements(profileId);

        assertTrue(mockRepository.getSavedAchievements().isEmpty(),
                "Nenhuma conquista deveria ser salva se os critérios falharem.");
    }

    // ==========================================
    // NOVOS CENÁRIOS DE TESTE DIRECIONADOS AO STREAK
    // ==========================================

    @Test
    @DisplayName("Deve desbloquear conquista de Streak de Bronze quando a ofensiva atual atingir 5 dias")
    void shouldUnlockStreakBronzeWhenCurrentStreakHitsFiveDays() {
        // Cenário: Usuário atingiu a meta de 5 dias corridos de streak
        mockStreakEvaluator.setEvaluateResult(true);
        mockStreakEvaluator.setMaxSimulatedLimit(5);

        // Para evitar colisões com os acumuladores de mesmo valor (x3 e x5) neste
        // assert específico,
        // simulamos que o usuário já possui as conquistas de repetição desbloqueadas
        // (Fail-Fast)
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
        // Cenário: O usuário já bateu a meta de 5 dias de streak por 3 vezes na
        // história
        mockStreakEvaluator.setEvaluateResult(true);
        mockStreakEvaluator.setMaxSimulatedLimit(5);

        // Simulamos que ele já pegou a de streak corrente para isolar o teste na de
        // repetição
        mockRepository.simulateAlreadyUnlocked("streak_current_5");
        mockRepository.simulateAlreadyUnlocked("streak_count_5_x5");

        achievementService.checkAndUnlockNewAchievements(profileId);

        List<Achievement> saved = mockRepository.getSavedAchievements();
        assertEquals(1, saved.size(), "Deveria salvar apenas a conquista de repetição pendente");

        assertEquals("streak_count_5_x3", saved.get(0).getAchievementKey());
    }

    // ==========================================
    // IMPLEMENTAÇÕES DE MOCKS MANUAIS PARA TESTE
    // ==========================================

    private static class MockAchievementRepository implements AchievementRepository {
        private final List<Achievement> savedAchievements = new ArrayList<>();
        private final Set<String> unlockedKeys = new HashSet<>();

        public void simulateAlreadyUnlocked(String key) {
            unlockedKeys.add(key);
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
            return 0;
        }
    }

    private static class MockEvaluator implements AchievementEvaluator {
        private final AchievementCategory category;
        private boolean evaluateResult = false;
        private int maxSimulatedLimit = Integer.MAX_VALUE;

        public MockEvaluator(AchievementCategory category) {
            this.category = category;
        }

        public void setEvaluateResult(boolean evaluateResult) {
            this.evaluateResult = evaluateResult;
        }

        public void setMaxSimulatedLimit(int maxSimulatedLimit) {
            this.maxSimulatedLimit = maxSimulatedLimit;
        }

        @Override
        public boolean evaluate(Long profileId, String achievementKey, int conditionValue) {
            if (this.category == AchievementCategory.DAILY_FOCUS) {
                if (!achievementKey.startsWith("focus_daily_") || !achievementKey.endsWith("_hours")) {
                    return false;
                }
                return evaluateResult && (conditionValue <= maxSimulatedLimit);
            }

            if (this.category == AchievementCategory.STREAK) {
                if (!achievementKey.startsWith("streak_current_") && !achievementKey.startsWith("streak_count_")) {
                    return false;
                }
                // Apenas valida se o valor exigido pela conquista está mapeado no limite do
                // mock
                return evaluateResult && (conditionValue <= maxSimulatedLimit);
            }

            return false;
        }

        @Override
        public AchievementCategory getCategory() {
            return this.category;
        }
    }
}