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
import java.util.HashMap;
import java.util.Map;

/**
 * Controller responsável pela gestão da View de Estatísticas.
 * Implementa renderização dinâmica de metas (Target Line), Tooltips
 * informativos,
 * elementos flutuantes de gamificação (Estrelas) e tratamento estético do
 * gráfico.
 */
public class StatsController {

    private static final Logger logger = LoggerFactory.getLogger(StatsController.class);
    private static final String GOAL_LINE_STYLE_CLASS = "goal-baseline";
    private static final String PREMIUM_BAR_STYLE_CLASS = "bar-premium";

    // CORREÇÃO: Nome da classe agora bate com o estilo .premium-star no seu
    // style.css
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
    /**
     * Container externo que envolve o BarChart no FXML.
     * As estrelas premium são adicionadas AQUI em vez de no plotPane interno do
     * chart. Isso evita o ciclo de clipagem: o BarChart re-aplica o seu clip
     * interno a cada layoutChildren(), sobrescrevendo qualquer setClip(null).
     * Como este StackPane não pertence à hierarquia interna do chart, ele nunca
     * é tocado pelo layoutChildren() do BarChart.
     */
    @FXML
    private StackPane chartWrapper;

    @FXML
    private ToggleButton btnLast7Days;
    @FXML
    private ToggleButton btnLast30Days;
    @FXML
    private ToggleButton btnLastYear;

    private ToggleGroup periodGroup;
    private StatsService statsService;
    private Profile userProfile;
    private FocusStatistics currentStats;

