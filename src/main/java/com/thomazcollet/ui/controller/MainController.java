package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.infra.database.SQLiteFocusSessionRepository;
import com.thomazcollet.infra.database.SQLiteProfileRepository;
import com.thomazcollet.service.*;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Controller principal que gerencia a navegação e o ciclo de vida das views.
 * Implementa cache de views para preservar o estado do Timer durante a navegação.
 */
public class MainController {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML private StackPane contentArea;
    @FXML private StackPane avatarContainer;
    @FXML private Button btnTimer;
    @FXML private Button btnStats;
    @FXML private Circle avatarCircle;
    @FXML private Label initialLabel;

    // Services
    private PomodoroService pomodoroService;
    private FocusSessionService focusSessionService;
    private ProfileService profileService;
    private StatsService statsService;

    // Cache de Views e Controllers
    private final Map<String, Parent> viewCache = new HashMap<>();
    private TimerController timerController;

    private Popup profilePopup;
    private long lastPopupCloseTime = 0;

    @FXML
    public void initialize() {
        initServices();
        setupAvatar();
        setupNavigation();

        // Carregamento inicial
        loadTimerView();
    }

    private void initServices() {
        try {
            var sessionRepository = new SQLiteFocusSessionRepository();
            var profileRepository = new SQLiteProfileRepository();

            this.focusSessionService = new FocusSessionService(sessionRepository);
            this.profileService = new ProfileService(profileRepository);
            this.statsService = new StatsService(sessionRepository, profileRepository);

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

    // --- GERENCIAMENTO DE TELAS (CACHE E NAVEGAÇÃO) ---

    private void loadTimerView() {
        try {
            if (!viewCache.containsKey("timer")) {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/TimerView.fxml"));
                Parent root = loader.load();
                
                this.timerController = loader.getController();
                
                // Inicializa o PomodoroService apenas uma vez com a primeira instância do controller
                if (pomodoroService == null) {
                    Profile activeProfile = profileService.getActiveProfile();
                    this.pomodoroService = new PomodoroService(activeProfile, timerController, focusSessionService);
                }
                
                timerController.setPomodoroService(pomodoroService);
                viewCache.put("timer", root);
            }
            
            updateContentArea(viewCache.get("timer"));
            btnTimer.getStyleClass().add("nav-button-active");
            btnStats.getStyleClass().remove("nav-button-active");
            
        } catch (IOException e) {
            logger.error("Erro ao carregar TimerView: ", e);
        }
    }

    private void loadStatsView() {
        try {
            // As estatísticas são recarregadas para garantir dados atualizados, 
            // mas você pode optar por cachear o Parent se a UI for pesada.
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/StatsView.fxml"));
            Parent statsView = loader.load();

            StatsController controller = loader.getController();
            controller.initData(statsService, profileService.getActiveProfile());

            updateContentArea(statsView);
            btnStats.getStyleClass().add("nav-button-active");
            btnTimer.getStyleClass().remove("nav-button-active");
            
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
        
        Profile profile = profileService.getActiveProfile();
        addStat(grid, "Streak", "🔥 " + profile.getMaxStreak() + "d", 0, 0);
        addStat(grid, "Recorde", formatTime(profile.getMaxFocusDaySeconds()), 0, 1);
        addStat(grid, "Nível", "⭐ Jr.", 1, 0);
        addStat(grid, "Foco Total", profile.getTotalFocusSessions() + " ses.", 1, 1);

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

    private String formatTime(int totalSeconds) {
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        return String.format("%02dh %02dm", hours, minutes);
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