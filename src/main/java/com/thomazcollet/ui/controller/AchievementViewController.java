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

    /**
     * Gera descrições amigáveis e específicas para cada conquista,
     * baseadas nos textos da planilha mas refinadas para o usuário final.
     * Cada key tem seu texto exato em vez de um template genérico.
     */
    private String translateDescription(String key, int val) {
        if (key == null)
            return "";

        // Fórmula da planilha: "nome criativo + descrição amigável ao usuário"
        // Textos em duas linhas: linha 1 = título criativo, linha 2 = instrução clara
        return switch (key.toLowerCase()) {

            // ── STREAK: Ofensiva Atual ──────────────────────────────────────────
            case "streak_current_5" -> "Faísca Inicial 🔥\nMantenha uma ofensiva ativa de 5 dias seguidos.";
            case "streak_current_12" -> "Chama Crescente 🔥\nAlcance 12 dias consecutivos sem quebrar a ofensiva.";
            case "streak_current_15" -> "Máquina de Hábitos 💪\nSustente uma ofensiva de 15 dias sem parar.";
            case "streak_current_30" -> "Lenda da Constância 🏆\nConquiste 30 dias seguidos de foco diário.";

            // ── STREAK: Contador X3 ─────────────────────────────────────────────
            case "streak_count_5_x3" -> "Rei do Recomeço 🥉\nAtinja uma ofensiva de 5 dias por 3 vezes.";
            case "streak_count_12_x3" -> "Persistência Total 🥈\nChegue a 12 dias de streak por 3 vezes.";
            case "streak_count_15_x3" -> "Guerreiro do Foco 🥇\nRepita uma ofensiva de 15 dias por 3 vezes.";
            case "streak_count_30_x3" -> "Triplicador Lendário 💎\nAtinja 30 dias de ofensiva por 3 vezes.";

            // ── STREAK: Contador X5 ─────────────────────────────────────────────
            case "streak_count_5_x5" -> "Cinco Inícios 🥉\nAtinja uma ofensiva de 5 dias por 5 vezes.";
            case "streak_count_12_x5" -> "Mestre da Repetição 🥈\nChegue a 12 dias de streak por 5 vezes.";
            case "streak_count_15_x5" -> "Cinco Vezes Guerreiro 🥇\nRepita uma ofensiva de 15 dias por 5 vezes.";
            case "streak_count_30_x5" -> "Pentacampeão do Foco 💎\nAtinja 30 dias de ofensiva por 5 vezes.";

            // ── FOCO DIÁRIO ─────────────────────────────────────────────────────
            case "focus_daily_2h_hours" -> "Bloco de Foco 🥉\nConcentre-se por 2 horas em um único dia.";
            case "focus_daily_3h_hours" -> "Turno Produtivo 🥈\nAcumule 3 horas de foco em um mesmo dia.";
            case "focus_daily_4h_hours" -> "Modo Ultra Foco 🥇\nDomine 4 horas de concentração em um único dia.";
            case "focus_daily_6h_hours" -> "Dia de Elite 💎\nAtinja 6 horas de foco absoluto em um único dia.";

            // ── FOCO ACUMULADO ──────────────────────────────────────────────────
            case "focus_accumulated_12h_hours" -> "Primeiras Horas 🥉\nAcumule 12 horas de foco ao longo do tempo.";
            case "focus_accumulated_24h_hours" -> "Um Dia Inteiro 🥈\nSome 24 horas de concentração na sua jornada.";
            case "focus_accumulated_300h_hours" -> "Mestre do Tempo 🥇\nAcumule 300 horas de foco no histórico.";
            case "focus_accumulated_1000h_hours" -> "As 1000 Horas 💎\nAtinja a marca lendária de 1.000 horas de foco.";

            // ── CICLOS POMODORO ─────────────────────────────────────────────────
            case "focus_cycles_1_bronze" -> "Primeiro Ciclo 🥉\nConclua seu primeiro ciclo Pomodoro completo.";
            case "focus_cycles_10_silver" -> "Ritmo Estabelecido 🥈\nComplete 10 ciclos Pomodoro ao todo.";
            case "focus_cycles_25_gold" -> "Veterano dos Ciclos 🥇\nFinalize 25 ciclos Pomodoro na plataforma.";
            case "focus_cycles_100_platinum" -> "Centenário do Foco 💎\nConclua 100 ciclos Pomodoro completos.";

            // ── DIAS ATIVOS ─────────────────────────────────────────────────────
            case "focus_total_days_15_bronze" -> "Primeiros Passos 🥉\nRegistre foco em pelo menos 15 dias diferentes.";
            case "focus_total_days_30_silver" -> "Um Mês Ativo 🥈\nEsteja presente com foco em 30 dias distintos.";
            case "focus_total_days_90_gold" -> "Trimestre de Ouro 🥇\nMantenha-se ativo por 90 dias de foco.";
            case "focus_total_days_365_platinum" ->
                "Um Ano de Dedicação 💎\nRegistre foco em 365 dias ao longo da jornada.";

            // ── DESAFIO: Constância — Trilha Iniciante ──────────────────────────
            case "challenge_constancy_days_7" -> "Semana de Desafio 🥉\nConclua um desafio de constância de 7+ dias.";
            case "challenge_constancy_days_15" ->
                "Quinzena Cumprida 🥈\nFinalize um desafio de constância de 15+ dias.";
            case "challenge_constancy_days_30" -> "Mês Completo 🥇\nConclua um desafio de constância de 30+ dias.";

            // ── DESAFIO: Constância — Trilha Avançada ───────────────────────────
            case "challenge_constancy_days_90" ->
                "Trimestre do Desafio 🥉\nVença um desafio de constância de 90+ dias.";
            case "challenge_constancy_days_180" ->
                "Meio Ano de Constância 🥈\nFinalize um desafio de 180+ dias sem desistir.";
            case "challenge_constancy_days_365" -> "Ano Épico 🥇\nConclua um desafio de constância de 365+ dias.";

            // ── DESAFIO: Dias Perfeitos ──────────────────────────────────────────
            case "challenge_perfect_days_5" -> "Perfeccionista 🥉\nConclua 5 dias de desafio sem perder nenhuma vida.";
            case "challenge_perfect_days_7" ->
                "Semana Imaculada 🥈\nFinalize 7 dias de desafio com todas as vidas intactas.";
            case "challenge_perfect_days_15" ->
                "Quinzena Impecável 🥇\nConclua 15 dias de desafio sem gastar nenhuma vida.";
            case "challenge_perfect_days_30" ->
                "Mestre da Perfeição 💎\nAtinja 30 dias de desafio sem perder uma vida sequer.";

            // ── DESAFIO: Repetição Constância ≥ 7 dias ──────────────────────────
            case "challenge_count_constancy_min_7_3" ->
                "Tripla Constância 🥉\nConclua 3 desafios de constância de 7+ dias.";
            case "challenge_count_constancy_min_7_6" ->
                "Seis Semanas de Garra 🥈\nFinalize 6 desafios de constância de 7+ dias.";
            case "challenge_count_constancy_min_7_10" ->
                "Dez e Constante 🥇\nConclua 10 desafios de constância de 7+ dias.";
            case "challenge_count_constancy_min_7_24" ->
                "Veterano da Constância 💎\nFinalize 24 desafios de constância de 7+ dias.";

            // ── DESAFIO: Repetição Constância ≥ 15 dias ─────────────────────────
            case "challenge_count_constancy_min_15_3" ->
                "Tripla Quinzena 🥉\nConclua 3 desafios de constância de 15+ dias.";
            case "challenge_count_constancy_min_15_6" ->
                "Seis Quinzenas 🥈\nFinalize 6 desafios de constância de 15+ dias.";
            case "challenge_count_constancy_min_15_10" ->
                "Dez Quinzenas 🥇\nConclua 10 desafios de constância de 15+ dias.";
            case "challenge_count_constancy_min_15_24" ->
                "Mestre das Quinzenas 💎\nFinalize 24 desafios de constância de 15+ dias.";

            // ── DESAFIO: Repetição Constância ≥ 30 dias ─────────────────────────
            case "challenge_count_constancy_min_30_3" ->
                "Trio Mensal 🥉\nConclua 3 desafios de constância de 30+ dias.";
            case "challenge_count_constancy_min_30_6" ->
                "Seis Meses de Desafio 🥈\nFinalize 6 desafios de constância de 30+ dias.";
            case "challenge_count_constancy_min_30_10" ->
                "Dez Meses Superados 🥇\nConclua 10 desafios de constância de 30+ dias.";
            case "challenge_count_constancy_min_30_18" ->
                "Lenda dos Desafios 💎\nFinalize 18 desafios de constância de 30+ dias.";

            // ── DESAFIO: Intensidade — Ciclo de 15 Dias ─────────────────────────
            case "challenge_intensity_hours_10" -> "Aquecimento 🥉\nConclua um desafio de 15 dias com 10h+ de foco.";
            case "challenge_intensity_hours_20" ->
                "Intensidade Crescente 🥈\nConclua um desafio de 15 dias com 20h+ de foco.";
            case "challenge_intensity_hours_40" ->
                "Bloco de Potência 🥇\nAtinja 40h+ de foco em um desafio de 15 dias.";
            case "challenge_intensity_hours_60" ->
                "Quinzena de Fogo 💎\nConclua um desafio de 15 dias com 60h+ de foco.";

            // ── DESAFIO: Intensidade — Ciclo de 30 Dias ─────────────────────────
            case "challenge_intensity_hours_22" -> "Mês com Ritmo 🥉\nConclua um desafio de 30 dias com 22h+ de foco.";
            case "challenge_intensity_hours_44" -> "Dobro do Esforço 🥈\nAtinja 44h+ de foco em um desafio de 30 dias.";
            case "challenge_intensity_hours_90" -> "Mês de Ouro 🥇\nConclua um desafio de 30 dias com 90h+ de foco.";
            case "challenge_intensity_hours_120" ->
                "Mês Lendário 💎\nAtinja 120h+ de foco em um único desafio de 30 dias.";

            // ── DESAFIO: Intensidade — Ciclo de 90 Dias ─────────────────────────
            case "challenge_intensity_hours_66" ->
                "Trimestre do Bem 🥉\nConclua um desafio de intensidade de 90+ dias.";
            case "challenge_intensity_hours_132" ->
                "Meio Ano de Força 🥈\nFinalize um desafio de intensidade de 180+ dias.";
            case "challenge_intensity_hours_200" ->
                "Um Ano de Elite 🥇\nConclua um desafio de intensidade de 365+ dias.";
            case "challenge_intensity_hours_270" ->
                "Trimestre de Lenda 💎\nAtinja 270h+ de foco em um desafio de 90 dias.";

            // ── DESAFIO: Platina Especial ────────────────────────────────────────
            case "challenge_intensity_hours_365_triple" ->
                "A Trindade Épica 💎\nConclua 3 desafios de intensidade de 365 dias.";

            // ── DESAFIO: Repetição de Intensidade ───────────────────────────────
            case "challenge_count_intensity_min_15_3" ->
                "Trio de Intensidade 🥉\nConclua 3 desafios de intensidade de 15+ dias.";
            case "challenge_count_intensity_min_15_6" ->
                "Seis Vezes Intenso 🥈\nFinalize 6 desafios de intensidade de 15+ dias.";
            case "challenge_count_intensity_min_15_10" ->
                "Dez Desafios Intensos 🥇\nConclua 10 desafios de intensidade de 15+ dias.";
            case "challenge_count_intensity_min_15_30" ->
                "Veterano da Intensidade 💎\nFinalize 30 desafios de intensidade de 15+ dias.";

            // ── META: Conquistador de Ouros ──────────────────────────────────────
            case "meta_gold_count_1" -> "Primeiro Ouro 🥉\nObtenha sua primeira conquista de nível Ouro.";
            case "meta_gold_count_5" -> "Colecionador de Ouros 🥈\nReúna 5 conquistas de nível Ouro.";
            case "meta_gold_count_10" -> "Medalhista de Ouro 🥇\nAlcance 10 conquistas de nível Ouro.";
            case "meta_all_gold" -> "Gabarito Total 💎\nDesbloqueie todas as conquistas de Ouro do sistema.";

            // ── META: Total de Conquistas ────────────────────────────────────────
            case "meta_total_5" -> "Colecionador Iniciante 🥉\nDesbloqueie um total de 5 conquistas.";
            case "meta_total_15" -> "Caçador de Medalhas 🥈\nAcumule um total de 15 conquistas.";
            case "meta_total_30" -> "Grande Colecionador 🥇\nAtinja a marca de 30 conquistas desbloqueadas.";

            // ── META: Jornada das Platinas ───────────────────────────────────────
            case "meta_total_platinum_1" -> "Primeira Relíquia 🥉\nObtenha sua primeira conquista de Platina.";
            case "meta_total_platinum_3" -> "Trio de Platina 🥈\nReúna 3 conquistas de nível Platina.";
            case "meta_total_platinum_7" -> "Coleção Sagrada 🥇\nAlcance 7 conquistas lendárias de Platina.";
            case "meta_total_platinum_12" -> "Senhor das Platinas 💎\nDesbloqueie 12 conquistas de Platina.";

            // ── RANKING ──────────────────────────────────────────────────────────
            case "ranking_tier_c" -> "Primeiro Posto 🥉\nAlcance o Rank C acumulando 2.000 XP.";
            case "ranking_tier_a" -> "Escalada de Elite 🥈\nAtinja o Rank A com 15.000 XP acumulados.";
            case "ranking_tier_s" -> "Alto Desempenho 🥇\nConquiste o cobiçado Rank S (40.000 XP).";
            case "ranking_tier_ss" -> "Ápice Absoluto 💎\nAlcance o lendário Rank SS com 100.000 XP.";

            default -> "Meta: " + val;
        };
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