    @FXML
    public void initialize() {
        setupToggleGroup();
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
            lblMaxStreak.setText("Recorde: " + currentStats.maxStreak() + " dias");
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

        // Respiro de 30% no topo para os emojis não serem cortados pelo teto
        double maxScaleValue = Math.max(targetGoalHours, maxBarValue);
        yAxis.setUpperBound(Math.ceil(maxScaleValue * 1.30));
        yAxis.setTickUnit(maxScaleValue > 20 ? 10.0 : 1.0);

        Platform.runLater(() -> {
            barChartFocus.applyCss();
            barChartFocus.layout();

            Region plotBackground = (Region) barChartFocus.lookup(".chart-plot-background");

            if (plotBackground != null && plotBackground.getParent() instanceof Pane plotPane) {

                // SOLUÇÃO CRUCIAL 1: Remove o efeito de clip do painel interno do gráfico para
                // as estrelas poderem "vazar" para cima da linha limite
                plotPane.setClip(null);

                // Limpa linhas de meta antigas do plotPane (continua funcionando lá)
                plotPane.getChildren()
                        .removeIf(node -> node instanceof Line && node.getStyleClass().contains(GOAL_LINE_STYLE_CLASS));

                // Limpa estrelas antigas do chartWrapper (não mais do plotPane)
                chartWrapper.getChildren()
                        .removeIf(node -> node instanceof Label
                                && node.getStyleClass().contains(PREMIUM_STAR_STYLE_CLASS));

                double plotHeight = plotBackground.getHeight();
                if (plotHeight > 0) {
                    double yPosInAxisSpace = yAxis.getDisplayPosition(targetGoalHours);
                    double lineY = yAxis.localToParent(0, yPosInAxisSpace).getY();

                    double plotTop = plotBackground.getLayoutY();
                    double plotBottom = plotTop + plotHeight;

                    if (lineY >= plotTop && lineY <= plotBottom) {
                        Line goalLine = new Line();
                        goalLine.getStyleClass().add(GOAL_LINE_STYLE_CLASS);
                        goalLine.setMouseTransparent(true);

                        goalLine.startXProperty().bind(plotBackground.layoutXProperty());
                        goalLine.endXProperty()
                                .bind(plotBackground.layoutXProperty().add(plotBackground.widthProperty()));
                        goalLine.setStartY(lineY);
                        goalLine.setEndY(lineY);

                        plotPane.getChildren().add(goalLine);
                    }
                }

                // --- MOTOR DE ESTRELAS (OVERLAY APPROACH) ---
                //
                // POR QUE plotPane NÃO FUNCIONAVA:
                // O BarChart chama layoutChildren() a cada pulse do JavaFX.
                // Esse método re-aplica um clip retangular ao plotPane (chartContent) que
                // delimita a área do gráfico. A sequência de desastre era:
                // 1. applyCss() + layout() → clip é aplicado ao plotPane
                // 2. plotPane.setClip(null) → clip é removido
                // 3. starLabel é adicionada ao plotPane → marca o chart como "dirty"
                // 4. PRÓXIMO PULSE: layoutChildren() roda novamente → clip é RE-APLICADO
                // → estrelas desaparecem
                //
                // setClip(null) é uma solução temporária que o próprio chart sobrescreve
                // no frame seguinte, tornando qualquer tentativa inútil.
                //
                // SOLUÇÃO: chartWrapper é um StackPane que envolve o BarChart no FXML.
                // Ele não pertence à hierarquia interna do chart, então o layoutChildren()
                // do BarChart nunca modifica o seu clip. Estrelas adicionadas aqui são
                // permanentes e ficam visualmente sobre o chart graças ao z-order do
                // StackPane (filhos adicionados depois ficam na frente).
                //
                // CONVERSÃO DE COORDENADAS:
                // barNode.localToScene() converte do espaço do barNode → cena (passando
                // por todos os containers intermediários do chart automaticamente).
                // chartWrapper.sceneToLocal() converte da cena → espaço do chartWrapper.
                // O resultado é sempre correto, independente da hierarquia interna do chart.
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

                        // Adiciona ao chartWrapper (fora do chart), não ao plotPane interno
                        chartWrapper.getChildren().add(starLabel);

                        // Binding X: centro horizontal do barNode em coordenadas do chartWrapper
                        starLabel.layoutXProperty().bind(Bindings.createDoubleBinding(
                                () -> {
                                    if (barNode.getScene() == null)
                                        return -9999.0;
                                    Bounds local = barNode.getBoundsInLocal();
                                    double midLocalX = (local.getMinX() + local.getMaxX()) / 2.0;
                                    Point2D inWrapper = chartWrapper.sceneToLocal(
                                            barNode.localToScene(midLocalX, local.getMinY()));
                                    return inWrapper.getX() - starLabel.getWidth() / 2.0;
                                },
                                barNode.boundsInLocalProperty(),
                                barNode.localToSceneTransformProperty(),
                                chartWrapper.localToSceneTransformProperty(),
                                starLabel.widthProperty()));

                        // Binding Y: topo do barNode em coordenadas do chartWrapper,
                        // recuado de starHeight + 4px de respiro acima da barra
                        starLabel.layoutYProperty().bind(Bindings.createDoubleBinding(
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
        });
    }

    private double resolveTargetGoalHours() {
        if (userProfile == null || periodGroup == null) {
            return 1.5;
        }

        ToggleButton selected = (ToggleButton) periodGroup.getSelectedToggle();

        if (selected == btnLast7Days) {
            return userProfile.getDailyGoalSeconds() / 3600.0;
        } else if (selected == btnLast30Days) {
            return userProfile.getWeeklyGoalSeconds() / 3600.0;
        } else if (selected == btnLastYear) {
            return userProfile.getMonthlyGoalSeconds() / 3600.0;
        }

        return 1.5;
    }

    private void renderHeatmap(Map<LocalDate, Long> dailyData) {
        heatmapGrid.getChildren().clear();
        String[] colors = { "#313244", "#45475a", "#585b70", "#a6e3a1", "#94e2d5" };

        Map<String, Long> standardizedData = new HashMap<>();

        if (dailyData != null) {
            ((Map<?, ?>) dailyData).forEach((key, value) -> {
                if (key != null && value != null) {
                    try {
                        String dateStr = key.toString();
                        if (dateStr.length() >= 10) {
                            dateStr = dateStr.substring(0, 10);
                        }
                        long secs = (long) Double.parseDouble(value.toString());
                        standardizedData.merge(dateStr, secs, Long::sum);
                    } catch (Exception e) {
                        logger.warn("Dado ignorado no parser do heatmap: {}={}", key, value);
                    }
                }
            });
        }

        LocalDate dateIter = LocalDate.now().minusWeeks(52).with(DayOfWeek.SUNDAY);

        for (int col = 0; col < 53; col++) {
            for (int row = 0; row < 7; row++) {
                Rectangle rect = createHeatmapRect(dateIter, standardizedData, colors);
                heatmapGrid.add(rect, col, row);
                dateIter = dateIter.plusDays(1);
            }
        }

        if (heatmapScrollPane != null) {
            Platform.runLater(() -> heatmapScrollPane.setHvalue(1.0));
        }
    }

    private Rectangle createHeatmapRect(LocalDate date, Map<String, Long> standardizedData, String[] colors) {
        Rectangle rect = new Rectangle(12, 12);
        rect.setArcWidth(3);
        rect.setArcHeight(3);

        Long seconds = standardizedData.getOrDefault(date.toString(), 0L);
        rect.setFill(Color.web(getColorForSeconds(seconds, colors)));

        Tooltip tooltip = new Tooltip(date + " • " + formatDuration(seconds));
        tooltip.setShowDelay(Duration.millis(200));
        Tooltip.install(rect, tooltip);

        return rect;
    }

    private String getColorForSeconds(long seconds, String[] colors) {
        if (seconds == 0)
            return colors[0];
        if (seconds < 3600)
            return colors[1];
        if (seconds < 10800)
            return colors[2];
        if (seconds < 18000)
            return colors[3];
        return colors[4];
    }

    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        return String.format("%02dh %02dm", hours, minutes);
    }
}