package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.dto.FocusStatistics;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.service.StatsService;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Controller responsável pela gestão da View de Estatísticas.
 * Implementa renderização dinâmica de metas (Target Line), Tooltips
 * informativos,
 * elementos flutuantes de gamificação (Estrelas), tratamento estético do
 * gráfico e pódios.
 */
public class StatsController {

    private static final Logger logger = LoggerFactory.getLogger(StatsController.class);
    private static final String GOAL_LINE_STYLE_CLASS = "goal-baseline";
    private static final String PREMIUM_BAR_STYLE_CLASS = "bar-premium";
    private static final String PREMIUM_STAR_STYLE_CLASS = "premium-star";

    @FXML
    private Label lblCurrentStreak;
    @FXML
    private Label lblFocusToday;
    @FXML
    private Label lblFocusWeek;
    @FXML
    private Label lblMaxStreak;
    @FXML
    private Label lblRecordToday;

    @FXML
    private GridPane heatmapGrid;
    @FXML
    private ScrollPane heatmapScrollPane;

    @FXML
    private BarChart<String, Number> barChartFocus;
    @FXML
    private CategoryAxis xAxis;
    @FXML
    private StackPane chartWrapper;

    @FXML
    private ToggleButton btnLast7Days;
    @FXML
    private ToggleButton btnLast30Days;
    @FXML
    private ToggleButton btnLastYear;

    // Componentes injetados para o sistema de pódios (Agora com 3 colunas)
    @FXML
    private ListView<String> listDailyPodium;
    @FXML
    private ListView<String> listMonthlyPodium;
    @FXML
    private ListView<String> listStreakPodium;

    private ToggleGroup periodGroup;
    private StatsService statsService;
    private Profile userProfile;
    private FocusStatistics currentStats;

    @FXML
    public void initialize() {
        setupToggleGroup();
        setupPodiumCellFactories();
    }

    public void initData(StatsService statsService, Profile profile) {
        this.statsService = statsService;
        this.userProfile = profile;
        refreshStatistics();
    }

    public void refreshStatistics() {
        if (statsService == null || userProfile == null)
            return;

        try {
            this.currentStats = statsService.getUserStatistics(userProfile);
            updateSummaryCards();
            renderHeatmap(currentStats.annualHeatmap());
            syncChartWithSelection();
            renderPodiums();
        } catch (Exception e) {
            logger.error("Falha ao renderizar estatísticas: ", e);
        }
    }

    private void updateSummaryCards() {
        String streakText = "🔥 " + currentStats.currentStreak() +
                (currentStats.currentStreak() == 1 ? " dia" : " dias");

        lblCurrentStreak.setText(streakText);
        lblFocusToday.setText(currentStats.timeToday());
        lblFocusWeek.setText(currentStats.timeThisWeek());

        if (lblMaxStreak != null)
            lblMaxStreak.setText("Recorde: " + currentStats.bestStreakDays() + " dias");
        if (lblRecordToday != null)
            lblRecordToday.setText(currentStats.recordDayTime());
    }

