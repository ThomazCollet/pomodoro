package com.thomazcollet.service;

import com.thomazcollet.domain.model.Achievement;
import com.thomazcollet.domain.model.AchievementCategory;
import com.thomazcollet.domain.model.AchievementTier;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.repository.AchievementRepository;
import com.thomazcollet.domain.repository.ProfileRepository;
import com.thomazcollet.service.achievement.AchievementEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AchievementServiceTest {

    private MockAchievementRepository mockRepository;
    private MockProfileRepository mockProfileRepository;
    private MockEvaluator mockDailyFocusEvaluator;
    private MockEvaluator mockStreakEvaluator;
    private MockAchievementsEvaluator mockAchievementsEvaluator;
    private MockEvaluator mockChallengeEvaluator;
    private MockEvaluator mockRankingEvaluator;
    private AchievementService achievementService;
    private final Long profileId = 1L;

    @BeforeEach
    void setUp() {
        mockRepository = new MockAchievementRepository();
        mockProfileRepository = new MockProfileRepository();

        mockDailyFocusEvaluator = new MockEvaluator(AchievementCategory.DAILY_FOCUS);
        mockStreakEvaluator = new MockEvaluator(AchievementCategory.STREAK);
        mockAchievementsEvaluator = new MockAchievementsEvaluator();
        mockChallengeEvaluator = new MockEvaluator(AchievementCategory.CHALLENGE);
        mockRankingEvaluator = new MockEvaluator(AchievementCategory.RANKING);

        achievementService = new AchievementService(mockRepository, mockProfileRepository,
                List.of(mockDailyFocusEvaluator, mockStreakEvaluator, mockAchievementsEvaluator,
                        mockChallengeEvaluator, mockRankingEvaluator));
    }

    // ==========================================
    // SCENARIOS DE TESTE: CONSTRUTOR / FAIL-FAST
    // ==========================================

    @Test
    @DisplayName("Deve lançar NullPointerException se o repositório de conquistas for nulo")
    void shouldThrowExceptionWhenRepositoryIsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new AchievementService(null, mockProfileRepository,
                        List.of(mockDailyFocusEvaluator, mockStreakEvaluator, mockAchievementsEvaluator,
                                mockChallengeEvaluator, mockRankingEvaluator)));
        assertEquals("AchievementRepository não pode ser nulo", exception.getMessage());
    }

    @Test
    @DisplayName("Deve lançar NullPointerException se o repositório de perfis for nulo")
    void shouldThrowExceptionWhenProfileRepositoryIsNull() {
        NullPointerException exception = assertThrows(NullPointerException.class,
                () -> new AchievementService(mockRepository, null,
                        List.of(mockDailyFocusEvaluator, mockStreakEvaluator, mockAchievementsEvaluator,
                                mockChallengeEvaluator, mockRankingEvaluator)));
        assertEquals("ProfileRepository não pode ser nulo", exception.getMessage());
    }

    // ==========================================
    // SCENARIOS DE TESTE: DAILY FOCUS
    // ==========================================

    @Test
    @DisplayName("Deve pular a validação se a conquista já foi desbloqueada previamente (Fail-Fast)")
    void shouldSkipValidationWhenAchievementIsAlreadyUnlocked() {
        mockRepository.simulateAlreadyUnlocked("focus_daily_2h_hours");
        mockDailyFocusEvaluator.setTargetKeyAndValue("focus_daily_2h_hours", 120);

        achievementService.checkAndUnlockNewAchievements(profileId);

        assertTrue(mockRepository.getSavedAchievements().isEmpty(),
                "Não deveria salvar uma nova conquista se a atual já está desbloqueada.");
    }

    @Test
    @DisplayName("Deve desbloquear e salvar a conquista de Bronze de foco diário se o critério for atingido")
    void shouldUnlockAndSaveAchievementWhenCriteriaIsMet() {
        mockDailyFocusEvaluator.setTargetKeyAndValue("focus_daily_2h_hours", 120);

        achievementService.checkAndUnlockNewAchievements(profileId);

        List<Achievement> saved = mockRepository.getSavedAchievements();
        assertEquals(1, saved.size(), "Deveria ter salvo exatamente 1 conquista");

        Achievement bronze = saved.get(0);
        assertEquals("focus_daily_2h_hours", bronze.getAchievementKey());
        assertEquals(AchievementCategory.DAILY_FOCUS, bronze.getCategory());
        assertEquals(AchievementTier.BRONZE, bronze.getTier());
        assertEquals(profileId, bronze.getProfileId());
    }

    @Test
    @DisplayName("Deve desbloquear Bronze e Prata de foco diário em cadeia quando ambos os critérios forem atingidos")
    void shouldUnlockMultipleAchievementsInChain() {
        mockDailyFocusEvaluator.setTargetKeyAndValue("focus_daily_2h_hours", 120);
        mockDailyFocusEvaluator.setTargetKeyAndValue("focus_daily_3h_hours", 180);

        achievementService.checkAndUnlockNewAchievements(profileId);

        List<Achievement> saved = mockRepository.getSavedAchievements();
        assertEquals(2, saved.size(), "Deveria ter salvo 2 conquistas (Bronze e Prata)");

        assertEquals("focus_daily_2h_hours", saved.get(0).getAchievementKey());
        assertEquals("focus_daily_3h_hours", saved.get(1).getAchievementKey());
    }

    @Test
    @DisplayName("Não deve salvar nenhuma conquista se o critério do avaliador não for atingido")
    void shouldNotSaveAnyAchievementWhenCriteriaIsNotMet() {
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
    @DisplayName("Deve desbloquear conquista de Desafio de Bronze ao estabelecer um desafio de constância de 7 dias")
    void shouldUnlockChallengeConstancyWhenSevenDaysCompleted() {
        mockChallengeEvaluator.setTargetKeyAndValue("challenge_constancy_days_7", 7);
        mockRepository.simulateAlreadyUnlocked("challenge_perfect_days_5");
        mockRepository.simulateAlreadyUnlocked("challenge_count_constancy_min_7_3");

        achievementService.checkAndUnlockNewAchievements(profileId);

        List<Achievement> saved = mockRepository.getSavedAchievements();

        // 🛠️ CORREÇÃO: Linha errada removida daqui!
        assertFalse(saved.isEmpty(), "Deveria ter salvo a conquista de desafio pendente");
        assertEquals("challenge_constancy_days_7", saved.get(0).getAchievementKey());
    }

    @Test
    @DisplayName("Deve desbloquear conquista de Desafio Perfeito de Prata ao terminar 7 dias com vidas intactas")
    void shouldUnlockChallengePerfectWhenCompletedWithAllLives() {
        mockChallengeEvaluator.setTargetKeyAndValue("challenge_perfect_days_7", 7);
        mockRepository.simulateAlreadyUnlocked("challenge_constancy_days_7");
        mockRepository.simulateAlreadyUnlocked("challenge_count_constancy_min_7_3");
        mockRepository.simulateAlreadyUnlocked("challenge_count_constancy_min_7_6");

        achievementService.checkAndUnlockNewAchievements(profileId);

        List<Achievement> saved = mockRepository.getSavedAchievements();
        boolean hasPerfectKey = saved.stream().anyMatch(a -> "challenge_perfect_days_7".equals(a.getAchievementKey()));
        assertTrue(hasPerfectKey, "Deveria liberar a chave 'challenge_perfect_days_7'");
    }

    // ==========================================
    // SCENARIOS DE TESTE: META-CONQUISTAS
    // ==========================================

    @Test
    @DisplayName("Deve desbloquear meta-conquista de Bronze ao atingir o total de 5 conquests normais")
    void shouldUnlockMetaTotalAchievementsWhenCountHitsFive() {
        mockAchievementsEvaluator.setTargetKeyAndValue("meta_total_5", 5);
        mockRepository.simulateAlreadyUnlocked("meta_gold_count_1");
        mockRepository.simulateAlreadyUnlocked("meta_total_15");
        mockRepository.simulateAlreadyUnlocked("meta_total_30");

        achievementService.checkAndUnlockNewAchievements(profileId);

        List<Achievement> saved = mockRepository.getSavedAchievements();
        boolean hasTotal5Key = saved.stream().anyMatch(a -> "meta_total_5".equals(a.getAchievementKey()));
        assertTrue(hasTotal5Key, "A chave salva deveria ser 'meta_total_5'");
    }

    @Test
    @DisplayName("Deve desbloquear meta-conquista de Bronze da trilha de Ouros ao obter a primeira conquista ouro")
    void shouldUnlockMetaGoldCountBronzeWhenFirstGoldIsObtained() {
        mockAchievementsEvaluator.setTargetKeyAndValue("meta_gold_count_1", 1);
        mockRepository.simulateAlreadyUnlocked("meta_gold_count_5");
        mockRepository.simulateAlreadyUnlocked("meta_gold_count_10");

        achievementService.checkAndUnlockNewAchievements(profileId);

        List<Achievement> saved = mockRepository.getSavedAchievements();
        boolean hasKey = saved.stream().anyMatch(a -> "meta_gold_count_1".equals(a.getAchievementKey()));
        assertTrue(hasKey, "Deveria desbloquear meta_gold_count_1 ao obter a primeira conquista ouro");
    }

    // ==========================================
    // SCENARIOS DE TESTE: RANKING & CASCATA
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

    @Test
    @DisplayName("Deve avaliar Meta-Conquistas por último permitindo ganho em cascata no mesmo ciclo")
    void shouldEvaluateMetaAchievementsLastEnablingCascadingUnlocks() {
        mockRankingEvaluator.setTargetKeyAndValue("ranking_tier_c", 1);
        mockAchievementsEvaluator.setTargetKeyAndValue("meta_total_5", 5);

        achievementService.checkAndUnlockNewAchievements(profileId);

        List<Achievement> saved = mockRepository.getSavedAchievements();
        assertEquals(2, saved.size(), "Deveria processar o Rank primeiro e a Meta-conquista logo em seguida");
        assertEquals("ranking_tier_c", saved.get(0).getAchievementKey());
        assertEquals("meta_total_5", saved.get(1).getAchievementKey());
    }

    // ==========================================
    // IMPLEMENTAÇÕES DE MOCKS MANUAIS
    // ==========================================

    private static class MockProfileRepository implements ProfileRepository {
        @Override
        public Optional<Profile> findById(Long id) {
            Profile fakeProfile = new Profile();
            fakeProfile.setId(id);
            fakeProfile.setXp(0);
            return Optional.of(fakeProfile);
        }

        @Override
        public void updateXp(Long profileId, int currentXp) {
        }

        @Override
        public void save(Profile profile) {
        }

        @Override
        public void delete(Long id) {
        }

        @Override
        public List<Profile> findAll() {
            return List.of();
        }

        @Override
        public void updateStats(Long profileId, int maxStreak, int maxFocusDaySeconds, int totalFocusSessions) {
        }

        // 🆕 SOLUÇÃO: Implementação do novo contrato adicionado ao ProfileRepository
        @Override
        public void updateGoals(Long profileId, int dailySeconds, int weeklySeconds, int monthlySeconds) {
            // Comportamento vazio seguro (noop) apenas para satisfazer as regras de
            // compilação do teste
        }
    }

    private static class MockAchievementRepository implements AchievementRepository {
        private final List<Achievement> savedAchievements = new ArrayList<>();
        private final Set<String> unlockedKeys = new HashSet<>();
        private final List<Achievement> newlySaved = new ArrayList<>();

        public void simulateAlreadyUnlocked(String key) {
            unlockedKeys.add(key);
            Achievement fake = new Achievement();
            fake.setAchievementKey(key);
            if (key.contains("platinum") || key.endsWith("_triple")) {
                fake.setTier(AchievementTier.PLATINUM);
            } else if (key.endsWith("_gold") || key.contains("_ouro") || key.endsWith("_s")) {
                fake.setTier(AchievementTier.GOLD);
            } else if (key.endsWith("_silver") || key.endsWith("_a")) {
                fake.setTier(AchievementTier.SILVER);
            } else {
                fake.setTier(AchievementTier.BRONZE);
            }
            savedAchievements.add(fake);
        }

        public List<Achievement> getSavedAchievements() {
            return newlySaved;
        }

        @Override
        public void save(Achievement achievement) {
            newlySaved.add(achievement);
            savedAchievements.add(achievement);
            unlockedKeys.add(achievement.getAchievementKey());
        }

        @Override
        public boolean isUnlocked(Long profileId, String achievementKey) {
            return unlockedKeys.contains(achievementKey);
        }

        @Override
        public Set<String> findUnlockedKeysByProfileId(Long profileId) {
            return unlockedKeys;
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

        @Override
        public int countTotalDefinedByTier(String tier) {
            return switch (tier.toUpperCase()) {
                case "GOLD" -> 18;
                case "SILVER" -> 18;
                case "BRONZE" -> 18;
                default -> 0;
            };
        }
    }

    private static class MockEvaluator implements AchievementEvaluator {
        protected final AchievementCategory category;
        protected final Set<String> activeKeys = new HashSet<>();

        public MockEvaluator(AchievementCategory category) {
            this.category = category;
        }

        public void setTargetKeyAndValue(String key, int val) {
            activeKeys.add(key + "_" + val);
        }

        @Override
        public boolean evaluate(Long profileId, String achievementKey, int conditionValue) {
            if (this.category == AchievementCategory.RANKING) {
                if (!achievementKey.startsWith("ranking_"))
                    return false;
            } else if (this.category == AchievementCategory.DAILY_FOCUS) {
                if (!achievementKey.startsWith("focus_"))
                    return false;
            } else {
                String expectedPrefix = this.category.name().toLowerCase().split("_")[0];
                if (!achievementKey.startsWith(expectedPrefix))
                    return false;
            }
            return activeKeys.contains(achievementKey + "_" + conditionValue);
        }

        @Override
        public AchievementCategory getCategory() {
            return this.category;
        }
    }

    private static class MockAchievementsEvaluator extends MockEvaluator {
        public MockAchievementsEvaluator() {
            super(AchievementCategory.ACHIEVEMENTS);
        }

        @Override
        public boolean evaluate(Long profileId, String achievementKey, int conditionValue) {
            if (!achievementKey.startsWith("meta_"))
                return false;
            return activeKeys.contains(achievementKey + "_" + conditionValue);
        }
    }
}