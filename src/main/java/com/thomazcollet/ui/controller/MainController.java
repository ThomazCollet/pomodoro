package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.infra.database.SQLiteFocusSessionRepository;
import com.thomazcollet.service.FocusSessionService;
import com.thomazcollet.service.PomodoroService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;

import java.io.IOException;

public class MainController {

    @FXML private StackPane contentArea;
    @FXML private Button btnTimer;
    @FXML private Button btnStats;
    @FXML private Button btnSettings;

    // Services que viverão durante toda a execução do App
    private PomodoroService pomodoroService;
    private FocusSessionService focusSessionService;

    @FXML
    public void initialize() {
        initServices();

        // Configura as ações do menu
        btnTimer.setOnAction(e -> loadTimerView());
        btnStats.setOnAction(e -> System.out.println("Estatísticas em breve..."));
        
        // Carrega a tela do Timer por padrão ao iniciar
        loadTimerView();
    }

    private void initServices() {
        // 1. Inicializamos o repositório e o serviço de persistência
        var repository = new SQLiteFocusSessionRepository();
        this.focusSessionService = new FocusSessionService(repository);

        // 2. Criamos um perfil padrão (depois isso virá das configurações/banco)
        Profile defaultProfile = new Profile("Trabalho", 25, 5, 15);
        defaultProfile.setId(1L);

        // 3. Criamos o motor do Pomodoro. 
        // Passaremos o listener nulo por enquanto, pois o TimerController será o listener.
        // Mas o PomodoroService exige um listener no construtor. 
        // Vamos resolver isso injetando o Controller como listener após o carregamento.
    }

    private void loadTimerView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TimerView.fxml"));
            Parent timerView = loader.load();

            // Pegamos o controller da tela que acabamos de carregar
            TimerController timerController = loader.getController();

            // Se o pomodoroService ainda não existe, criamos agora 
            // passando o controller como o listener oficial!
            if (pomodoroService == null) {
                Profile defaultProfile = new Profile("Trabalho", 25, 5, 15);
                defaultProfile.setId(1L);
                this.pomodoroService = new PomodoroService(defaultProfile, timerController, focusSessionService);
            }

            // Injetamos o service no controller para que os botões funcionem
            timerController.setPomodoroService(pomodoroService);

            // Limpa o centro e coloca a nova tela
            contentArea.getChildren().setAll(timerView);

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Erro ao carregar TimerView.fxml");
        }
    }
}