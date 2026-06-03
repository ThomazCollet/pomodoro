package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.dto.FocusStatistics;
import com.thomazcollet.domain.model.Notification;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.model.RankingType;
import com.thomazcollet.domain.repository.AchievementRepository;
import com.thomazcollet.infra.database.SQLiteAchievementRepository;
import com.thomazcollet.infra.database.SQLiteChallengeRepository;
import com.thomazcollet.infra.database.SQLiteFocusSessionRepository;
import com.thomazcollet.infra.database.SQLiteNotificationRepository;
import com.thomazcollet.infra.database.SQLiteProfileRepository;
import com.thomazcollet.infra.database.SQLiteStreakRecordRepository;
import com.thomazcollet.service.*;
import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.PauseTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.stage.Popup;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainController {

    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    // -----------------------------------------------------------------------
    // FXML — Layout principal
    // -----------------------------------------------------------------------
    @FXML private StackPane  contentArea;
    @FXML private StackPane  avatarContainer;
    @FXML private HBox       topBar;
    @FXML private StackPane  bellContainer;
    @FXML private Button     btnBell;
    @FXML private Label      bellBadge;
    @FXML private Button     btnTimer;
    @FXML private Button     btnStats;
    @FXML private Button     btnChallenges;
    @FXML private Button     btnAchievements;
    @FXML private Circle     avatarCircle;
    @FXML private Label      initialLabel;

    // -----------------------------------------------------------------------
    // Services
    // -----------------------------------------------------------------------
    private PomodoroService      pomodoroService;
    private FocusSessionService  focusSessionService;
    private ProfileService       profileService;
    private StatsService         statsService;
    private ChallengeService     challengeService;
    private AchievementService   achievementService;
    private AudioService         audioService;
    private NotificationService  notificationService;  // NOVO

    private AchievementRepository achievementRepository;

    // -----------------------------------------------------------------------
    // Estado da UI
    // -----------------------------------------------------------------------
    private final Map<String, Parent> viewCache = new HashMap<>();
    private TimerController timerController;

    private Popup profilePopup;
    private Popup notificationPopup;                    // NOVO
    private long  lastPopupCloseTime    = 0;
    private long  lastNotifPopupClose   = 0;            // NOVO

    private static final DateTimeFormatter NOTIF_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM HH:mm");

    // ==========================================================================
    // INICIALIZAÇÃO
    // ==========================================================================

    @FXML
    public void initialize() {
        initServices();
        setupAvatar();
        setupNavigation();
        setupBell();           // NOVO
        setupNotificationToast(); // NOVO
        loadTimerView();
    }

    private void initServices() {
        try {
            var sessionRepository      = new SQLiteFocusSessionRepository();
            var profileRepository      = new SQLiteProfileRepository();
            var challengeRepository    = new SQLiteChallengeRepository();
            var streakRepository       = new SQLiteStreakRecordRepository();
            var notificationRepository = new SQLiteNotificationRepository();
            this.achievementRepository = new SQLiteAchievementRepository();

            // NotificationService primeiro — sem dependências circulares
            this.notificationService = new NotificationService(notificationRepository);

            this.focusSessionService = new FocusSessionService(sessionRepository, profileRepository);
            this.profileService      = new ProfileService(profileRepository);
            this.statsService        = new StatsService(sessionRepository, profileRepository,
                                            streakRepository, notificationService);
            this.challengeService    = new ChallengeService(challengeRepository, profileRepository,
                                            notificationService);
            this.achievementService  = new AchievementService(achievementRepository, profileRepository,
                                            List.of(), notificationService);
            this.audioService        = new AudioService();

            profileService.ensureProfileExists();

            int profileId = profileService.getActiveProfile().getId().intValue();
            notificationService.loadUnreadCount(profileId);
            notificationService.clearOldNotifications(30);
            challengeService.checkAndNotifyNoActiveChallenges(
                    profileService.getActiveProfile().getId());

            logger.info("Todos os serviços inicializados com sucesso.");
        } catch (Exception e) {
            logger.error("Erro crítico ao inicializar serviços core: ", e);
        }
    }

    // ==========================================================================
    // SINO — SETUP, BADGE E CLICK
    // ==========================================================================

    /**
     * Conecta o contador reativo do NotificationService ao badge visual do sino.
     * O bind é unidirecional: qualquer send() no service atualiza o badge
     * automaticamente, sem polling.
     */
    private void setupBell() {
        if (notificationService == null) return;

        // Badge: texto com o número de não lidas
        bellBadge.textProperty().bind(
                notificationService.unreadCountProperty().asString()
        );

        // Badge: visível apenas quando há não lidas
        notificationService.unreadCountProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> {
                boolean hasUnread = newVal.intValue() > 0;
                bellBadge.setVisible(hasUnread);
                bellBadge.setManaged(hasUnread);
                updateBellStyle(hasUnread);
            });
        });

        // Estado inicial
        boolean hasUnread = notificationService.getUnreadCount() > 0;
        bellBadge.setVisible(hasUnread);
        bellBadge.setManaged(hasUnread);
        updateBellStyle(hasUnread);
    }

    /**
     * Alterna o estilo do sino entre estado normal e estado "com não lidas".
     * Estado ativo: borda vermelha suave + glow + ícone colorido.
     */
    private void updateBellStyle(boolean hasUnread) {
        btnBell.getStyleClass().removeAll("bell-button-active");
        if (hasUnread) {
            btnBell.getStyleClass().add("bell-button-active");
        }
    }

    @FXML
    private void handleBellClick() {
        // Toggle: se está aberto, fecha
        if (notificationPopup != null && notificationPopup.isShowing()) {
            closeNotificationPopup();
            return;
        }
        // Debounce para evitar reabrir imediatamente após fechar com autoHide
        if (System.currentTimeMillis() - lastNotifPopupClose < 150) return;

        showNotificationPopover();
    }

    // ==========================================================================
    // POPOVER DE NOTIFICAÇÕES
    // ==========================================================================

    private void showNotificationPopover() {
        if (notificationService == null) return;

        int profileId = profileService.getActiveProfile().getId().intValue();
        List<Notification> notifications = notificationService.getHistory(profileId);

        notificationPopup = new Popup();
        notificationPopup.setAutoHide(true);
        notificationPopup.setOnHiding(e -> {
            lastNotifPopupClose = System.currentTimeMillis();
            // Marca todas como lidas ao fechar
            notificationService.markAllAsRead(profileId);
        });

        VBox popoverRoot = buildNotificationPopover(notifications);
        notificationPopup.getContent().add(popoverRoot);

        // Ancora abaixo do sino
        Bounds bounds = btnBell.localToScreen(btnBell.getBoundsInLocal());
        double x = bounds.getMaxX() - 320; // alinha à direita do sino
        double y = bounds.getMaxY() + 6;

        notificationPopup.show(btnBell.getScene().getWindow(), x, y);
        playEntranceAnimation(popoverRoot);
    }

    private void closeNotificationPopup() {
        if (notificationPopup != null && notificationPopup.isShowing()) {
            notificationPopup.hide();
        }
    }

    /**
     * Constrói o VBox completo do popover de notificações.
     * Layout:
     *   ┌─────────────────────────────────┐
     *   │  🔔 Notificações          Limpar │
     *   ├─────────────────────────────────┤
     *   │  [item não lido — destaque]     │
     *   │  [item não lido — destaque]     │
     *   │  [item lido — opaco]            │
     *   │  [item lido — opaco]            │
     *   └─────────────────────────────────┘
     */
    private VBox buildNotificationPopover(List<Notification> notifications) {
        VBox root = new VBox(0);
        root.setPrefWidth(320);
        root.setMaxWidth(320);
        root.getStyleClass().add("notif-popover");
        root.setOpacity(0);
        root.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

        // ── Cabeçalho ──
        HBox header = new HBox();
        header.getStyleClass().add("notif-header");
        header.setAlignment(Pos.CENTER_LEFT);

        Label lblTitle = new Label("🔔  Notificações");
        lblTitle.getStyleClass().add("notif-header-title");
        HBox.setHgrow(lblTitle, Priority.ALWAYS);

        Button btnMarkRead = new Button("Marcar lidas");
        btnMarkRead.getStyleClass().add("notif-mark-read-btn");
        btnMarkRead.setOnAction(e -> {
            int profileId = profileService.getActiveProfile().getId().intValue();
            notificationService.markAllAsRead(profileId);
            closeNotificationPopup();
        });

        header.getChildren().addAll(lblTitle, btnMarkRead);
        root.getChildren().add(header);

        // Separador fino
        root.getChildren().add(buildDivider());

        // ── Lista ──
        if (notifications.isEmpty()) {
            VBox emptyState = new VBox(8);
            emptyState.setAlignment(Pos.CENTER);
            emptyState.setPadding(new Insets(32, 16, 32, 16));
            Label icon = new Label("🔕");
            icon.setStyle("-fx-font-size: 28px;");
            Label msg = new Label("Nenhuma notificação ainda.");
            msg.getStyleClass().add("notif-empty-text");
            emptyState.getChildren().addAll(icon, msg);
            root.getChildren().add(emptyState);
        } else {
            // ScrollPane para listas longas (máx. ~5 itens visíveis)
            VBox listBox = new VBox(0);
            for (Notification n : notifications) {
                listBox.getChildren().add(buildNotificationItem(n));
            }

            ScrollPane scroll = new ScrollPane(listBox);
            scroll.setFitToWidth(true);
            scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            scroll.setPrefViewportHeight(Math.min(notifications.size() * 76.0, 380.0));
            scroll.setMaxHeight(380);
            scroll.getStyleClass().add("notif-scroll");
            root.getChildren().add(scroll);
        }

        return root;
    }

    /**
     * Constrói um item individual da lista de notificações.
     * Itens não lidos têm fundo ligeiramente iluminado e um ponto indicador azul.
     */
    private HBox buildNotificationItem(Notification notification) {
        HBox item = new HBox(10);
        item.setAlignment(Pos.TOP_LEFT);
        item.getStyleClass().add("notif-item");
        if (!notification.isRead()) {
            item.getStyleClass().add("notif-item-unread");
        }

        // Ponto indicador (azul = não lido, transparente = lido)
        Circle dot = new Circle(4);
        dot.getStyleClass().add(notification.isRead() ? "notif-dot-read" : "notif-dot-unread");
        VBox.setMargin(dot, new Insets(6, 0, 0, 0));
        VBox dotWrapper = new VBox(dot);
        dotWrapper.setAlignment(Pos.TOP_CENTER);
        dotWrapper.setMinWidth(12);

        // Conteúdo textual
        VBox content = new VBox(3);
        HBox.setHgrow(content, Priority.ALWAYS);

        Label lblTitle = new Label(notification.getTitle());
        lblTitle.getStyleClass().add("notif-item-title");
        lblTitle.setWrapText(true);
        lblTitle.setMaxWidth(240);

        Label lblMessage = new Label(notification.getMessage());
        lblMessage.getStyleClass().add("notif-item-message");
        lblMessage.setWrapText(true);
        lblMessage.setMaxWidth(240);

        // Timestamp formatado
        String timeStr = notification.getCreatedAt() != null
                ? notification.getCreatedAt().format(NOTIF_FORMATTER)
                : "";
        Label lblTime = new Label(timeStr);
        lblTime.getStyleClass().add("notif-item-time");

        content.getChildren().addAll(lblTitle, lblMessage, lblTime);
        item.getChildren().addAll(dotWrapper, content);

        // Separador entre itens
        VBox wrapper = new VBox(0);
        wrapper.getChildren().addAll(item, buildDivider());

        // Retornamos o wrapper mas precisamos de HBox — usamos um truque:
        // embrulhamos tudo em um HBox que contém o VBox
        HBox outer = new HBox(wrapper);
        HBox.setHgrow(wrapper, Priority.ALWAYS);
        // Remover padding do outer para o wrapper ocupar 100%
        outer.setMaxWidth(Double.MAX_VALUE);
        return outer;
    }

    private Region buildDivider() {
        Region line = new Region();
        line.getStyleClass().add("notif-divider");
        line.setMinHeight(1);
        line.setMaxHeight(1);
        return line;
    }

    // ==========================================================================
    // TOAST / SNACKBAR
    // ==========================================================================

    /**
     * Registra o callback do NotificationService para exibir toasts em tempo real.
     * Usa Platform.runLater para garantir execução na thread do JavaFX mesmo
     * quando a notificação vier de uma thread de serviço (ex: PomodoroService).
     */
    private void setupNotificationToast() {
        if (notificationService == null) return;

        notificationService.setToastCallback(notification ->
            Platform.runLater(() ->
                showToastNotification(notification.getTitle(), notification.getMessage())
            )
        );
    }

    /**
     * Exibe um toast no canto superior direito da área de conteúdo.
     * Traz a janela para frente se estiver minimizada.
     * Duração total: 300ms fade-in + 4s visível + 400ms fade-out.
     */
    private void showToastNotification(String title, String message) {
        // Traz a janela para frente se minimizada
        if (contentArea.getScene() != null && contentArea.getScene().getWindow() instanceof Stage stage) {
            if (stage.isIconified()) stage.setIconified(false);
            stage.toFront();
        }

        // Container do toast
        VBox toast = new VBox(4);
        toast.setMaxWidth(300);
        toast.setPickOnBounds(false);
        toast.getStyleClass().add("toast-notification");
        toast.setOpacity(0);

        Label lblTitle = new Label(title);
        lblTitle.getStyleClass().add("toast-notification-title");
        lblTitle.setWrapText(true);

        Label lblMsg = new Label(message);
        lblMsg.getStyleClass().add("toast-notification-message");
        lblMsg.setWrapText(true);
        lblMsg.setMaxWidth(270);

        toast.getChildren().addAll(lblTitle, lblMsg);

        StackPane.setAlignment(toast, Pos.TOP_RIGHT);
        StackPane.setMargin(toast, new Insets(12, 12, 0, 0));
        contentArea.getChildren().add(toast);

        // Animação sequencial
        FadeTransition fadeIn = new FadeTransition(Duration.millis(280), toast);
        fadeIn.setFromValue(0); fadeIn.setToValue(1);

        // Leve slide para baixo ao entrar
        TranslateTransition slideIn = new TranslateTransition(Duration.millis(280), toast);
        slideIn.setFromY(-8); slideIn.setToY(0);

        ParallelTransition enter = new ParallelTransition(fadeIn, slideIn);

        PauseTransition hold = new PauseTransition(Duration.seconds(4));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(350), toast);
        fadeOut.setFromValue(1); fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> contentArea.getChildren().remove(toast));

        new SequentialTransition(enter, hold, fadeOut).play();
    }

    // ==========================================================================
    // SETUP DE NAVEGAÇÃO E AVATAR (inalterados)
    // ==========================================================================

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
        StackPane.setAlignment(sidebarHex, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(sidebarHex, new Insets(0, 44, -4, 0));
        avatarContainer.getChildren().add(sidebarHex);
    }

    // ==========================================================================
    // CARREGAMENTO DE VIEWS (inalteradas)
    // ==========================================================================

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
                            audioService,
                            notificationService);  // passa o notificationService
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
                if (param == ChallengeController.class) return new ChallengeController(challengeService);
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

    // ==========================================================================
    // POPOVER DE PERFIL (inalterado)
    // ==========================================================================

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

        Profile profile = profileService.getActiveProfile();
        RankingType currentRank = RankingType.fromXp(profile.getXp());

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
        lblRankTitle.setStyle("-fx-font-size: 10px; -fx-text-fill: #8a8f9d; -fx-font-weight: bold;");
        StackPane popupHex = buildHexBadge(currentRank, 38.0, 13.0);
        rankBadgeContainer.getChildren().addAll(lblRankTitle, popupHex);

        GridPane grid = new GridPane();
        grid.getStyleClass().add("stats-grid");
        grid.setAlignment(Pos.CENTER);
        grid.setHgap(20);
        grid.setVgap(12);
        addStat(grid, "Streak Atual",   "🔥 " + currentStreak + "d",      0, 0);
        addStat(grid, "Recorde Máximo", "🏆 " + bestStreakDays + "d",      1, 0);
        addStat(grid, "Foco Total",     profile.getTotalFocusSessions() + " ses.", 0, 1);
        addStat(grid, "Experiência",    profile.getXp() + " XP",           1, 1);

        Button btnSync = new Button("Sincronizar Dados");
        btnSync.getStyleClass().add("sync-button");
        btnSync.setMaxWidth(Double.MAX_VALUE);
        btnSync.setCursor(javafx.scene.Cursor.HAND);
        btnSync.setOnAction(e -> showSyncAlert());

        root.getChildren().addAll(headerAvatar, lblName, rankBadgeContainer, new Separator(), grid, btnSync);
        root.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());
        return root;
    }

    // ==========================================================================
    // HEX BADGE (inalterado)
    // ==========================================================================

    private StackPane buildHexBadge(RankingType rank, double radius, double fontSize) {
        Polygon hex = new Polygon();
        for (int i = 0; i < 6; i++) {
            double angleDeg = 60.0 * i - 90.0;
            double angleRad = Math.toRadians(angleDeg);
            hex.getPoints().addAll(
                    radius + radius * Math.cos(angleRad),
                    radius + radius * Math.sin(angleRad));
        }

        Color fill; Color glowColor; String letterColor;
        switch (rank) {
            case E  -> { fill = Color.web("#313244"); glowColor = null;             letterColor = "#a6adc8"; }
            case D  -> { fill = Color.web("#4c1d95"); glowColor = Color.web("#7c3aed"); letterColor = "#ddd6fe"; }
            case C  -> { fill = Color.web("#1e3a8a"); glowColor = Color.web("#3b82f6"); letterColor = "#bfdbfe"; }
            case B  -> { fill = Color.web("#134e4a"); glowColor = Color.web("#0d9488"); letterColor = "#99f6e4"; }
            case A  -> { fill = Color.web("#14532d"); glowColor = Color.web("#22c55e"); letterColor = "#bbf7d0"; }
            case S  -> { fill = Color.web("#78350f"); glowColor = Color.web("#f59e0b"); letterColor = "#fde68a"; }
            case SS -> { fill = Color.web("#1e293b"); glowColor = Color.web("#94a3b8"); letterColor = "#e2e8f0"; }
            default -> { fill = Color.web("#313244"); glowColor = null;             letterColor = "#a6adc8"; }
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
        lblRank.setStyle(String.format(
                "-fx-font-size: %.0fpx; -fx-font-weight: bold; -fx-text-fill: %s; -fx-padding: 0;",
                fontSize, letterColor));
        lblRank.setMouseTransparent(true);

        StackPane container = new StackPane(hex, lblRank);
        container.setAlignment(Pos.CENTER);
        container.setPrefSize(radius * 2, radius * 2);
        container.setMaxSize(radius * 2, radius * 2);

        if (radius > 20) {
            String accent = switch (rank) { case S -> "★"; case SS -> "♛"; default -> null; };
            if (accent != null) {
                Label lblAccent = new Label(accent);
                String accentColor = rank == RankingType.SS ? "#94a3b8" : "#fbbf24";
                lblAccent.setStyle(String.format(
                        "-fx-font-size: 13px; -fx-text-fill: %s; -fx-padding: 0 0 %dpx 0;",
                        accentColor, (int) (radius * 1.75)));
                lblAccent.setMouseTransparent(true);
                container.getChildren().add(lblAccent);
            }
        }
        return container;
    }

    // ==========================================================================
    // HELPERS
    // ==========================================================================

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
        fade.setFromValue(0); fade.setToValue(1);
        TranslateTransition slide = new TranslateTransition(Duration.millis(200), node);
        slide.setFromX(-15); slide.setToX(0);
        new ParallelTransition(fade, slide).play();
    }

    private void showSyncAlert() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Sincronização");
        alert.setHeaderText(null);
        alert.setContentText("A sincronização em nuvem estará disponível em breve!");
        alert.showAndWait();
    }

    public NotificationService getNotificationService() {
        return notificationService;
    }
}