package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.service.StatsService;
import com.thomazcollet.infra.database.SQLiteFocusSessionRepository;
import com.thomazcollet.infra.database.SQLiteProfileRepository;
import com.thomazcollet.service.FocusSessionService;
import com.thomazcollet.service.PomodoroService;
import com.thomazcollet.service.ProfileService;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.stage.Popup;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class MainController {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML private StackPane contentArea;
    @FXML private StackPane avatarContainer;
    @FXML private Button btnTimer;
    @FXML private Button btnStats;
    @FXML private Button btnSettings;
    @FXML private Circle avatarCircle;
    @FXML private Label initialLabel;

    // Services
    private PomodoroService pomodoroService;
    private FocusSessionService focusSessionService;
    private ProfileService profileService;
    private StatsService statsService;

    private Popup profilePopup;
    private long lastPopupCloseTime = 0;

    @FXML
    public void initialize() {
        initServices();
        setupAvatar();
        setupNavigation();

        // Carregamento inicial (Dashboard padrão)
        loadTimerView();
    }

    private void initServices() {
        try {
            var sessionRepository = new SQLiteFocusSessionRepository();
            var profileRepository = new SQLiteProfileRepository();

            this.focusSessionService = new FocusSessionService(sessionRepository);
            this.profileService = new ProfileService(profileRepository);
            this.statsService = new StatsService(sessionRepository, profileRepository);

            // Garante que o perfil padrão exista antes de qualquer operação
            profileService.ensureProfileExists();
            logger.info("Serviços de infraestrutura inicializados com sucesso.");
        } catch (Exception e) {
            logger.error("Erro crítico ao inicializar serviços core: ", e);
        }
    }

    private void setupNavigation() {
        btnTimer.setOnAction(e -> loadTimerView());
        btnStats.setOnAction(e -> loadStatsView());
        avatarContainer.setOnMouseClicked(this::handleAvatarClick);
        avatarContainer.setCursor(javafx.scene.Cursor.HAND);
    }

    private void setupAvatar() {
        String hexColor = profileService.getAvatarColor();
        avatarCircle.setFill(Paint.valueOf(hexColor));
        initialLabel.setText(profileService.getProfileInitial());
    }

    // --- CARREGAMENTO DE VISÕES (VIEWS) ---

    private void loadTimerView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TimerView.fxml"));
            Parent timerView = loader.load();
            TimerController timerController = loader.getController();

            if (pomodoroService == null) {
                Profile activeProfile = profileService.getActiveProfile();
                this.pomodoroService = new PomodoroService(activeProfile, timerController, focusSessionService);
            }

            timerController.setPomodoroService(pomodoroService);
            updateContentArea(timerView);
        } catch (IOException e) {
            logger.error("Erro ao carregar TimerView: ", e);
        }
    }

    private void loadStatsView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/StatsView.fxml"));
            Parent statsView = loader.load();

            // Injeção de dependências no StatsController
            StatsController statsController = loader.getController();
            statsController.initData(statsService, profileService.getActiveProfile());

            updateContentArea(statsView);
        } catch (IOException e) {
            logger.error("Erro ao carregar StatsView: ", e);
        }
    }

    private void updateContentArea(Parent view) {
        contentArea.getChildren().setAll(view);
    }

    // --- LÓGICA DO POPOVER DE PERFIL ---

    private void handleAvatarClick(MouseEvent event) {
        if (profilePopup != null && profilePopup.isShowing()) {
            profilePopup.hide();
            return;
        }

        if (System.currentTimeMillis() - lastPopupCloseTime < 150) return;
        showProfilePopover();
    }

    private void showProfilePopover() {
        profilePopup = new Popup();
        profilePopup.setAutoHide(true);
        profilePopup.setOnHiding(e -> lastPopupCloseTime = System.currentTimeMillis());

        VBox root = createPopoverContent();
        profilePopup.getContent().add(root);

        Bounds bounds = avatarContainer.localToScreen(avatarContainer.getBoundsInLocal());
        profilePopup.show(avatarContainer.getScene().getWindow(),
                bounds.getMinX() + bounds.getWidth() + 10,
                bounds.getMinY());

        playEntranceAnimation(root);
    }

    private VBox createPopoverContent() {
        VBox root = new VBox(15);
        root.getStyleClass().add("profile-popover");
        root.setPrefWidth(280);
        root.setAlignment(Pos.CENTER);
        root.setOpacity(0);

        // Header Avatar
        StackPane headerAvatar = new StackPane();
        Circle bigCircle = new Circle(40, avatarCircle.getFill());
        Label bigInitial = new Label(profileService.getProfileInitial());
        bigInitial.setStyle("-fx-font-size: 26px; -fx-text-fill: white; -fx-font-weight: bold;");
        headerAvatar.getChildren().addAll(bigCircle, bigInitial);

        // Info
        Label lblName = new Label(profileService.getActiveProfile().getUsername());
        lblName.getStyleClass().add("popover-name");

        // Stats Grid
        GridPane grid = new GridPane();
        grid.getStyleClass().add("stats-grid");
        grid.setAlignment(Pos.CENTER);
        
        // Aqui você pode futuramente usar o statsService para preencher estes valores
        addStat(grid, "Hoje", "00:00h", 0, 0);
        addStat(grid, "Total", "00:00h", 0, 1);
        addStat(grid, "Streak", "🔥 " + profileService.getActiveProfile().getMaxStreak() + "d", 1, 0);
        addStat(grid, "Nível", "⭐ Jr.", 1, 1);

        Button btnSync = new Button("Sincronizar Dados");
        btnSync.getStyleClass().add("sync-button");
        btnSync.setMaxWidth(Double.MAX_VALUE);
        btnSync.setCursor(javafx.scene.Cursor.HAND);
        btnSync.setOnAction(e -> showSyncAlert());

        root.getChildren().addAll(headerAvatar, lblName, new Separator(), grid, btnSync);
        root.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

        return root;
    }

    private void addStat(GridPane grid, String label, String value, int col, int row) {
        VBox box = new VBox(2);
        box.setAlignment(Pos.CENTER);
        Label lblTitle = new Label(label);
        lblTitle.getStyleClass().add("stat-label");
        Label lblVal = new Label(value);
        lblVal.getStyleClass().add("stat-value");
        box.getChildren().addAll(lblTitle, lblVal);
        grid.add(box, col, row);
    }

    private void playEntranceAnimation(VBox node) {
        FadeTransition fade = new FadeTransition(Duration.millis(200), node);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(Duration.millis(200), node);
        slide.setFromX(-15);
        slide.setToX(0);

        ParallelTransition combined = new ParallelTransition(fade, slide);
        combined.play();
    }

    private void showSyncAlert() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Sincronização");
        alert.setHeaderText(null);
        alert.setContentText("A sincronização em nuvem estará disponível em breve!");
        alert.showAndWait();
    }
}