    private void setupToggleGroup() {
        periodGroup = new ToggleGroup();
        btnLast7Days.setToggleGroup(periodGroup);
        btnLast30Days.setToggleGroup(periodGroup);
        btnLastYear.setToggleGroup(periodGroup);

        btnLast7Days.setSelected(true);

        periodGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null)
                syncChartWithSelection();
        });
    }

    private void setupPodiumCellFactories() {
        var cellFactory = new javafx.util.Callback<ListView<String>, ListCell<String>>() {
            @Override
            public ListCell<String> call(ListView<String> param) {
                return new ListCell<>() {
                    @Override
                    protected void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (empty || item == null) {
                            setText(null);
                            setGraphic(null);
                            setStyle("-fx-background-color: transparent;"); // Garante fundo limpo
                        } else {
                            String medal = switch (getIndex()) {
                                case 0 -> "🥇 ";
                                case 1 -> "🥈 ";
                                case 2 -> "🥉 ";
                                default -> "✨ ";
                            };
                            setText(medal + item);

                            // Base Style (Fonte maior e centralizada verticalmente)
                            String style = "-fx-font-size: 14px; -fx-text-fill: #cdd6f4; -fx-padding: 8px 10px; -fx-background-color: transparent;";

                            // Ouro recebe destaque especial (Bold + Amarelo do tema)
                            if (getIndex() == 0 && !item.contains("Aguardando")) {
                                style += " -fx-font-weight: bold; -fx-text-fill: #f9e2af;";
                            }

                            setStyle(style);
                        }
                    }
                };
            }
        };

        listDailyPodium.setCellFactory(cellFactory);
        listMonthlyPodium.setCellFactory(cellFactory);
        if (listStreakPodium != null) {
            listStreakPodium.setCellFactory(cellFactory);
        }
    }

    private void renderPodiums() {
        if (currentStats == null)
            return;

        // 1. Pódio Diário
        listDailyPodium.getItems().clear();
        List<String> dailyData = currentStats.dailyPodium();
        if (dailyData == null || dailyData.isEmpty()) {
            listDailyPodium.getItems().add("Nenhum recorde registrado ainda.");
        } else {
            listDailyPodium.getItems().addAll(dailyData);
        }

        // 2. Pódio Mensal
        listMonthlyPodium.getItems().clear();
        List<String> monthlyData = currentStats.monthlyPodium();
        if (monthlyData == null || monthlyData.isEmpty()) {
            listMonthlyPodium.getItems().add("Nenhum recorde registrado ainda.");
        } else {
            listMonthlyPodium.getItems().addAll(monthlyData);
        }

        // 3. Pódio de Streaks (REFATORADO)
        if (listStreakPodium != null) {
            listStreakPodium.getItems().clear();

            // Supondo que o nome do método no seu FocusStatistics Record seja
            // streakPodium()
            List<String> streakData = currentStats.streakPodium();

            if (streakData == null || streakData.isEmpty()) {
                listStreakPodium.getItems().add("Nenhuma sequência registrada ainda.");
            } else {
                listStreakPodium.getItems().addAll(streakData);
            }
        }
    }

    private void syncChartWithSelection() {
        if (currentStats == null)
            return;

        ToggleButton selected = (ToggleButton) periodGroup.getSelectedToggle();

        if (selected == btnLast7Days) {
            populateChart(currentStats.dailyDistribution());
        } else if (selected == btnLast30Days) {
            populateChart(currentStats.weeklyDistribution());
        } else if (selected == btnLastYear) {
            populateChart(currentStats.monthlyDistribution());
        }
    }

    private void populateChart(Map<String, Double> data) {
        barChartFocus.getData().clear();

        if (data == null || data.isEmpty())
            return;

        xAxis.setTickMarkVisible(true);
        xAxis.setTickLabelsVisible(true);
        barChartFocus.setAnimated(false);

        String firstKey = data.keySet().iterator().next();
        boolean isDateInterval = firstKey.contains("-");
        xAxis.setTickLabelRotation(isDateInterval ? -30 : 0);

        if (data.size() > 10) {
            barChartFocus.setBarGap(2);
            barChartFocus.setCategoryGap(10);
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        data.forEach((label, value) -> series.getData().add(new XYChart.Data<>(label, value)));
        barChartFocus.getData().add(series);

        double targetGoalHours = resolveTargetGoalHours();
        NumberAxis yAxis = (NumberAxis) barChartFocus.getYAxis();
        double maxBarValue = data.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);

        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(0.0);

        double maxScaleValue = Math.max(targetGoalHours, maxBarValue);
        yAxis.setUpperBound(Math.ceil(maxScaleValue * 1.30));
        yAxis.setTickUnit(maxScaleValue > 20 ? 10.0 : 1.0);

        Platform.runLater(() -> renderChartOverlays(targetGoalHours, series, yAxis));
    }

    private void renderChartOverlays(double targetGoalHours, XYChart.Series<String, Number> series, NumberAxis yAxis) {
        barChartFocus.applyCss();
        barChartFocus.layout();

        Region plotBackground = (Region) barChartFocus.lookup(".chart-plot-background");

        if (plotBackground != null && plotBackground.getParent() instanceof Pane plotPane) {

            plotPane.getChildren()
                    .removeIf(node -> node instanceof Line && node.getStyleClass().contains(GOAL_LINE_STYLE_CLASS));
            chartWrapper.getChildren()
                    .removeIf(node -> node instanceof Label && node.getStyleClass().contains(PREMIUM_STAR_STYLE_CLASS));

            double plotHeight = plotBackground.getHeight();
            if (plotHeight > 0) {
                double yPosInAxisSpace = yAxis.getDisplayPosition(targetGoalHours);
                double lineY = yAxis.localToParent(0, yPosInAxisSpace).getY();

                double plotTop = plotBackground.getLayoutY();
                double plotBottom = plotTop + plotHeight;

                if (lineY >= plotTop && lineY <= plotBottom) {
                    Line goalLine = new Line();
                    goalLine.getStyleClass().add(GOAL_LINE_STYLE_CLASS);
                    goalLine.setMouseTransparent(false);

                    goalLine.startXProperty().bind(plotBackground.layoutXProperty());
                    goalLine.endXProperty().bind(plotBackground.layoutXProperty().add(plotBackground.widthProperty()));
                    goalLine.setStartY(lineY);
                    goalLine.setEndY(lineY);

                    Tooltip targetTooltip = createTargetLineTooltip();
                    if (targetTooltip != null) {
                        Tooltip.install(goalLine, targetTooltip);
                    }

                    plotPane.getChildren().add(goalLine);
                }
            }

            for (XYChart.Data<String, Number> nodeData : series.getData()) {
                var barNode = nodeData.getNode();
                if (barNode == null)
                    continue;

                double barValueHours = nodeData.getYValue().doubleValue();
                String barLabel = nodeData.getXValue();
                long totalSeconds = (long) (barValueHours * 3600);

                Tooltip tooltip = new Tooltip(barLabel + " • " + formatDuration(totalSeconds));
                tooltip.setShowDelay(Duration.millis(150));
                Tooltip.install(barNode, tooltip);

                if (barValueHours >= targetGoalHours) {
                    barNode.getStyleClass().add(PREMIUM_BAR_STYLE_CLASS);

                    Label starLabel = new Label("⭐");
                    starLabel.getStyleClass().add(PREMIUM_STAR_STYLE_CLASS);
                    starLabel.setMouseTransparent(true);

                    StackPane.setAlignment(starLabel, javafx.geometry.Pos.TOP_LEFT);
                    chartWrapper.getChildren().add(starLabel);

                    starLabel.translateXProperty().bind(Bindings.createDoubleBinding(
                            () -> {
                                if (barNode.getScene() == null)
                                    return -9999.0;
                                Bounds local = barNode.getBoundsInLocal();
                                double midLocalX = (local.getMinX() + local.getMaxX()) / 2.0;
                                Point2D inWrapper = chartWrapper.sceneToLocal(
                                        barNode.localToScene(midLocalX, local.getMinY()));

                                return inWrapper.getX() - (starLabel.getWidth() / 2.0);
                            },
                            barNode.boundsInLocalProperty(),
                            barNode.localToSceneTransformProperty(),
                            chartWrapper.localToSceneTransformProperty(),
                            starLabel.widthProperty()));

                    starLabel.translateYProperty().bind(Bindings.createDoubleBinding(
                            () -> {
                                if (barNode.getScene() == null)
                                    return -9999.0;
                                Bounds local = barNode.getBoundsInLocal();
                                Point2D topInWrapper = chartWrapper.sceneToLocal(
                                        barNode.localToScene(local.getMinX(), local.getMinY()));

                                return topInWrapper.getY() - starLabel.getHeight() - 4.0;
                            },
                            barNode.boundsInLocalProperty(),
                            barNode.localToSceneTransformProperty(),
                            chartWrapper.localToSceneTransformProperty(),
                            starLabel.heightProperty()));
                }
            }
        }
    }

    private Tooltip createTargetLineTooltip() {
        if (userProfile == null || periodGroup == null)
            return null;

        ToggleButton selected = (ToggleButton) periodGroup.getSelectedToggle();
        String tooltipText = "";

        if (selected == btnLast7Days) {
            long seconds = userProfile.getDailyGoalSeconds();
            if (seconds < 3600) {
                tooltipText = "Meta diária: " + (seconds / 60) + " minutos";
            } else {
                double hours = seconds / 3600.0;
                tooltipText = String.format("Meta diária: %.1fh", hours).replace(".0", "");
            }
        } else if (selected == btnLast30Days) {
            double hours = userProfile.getWeeklyGoalSeconds() / 3600.0;
            tooltipText = String.format("Meta semanal: %.1f horas", hours).replace(".0", "");
        } else if (selected == btnLastYear) {
            double hours = userProfile.getMonthlyGoalSeconds() / 3600.0;
            tooltipText = String.format("Meta mensal: %.1f horas", hours).replace(".0", "");
        }

        Tooltip tooltip = new Tooltip(tooltipText);
        tooltip.setShowDelay(Duration.ZERO);

        tooltip.setStyle(
                "-fx-background-color: #363a4f; " +
                        "-fx-text-fill: #f9e2af; " +
                        "-fx-font-size: 12px; " +
                        "-fx-font-family: 'Segoe UI', sans-serif; " +
                        "-fx-padding: 6px 10px; " +
                        "-fx-background-radius: 4px;");

        return tooltip;
    }

    private double resolveTargetGoalHours() {
        if (userProfile == null || periodGroup == null)
            return 1.5;

        ToggleButton selected = (ToggleButton) periodGroup.getSelectedToggle();

        if (selected == btnLast7Days)
            return userProfile.getDailyGoalSeconds() / 3600.0;
        if (selected == btnLast30Days)
            return userProfile.getWeeklyGoalSeconds() / 3600.0;
        if (selected == btnLastYear)
            return userProfile.getMonthlyGoalSeconds() / 3600.0;

        return 1.5;
    }

    // Paleta do heatmap — Catppuccin Mocha, rampa verde + Sky para dias supremos.
    // Índices: 0=vazio  1=rastro(<30m)  2=leve(30m–2h)  3=bom(2–4h)  4=ótimo(4–6h)  5=supremo(≥6h)
    private static final String[] HEATMAP_COLORS = {
        "#1e2030", // 0 — vazio      (funde com o fundo #1e1e2e)
        "#2d4a3e", // 1 — rastro     (verde bem escuro)
        "#40724f", // 2 — leve       (verde médio-escuro)
        "#5a9e64", // 3 — bom        (verde médio)
        "#a6e3a1", // 4 — ótimo      (Catppuccin Green claro)
        "#89dceb"  // 5 — supremo    (Catppuccin Sky — destaque especial ≥ 6h)
    };

    private void renderHeatmap(Map<LocalDate, Long> dailyData) {
        heatmapGrid.getChildren().clear();

        // O repositório já entrega Map<LocalDate, Long> — acesso direto, sem conversão.
        Map<LocalDate, Long> data = (dailyData != null) ? dailyData : Map.of();

        LocalDate dateIter = LocalDate.now().minusWeeks(52).with(DayOfWeek.SUNDAY);

        for (int col = 0; col < 53; col++) {
            for (int row = 0; row < 7; row++) {
                Rectangle rect = createHeatmapRect(dateIter, data);
                heatmapGrid.add(rect, col, row);
                dateIter = dateIter.plusDays(1);
            }
        }

        if (heatmapScrollPane != null) {
            Platform.runLater(() -> heatmapScrollPane.setHvalue(1.0));
        }
    }

    private Rectangle createHeatmapRect(LocalDate date, Map<LocalDate, Long> data) {
        Rectangle rect = new Rectangle(12, 12);
        rect.setArcWidth(3);
        rect.setArcHeight(3);

        long seconds = data.getOrDefault(date, 0L);
        rect.setFill(Color.web(getColorForSeconds(seconds)));

        Tooltip tooltip = new Tooltip(date + " • " + formatDuration(seconds));
        tooltip.setShowDelay(Duration.millis(200));
        Tooltip.install(rect, tooltip);

        return rect;
    }

    /**
     * Mapeia segundos de foco para um dos 6 níveis da paleta do heatmap.
     *
     *  0 — vazio    :  = 0 s
     *  1 — rastro   :  1 s  –  1 799 s   (< 30 min)
     *  2 — leve     :  1 800 s – 7 199 s  (30 min – 2 h)
     *  3 — bom      :  7 200 s – 14 399 s (2 h – 4 h)
     *  4 — ótimo    : 14 400 s – 21 599 s (4 h – 6 h)
     *  5 — supremo  : ≥ 21 600 s          (6 h ou mais)
     */
    private String getColorForSeconds(long seconds) {
        if (seconds == 0)      return HEATMAP_COLORS[0];
        if (seconds < 1800)    return HEATMAP_COLORS[1];
        if (seconds < 7200)    return HEATMAP_COLORS[2];
        if (seconds < 14400)   return HEATMAP_COLORS[3];
        if (seconds < 21600)   return HEATMAP_COLORS[4];
        return                        HEATMAP_COLORS[5];
    }

    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        return String.format("%02dh %02dm", hours, minutes);
    }
}