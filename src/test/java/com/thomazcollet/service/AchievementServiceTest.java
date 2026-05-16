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
    private AchievementService achievementService;
    private final Long profileId = 1L;

    @BeforeEach
    void setUp() {
        // Inicializa os dublês de testes limpos antes de cada execução
        mockRepository = new MockAchievementRepository();
        mockDailyFocusEvaluator = new MockEvaluator(AchievementCategory.DAILY_FOCUS);
        
        // Instancia o serviço injetando manualmente as dependências (IoC puro)
        achievementService = new AchievementService(mockRepository, List.of(mockDailyFocusEvaluator));
    }

    @Test
    @DisplayName("Deve lançar NullPointerException se o repositório fornecido for nulo")
    void shouldThrowExceptionWhenRepositoryIsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> {
            new AchievementService(null, List.of(mockDailyFocusEvaluator));
        });
        assertEquals("AchievementRepository não pode ser nulo", exception.getMessage());
    }

    @Test
    @DisplayName("Deve pular a validação se a conquista já foi desbloqueada previamente (Fail-Fast)")
    void shouldSkipValidationWhenAchievementIsAlreadyUnlocked() {
        // Cenário: O usuário já tem a conquista de Bronze salva no banco
        mockRepository.simulateAlreadyUnlocked("focus_daily_1h_bronze");
        
        // Configuramos o avaliador para retornar true, mas limitamos o teto simulado 
        // para a meta de Bronze (60), simulando que o usuário não atingiu os níveis superiores ainda
        mockDailyFocusEvaluator.setEvaluateResult(true);
        mockDailyFocusEvaluator.setMaxSimulatedLimit(60); 

        // Execução
        achievementService.checkAndUnlockNewAchievements(profileId);

        // Verificação: Como a de Bronze já estava desbloqueada e as outras não atingiram os critérios,
        // nenhuma nova conquista deve ter sido adicionada à lista de salvamento.
        assertTrue(mockRepository.getSavedAchievements().isEmpty(), 
                "Não deveria salvar uma nova conquista se a atual já está desbloqueada e as metas superiores não foram batidas.");
    }

    @Test
    @DisplayName("Deve desbloquear e salvar a conquista de Bronze se o critério for atingido")
    void shouldUnlockAndSaveAchievementWhenCriteriaIsMet() {
        // Cenário: Usuário focou 70 minutos (passando a meta de 60 do Bronze, mas abaixo dos 120 da Prata)
        mockDailyFocusEvaluator.setEvaluateResult(true); 
        mockDailyFocusEvaluator.setMaxSimulatedLimit(60); 

        // Execução
        achievementService.checkAndUnlockNewAchievements(profileId);

        // Verificação
        List<Achievement> saved = mockRepository.getSavedAchievements();
        assertEquals(1, saved.size(), "Deveria ter salvo exatamente 1 conquista");
        
        Achievement bronze = saved.get(0);
        assertEquals("focus_daily_1h_bronze", bronze.getAchievementKey());
        assertEquals(AchievementCategory.DAILY_FOCUS, bronze.getCategory());
        assertEquals(AchievementTier.BRONZE, bronze.getTier());
        assertEquals(profileId, bronze.getProfileId());
    }

    @Test
    @DisplayName("Deve desbloquear múltiplas conquistas em cadeia se o critério atingir níveis mais altos")
    void shouldUnlockMultipleAchievementsInChain() {
        // Cenário: Usuário focou 150 minutos em um único dia.
        // Isso atinge os critérios de Bronze (60) e Prata (120) ao mesmo tempo!
        mockDailyFocusEvaluator.setEvaluateResult(true);
        mockDailyFocusEvaluator.setMaxSimulatedLimit(120); 

        // Execução
        achievementService.checkAndUnlockNewAchievements(profileId);

        // Verificação
        List<Achievement> saved = mockRepository.getSavedAchievements();
        assertEquals(2, saved.size(), "Deveria ter salvo 2 conquistas (Bronze e Prata)");
        
        assertEquals("focus_daily_1h_bronze", saved.get(0).getAchievementKey());
        assertEquals("focus_daily_2h_silver", saved.get(1).getAchievementKey());
    }

    @Test
    @DisplayName("Não deve salvar nenhuma conquista se o critério do avaliador não for atingido")
    void shouldNotSaveAnyAchievementWhenCriteriaIsNotMet() {
        // Cenário: Avaliador retorna false para todas as metas exigidas
        mockDailyFocusEvaluator.setEvaluateResult(false);

        // Execução
        achievementService.checkAndUnlockNewAchievements(profileId);

        // Verificação
        assertTrue(mockRepository.getSavedAchievements().isEmpty(), 
                "Nenhuma conquista deveria ser salva se os critérios falharem.");
    }

    // ==========================================
    // IMPLEMENTAÇÕES DE MOCKS MANUAIS PARA TESTE
    // ==========================================

    /**
     * Dublê estanque (Mock/Fake) que substitui o acesso ao SQLite real.
     */
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

    /**
     * Dublê estanque (Mock/Stub) que simula o comportamento dos Evaluators de domínio.
     */
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
        public boolean evaluate(Long profileId, int conditionValue) {
            return evaluateResult && (conditionValue <= maxSimulatedLimit);
        }

        @Override
        public AchievementCategory getCategory() {
            return this.category;
        }
    }
}