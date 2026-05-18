package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.AchievementCategory;
import com.thomazcollet.domain.model.AchievementTier;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.repository.AchievementRepository;
import com.thomazcollet.service.AchievementService.AchievementDefinition;
import com.thomazcollet.ui.model.AchievementDisplayModel;
import com.thomazcollet.ui.model.AchievementDisplayModel.CardState;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class AchievementViewController {

    @FXML
    private HBox btnBronzeSummary;
    @FXML
    private HBox btnSilverSummary;
    @FXML
    private HBox btnGoldSummary;
    @FXML
    private Label lblBronzeCount;
    @FXML
    private Label lblSilverCount;
    @FXML
    private Label lblGoldCount;
    @FXML
    private VBox categoryContainer;
    @FXML
    private FlowPane platinumGridPane;

    private AchievementRepository achievementRepository;

    public void initializeData(Profile profile, AchievementRepository achievementRepository,
            List<AchievementDefinition> definitions) {

        if (profile == null)
            throw new IllegalArgumentException("O perfil não pode ser nulo para inicializar as conquistas.");
        if (achievementRepository == null)
            throw new IllegalArgumentException("O repositório de conquistas não pode ser nulo.");
        if (definitions == null)
            throw new IllegalArgumentException("As definições de conquistas não podem ser nulas.");

        this.achievementRepository = achievementRepository;

        Set<String> unlockedKeys = achievementRepository.findUnlockedKeysByProfileId(profile.getId());
        List<AchievementDisplayModel> allStandardCards = new ArrayList<>();
        List<AchievementDisplayModel> platinumCards = new ArrayList<>();

        for (AchievementDefinition def : definitions) {
            String baseKey = extractBaseKey(def.key());

            CardState state = determineState(profile, def.key(), unlockedKeys, def.tier(), baseKey, definitions);
            double progress = calculateProgress(profile, def.category(), def.conditionValue());
            String progressText = formatProgressText(profile, def.category(), def.conditionValue(), state);
            String localizedDescription = translateDescription(def.key(), def.conditionValue());

            AchievementDisplayModel displayModel = new AchievementDisplayModel(
                    def.key(), localizedDescription, def.tier(), progress, progressText, state);

            if (def.tier() == AchievementTier.PLATINUM) {
                platinumCards.add(displayModel);
            } else {
                allStandardCards.add(displayModel);
            }
        }

        renderSummaryHeader(allStandardCards);
        renderDynamicCategories(allStandardCards);
        renderPlatinumTab(platinumCards);
    }

    private void renderSummaryHeader(List<AchievementDisplayModel> standardCards) {
        long totalBronze = standardCards.stream().filter(c -> c.getTier() == AchievementTier.BRONZE).count();
        long totalSilver = standardCards.stream().filter(c -> c.getTier() == AchievementTier.SILVER).count();
        long totalGold = standardCards.stream().filter(c -> c.getTier() == AchievementTier.GOLD).count();

        long completedBronze = standardCards.stream()
                .filter(c -> c.getTier() == AchievementTier.BRONZE && c.getState() == CardState.COMPLETED).count();
        long completedSilver = standardCards.stream()
                .filter(c -> c.getTier() == AchievementTier.SILVER && c.getState() == CardState.COMPLETED).count();
        long completedGold = standardCards.stream()
                .filter(c -> c.getTier() == AchievementTier.GOLD && c.getState() == CardState.COMPLETED).count();

        lblBronzeCount.setText(completedBronze + " / " + totalBronze);
        lblSilverCount.setText(completedSilver + " / " + totalSilver);
        lblGoldCount.setText(completedGold + " / " + totalGold);

        btnBronzeSummary.setOnMouseClicked(e -> handleSummaryClick("BRONZE"));
        btnSilverSummary.setOnMouseClicked(e -> handleSummaryClick("SILVER"));
        btnGoldSummary.setOnMouseClicked(e -> handleSummaryClick("GOLD"));
    }

    private void renderDynamicCategories(List<AchievementDisplayModel> standardCards) {
        categoryContainer.getChildren().clear();

        Map<AchievementCategory, List<AchievementDisplayModel>> groupedByCategory = standardCards.stream()
                .collect(Collectors.groupingBy(c -> getCategoryByBaseKey(extractBaseKey(c.getKey()))));

        AchievementCategory[] desiredOrder = {
                AchievementCategory.STREAK,
                AchievementCategory.CHALLENGE,
                AchievementCategory.DAILY_FOCUS,
                AchievementCategory.ACHIEVEMENTS,
                AchievementCategory.RANKING
        };

        for (AchievementCategory category : desiredOrder) {
            List<AchievementDisplayModel> categoryCards = groupedByCategory.getOrDefault(category,
                    Collections.emptyList());

            if (!categoryCards.isEmpty()) {
                try {
                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AchievementCategorySection.fxml"));
                    VBox categorySectionNode = loader.load();

                    AchievementCategorySectionController sectionController = loader.getController();
                    String sectionTitle = getFriendlyCategoryTitle(category);

                    sectionController.setCategoryData(sectionTitle, categoryCards);
                    categoryContainer.getChildren().add(categorySectionNode);

                } catch (IOException e) {
                    throw new IllegalStateException("Erro fatal ao inflar componente vertical de Seção de Categorias.",
                            e);
                }
            }
        }
    }

    private void renderPlatinumTab(List<AchievementDisplayModel> platinums) {
        platinumGridPane.getChildren().clear();

        for (AchievementDisplayModel platina : platinums) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AchievementSubCard.fxml"));
                StackPane subCardNode = loader.load();

                AchievementSubCardController subCardController = loader.getController();
                subCardController.setCardData(platina);

                platinumGridPane.getChildren().add(subCardNode);
            } catch (IOException e) {
                throw new IllegalStateException("Erro fatal ao inflar o componente FXML: AchievementSubCard.fxml", e);
            }
        }
    }

    private void handleSummaryClick(String tierName) {
        System.out.println("Ação acionada: Exibir Caixa de diálogo para medalhas de tipo: " + tierName);
    }

    private String getFriendlyCategoryTitle(AchievementCategory category) {
        return switch (category) {
            case STREAK -> "Ofensiva de Streak";
            case CHALLENGE -> "Desafios Completados";
            case DAILY_FOCUS -> "Foco Acumulado e Ciclos";
            case ACHIEVEMENTS -> "Coleção & Jornada";
            case RANKING -> "Evolução de Elo e Rankings";
        };
    }

    private CardState determineState(Profile profile, String key, Set<String> unlockedKeys,
            AchievementTier tier, String baseKey, List<AchievementDefinition> allDefs) {
        if (unlockedKeys.contains(key)) {
            return CardState.COMPLETED;
        }

        if (tier == AchievementTier.BRONZE || tier == AchievementTier.PLATINUM) {
            return CardState.ACTIVE;
        }

        AchievementTier previousTier = (tier == AchievementTier.GOLD) ? AchievementTier.SILVER : AchievementTier.BRONZE;
        String previousKey = allDefs.stream()
                .filter(d -> extractBaseKey(d.key()).equals(baseKey) && d.tier() == previousTier)
                .map(AchievementDefinition::key)
                .findFirst()
                .orElse("");

        return unlockedKeys.contains(previousKey) ? CardState.ACTIVE : CardState.LOCKED;
    }

    private double calculateProgress(Profile profile, AchievementCategory category, int target) {
        int current = getCurrentProfileValue(profile, category);
        return target <= 0 ? 0.0 : Math.min(1.0, (double) current / target);
    }

    private String formatProgressText(Profile profile, AchievementCategory category, int target, CardState state) {
        if (state == CardState.COMPLETED)
            return "Concluído";
        if (state == CardState.LOCKED)
            return "Bloqueado";

        int current = getCurrentProfileValue(profile, category);
        return Math.min(current, target) + "/" + target;
    }

    private int getCurrentProfileValue(Profile profile, AchievementCategory category) {
        if (profile == null)
            return 0;
        return switch (category) {
            case STREAK -> profile.getMaxStreak();
            case DAILY_FOCUS -> profile.getTotalFocusSessions();
            default -> 0;
        };
    }

    private String translateDescription(String key, int val) {
        if (key == null)
            return "";
        String k = key.toLowerCase();
        if (k.contains("streak_current"))
            return "Atingir uma ofensiva atual de " + val + " dias.";
        if (k.contains("streak_count"))
            return "Atingir a marca de " + val + " streaks acumulados.";
        if (k.contains("focus_daily"))
            return "Manter " + val + " minutos de foco em um único dia.";
        if (k.contains("focus_accumulated"))
            return "Acumular um total de " + val + " minutos de foco.";
        if (k.contains("focus_cycles"))
            return "Completar " + val + " ciclos pomodoro.";
        if (k.contains("focus_total_days"))
            return "Permanecer ativo por " + val + " dias no app.";
        return "Meta de progresso: " + val;
    }

    /**
     * BUG CORRIGIDO: RANKING não era coberto e caía no fallback ACHIEVEMENTS.
     * Adicionado o prefixo "RANKING" antes do fallback final.
     */
    private AchievementCategory getCategoryByBaseKey(String baseKey) {
        if (baseKey == null || baseKey.isEmpty())
            return AchievementCategory.ACHIEVEMENTS;
        String u = baseKey.toUpperCase();
        if (u.startsWith("STREAK"))
            return AchievementCategory.STREAK;
        if (u.startsWith("CHALLENGE"))
            return AchievementCategory.CHALLENGE;
        if (u.startsWith("FOCUS"))
            return AchievementCategory.DAILY_FOCUS;
        if (u.startsWith("RANKING"))
            return AchievementCategory.RANKING; // ← LINHA QUE FALTAVA
        return AchievementCategory.ACHIEVEMENTS;
    }

    /**
     * BUG CORRIGIDO: o bloco `if (u.startsWith("CHALLENGE"))` colapsava TODOS os
     * desafios em "CHALLENGE_TRACK", impedindo o agrupamento correto por trilha
     * no AchievementCategorySectionController. Removido — o mapeamento fino de
     * trilhas de challenge já é feito dentro do resolveTrackKey() do Section.
     */
    private String extractBaseKey(String key) {
        if (key == null || key.isEmpty())
            return "";
        String u = key.toUpperCase().trim();

        if (u.startsWith("STREAK_COUNT")) {
            if (u.endsWith("X3") || u.contains("_X3_"))
                return "STREAK_COUNT_X3";
            if (u.endsWith("X5") || u.contains("_X5_"))
                return "STREAK_COUNT_X5";
            return "STREAK_COUNT";
        }
        if (u.startsWith("STREAK_CURRENT"))
            return "STREAK_CURRENT";
        if (u.startsWith("FOCUS_DAILY"))
            return "FOCUS_DAILY";
        if (u.startsWith("FOCUS_ACCUMULATED"))
            return "FOCUS_ACCUMULATED";
        if (u.startsWith("FOCUS_CYCLES"))
            return "FOCUS_CYCLES";
        if (u.startsWith("FOCUS_TOTAL_DAYS"))
            return "FOCUS_TOTAL_DAYS";
        if (u.startsWith("RANKING"))
            return "RANKING_TIER";
        if (u.startsWith("META"))
            return "META";

        // Para challenges, preserva o prefixo completo até o terceiro segmento
        // para que o resolveTrackKey() do Section consiga distinguir as trilhas.
        // Ex: "challenge_constancy_days_7" → "CHALLENGE_CONSTANCY_DAYS"
        if (u.startsWith("CHALLENGE")) {
            String[] parts = u.split("_");
            if (parts.length >= 3)
                return parts[0] + "_" + parts[1] + "_" + parts[2];
            return parts.length >= 2 ? parts[0] + "_" + parts[1] : u;
        }

        String[] parts = u.split("_");
        return parts.length >= 2 ? parts[0] + "_" + parts[1] : u;
    }
}