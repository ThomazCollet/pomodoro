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

import java.time.LocalDate;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller responsável pela gestão da View de Estatísticas.
 */
public class StatsController {

    private static final Logger logger = LoggerFactory.getLogger(StatsController.class);

    // --- Elementos FXML (Cards Principais) ---
    @FXML private Label lblCurrentStreak;
    @FXML private Label lblFocusToday;
    @FXML private Label lblFocusWeek;

    // --- Elementos FXML (Recordes e Sublegendas) ---
    @FXML private Label lblMaxStreak;
    @FXML private Label lblRecordToday;

    // --- Elementos FXML (Gráficos e Heatmap) ---
    @FXML private GridPane heatmapGrid;
    @FXML private ScrollPane heatmapScrollPane; // Adicionado para controle de scroll
    @FXML private BarChart<String, Number> barChartFocus;
    @FXML private CategoryAxis xAxis;
    
    @FXML private ToggleButton btnLast7Days;
    @FXML private ToggleButton btnLast30Days;
    @FXML private ToggleButton btnLastYear;

    private ToggleGroup periodGroup;
    private StatsService statsService;
    private Profile userProfile;

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
        if (statsService == null || userProfile == null) return;

        try {
            FocusStatistics stats = statsService.getUserStatistics(userProfile);

            // Atualização dos Cards com pluralização simples
            String streakText = "🔥 " + stats.currentStreak() + (stats.currentStreak() == 1 ? " dia" : " dias");
            lblCurrentStreak.setText(streakText);
            
            lblFocusToday.setText(stats.timeToday());
            lblFocusWeek.setText(stats.timeThisWeek());

            if (lblMaxStreak != null)
                lblMaxStreak.setText("Recorde: " + stats.maxStreak() + " dias");
            if (lblRecordToday != null)
                lblRecordToday.setText(stats.recordDayTime());

            // Renderização do Heatmap
            renderHeatmap(stats.annualHeatmap());
            updateChart(getSelectedPeriod());

        } catch (Exception e) {
            logger.error("Falha ao renderizar estatísticas: ", e);
        }
    }

    private void renderHeatmap(Map<LocalDate, Long> dailyData) {
        heatmapGrid.getChildren().clear();

        // Paleta baseada no tema (Inativo -> Lendário)
        String[] colors = { "#313244", "#45475a", "#585b70", "#a6e3a1", "#94e2d5" };

        // Define o início do grid (últimas 52 semanas retrocedendo até o domingo)
        LocalDate dateIter = LocalDate.now().minusWeeks(52).with(java.time.DayOfWeek.SUNDAY);

        for (int col = 0; col < 53; col++) {
            for (int row = 0; row < 7; row++) {
                Rectangle rect = new Rectangle(12, 12);
                rect.setArcWidth(3);
                rect.setArcHeight(3);

                Long seconds = dailyData.getOrDefault(dateIter, 0L);
                rect.setFill(Color.web(getColorForSeconds(seconds, colors)));

                // Tooltip informativa
                Tooltip tooltip = new Tooltip(dateIter + " • " + formatDuration(seconds));
                tooltip.setShowDelay(javafx.util.Duration.millis(200)); // Reduz o delay do hover
                Tooltip.install(rect, tooltip);

                heatmapGrid.add(rect, col, row);
                dateIter = dateIter.plusDays(1);
            }
        }

        // Garante que o scroll vá para o final (hoje) após a renderização
        if (heatmapScrollPane != null) {
            Platform.runLater(() -> heatmapScrollPane.setHvalue(1.0));
        }
    }

    private String getColorForSeconds(long seconds, String[] colors) {
        if (seconds == 0) return colors[0];
        if (seconds < 3600) return colors[1];
        if (seconds < 10800) return colors[2];
        if (seconds < 18000) return colors[3];
        return colors[4];
    }

    private String formatDuration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        return String.format("%02dh %02dm", hours, minutes);
    }

    private void setupToggleGroup() {
        periodGroup = new ToggleGroup();
        btnLast7Days.setToggleGroup(periodGroup);
        btnLast30Days.setToggleGroup(periodGroup);
        btnLastYear.setToggleGroup(periodGroup);
        btnLast7Days.setSelected(true);

        periodGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) updateChart(getSelectedPeriod());
        });
    }

    private String getSelectedPeriod() {
        return ((ToggleButton) periodGroup.getSelectedToggle()).getText();
    }

    private void updateChart(String period) {
        barChartFocus.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();

        if (period.contains("7")) {
            series.getData().add(new XYChart.Data<>("Seg", 4.5));
            series.getData().add(new XYChart.Data<>("Ter", 6.0));
        } else {
            series.getData().add(new XYChart.Data<>("Semana 1", 22));
        }

        barChartFocus.getData().add(series);
    }
}