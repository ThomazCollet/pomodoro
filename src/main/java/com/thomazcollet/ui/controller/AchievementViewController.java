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

/**
 * Controller responsável por gerenciar a visualização da Central de Progresso e
 * Conquistas.
 */
public class AchievementViewController {

    // Elementos do Header de Resumo (Medalhômetro)
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

    // Contêiner de fluxo rolável vertical
    @FXML
    private VBox categoryContainer;

    // Aba isolada para Troféus de Platina
    @FXML
    private FlowPane platinumGridPane;

    private AchievementRepository achievementRepository;

    /**
     * Inicializa a tela mapeando as conquistas do perfil atual e renderizando a
     * interface.
     */
    public void initializeData(Profile profile, AchievementRepository achievementRepository,
            List<AchievementDefinition> definitions) {

        // Princípio Fail-Fast para garantir a integridade dos parâmetros de entrada
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

        // 1. Renderiza o painel de contagem global fixa (Header)
        renderSummaryHeader(allStandardCards);

        // 2. Processa o agrupamento vertical dinâmico por Categoria
        renderDynamicCategories(allStandardCards);

        // 3. Renderiza a aba isolada de Troféus de Platina
        renderPlatinumTab(platinumCards);
    }

    /**
     * Calcula as métricas globais de medalhas conquistadas e atualiza o Header
     * fixo.
     */
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

