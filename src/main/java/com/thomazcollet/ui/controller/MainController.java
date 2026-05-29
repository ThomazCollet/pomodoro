package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.dto.FocusStatistics;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.model.RankingType;
import com.thomazcollet.domain.repository.AchievementRepository;
import com.thomazcollet.infra.database.SQLiteChallengeRepository;
import com.thomazcollet.infra.database.SQLiteFocusSessionRepository;
import com.thomazcollet.infra.database.SQLiteProfileRepository;
import com.thomazcollet.infra.database.SQLiteAchievementRepository;
import com.thomazcollet.infra.database.SQLiteStreakRecordRepository; // Novo Import
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
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.stage.Popup;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private AudioService audioService;

    private AchievementRepository achievementRepository;

    private final Map<String, Parent> viewCache = new HashMap<>();
    private TimerController timerController;

    private Popup profilePopup;
    private long lastPopupCloseTime = 0;

    @FXML
    public void initialize() {
        initServices();
        setupAvatar();
        setupNavigation();
        loadTimerView();
    }

    private void initServices() {
        try {
            var sessionRepository = new SQLiteFocusSessionRepository();
            var profileRepository = new SQLiteProfileRepository();
            var challengeRepository = new SQLiteChallengeRepository();
            var streakRepository = new SQLiteStreakRecordRepository(); // 1. Nova Instanciação
            this.achievementRepository = new SQLiteAchievementRepository();

            this.focusSessionService = new FocusSessionService(sessionRepository, profileRepository);
            this.profileService = new ProfileService(profileRepository);

            // 2. Construtor Atualizado com o novo repositório de streaks
            this.statsService = new StatsService(sessionRepository, profileRepository, streakRepository);

            this.challengeService = new ChallengeService(challengeRepository, profileRepository);
            this.achievementService = new AchievementService(achievementRepository, profileRepository, List.of());
            this.audioService = new AudioService();

            profileService.ensureProfileExists();
            logger.info("Serviços de infraestrutura, streaks e áudio inicializados com sucesso.");
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

        Profile activeProfile = profileService.getActiveProfile();
        RankingType currentRank = RankingType.fromXp(activeProfile.getXp());

        StackPane sidebarHex = buildHexBadge(currentRank, 13.0, 9.0);
        sidebarHex.setMouseTransparent(true);
        StackPane.setAlignment(sidebarHex, javafx.geometry.Pos.BOTTOM_RIGHT);
        StackPane.setMargin(sidebarHex, new javafx.geometry.Insets(0, 44, -4, 0));
        avatarContainer.getChildren().add(sidebarHex);
    }

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
                            challengeService,
                            audioService);
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

    @FXML
    private void loadAchievementsView() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AchievementView.fxml"));
            Parent achievementsView = loader.load();

            AchievementViewController controller = loader.getController();
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

        Profile profile = profileService.getActiveProfile();
        RankingType currentRank = RankingType.fromXp(profile.getXp());

        // Busca as estatísticas em tempo real para extrair streak atual e melhor
        // histórico
        int currentStreak = 0;
        int bestStreakDays = 0;
        try {
            FocusStatistics stats = statsService.getUserStatistics(profile);
            currentStreak = stats.currentStreak();
            bestStreakDays = stats.bestStreakDays();
        } catch (Exception e) {
            logger.error("Erro ao carregar streak para o popover", e);
        }

        StackPane headerAvatar = new StackPane();
        Circle bigCircle = new Circle(40, avatarCircle.getFill());
        Label bigInitial = new Label(profileService.getProfileInitial());
        bigInitial.setStyle("-fx-font-size: 26px; -fx-text-fill: white; -fx-font-weight: bold;");
        headerAvatar.getChildren().addAll(bigCircle, bigInitial);

        Label lblName = new Label(profile.getUsername());
        lblName.getStyleClass().add("popover-name");

        VBox rankBadgeContainer = new VBox(6);
        rankBadgeContainer.setAlignment(Pos.CENTER);

        Label lblRankTitle = new Label("RANK ATUAL");
        lblRankTitle.setStyle(
                "-fx-font-size: 10px; -fx-text-fill: #8a8f9d; -fx-font-weight: bold; -fx-text-alignment: center;");

        StackPane popupHex = buildHexBadge(currentRank, 38.0, 13.0);
        rankBadgeContainer.getChildren().addAll(lblRankTitle, popupHex);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("stats-grid");
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(20);
        grid.setVgap(12);

        // Streak Ativa vs Melhor Histórico vindos do StreakRecordRepository
        addStat(grid, "Streak Atual", "🔥 " + currentStreak + "d", 0, 0);
        addStat(grid, "Recorde Máximo", "🏆 " + bestStreakDays + "d", 1, 0);
        addStat(grid, "Foco Total", profile.getTotalFocusSessions() + " ses.", 0, 1);
        addStat(grid, "Experiência", profile.getXp() + " XP", 1, 1);

        Button btnSync = new Button("Sincronizar Dados");
        btnSync.getStyleClass().add("sync-button");
        btnSync.setMaxWidth(Double.MAX_VALUE);
        btnSync.setCursor(javafx.scene.Cursor.HAND);
        btnSync.setOnAction(e -> showSyncAlert());

        root.getChildren().addAll(headerAvatar, lblName, rankBadgeContainer, new Separator(), grid, btnSync);
        root.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

        return root;
    }

    private StackPane buildHexBadge(RankingType rank, double radius, double fontSize) {
        Polygon hex = new Polygon();
        for (int i = 0; i < 6; i++) {
            double angleDeg = 60.0 * i - 90.0;
            double angleRad = Math.toRadians(angleDeg);
            hex.getPoints().addAll(
                    radius + radius * Math.cos(angleRad),
                    radius + radius * Math.sin(angleRad));
        }

        Color fill;
        Color glowColor;
        String letterColor;

        switch (rank) {
            case E -> {
                fill = Color.web("#313244");
                glowColor = null;
                letterColor = "#a6adc8";
            }
            case D -> {
                fill = Color.web("#4c1d95");
                glowColor = Color.web("#7c3aed");
                letterColor = "#ddd6fe";
            }
            case C -> {
                fill = Color.web("#1e3a8a");
                glowColor = Color.web("#3b82f6");
                letterColor = "#bfdbfe";
            }
            case B -> {
                fill = Color.web("#134e4a");
                glowColor = Color.web("#0d9488");
                letterColor = "#99f6e4";
            }
            case A -> {
                fill = Color.web("#14532d");
                glowColor = Color.web("#22c55e");
                letterColor = "#bbf7d0";
            }
            case S -> {
                fill = Color.web("#78350f");
                glowColor = Color.web("#f59e0b");
                letterColor = "#fde68a";
            }
            case SS -> {
                fill = Color.web("#1e293b");
                glowColor = Color.web("#94a3b8");
                letterColor = "#e2e8f0";
            }
            default -> {
                fill = Color.web("#313244");
                glowColor = null;
                letterColor = "#a6adc8";
            }
        }

        hex.setFill(fill);
        hex.setStroke(fill.brighter());
        hex.setStrokeWidth(radius > 20 ? 1.2 : 0.8);

        if (glowColor != null) {
            DropShadow glow = new DropShadow();
            glow.setColor(glowColor);
            glow.setRadius(radius > 20 ? 14 : 6);
            glow.setSpread(0.2);
            hex.setEffect(glow);
        }

        Label lblRank = new Label(rank.name());
        lblRank.setStyle(
                String.format("-fx-font-size: %.0fpx; -fx-font-weight: bold; -fx-text-fill: %s; -fx-padding: 0;",
                        fontSize, letterColor));
        lblRank.setMouseTransparent(true);

        StackPane container = new StackPane(hex, lblRank);
        container.setAlignment(Pos.CENTER);
        container.setPrefSize(radius * 2, radius * 2);
        container.setMaxSize(radius * 2, radius * 2);

        if (radius > 20) {
            String accent = switch (rank) {
                case S -> "★";
                case SS -> "♛";
                default -> null;
            };
            if (accent != null) {
                Label lblAccent = new Label(accent);
                String accentColor = rank == RankingType.SS ? "#94a3b8" : "#fbbf24";
                lblAccent.setStyle(String.format("-fx-font-size: 13px; -fx-text-fill: %s; -fx-padding: 0 0 %dpx 0;",
                        accentColor, (int) (radius * 1.75)));
                lblAccent.setMouseTransparent(true);
                container.getChildren().add(lblAccent);
            }
        }
        return container;
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