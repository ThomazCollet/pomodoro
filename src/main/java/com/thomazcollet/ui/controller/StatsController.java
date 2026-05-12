package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.dto.FocusStatistics;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.service.StatsService;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Map;

/**
 * Controller responsável pela gestão da View de Estatísticas.
 * Implementa cache local de dados para transições suaves de interface.
 */
public class StatsController {

    private static final Logger logger = LoggerFactory.getLogger(StatsController.class);

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
            // Agora sim, chamamos a distribuição mensal correta!
            populateChart(currentStats.monthlyDistribution());
        }
    }

    /**
     * Preenche o gráfico e ajusta a estética das legendas.
     */
    private void populateChart(Map<String, Double> data) {
        barChartFocus.getData().clear();
        if (data == null || data.isEmpty())
            return;

        // FORÇAR EXIBIÇÃO DE TODAS AS LABELS
        xAxis.setTickMarkVisible(true);
        xAxis.setTickLabelsVisible(true);
        // Esta linha impede que o JavaFX esconda labels para "economizar espaço"
        barChartFocus.setAnimated(false); // Desativar animação temporariamente ajuda no ajuste de labels

        String firstKey = data.keySet().iterator().next();
        boolean isDateInterval = firstKey.contains("-");

        // Se for mensal (12 meses), garantimos que o gap entre barras seja pequeno
        if (data.size() > 10) {
            barChartFocus.setBarGap(2);
            barChartFocus.setCategoryGap(10);
        }

        xAxis.setTickLabelRotation(isDateInterval ? -30 : 0);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        data.forEach((label, value) -> series.getData().add(new XYChart.Data<>(label, value)));

        barChartFocus.getData().add(series);
    }

    private void renderHeatmap(Map<LocalDate, Long> dailyData) {
        heatmapGrid.getChildren().clear();
        String[] colors = { "#313244", "#45475a", "#585b70", "#a6e3a1", "#94e2d5" };

        LocalDate dateIter = LocalDate.now().minusWeeks(52).with(java.time.DayOfWeek.SUNDAY);

        for (int col = 0; col < 53; col++) {
            for (int row = 0; row < 7; row++) {
                Rectangle rect = createHeatmapRect(dateIter, dailyData, colors);
                heatmapGrid.add(rect, col, row);
                dateIter = dateIter.plusDays(1);
            }
        }

        if (heatmapScrollPane != null) {
            Platform.runLater(() -> heatmapScrollPane.setHvalue(1.0));
        }
    }

    private Rectangle createHeatmapRect(LocalDate date, Map<LocalDate, Long> data, String[] colors) {
        Rectangle rect = new Rectangle(12, 12);
        rect.setArcWidth(3);
        rect.setArcHeight(3);

        Long seconds = data.getOrDefault(date, 0L);
        rect.setFill(Color.web(getColorForSeconds(seconds, colors)));

        Tooltip tooltip = new Tooltip(date + " • " + formatDuration(seconds));
        tooltip.setShowDelay(javafx.util.Duration.millis(200));
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