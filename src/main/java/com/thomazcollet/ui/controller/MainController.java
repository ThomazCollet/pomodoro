package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.infra.database.SQLiteFocusSessionRepository;
import com.thomazcollet.infra.database.SQLiteProfileRepository;
import com.thomazcollet.service.FocusSessionService;
import com.thomazcollet.service.PomodoroService;
import com.thomazcollet.service.ProfileService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;

import java.io.IOException;

public class MainController {

    @FXML private StackPane contentArea;
    @FXML private Button btnTimer;
    @FXML private Button btnStats;
    @FXML private Button btnSettings;
    
    // Elementos do Avatar na UI
    @FXML private Circle avatarCircle;
    @FXML private Label initialLabel;

    private PomodoroService pomodoroService;
    private FocusSessionService focusSessionService;
    private ProfileService profileService;

    @FXML
    public void initialize() {
        initServices();
        setupAvatar();

        // Configura as ações do menu
        btnTimer.setOnAction(e -> loadTimerView());
        btnStats.setOnAction(e -> System.out.println("Estatísticas em breve..."));
        
        // Carrega a tela do Timer por padrão ao iniciar
        loadTimerView();
    }

    private void initServices() {
        // 1. Inicializa persistência de sessões
        var sessionRepository = new SQLiteFocusSessionRepository();
        this.focusSessionService = new FocusSessionService(sessionRepository);

        // 2. Inicializa serviço de perfil e garante que um perfil exista no banco
        var profileRepository = new SQLiteProfileRepository();
        this.profileService = new ProfileService(profileRepository);
        
        // Fail-Fast: O app só continua se houver um perfil carregado/criado
        profileService.ensureProfileExists();
    }

    private void setupAvatar() {
        // Aplica a lógica de cores e inicial baseada no perfil ativo
        String hexColor = profileService.getAvatarColor();
        avatarCircle.setFill(Paint.valueOf(hexColor));
        initialLabel.setText(profileService.getProfileInitial());
    }

    private void loadTimerView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TimerView.fxml"));
            Parent timerView = loader.load();

            TimerController timerController = loader.getController();

            // Se o pomodoroService ainda não existe, criamos usando o perfil real do banco
            if (pomodoroService == null) {
                Profile activeProfile = profileService.getActiveProfile();
                this.pomodoroService = new PomodoroService(activeProfile, timerController, focusSessionService);
            }

            timerController.setPomodoroService(pomodoroService);
            contentArea.getChildren().setAll(timerView);

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Erro ao carregar TimerView.fxml");
        }
    }
}