        // Configura ações de clique do usuário nas caixas de resumo
        btnBronzeSummary.setOnMouseClicked(e -> handleSummaryClick("BRONZE"));
        btnSilverSummary.setOnMouseClicked(e -> handleSummaryClick("SILVER"));
        btnGoldSummary.setOnMouseClicked(e -> handleSummaryClick("GOLD"));
    }

    /**
     * Cria e injeta as seções dinâmicas de categoria verticalmente dentro do
     * ScrollPane pai.
     */
    private void renderDynamicCategories(List<AchievementDisplayModel> standardCards) {
        categoryContainer.getChildren().clear();

        // Agrupa as conquistas com base na categoria conceitual limpa e mapeada
        Map<AchievementCategory, List<AchievementDisplayModel>> groupedByCategory = standardCards.stream()
                .collect(Collectors.groupingBy(c -> getCategoryByBaseKey(extractBaseKey(c.getKey()))));

        // Mantemos a ordem visual estipulada para a interface fluir com elegância
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

                    // Executa a chamada limpa mapeada para a assinatura original de 2 parâmetros
                    sectionController.setCategoryData(sectionTitle, categoryCards);
                    categoryContainer.getChildren().add(categorySectionNode);

                } catch (IOException e) {
                    throw new IllegalStateException("Erro fatal ao inflar componente vertical de Seção de Categorias.",
                            e);
                }
            }
        }
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

    /**
     * Gatilho de evento para cliques nos cartões de resumo do topo.
     */
    private void handleSummaryClick(String tierName) {
        System.out.println("Ação acionada: Exibir Caixa de diálogo para medalhas de tipo: " + tierName);
    }

    private String getFriendlyCategoryTitle(AchievementCategory category) {
        return switch (category) {
            case STREAK -> "Ofensiva de Streak";
            case CHALLENGE -> "Desafios Completados";
            case DAILY_FOCUS -> "Foco Acumulado e Ciclos";
            case ACHIEVEMENTS -> "Coleção & Jornada";
            case RANKING -> "Evolução de Elo e Rankings"; // O título exclusivo que vai substituir "CATEGORIA"
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

    /**
     * Retorna o valor numérico atual do perfil para exibição nas barras de
     * progresso.
     *
     * Nota: conquistas da categoria STREAK têm seu progresso real calculado
     * pelo StreakEvaluator via FocusSessionRepository
     * (findCurrentStreakDaysByProfileId
     * e countTimesStreakTargetWasReached). O Profile não armazena mais esse dado,
     * portanto retornamos 0 aqui — o estado visual correto
     * (ACTIVE/COMPLETED/LOCKED)
     * já é suficiente para transmitir o progresso ao usuário.
     */
    private int getCurrentProfileValue(Profile profile, AchievementCategory category) {
        if (profile == null)
            return 0;
        return switch (category) {
            case DAILY_FOCUS -> profile.getTotalFocusSessions();
            default -> 0;
        };
    }

    private String translateDescription(String key, int val) {
        if (key == null)
            return "";
        return switch (key.toLowerCase()) {
            case "streak_current_5" -> "Faísca Inicial — 5 dias de ofensiva";
            case "streak_current_12" -> "Chama Crescente — 12 dias seguidos";
            case "streak_current_15" -> "Máquina de Hábitos — 15 dias seguidos";
            case "streak_current_30" -> "Lenda da Constância — 30 dias seguidos";
            case "streak_count_5_x3" -> "Rei do Recomeço — streak 5 dias × 3";
            case "streak_count_12_x3" -> "Persistência Total — 12 dias × 3";
            case "streak_count_15_x3" -> "Guerreiro do Foco — 15 dias × 3";
            case "streak_count_30_x3" -> "Triplicador Lendário — 30 dias × 3";
            case "streak_count_5_x5" -> "Cinco Inícios — streak 5 dias × 5";
            case "streak_count_12_x5" -> "Mestre da Repetição — 12 dias × 5";
            case "streak_count_15_x5" -> "Cinco Vezes Guerreiro — 15 dias × 5";
            case "streak_count_30_x5" -> "Pentacampeão do Foco — 30 dias × 5";
            case "focus_daily_2h_hours" -> "Bloco de Foco — 2h em um único dia";
            case "focus_daily_3h_hours" -> "Turno Produtivo — 3h em um único dia";
            case "focus_daily_4h_hours" -> "Modo Ultra Foco — 4h em um único dia";
            case "focus_daily_6h_hours" -> "Dia de Elite — 6h de foco absoluto";
            case "focus_accumulated_12h_hours" -> "Primeiras Horas — 12h acumuladas";
            case "focus_accumulated_24h_hours" -> "Um Dia Inteiro — 24h acumuladas";
            case "focus_accumulated_300h_hours" -> "Mestre do Tempo — 300h acumuladas";
            case "focus_accumulated_1000h_hours" -> "As 1000 Horas — marco lendário";
            case "focus_cycles_1_bronze" -> "Primeiro Ciclo — 1 pomodoro";
            case "focus_cycles_10_silver" -> "Ritmo Estabelecido — 10 ciclos";
            case "focus_cycles_25_gold" -> "Veterano dos Ciclos — 25 ciclos";
            case "focus_cycles_100_platinum" -> "Centenário do Foco — 100 ciclos";
            case "focus_total_days_15_bronze" -> "Primeiros Passos — 15 dias ativos";
            case "focus_total_days_30_silver" -> "Um Mês Ativo — 30 dias com foco";
            case "focus_total_days_90_gold" -> "Trimestre de Ouro — 90 dias";
            case "focus_total_days_365_platinum" -> "Um Ano de Dedicação — 365 dias";
            case "challenge_constancy_days_7" -> "Semana de Desafio — 7+ dias";
            case "challenge_constancy_days_15" -> "Quinzena Cumprida — 15+ dias";
            case "challenge_constancy_days_30" -> "Mês Completo — 30+ dias";
            case "challenge_constancy_days_90" -> "Trimestre do Desafio — 90+ dias";
            case "challenge_constancy_days_180" -> "Meio Ano de Constância — 180+ dias";
            case "challenge_constancy_days_365" -> "Ano Épico — 365+ dias";
            case "challenge_perfect_days_5" -> "Perfeccionista — 5 dias perfeitos";
            case "challenge_perfect_days_7" -> "Semana Imaculada — 7 perfeitos";
            case "challenge_perfect_days_15" -> "Quinzena Impecável — 15 perfeitos";
            case "challenge_perfect_days_30" -> "Mestre da Perfeição — 30 perfeitos";
            case "challenge_count_constancy_min_7_3" -> "Tripla Constância — 3× (7+ dias)";
            case "challenge_count_constancy_min_7_6" -> "Seis Semanas de Garra — 6×";
            case "challenge_count_constancy_min_7_10" -> "Dez e Constante — 10× (7+ d)";
            case "challenge_count_constancy_min_7_24" -> "Veterano — 24× (7+ dias)";
            case "challenge_count_constancy_min_15_3" -> "Tripla Quinzena — 3× (15+ dias)";
            case "challenge_count_constancy_min_15_6" -> "Seis Quinzenas — 6× (15+ dias)";
            case "challenge_count_constancy_min_15_10" -> "Dez Quinzenas — 10× (15+ dias)";
            case "challenge_count_constancy_min_15_24" -> "Mestre das Quinzenas — 24×";
            case "challenge_count_constancy_min_30_3" -> "Trio Mensal — 3× (30+ dias)";
            case "challenge_count_constancy_min_30_6" -> "Seis Meses de Desafio — 6×";
            case "challenge_count_constancy_min_30_10" -> "Dez Meses Superados — 10×";
            case "challenge_count_constancy_min_30_18" -> "Lenda dos Desafios — 18×";
            case "challenge_intensity_hours_10" -> "Aquecimento — 10h em 15 dias";
            case "challenge_intensity_hours_20" -> "Intensidade Crescente — 20h/15d";
            case "challenge_intensity_hours_40" -> "Bloco de Potência — 40h/15 dias";
            case "challenge_intensity_hours_60" -> "Quinzena de Fogo — 60h/15 dias";
            case "challenge_intensity_hours_22" -> "Mês com Ritmo — 22h em 30 dias";
            case "challenge_intensity_hours_44" -> "Dobro do Esforço — 44h/30 dias";
            case "challenge_intensity_hours_90" -> "Mês de Ouro — 90h em 30 dias";
            case "challenge_intensity_hours_120" -> "Mês Lendário — 120h em 30 dias";
            case "challenge_intensity_hours_66" -> "Trimestre do Bem — 90+ dias";
            case "challenge_intensity_hours_132" -> "Meio Ano de Força — 180+ dias";
            case "challenge_intensity_hours_200" -> "Um Ano de Elite — 365+ dias";
            case "challenge_intensity_hours_270" -> "Trimestre de Lenda — 270h/90d";
            case "challenge_intensity_hours_365_triple" -> "A Trindade Épica — 3× 365 dias";
            case "challenge_count_intensity_min_15_3" -> "Trio de Intensidade — 3×";
            case "challenge_count_intensity_min_15_6" -> "Seis Vezes Intenso — 6×";
            case "challenge_count_intensity_min_15_10" -> "Dez Desafios Intensos — 10×";
            case "challenge_count_intensity_min_15_30" -> "Veterano da Intensidade — 30×";
            case "meta_gold_count_1" -> "Primeiro Ouro — 1 conquista ouro";
            case "meta_gold_count_5" -> "Colecionador de Ouros — 5 ouros";
            case "meta_gold_count_10" -> "Medalhista de Ouro — 10 ouros";
            case "meta_all_gold" -> "Gabarito Total — todos os ouros";
            case "meta_total_5" -> "Colecionador Iniciante — 5 conquistas";
            case "meta_total_15" -> "Caçador de Medalhas — 15 conquistas";
            case "meta_total_30" -> "Grande Colecionador — 30 conquistas";
            case "meta_total_platinum_1" -> "Primeira Relíquia — 1 platina";
            case "meta_total_platinum_3" -> "Trio de Platina — 3 platinas";
            case "meta_total_platinum_7" -> "Coleção Sagrada — 7 platinas";
            case "meta_total_platinum_12" -> "Senhor das Platinas — 12 platinas";
            case "ranking_tier_c" -> "Primeiro Posto — Rank C (2.000 XP)";
            case "ranking_tier_a" -> "Escalada de Elite — Rank A (15k XP)";
            case "ranking_tier_s" -> "Alto Desempenho — Rank S (40k XP)";
            case "ranking_tier_ss" -> "Ápice Absoluto — Rank SS (100k XP)";
            default -> "Meta: " + val;
        };
    }

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
        return AchievementCategory.ACHIEVEMENTS;
    }

    /**
     * Normaliza as strings extraindo chaves puras e padronizadas.
     * Evita que chaves de multiplicadores ou valores específicos quebrem o
     * mapeamento de trilha.
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
        if (u.startsWith("CHALLENGE"))
            return "CHALLENGE_TRACK";

        String[] parts = u.split("_");
        return parts.length >= 2 ? parts[0] + "_" + parts[1] : u;
    }
}