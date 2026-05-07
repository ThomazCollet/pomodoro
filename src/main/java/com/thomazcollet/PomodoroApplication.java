package com.thomazcollet;

import com.thomazcollet.infra.database.DatabaseInitializer;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class PomodoroApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // 1. Inicializa o Banco de Dados
        DatabaseInitializer.initialize();

        // 2. Carrega o FXML do Layout Principal
        // Certifique-se que o arquivo existe em: src/main/resources/fxml/MainLayout.fxml
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainLayout.fxml"));
        Parent root = loader.load();

        // 3. Cria a Cena e aplica o CSS
        Scene scene = new Scene(root, 1000, 700);
        
        // Certifique-se que o arquivo existe em: src/main/resources/css/style.css
        String css = getClass().getResource("/css/style.css").toExternalForm();
        scene.getStylesheets().add(css);

        // 4. Configura o Palco (Janela)
        primaryStage.setTitle("Pomodoro Focus - Thomaz Collet");
        primaryStage.setScene(scene);
        primaryStage.setResizable(false); // Mantém o design elegante e fixo por enquanto
        primaryStage.show();
    }

    public static void main(String[] args) {
        // No JavaFX, o launch inicia a infraestrutura e chama o start()
        launch(args);
    }
}