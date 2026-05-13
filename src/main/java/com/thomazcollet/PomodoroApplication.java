package com.thomazcollet;

import com.thomazcollet.infra.database.DatabaseInitializer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import java.util.Objects;

public class PomodoroApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // 1. Inicializa o Banco de Dados
        DatabaseInitializer.initialize();

        // 2. Carrega o FXML do Layout Principal
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainLayout.fxml"));
        Parent root = loader.load();

        // 3. Cria a Cena e aplica o CSS
        Scene scene = new Scene(root, 1000, 700);
        String css = Objects.requireNonNull(getClass().getResource("/css/style.css")).toExternalForm();
        scene.getStylesheets().add(css);

        // 4. Configura o Palco (Janela) e Ícone
        setupStage(primaryStage, scene);

        primaryStage.show();
    }

    private void setupStage(Stage stage, Scene scene) {
        stage.setTitle("Pomodoro Focus - Thomaz Collet");
        stage.setScene(scene);
        stage.setResizable(false);

        // Define o ícone da aplicação (O "cafezinho" ou ícone personalizado)
        // Certifique-se de ter uma imagem em: src/main/resources/assets/icon.png
        try {
            var iconResource = getClass().getResourceAsStream("/assets/img/icon.png");
            if (iconResource != null) {
                stage.getIcons().add(new Image(iconResource));
            }
        } catch (Exception e) {
            // Se falhar, o Java usa o ícone padrão do sistema sem travar o app
            System.err.println("Aviso: Ícone da aplicação não encontrado em /assets/icon.png");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}