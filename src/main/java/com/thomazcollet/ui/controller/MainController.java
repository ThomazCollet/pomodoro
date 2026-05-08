package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Profile;
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

import java.io.IOException;

public class MainController {

    @FXML
    private StackPane contentArea;
    @FXML
    private StackPane avatarContainer;
    @FXML
    private Button btnTimer;
    @FXML
    private Button btnStats;
    @FXML
    private Button btnSettings;
    @FXML
    private Circle avatarCircle;
    @FXML
    private Label initialLabel;

    private PomodoroService pomodoroService;
    private FocusSessionService focusSessionService;
    private ProfileService profileService;

    private Popup profilePopup;
    private long lastPopupCloseTime = 0;

    @FXML
    public void initialize() {
        initServices();
        setupAvatar();

        btnTimer.setOnAction(e -> loadTimerView());
        btnStats.setOnAction(e -> System.out.println("Estatísticas em breve..."));

        avatarContainer.setOnMouseClicked(this::handleAvatarClick);

        loadTimerView();
    }

    private void initServices() {
        var sessionRepository = new SQLiteFocusSessionRepository();
        this.focusSessionService = new FocusSessionService(sessionRepository);
        var profileRepository = new SQLiteProfileRepository();
        this.profileService = new ProfileService(profileRepository);
        profileService.ensureProfileExists();
    }

    private void setupAvatar() {
        String hexColor = profileService.getAvatarColor();
        avatarCircle.setFill(Paint.valueOf(hexColor));
        initialLabel.setText(profileService.getProfileInitial());
        avatarContainer.setCursor(javafx.scene.Cursor.HAND);
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

        // Posicionamento
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

        // 1. Header (Avatar Grande)
        StackPane headerAvatar = new StackPane();
        Circle bigCircle = new Circle(40, avatarCircle.getFill());
        Label bigInitial = new Label(profileService.getProfileInitial());
        bigInitial.setStyle("-fx-font-size: 26px; -fx-text-fill: white; -fx-font-weight: bold;");
        headerAvatar.getChildren().addAll(bigCircle, bigInitial);

        // 2. Info do Perfil
        Label lblName = new Label(profileService.getActiveProfile().getUsername());
        lblName.getStyleClass().add("popover-name");

        // 3. Grid de Stats
        GridPane grid = new GridPane();
        grid.getStyleClass().add("stats-grid");
        grid.setAlignment(Pos.CENTER);
        addStat(grid, "Hoje", "00:00h", 0, 0);
        addStat(grid, "Total", "00:00h", 0, 1);
        addStat(grid, "Streak", "🔥 0 dias", 1, 0);
        addStat(grid, "Conquistas", "🏆 0/0", 1, 1);

        /// 4. Botão Sync
        Button btnSync = new Button("Sincronizar Dados");
        btnSync.getStyleClass().add("sync-button");
        btnSync.setMaxWidth(Double.MAX_VALUE);
        btnSync.setCursor(javafx.scene.Cursor.HAND); // <--- ADICIONE ESTA LINHA (Cursor de mãozinha)
        btnSync.setOnAction(e -> showSyncAlert());

        // Garante que o botão aceite eventos de hover e clique imediatamente
        btnSync.setMouseTransparent(false);

        root.getChildren().addAll(headerAvatar, lblName, new Separator(), grid, btnSync);

        String css = getClass().getResource("/css/style.css").toExternalForm();
        root.getStylesheets().add(css);

        return root;
    }

    private void playEntranceAnimation(VBox node) {
        FadeTransition fade = new FadeTransition(Duration.millis(200), node);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition slide = new TranslateTransition(Duration.millis(200), node);
        slide.setFromX(-15);
        slide.setToX(0);

        ParallelTransition combined = new ParallelTransition(fade, slide);
        // Garante que o clique funcione após a animação
        combined.setOnFinished(e -> node.setMouseTransparent(false));
        combined.play();
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

    private void showSyncAlert() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Sincronização");
        alert.setHeaderText(null);
        alert.setContentText("Este recurso ainda não está disponível, obrigado!");
        alert.getDialogPane().getStyleClass().add("my-dialog");
        alert.showAndWait();
    }

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
            contentArea.getChildren().setAll(timerView);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}