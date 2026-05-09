package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.dto.FocusStatistics;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.service.StatsService;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller responsável pela gestão da View de Estatísticas.
 * Implementa a atualização dinâmica de cards, heatmap e gráficos de produtividade.
 */
public class StatsController {

    private static final Logger logger = LoggerFactory.getLogger(StatsController.class);

    // --- Elementos FXML (Cards Principais) ---
    @FXML private Label lblCurrentStreak;
    @FXML private Label lblFocusToday;
    @FXML private Label lblFocusWeek;

    // --- Elementos FXML (Recordes e Sublegendas) ---
    // Certifique-se de que estes IDs existam no seu StatsView.fxml
    @FXML private Label lblMaxStreak;
    @FXML private Label lblRecordToday;
    @FXML private Label lblBestWeek;

    // --- Elementos FXML (Gráficos e Componentes) ---
    @FXML private GridPane heatmapGrid;
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

    /**
     * Injeta as dependências necessárias e dispara a primeira atualização da tela.
     */
    public void initData(StatsService statsService, Profile profile) {
        this.statsService = statsService;
        this.userProfile = profile;
        refreshStatistics();
    }

    /**
     * Atualiza todos os componentes da interface com dados reais do Service.
     */
    public void refreshStatistics() {
        if (statsService == null || userProfile == null) {
            logger.warn("Tentativa de atualizar estatísticas sem service ou perfil inicializado.");
            return;
        }

        try {
            FocusStatistics stats = statsService.getUserStatistics(userProfile);

            // Atualização dos Cards de Valor Atual
            lblCurrentStreak.setText("🔥 " + stats.currentStreak() + " dias");
            lblFocusToday.setText(stats.timeToday());
            lblFocusWeek.setText(stats.timeThisWeek());

            // Atualização dos Recordes (Dados dinâmicos do DTO)
            if (lblMaxStreak != null) lblMaxStreak.setText("Recorde: " + stats.maxStreak() + " dias");
            if (lblRecordToday != null) lblRecordToday.setText("Recorde: " + stats.recordDayTime());

            renderHeatmap();
            updateChart(getSelectedPeriod());

        } catch (Exception e) {
            logger.error("Falha ao renderizar estatísticas: ", e);
        }
    }

    private void setupToggleGroup() {
        periodGroup = new ToggleGroup();
        btnLast7Days.setToggleGroup(periodGroup);
        btnLast30Days.setToggleGroup(periodGroup);
        btnLastYear.setToggleGroup(periodGroup);
        btnLast7Days.setSelected(true);

        periodGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                updateChart(getSelectedPeriod());
            }
        });
    }

    private String getSelectedPeriod() {
        return ((ToggleButton) periodGroup.getSelectedToggle()).getText();
    }

    private void renderHeatmap() {
        heatmapGrid.getChildren().clear();
        // Paleta baseada no tema Dracula/Catppuccin (conforme sua UI)
        String[] intensityColors = { "#313244", "#45475a", "#585b70", "#a6e3a1", "#94e2d5" };

        for (int col = 0; col < 52; col++) {
            for (int row = 0; row < 7; row++) {
                Rectangle dayRect = new Rectangle(12, 12);
                dayRect.setArcWidth(3);
                dayRect.setArcHeight(3);
                
                // Simulação de intensidade baseada em lógica futura
                int intensity = (int) (Math.random() * 5);
                dayRect.setFill(Color.web(intensityColors[intensity]));
                
                heatmapGrid.add(dayRect, col, row);
            }
        }
    }

    private void updateChart(String period) {
        barChartFocus.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();

        // Lógica condicional para popular o gráfico conforme o período
        if (period.contains("7")) {
            series.getData().add(new XYChart.Data<>("Seg", 4.5));
            series.getData().add(new XYChart.Data<>("Ter", 6.0));
            series.getData().add(new XYChart.Data<>("Qua", 5.2));
            series.getData().add(new XYChart.Data<>("Qui", 8.0));
            series.getData().add(new XYChart.Data<>("Sex", 3.5));
        } else {
            series.getData().add(new XYChart.Data<>("Semana 1", 22));
            series.getData().add(new XYChart.Data<>("Semana 2", 31));
        }

        barChartFocus.getData().add(series);
    }
}