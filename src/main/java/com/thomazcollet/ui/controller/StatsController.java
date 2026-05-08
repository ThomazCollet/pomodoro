package com.thomazcollet.ui.controller;

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
import javafx.scene.layout.VBox;

public class StatsController {

    @FXML private Label lblCurrentStreak, lblFocusToday, lblFocusWeek;
    @FXML private GridPane heatmapGrid;
    @FXML private BarChart<String, Number> barChartFocus;
    @FXML private CategoryAxis xAxis;
    @FXML private ToggleButton btnLast7Days, btnLast30Days, btnLastYear;

    private ToggleGroup periodGroup;

    @FXML
    public void initialize() {
        setupToggleGroup();
        renderPlaceholderHeatmap();
        updateChart("7 dias"); // Carga inicial
    }

    private void setupToggleGroup() {
        periodGroup = new ToggleGroup();
        btnLast7Days.setToggleGroup(periodGroup);
        btnLast30Days.setToggleGroup(periodGroup);
        btnLastYear.setToggleGroup(periodGroup);

        // Listener para mudar o gráfico quando clicar nos botões
        periodGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                ToggleButton selected = (ToggleButton) newVal;
                updateChart(selected.getText());
            }
        });
    }

    /**
     * Renderiza o Heatmap estilo GitHub.
     * No futuro, este método receberá uma lista de datas e intensidades.
     */
    private void renderPlaceholderHeatmap() {
        heatmapGrid.getChildren().clear();
        String[] colors = {"#313244", "#45475a", "#585b70", "#a6e3a1", "#94e2d5"};
        
        // Simulação de 52 semanas (colunas) x 7 dias (linhas)
        for (int col = 0; col < 52; col++) {
            for (int row = 0; row < 7; row++) {
                Rectangle rect = new Rectangle(12, 12);
                rect.setArcWidth(4);
                rect.setArcHeight(4);
                
                // Mock de intensidade aleatória
                int intensity = (int) (Math.random() * 5);
                rect.setFill(Color.web(colors[intensity]));
                
                heatmapGrid.add(rect, col, row);
            }
        }
    }

    private void updateChart(String period) {
        barChartFocus.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        
        if (period.equals("7 dias")) {
            series.getData().add(new XYChart.Data<>("Seg", 4));
            series.getData().add(new XYChart.Data<>("Ter", 6));
            series.getData().add(new XYChart.Data<>("Qua", 5));
            series.getData().add(new XYChart.Data<>("Qui", 8)); // Recorde!
            series.getData().add(new XYChart.Data<>("Sex", 3));
            series.getData().add(new XYChart.Data<>("Sab", 0));
            series.getData().add(new XYChart.Data<>("Dom", 2));
        } else {
            // Mock para outros períodos
            series.getData().add(new XYChart.Data<>("Semana 1", 20));
            series.getData().add(new XYChart.Data<>("Semana 2", 25));
        }
        
        barChartFocus.getData().add(series);
    }
}