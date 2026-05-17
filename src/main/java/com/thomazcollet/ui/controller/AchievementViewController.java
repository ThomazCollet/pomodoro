package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.AchievementCategory;
import com.thomazcollet.domain.model.AchievementTier;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.repository.AchievementRepository;
import com.thomazcollet.service.AchievementService;
import com.thomazcollet.service.AchievementService.AchievementDefinition;
import com.thomazcollet.ui.model.AchievementDisplayModel;
import com.thomazcollet.ui.model.AchievementDisplayModel.CardState;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;

import java.io.IOException;
import java.util.*;

/**
 * Controller responsável por gerenciar a visualização da Central de Progresso e
 * Conquistas.
 */
public class AchievementViewController {

    @FXML
    private FlowPane streakCategoryPane;
    @FXML
    private FlowPane challengeCategoryPane;
    @FXML
    private FlowPane focusCategoryPane;
    @FXML
    private FlowPane metaCategoryPane;
    @FXML
    private FlowPane platinumGridPane;

    private AchievementRepository achievementRepository;

    /**
     * Inicializa a tela mapeando as conquistas do perfil atual e renderizando a
     * interface.
     */
    public void initializeData(Profile profile, AchievementRepository achievementRepository,
            List<AchievementDefinition> definitions) {
        this.achievementRepository = achievementRepository;

        Set<String> unlockedKeys = achievementRepository.findUnlockedKeysByProfileId(profile.getId());
        Map<String, List<AchievementDisplayModel>> standardGroups = new LinkedHashMap<>();
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
                standardGroups.computeIfAbsent(baseKey, k -> new ArrayList<>()).add(displayModel);
            }
        }

        renderCentralTabs(standardGroups);
        renderPlatinumTab(platinumCards);
    }

    /**
     * Determina o estado atual do card aplicando as regras de dependência em cadeia
     * (Bronze -> Prata -> Ouro).
     */
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

    /**
     * Calcula a porcentagem de preenchimento da barra de progresso.
     */
    private double calculateProgress(Profile profile, AchievementCategory category, int target) {
        double current = switch (category) {
            case STREAK -> profile.getMaxStreak();
            case DAILY_FOCUS -> profile.getTotalFocusSessions();
            default -> 0.0;
        };
        return target <= 0 ? 0.0 : Math.min(1.0, current / target);
    }

    /**
     * Formata o texto descritivo do progresso atual em relação à meta.
     */
    private String formatProgressText(Profile profile, AchievementCategory category, int target, CardState state) {
        if (state == CardState.COMPLETED)
            return "Concluído";
        if (state == CardState.LOCKED)
            return "Bloqueado";

        int current = switch (category) {
            case STREAK -> profile.getMaxStreak();
            case DAILY_FOCUS -> profile.getTotalFocusSessions();
            default -> 0;
        };
        return Math.min(current, target) + "/" + target;
    }

    /**
     * Traduz e monta as strings de descrição com base nas chaves do sistema.
     */
    private String translateDescription(String key, int val) {
        if (key == null)
            return "";
        if (key.startsWith("streak_current"))
            return "Atingir um total de " + val + " streaks.";
        if (key.startsWith("focus_cycles"))
            return "Completar " + val + " ciclos pomodoro completos.";
        return "Meta de progresso: " + val;
    }

    /**
     * Infla e agrupa os cards de categorias padrão (Bronze, Prata, Ouro).
     */
    private void renderCentralTabs(Map<String, List<AchievementDisplayModel>> groups) {
        clearAllPanes();

        groups.forEach((baseKey, childCards) -> {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AchievementGroupCard.fxml"));
                VBox groupNode = loader.load();

                AchievementGroupCardController groupController = loader.getController();
                String creativeTitle = getCreativeTitleByBaseKey(baseKey);

                childCards.sort(Comparator.comparing(AchievementDisplayModel::getTier));
                groupController.setGroupData(creativeTitle, childCards);

                FlowPane targetPane = getPaneByCategory(getCategoryByBaseKey(baseKey));
                if (targetPane != null) {
                    targetPane.getChildren().add(groupNode);
                }

            } catch (IOException e) {
                throw new IllegalStateException("Erro fatal ao inflar o componente FXML: AchievementGroupCard.fxml", e);
            }
        });
    }

    /**
     * Infla os troféus de Platina diretamente no grid principal da segunda aba.
     */
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

    private String getCreativeTitleByBaseKey(String baseKey) {
        return switch (baseKey) {
            case "streak_current" -> "Rei do Foco";
            case "streak_count_5" -> "Constância Inabalável";
            case "focus_cycles" -> "Medalhista Olímpico";
            case "focus_accumulated" -> "Marcos de Foco";
            default -> "Explorador da Jornada";
        };
    }

    private AchievementCategory getCategoryByBaseKey(String baseKey) {
        if (baseKey == null)
            return AchievementCategory.ACHIEVEMENTS;
        if (baseKey.startsWith("streak"))
            return AchievementCategory.STREAK;
        if (baseKey.startsWith("challenge"))
            return AchievementCategory.CHALLENGE;
        if (baseKey.startsWith("focus"))
            return AchievementCategory.DAILY_FOCUS;
        return AchievementCategory.ACHIEVEMENTS;
    }

    private FlowPane getPaneByCategory(AchievementCategory category) {
        return switch (category) {
            case STREAK -> streakCategoryPane;
            case CHALLENGE -> challengeCategoryPane;
            case DAILY_FOCUS -> focusCategoryPane;
            case ACHIEVEMENTS, RANKING -> metaCategoryPane;
        };
    }

    private void clearAllPanes() {
        streakCategoryPane.getChildren().clear();
        challengeCategoryPane.getChildren().clear();
        focusCategoryPane.getChildren().clear();
        metaCategoryPane.getChildren().clear();
    }

    private String extractBaseKey(String key) {
        if (key == null || key.isEmpty())
            return "";
        String[] parts = key.split("_");
        return parts.length >= 2 ? parts[0] + "_" + parts[1] : key;
    }
}