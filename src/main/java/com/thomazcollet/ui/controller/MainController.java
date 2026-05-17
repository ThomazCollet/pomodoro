package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.repository.AchievementRepository;
import com.thomazcollet.infra.database.SQLiteChallengeRepository;
import com.thomazcollet.infra.database.SQLiteFocusSessionRepository;
import com.thomazcollet.infra.database.SQLiteProfileRepository;
import com.thomazcollet.infra.database.SQLiteAchievementRepository;
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
import java.util.List;
import java.util.Map;

/**
 * Controller principal que gerencia a navegação e o ciclo de vida das views.
 * Implementa cache de views para preservar o estado do Timer durante a
 * navegação.
 */
public class MainController {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML
    private StackPane contentArea;
    @FXML
    private StackPane avatarContainer;
    @FXML
    private Button btnTimer;
    @FXML
    private Button btnStats;
    @FXML
    private Button btnChallenges;
    @FXML
    private Button btnAchievements;
    @FXML
    private Circle avatarCircle;
    @FXML
    private Label initialLabel;

    // Services
    private PomodoroService pomodoroService;
    private FocusSessionService focusSessionService;
    private ProfileService profileService;
    private StatsService statsService;
    private ChallengeService challengeService;
    private AchievementService achievementService;

    // Repositories para injeção dinâmica
    private AchievementRepository achievementRepository;

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
            var challengeRepository = new SQLiteChallengeRepository();
            this.achievementRepository = new SQLiteAchievementRepository();

            // Computação de XP e Core Services
            this.focusSessionService = new FocusSessionService(sessionRepository, profileRepository);
            this.profileService = new ProfileService(profileRepository);
            this.statsService = new StatsService(sessionRepository, profileRepository);
            this.challengeService = new ChallengeService(challengeRepository, profileRepository);

            // Inicialização do Service de Conquistas
            this.achievementService = new AchievementService(achievementRepository, profileRepository,
                    java.util.List.of());

            profileService.ensureProfileExists();
            logger.info("Serviços de infraestrutura inicializados com sucesso.");
        } catch (Exception e) {
            logger.error("Erro crítico ao inicializar serviços core: ", e);
        }
    }

    private void setupNavigation() {
        btnTimer.setOnAction(e -> loadTimerView());
        btnStats.setOnAction(e -> loadStatsView());
        btnChallenges.setOnAction(e -> loadChallengesView());
        btnAchievements.setOnAction(e -> loadAchievementsView());
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

                if (pomodoroService == null) {
                    Profile activeProfile = profileService.getActiveProfile();

                    this.pomodoroService = new PomodoroService(
                            activeProfile,
                            timerController,
                            focusSessionService,
                            challengeService);
                }
                timerController.setPomodoroService(pomodoroService);
                viewCache.put("timer", root);
            }

            updateContentArea(viewCache.get("timer"));
            updateNavStyles(btnTimer);

        } catch (IOException e) {
            logger.error("Erro ao carregar TimerView: ", e);
        }
    }

    private void loadStatsView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/StatsView.fxml"));
            Parent statsView = loader.load();

            StatsController controller = loader.getController();
            controller.initData(statsService, profileService.getActiveProfile());

            updateContentArea(statsView);
            updateNavStyles(btnStats);

        } catch (IOException e) {
            logger.error("Erro ao carregar StatsView: ", e);
        }
    }

    private void loadChallengesView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ChallengeView.fxml"));

            loader.setControllerFactory(param -> {
                if (param == ChallengeController.class) {
                    return new ChallengeController(challengeService);
                }
                return null;
            });

            Parent challengesView = loader.load();
            updateContentArea(challengesView);
            updateNavStyles(btnChallenges);

        } catch (IOException e) {
            logger.error("Erro ao carregar ChallengeView: ", e);
        }
    }

    /**
     * Mapeado com @FXML para responder diretamente à ação do botão configurada no
     * XML.
     */
    @FXML
    private void loadAchievementsView() {
        try {
            // Corrigido para buscar o arquivo real no singular: AchievementView.fxml
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AchievementView.fxml"));
            Parent achievementsView = loader.load();

            AchievementViewController controller = loader.getController();

            // Passa a lista limpa e original diretamente do Service
            controller.initializeData(
                    profileService.getActiveProfile(),
                    achievementRepository,
                    achievementService.getDefinitions());

            updateContentArea(achievementsView);
            updateNavStyles(btnAchievements);

        } catch (IOException e) {
            logger.error("Erro ao carregar AchievementView: ", e);
        }
    }

    private void updateNavStyles(Button activeBtn) {
        btnTimer.getStyleClass().remove("nav-button-active");
        btnStats.getStyleClass().remove("nav-button-active");
        btnChallenges.getStyleClass().remove("nav-button-active");
        btnAchievements.getStyleClass().remove("nav-button-active");

        activeBtn.getStyleClass().add("nav-button-active");
    }

    private void updateContentArea(Parent view) {
        contentArea.getChildren().setAll(view);
    }

    // --- LÓGICA DO POPOVER DE PERFIL ---

    /**
     * Responde ao evento de clique no avatar container, mapeado via
     * setupNavigation.
     */
    private void handleAvatarClick(MouseEvent event) {
        if (profilePopup != null && profilePopup.isShowing()) {
            profilePopup.hide();
            return;
        }

        if (System.currentTimeMillis() - lastPopupCloseTime < 150)
            return;
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

        StackPane headerAvatar = new StackPane();
        Circle bigCircle = new Circle(40, avatarCircle.getFill());
        Label bigInitial = new Label(profileService.getProfileInitial());
        bigInitial.setStyle("-fx-font-size: 26px; -fx-text-fill: white; -fx-font-weight: bold;");
        headerAvatar.getChildren().addAll(bigCircle, bigInitial);

        Label lblName = new Label(profileService.getActiveProfile().getUsername());
        lblName.getStyleClass().add("popover-name");

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