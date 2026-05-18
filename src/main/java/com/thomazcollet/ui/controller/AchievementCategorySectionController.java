package com.thomazcollet.ui.controller;

import com.thomazcollet.ui.model.AchievementDisplayModel;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

public class AchievementCategorySectionController {

    @FXML
    private Label categoryTitleLabel;
    @FXML
    private Label categoryGroupProgressLabel;
    @FXML
    private Label lblMiniBronzeCount;
    @FXML
    private Label lblMiniSilverCount;
    @FXML
    private Label lblMiniGoldCount;

    @FXML
    private VBox categoryContainer;

    public void setCategoryData(String title, List<AchievementDisplayModel> categoryCards) {
        if (categoryCards == null || categoryCards.isEmpty()) {
            return;
        }

        categoryTitleLabel.setText(title.toUpperCase());
        categoryContainer.getChildren().clear();

        // 1. Contagem de medalhas do cabeçalho
        long totalBronze = categoryCards.stream().filter(c -> "BRONZE".equals(c.getTier().name())).count();
        long totalSilver = categoryCards.stream().filter(c -> "SILVER".equals(c.getTier().name())).count();
        long totalGold = categoryCards.stream().filter(c -> "GOLD".equals(c.getTier().name())).count();

        long completedBronze = categoryCards.stream()
                .filter(c -> "BRONZE".equals(c.getTier().name()) && "COMPLETED".equals(c.getState().name())).count();
        long completedSilver = categoryCards.stream()
                .filter(c -> "SILVER".equals(c.getTier().name()) && "COMPLETED".equals(c.getState().name())).count();
        long completedGold = categoryCards.stream()
                .filter(c -> "GOLD".equals(c.getTier().name()) && "COMPLETED".equals(c.getState().name())).count();

        lblMiniBronzeCount.setText(completedBronze + " / " + totalBronze);
        lblMiniSilverCount.setText(completedSilver + " / " + totalSilver);
        lblMiniGoldCount.setText(completedGold + " / " + totalGold);

        // 2. Deduplicação por key antes do agrupamento
        List<AchievementDisplayModel> uniqueCards = new ArrayList<>(
                categoryCards.stream()
                        .collect(Collectors.toMap(
                                AchievementDisplayModel::getKey,
                                card -> card,
                                (existing, replacement) -> existing,
                                LinkedHashMap::new))
                        .values());

        Map<String, List<AchievementDisplayModel>> groupedByTrack = uniqueCards.stream()
                .collect(Collectors.groupingBy(c -> resolveTrackKey(c.getKey()), LinkedHashMap::new,
                        Collectors.toList()));

        int totalLines = groupedByTrack.size();
        long completedLines = groupedByTrack.values().stream()
                .filter(list -> list.stream().allMatch(c -> "COMPLETED".equals(c.getState().name())))
                .count();

        categoryGroupProgressLabel.setText(completedLines + " de " + totalLines + " trilhas completadas");

        // 3. Instanciar os GroupCards na tela
        for (Map.Entry<String, List<AchievementDisplayModel>> entry : groupedByTrack.entrySet()) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AchievementGroupCard.fxml"));
                VBox groupCardNode = loader.load();

                groupCardNode.setMinWidth(560.0);
                groupCardNode.setPrefWidth(VBox.USE_COMPUTED_SIZE);
                groupCardNode.setMaxWidth(Double.MAX_VALUE);
                groupCardNode.setAlignment(Pos.CENTER);

                AchievementGroupCardController groupCardController = loader.getController();
                groupCardController.setGroupData(getFriendlyGroupName(entry.getKey()), entry.getValue());

                categoryContainer.getChildren().add(groupCardNode);

            } catch (IOException e) {
                throw new RuntimeException("Falha ao carregar componente AchievementGroupCard na categoria: " + title,
                        e);
            }
        }
    }

    /**
     * Mapeia a chave individual de uma conquista para a chave da sua trilha
     * (GroupCard).
     *
     * REGRAS DE OURO:
     * 1. Cada trilha deve ter exatamente Bronze + Prata + Ouro (3 subcards por
     * GroupCard).
     * Platinas são exibidas na aba separada e não chegam aqui.
     * 2. Para challenge_intensity, a trilha é determinada pelo conditionValue
     * numérico
     * embutido no final da key (ex: _15, _30, _90), NÃO por substrings ambíguas.
     * 3. challenge_constancy_days tem DUAS trilhas distintas:
     * - SHORT (7/15/30 dias) → Bronze, Prata, Ouro da trilha curta
     * - LONG (90/180/365 dias) → Bronze, Prata, Ouro da trilha longa
     */
    private String resolveTrackKey(String key) {
        if (key == null || key.isEmpty())
            return "UNKNOWN";

        String u = key.toUpperCase().trim();

        // --- DAILY FOCUS ---
        if (u.startsWith("FOCUS_DAILY"))
            return "FOCUS_DAILY_HOURS";
        if (u.startsWith("FOCUS_ACCUMULATED"))
            return "FOCUS_ACCUMULATED_HOURS";
        if (u.startsWith("FOCUS_CYCLES"))
            return "FOCUS_CYCLES";
        if (u.startsWith("FOCUS_TOTAL_DAYS"))
            return "FOCUS_TOTAL_DAYS";

        // --- STREAK ---
        if (u.startsWith("STREAK_COUNT")) {
            if (u.endsWith("X3") || u.contains("_X3_"))
                return "STREAK_COUNT_X3";
            if (u.endsWith("X5") || u.contains("_X5_"))
                return "STREAK_COUNT_X5";
            return "STREAK_COUNT";
        }
        if (u.startsWith("STREAK_CURRENT"))
            return "STREAK_CURRENT";

        // --- CHALLENGE: Constância em Dias ---
        // BUG A CORRIGIDO: antes tudo ia para "CHALLENGE_CONSTANCY_DAYS", gerando
        // um GroupCard com 6 subcards (3 da trilha curta + 3 da trilha longa).
        // Agora separamos as duas trilhas pelo valor do limiar de dias na key.
        if (u.startsWith("CHALLENGE_CONSTANCY_DAYS_")) {
            // Extrai o número de dias do final: "CHALLENGE_CONSTANCY_DAYS_90" → 90
            String suffix = u.substring("CHALLENGE_CONSTANCY_DAYS_".length());
            try {
                int days = Integer.parseInt(suffix);
                // Trilha curta: 7, 15, 30 dias | Trilha longa: 90, 180, 365 dias
                return days <= 30 ? "CHALLENGE_CONSTANCY_DAYS_SHORT" : "CHALLENGE_CONSTANCY_DAYS_LONG";
            } catch (NumberFormatException e) {
                return "CHALLENGE_CONSTANCY_DAYS_SHORT";
            }
        }

        // --- CHALLENGE: Dias Perfeitos ---
        if (u.startsWith("CHALLENGE_PERFECT_DAYS"))
            return "CHALLENGE_PERFECT_DAYS";

        // --- CHALLENGE: Contadores de Constância ---
        if (u.startsWith("CHALLENGE_COUNT_CONSTANCY_MIN_7"))
            return "CHALLENGE_COUNT_CONSTANCY_MIN_7";
        if (u.startsWith("CHALLENGE_COUNT_CONSTANCY_MIN_15"))
            return "CHALLENGE_COUNT_CONSTANCY_MIN_15";
        if (u.startsWith("CHALLENGE_COUNT_CONSTANCY_MIN_30"))
            return "CHALLENGE_COUNT_CONSTANCY_MIN_30";

        // --- CHALLENGE: Intensidade ---
        // BUG B CORRIGIDO: a lógica anterior usava u.contains("_90"), u.contains("_30")
        // etc., que são substrings ambíguas. Ex: "CHALLENGE_INTENSITY_HOURS_270"
        // contém "_270" que por acidente bate com "_30" (2**70** → não, mas "_120"
        // contém "1**20**" e u.contains("_20") = true → ia para INTENSITY_15_DAYS ❌).
        // Solução: extraímos o conditionValue NUMÉRICO do final da key e comparamos
        // com os dias de ciclo conhecidos (15, 30, 90).
        if (u.startsWith("CHALLENGE_INTENSITY_HOURS_")) {
            // Keys especiais que não seguem o padrão numérico de conditionValue
            if (u.equals("CHALLENGE_INTENSITY_HOURS_365_TRIPLE"))
                return "CHALLENGE_INTENSITY_SPECIAL";

            // Para as demais, o conditionValue está embutido no final da key como
            // o número de horas. A trilha é determinada pelo número de DIAS do ciclo,
            // que está mapeado estaticamente por faixa de horas conhecida:
            // Ciclo 15 dias → horas: 10, 20, 40 (60 é platina, não exibida aqui)
            // Ciclo 30 dias → horas: 22, 44, 90 (120 é platina)
            // Ciclo 90 dias → horas: 66, 132, 200 (270 é platina)
            // Em vez de parsear horas, usamos o sufixo da key diretamente via switch
            // para mapear sem ambiguidade.
            String suffix = u.substring("CHALLENGE_INTENSITY_HOURS_".length());
            return switch (suffix) {
                case "10", "20", "40" -> "CHALLENGE_INTENSITY_15_DAYS";
                case "22", "44", "90" -> "CHALLENGE_INTENSITY_30_DAYS";
                case "66", "132", "200" -> "CHALLENGE_INTENSITY_90_DAYS";
                // Platinas (60, 120, 270) não chegam aqui — são filtradas na aba Platina.
                // Se chegarem por algum motivo, agrupamos em "special" e não quebra.
                default -> "CHALLENGE_INTENSITY_SPECIAL";
            };
        }

        // --- CHALLENGE: Contador de Intensidade ---
        if (u.startsWith("CHALLENGE_COUNT_INTENSITY_MIN_15"))
            return "CHALLENGE_COUNT_INTENSITY_MIN_15";

        // --- META-CONQUISTAS ---
        if (u.startsWith("META_TOTAL_PLATINUM"))
            return "META_TOTAL_PLATINUM";
        if (u.startsWith("META_TOTAL"))
            return "META_TOTAL_COUNT";
        // BUG C CORRIGIDO: meta_first_gold/platinum foram renomeadas para
        // meta_gold_count_X.
        // A checagem antiga (META_FIRST) não cobria mais as novas keys, fazendo cada
        // conquista criar seu próprio GroupCard isolado em vez de agrupar na trilha.
        if (u.startsWith("META_GOLD_COUNT") || u.equals("META_ALL_GOLD"))
            return "META_GOLD_TRACK";

        // --- RANKING ---
        if (u.startsWith("RANKING_TIER"))
            return "RANKING_TIERS";

        return u;
    }

    private String getFriendlyGroupName(String trackKey) {
        return switch (trackKey) {
            case "FOCUS_DAILY_HOURS" -> "Meta Diária de Foco";
            case "FOCUS_ACCUMULATED_HOURS" -> "Tempo de Foco Acumulado";
            case "FOCUS_CYCLES" -> "Ciclos de Pomodoro Acumulados";
            case "FOCUS_TOTAL_DAYS" -> "Dias Ativos no Aplicativo";
            case "STREAK_CURRENT" -> "Constância: Ofensiva Atual de Dias";
            case "STREAK_COUNT" -> "Volume de Ofensivas Concluídas";
            case "STREAK_COUNT_X3" -> "Rei do Foco: Trilha Multiplicadora X3";
            case "STREAK_COUNT_X5" -> "Rei do Foco: Trilha Multiplicadora X5";
            // BUG A: os dois novos nomes das trilhas de constância separadas
            case "CHALLENGE_CONSTANCY_DAYS_SHORT" -> "Desafio de Constância: Trilha Iniciante (7 a 30 dias)";
            case "CHALLENGE_CONSTANCY_DAYS_LONG" -> "Desafio de Constância: Trilha Avançada (90 a 365 dias)";
            case "CHALLENGE_PERFECT_DAYS" -> "Desafio: Dias Perfeitos Completados";
            case "CHALLENGE_COUNT_CONSTANCY_MIN_7" -> "Desafio Repetição: Constância Mínima 7 Dias";
            case "CHALLENGE_COUNT_CONSTANCY_MIN_15" -> "Desafio Repetição: Constância Mínima 15 Dias";
            case "CHALLENGE_COUNT_CONSTANCY_MIN_30" -> "Desafio Repetição: Constância Mínima 30 Dias";
            case "CHALLENGE_INTENSITY_15_DAYS" -> "Desafio Intensidade: Ciclo de 15 Dias";
            case "CHALLENGE_INTENSITY_30_DAYS" -> "Desafio Intensidade: Ciclo de 30 Dias";
            case "CHALLENGE_INTENSITY_90_DAYS" -> "Desafio Intensidade: Ciclo de 90 Dias";
            case "CHALLENGE_INTENSITY_SPECIAL" -> "Desafio Intensidade: Feito Lendário";
            case "CHALLENGE_COUNT_INTENSITY_MIN_15" -> "Repetição de Intensidade Mínima";
            case "META_TOTAL_PLATINUM" -> "Jornada das Platinas";
            case "META_TOTAL_COUNT" -> "Volume Total de Conquistas";
            // BUG C: novo nome para a trilha meta_gold_count_X + meta_all_gold
            case "META_GOLD_TRACK" -> "Coleção: Conquistador de Ouros";
            case "RANKING_TIERS" -> "Evolução de Elo do Sistema";
            default -> trackKey.replace("_", " ");
        };
    }
}