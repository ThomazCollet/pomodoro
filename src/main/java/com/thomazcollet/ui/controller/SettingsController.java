package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.model.StreakRule;
import com.thomazcollet.service.*;
import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.scene.paint.Color;
import javafx.scene.paint.ImagePattern;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller da tela de Configurações.
 *
 * <p>
 * Fases implementadas:
 * <ul>
 * <li>Fase 1 — esqueleto e carregamento de dados reais.</li>
 * <li>Fase 2 — seção de Metas.</li>
 * <li>Fase 3 — volume de áudio, notificações e idioma.</li>
 * <li>Fase 4 — durações de foco e pausas.</li>
 * <li>Fase 5 — avatar (FileChooser + cópia gerenciada) e username.</li>
 * <li>Fase final — Zona de Risco com countdown de segurança.</li>
 * </ul>
 */
public class SettingsController {

    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);
    private static final int DANGER_COUNTDOWN_SECONDS = 4;

    // ── Seção Perfil ────────────────────────────────────────────────────────
    @FXML
    private StackPane settingsAvatarContainer;
    @FXML
    private Circle settingsAvatarCircle;
    @FXML
    private Label settingsAvatarInitial;
    @FXML
    private TextField txtUsername;
    @FXML
    private Label lblUsernameError;
    @FXML
    private Button btnSaveProfile;

    // ── Seção Metas ─────────────────────────────────────────────────────────
    @FXML
    private Spinner<Integer> spnDailyGoalHours;
    @FXML
    private Spinner<Integer> spnDailyGoalMinutes;
    @FXML
    private Spinner<Integer> spnWeeklyGoalHours;
    @FXML
    private Spinner<Integer> spnMonthlyGoalHours;
    @FXML
    private Button btnSaveGoals;

    // ── Seção Foco ──────────────────────────────────────────────────────────
    @FXML
    private RadioButton rdoFocusDefault;
    @FXML
    private RadioButton rdoFocusCustom;
    @FXML
    private HBox boxFocusCustomFields;
    @FXML
    private Spinner<Integer> spnWorkDuration;
    @FXML
    private Spinner<Integer> spnShortBreak;
    @FXML
    private Spinner<Integer> spnLongBreak;
    @FXML
    private Label lblFocusError;
    @FXML
    private Button btnSaveFocus;

    // ── Seção Áudio ─────────────────────────────────────────────────────────
    @FXML
    private Slider sliderVolume;
    @FXML
    private Label lblVolumeValue;
    @FXML
    private Button btnSaveAudio;

    // ── Seção Notificações ──────────────────────────────────────────────────
    @FXML
    private ToggleButton tglNotifications;
    @FXML
    private Button btnSaveNotifications;

    // ── Seção Idioma ─────────────────────────────────────────────────────────
    @FXML
    private ComboBox<String> cmbLanguage;
    @FXML
    private Button btnSaveLanguage;

    // ── Seção Zona de Risco ──────────────────────────────────────────────────
    @FXML
    private Button btnClearHistory;
    @FXML
    private Button btnResetProgress;

    // ── Dependências ────────────────────────────────────────────────────────
    private ProfileService profileService;
    private AudioService audioService;
    private NotificationService notificationService;
    private PomodoroService pomodoroService;
    private DataResetService dataResetService;
    private Runnable onSidebarUpdateNeeded;

    private static final Map<String, String> LANGUAGE_OPTIONS = new LinkedHashMap<>();
    static {
        LANGUAGE_OPTIONS.put("Português (Brasil)", "pt_BR");
        LANGUAGE_OPTIONS.put("English (US)", "en_US");
        LANGUAGE_OPTIONS.put("Español", "es_ES");
    }

    // ==========================================================================
    // INICIALIZAÇÃO
    // ==========================================================================

    public void initData(ProfileService profileService,
            AudioService audioService,
            NotificationService notificationService,
            PomodoroService pomodoroService,
            DataResetService dataResetService,
            Runnable onSidebarUpdateNeeded) {
        this.profileService = profileService;
        this.audioService = audioService;
        this.notificationService = notificationService;
        this.pomodoroService = pomodoroService;
        this.dataResetService = dataResetService;
        this.onSidebarUpdateNeeded = onSidebarUpdateNeeded;
        setupStaticComponents();
        loadProfileData();
    }

    private void setupStaticComponents() {
        spnDailyGoalHours.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 1));
        spnDailyGoalMinutes.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 30, 5));
        spnWeeklyGoalHours.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 168, 7));
        spnMonthlyGoalHours.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 744, 30));

        spnWorkDuration.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 60, 25));
        spnShortBreak.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 30, 5));
        spnLongBreak.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 60, 15));

        ToggleGroup focusGroup = new ToggleGroup();
        rdoFocusDefault.setToggleGroup(focusGroup);
        rdoFocusCustom.setToggleGroup(focusGroup);

        sliderVolume.valueProperty()
                .addListener((obs, oldVal, newVal) -> lblVolumeValue.setText(newVal.intValue() + "%"));

        cmbLanguage.getItems().addAll(LANGUAGE_OPTIONS.keySet());
    }

    private void loadProfileData() {
        if (profileService == null)
            return;
        Profile p = profileService.getActiveProfile();

        // Perfil — avatar e username
        txtUsername.setText(p.getUsername());
        applyAvatarPreview(p.getImagePath());

        // Metas
        int daily = p.getDailyGoalSeconds();
        spnDailyGoalHours.getValueFactory().setValue(daily / 3600);
        spnDailyGoalMinutes.getValueFactory().setValue((daily % 3600) / 60);
        spnWeeklyGoalHours.getValueFactory().setValue(p.getWeeklyGoalSeconds() / 3600);
        spnMonthlyGoalHours.getValueFactory().setValue(p.getMonthlyGoalSeconds() / 3600);

        // Foco
        boolean isDefault = p.getWorkDuration() == 25
                && p.getShortBreak() == 5
                && p.getLongBreak() == 15;
        rdoFocusDefault.setSelected(isDefault);
        rdoFocusCustom.setSelected(!isDefault);
        boxFocusCustomFields.setDisable(isDefault);
        spnWorkDuration.getValueFactory().setValue(p.getWorkDuration());
        spnShortBreak.getValueFactory().setValue(p.getShortBreak());
        spnLongBreak.getValueFactory().setValue(p.getLongBreak());

        // Áudio
        sliderVolume.setValue(p.getAudioVolume());
        lblVolumeValue.setText(p.getAudioVolume() + "%");

        // Notificações
        tglNotifications.setSelected(p.isNotificationsEnabled());
        updateNotificationsToggleText(p.isNotificationsEnabled());

        // Idioma
        String currentLang = p.getLanguage() != null ? p.getLanguage() : "pt_BR";
        LANGUAGE_OPTIONS.entrySet().stream()
                .filter(e -> e.getValue().equals(currentLang))
                .findFirst()
                .ifPresentOrElse(
                        e -> cmbLanguage.setValue(e.getKey()),
                        () -> cmbLanguage.setValue("Português (Brasil)"));
    }

    // ==========================================================================
    // HANDLERS — FASE 5: AVATAR E USERNAME
    // ==========================================================================

    @FXML
    private void handleChangeAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Escolher foto de perfil");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imagens", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File chosen = chooser.showOpenDialog(btnSaveProfile.getScene().getWindow());
        if (chosen == null)
            return;

        try {
            // Copia para pasta gerenciada pelo app — não depende da localização original
            File avatarDir = new File("data/avatars");
            if (!avatarDir.exists())
                avatarDir.mkdirs();

            String ext = getFileExtension(chosen.getName());
            File dest = new File(avatarDir,
                    "profile_" + profileService.getActiveProfile().getId() + "." + ext);

            Files.copy(chosen.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);

            String destPath = dest.getAbsolutePath();
            profileService.saveProfileInfo(
                    profileService.getActiveProfile().getUsername(), destPath);

            // Atualiza o preview na tela de configurações
            applyAvatarPreview(destPath);

            // Atualiza a sidebar imediatamente
            if (onSidebarUpdateNeeded != null)
                onSidebarUpdateNeeded.run();

            logger.info("Avatar atualizado: {}", destPath);
        } catch (Exception e) {
            logger.error("Erro ao copiar arquivo de avatar.", e);
        }
    }

    @FXML
    private void handleSaveProfile() {
        hideUsernameError();
        String newUsername = txtUsername.getText();

        if (newUsername == null || newUsername.isBlank()) {
            showUsernameError("O nome não pode estar vazio.");
            return;
        }
        if (newUsername.trim().length() > 20) {
            showUsernameError("O nome deve ter no máximo 20 caracteres.");
            return;
        }

        try {
            profileService.saveProfileInfo(newUsername, null);
            if (onSidebarUpdateNeeded != null)
                onSidebarUpdateNeeded.run();
            showSavedFeedback(btnSaveProfile, "Salvar Perfil");
            logger.info("Username atualizado para: '{}'.", newUsername.trim());
        } catch (IllegalArgumentException e) {
            showUsernameError(e.getMessage());
        }
    }

    // ==========================================================================
    // HANDLERS — FASE 2: METAS
    // ==========================================================================

    @FXML
    private void handleSaveGoals() {
        int dailySeconds = spnDailyGoalHours.getValue() * 3600
                + spnDailyGoalMinutes.getValue() * 60;
        int weeklySeconds = spnWeeklyGoalHours.getValue() * 3600;
        int monthlySeconds = spnMonthlyGoalHours.getValue() * 3600;

        try {
            profileService.saveGoals(dailySeconds, weeklySeconds, monthlySeconds);
            showSavedFeedback(btnSaveGoals, "Salvar Metas");
        } catch (Exception e) {
            logger.error("Erro ao salvar metas.", e);
        }
    }

    // ==========================================================================
    // HANDLERS — FASE 3: ÁUDIO, NOTIFICAÇÕES E IDIOMA
    // ==========================================================================

    @FXML
    private void handleSaveAudio() {
        int volume = (int) sliderVolume.getValue();
        Profile p = profileService.getActiveProfile();
        audioService.setVolume(volume);
        profileService.saveSettings(volume, p.isNotificationsEnabled(),
                p.getLanguage(), p.getStreakRule());
        showSavedFeedback(btnSaveAudio, "Salvar Áudio");
    }

    @FXML
    private void handleNotificationsToggle() {
        updateNotificationsToggleText(tglNotifications.isSelected());
    }

    @FXML
    private void handleSaveNotifications() {
        boolean enabled = tglNotifications.isSelected();
        Profile p = profileService.getActiveProfile();
        if (notificationService != null)
            notificationService.setEnabled(enabled);
        profileService.saveSettings(p.getAudioVolume(), enabled,
                p.getLanguage(), p.getStreakRule());
        showSavedFeedback(btnSaveNotifications, "Salvar Notificações");
    }

    @FXML
    private void handleSaveLanguage() {
        String selectedLabel = cmbLanguage.getValue();
        if (selectedLabel == null)
            return;
        String langCode = LANGUAGE_OPTIONS.getOrDefault(selectedLabel, "pt_BR");
        Profile p = profileService.getActiveProfile();
        profileService.saveSettings(p.getAudioVolume(), p.isNotificationsEnabled(),
                langCode, p.getStreakRule());
        showSavedFeedback(btnSaveLanguage, "Salvar Idioma");
    }

    // ==========================================================================
    // HANDLERS — FASE 4: DURAÇÕES DE FOCO
    // ==========================================================================

    @FXML
    private void handleFocusModeChanged() {
        boolean custom = rdoFocusCustom.isSelected();
        boxFocusCustomFields.setDisable(!custom);
        if (!custom) {
            spnWorkDuration.getValueFactory().setValue(25);
            spnShortBreak.getValueFactory().setValue(5);
            spnLongBreak.getValueFactory().setValue(15);
        }
        hideFocusError();
    }

    @FXML
    private void handleSaveFocus() {
        hideFocusError();
        int work = spnWorkDuration.getValue();
        int small = spnShortBreak.getValue();
        int large = spnLongBreak.getValue();

        try {
            profileService.saveDurations(work, small, large);
        } catch (IllegalArgumentException e) {
            showFocusError(e.getMessage());
            return;
        }

        if (pomodoroService != null) {
            pomodoroService.updateProfile(profileService.getActiveProfile());
        }
        showSavedFeedback(btnSaveFocus, "Salvar Configurações de Foco");
    }

    // ==========================================================================
    // HANDLERS — ZONA DE RISCO (com countdown de segurança)
    // ==========================================================================

    @FXML
    private void handleClearHistory() {
        showCountdownConfirmation(
                "⚠️ Limpar Histórico de Foco",
                "Esta ação irá apagar permanentemente todas as suas sessões de foco, "
                        + "streaks e estatísticas acumuladas.\n\n"
                        + "Conquistas, desafios, notificações e XP serão preservados.",
                DANGER_COUNTDOWN_SECONDS,
                () -> {
                    Long profileId = profileService.getActiveProfile().getId();
                    dataResetService.clearFocusHistory(profileId);
                    profileService.reloadActiveProfile();
                    if (pomodoroService != null) {
                        pomodoroService.updateProfile(profileService.getActiveProfile());
                    }
                    logger.info("Histórico de foco limpo pelo usuário.");
                });
    }

    @FXML
    private void handleResetProgress() {
        showCountdownConfirmation(
                "⚠️ Resetar Todo o Progresso",
                "Esta ação irá apagar PERMANENTEMENTE todo o seu progresso:\n\n"
                        + "• Histórico de foco e estatísticas\n"
                        + "• Todas as conquistas desbloqueadas\n"
                        + "• Todos os desafios\n"
                        + "• Notificações\n"
                        + "• XP e ranking\n\n"
                        + "Suas preferências de configuração serão preservadas.\n"
                        + "Esta ação não pode ser desfeita.",
                DANGER_COUNTDOWN_SECONDS,
                () -> {
                    Long profileId = profileService.getActiveProfile().getId();
                    dataResetService.resetAllProgress(profileId);
                    profileService.reloadActiveProfile();
                    if (notificationService != null) {
                        notificationService.loadUnreadCount(profileId.intValue());
                    }
                    if (pomodoroService != null) {
                        pomodoroService.updateProfile(profileService.getActiveProfile());
                    }
                    logger.info("Progresso completo resetado pelo usuário.");
                });
    }

    // ==========================================================================
    // COUNTDOWN CONFIRMATION DIALOG
    // ==========================================================================

    /**
     * Exibe um diálogo modal com countdown de segurança antes de permitir
     * a confirmação de uma ação destrutiva. O botão "Confirmar" só fica
     * disponível após o countdown zerar — impossibilitando cliques acidentais.
     */
    private void showCountdownConfirmation(String title, String message,
            int seconds, Runnable onConfirmed) {
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);

        // ── Container principal ──
        VBox root = new VBox(20);
        root.getStyleClass().addAll("dialog-container", "danger-confirm-dialog");
        root.setPadding(new Insets(28, 32, 24, 32));
        root.setPrefWidth(460);
        root.setMaxWidth(460);

        // ── Título ──
        Label lblTitle = new Label(title);
        lblTitle.getStyleClass().add("settings-section-title-danger");
        lblTitle.setWrapText(true);

        // ── Mensagem ──
        Label lblMessage = new Label(message);
        lblMessage.setWrapText(true);
        lblMessage.setStyle("-fx-text-fill: #a6adc8; -fx-font-size: 12px;");

        // ── Countdown ──
        Label lblCountdown = new Label(
                "Aguarde " + seconds + " segundos para confirmar...");
        lblCountdown.getStyleClass().add("danger-countdown-label");

        // ── Botões ──
        Button btnCancel = new Button("Cancelar");
        btnCancel.getStyleClass().add("danger-cancel-button");

        Button btnConfirm = new Button("Confirmar (" + seconds + ")");
        btnConfirm.getStyleClass().add("settings-danger-button-strong");
        btnConfirm.setDisable(true);

        HBox buttons = new HBox(12, btnCancel, btnConfirm);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(lblTitle, lblMessage, lblCountdown, buttons);

        Scene scene = new Scene(root);
        String cssPath = getClass().getResource("/css/style.css").toExternalForm();
        scene.getStylesheets().add(cssPath);
        dialog.setScene(scene);

        // ── Countdown timeline ──
        int[] remaining = { seconds };
        Timeline countdown = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            remaining[0]--;
            if (remaining[0] <= 0) {
                btnConfirm.setText("Confirmar");
                btnConfirm.setDisable(false);
                lblCountdown.setVisible(false);
                lblCountdown.setManaged(false);
            } else {
                btnConfirm.setText("Confirmar (" + remaining[0] + ")");
                lblCountdown.setText("Aguarde " + remaining[0] + " segundos para confirmar...");
            }
        }));
        countdown.setCycleCount(seconds);

        btnConfirm.setOnAction(e -> {
            countdown.stop();
            dialog.close();
            onConfirmed.run();
        });

        btnCancel.setOnAction(e -> {
            countdown.stop();
            dialog.close();
        });

        dialog.setOnHiding(e -> countdown.stop());

        dialog.sizeToScene();
        dialog.centerOnScreen();
        countdown.play();
        dialog.showAndWait();
    }

    // ==========================================================================
    // HELPERS
    // ==========================================================================

    /**
     * Aplica o avatar correto no círculo de preview da tela de configurações:
     * ImagePattern se o caminho for válido, cor por hash como fallback.
     */
    private void applyAvatarPreview(String imagePath) {
        if (imagePath != null && !imagePath.isBlank() && new File(imagePath).exists()) {
            Image img = new Image(new File(imagePath).toURI().toString());
            settingsAvatarCircle.setFill(new ImagePattern(img));
            settingsAvatarInitial.setVisible(false);
            settingsAvatarInitial.setManaged(false);

            // Foto disponível — clique no círculo abre o lightbox
            settingsAvatarContainer.setOnMouseClicked(e -> showAvatarLightbox(imagePath,
                    profileService.getActiveProfile().getUsername()));
            settingsAvatarContainer.setCursor(javafx.scene.Cursor.HAND);
        } else {
            settingsAvatarCircle.setFill(Paint.valueOf(profileService.getAvatarColor()));
            settingsAvatarInitial.setText(profileService.getProfileInitial());
            settingsAvatarInitial.setVisible(true);
            settingsAvatarInitial.setManaged(true);

            // Sem foto — clique abre diretamente o seletor de arquivo
            settingsAvatarContainer.setOnMouseClicked(e -> handleChangeAvatar());
        }
    }

    /**
     * Exibe a foto de perfil ampliada em um Stage flutuante centralizado.
     * Fecha ao clicar em qualquer lugar ou pressionar ESC.
     */
    private void showAvatarLightbox(String imagePath, String username) {
        if (imagePath == null || imagePath.isBlank() || !new File(imagePath).exists())
            return;

        Stage lightbox = new Stage();
        lightbox.initStyle(StageStyle.UNDECORATED);

        Circle bigCircle = new Circle(130);
        bigCircle.setFill(new ImagePattern(new Image(new File(imagePath).toURI().toString())));
        bigCircle.setEffect(new DropShadow(24, Color.web("#000000", 0.55)));

        Label lblName = new Label(username);
        lblName.getStyleClass().add("popover-name");

        Label lblHint = new Label("Clique em qualquer lugar para fechar  ·  ESC");
        lblHint.setStyle("-fx-text-fill: #585b70; -fx-font-size: 10px;");

        VBox content = new VBox(18, bigCircle, lblName, lblHint);
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("avatar-lightbox");

        Scene scene = new Scene(content);
        scene.getStylesheets().add(
                getClass().getResource("/css/style.css").toExternalForm());

        content.setOnMouseClicked(e -> lightbox.close());
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE)
                lightbox.close();
        });

        lightbox.setScene(scene);
        lightbox.sizeToScene();
        lightbox.centerOnScreen();

        content.setOpacity(0);
        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), content);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        lightbox.show();
        fadeIn.play();
    }

    private String getFileExtension(String filename) {
        int dotIdx = filename.lastIndexOf('.');
        return dotIdx > 0 ? filename.substring(dotIdx + 1).toLowerCase() : "png";
    }

    private void updateNotificationsToggleText(boolean enabled) {
        tglNotifications.setText(enabled ? "Ativadas ✓" : "Desativadas");
        tglNotifications.getStyleClass().removeAll("settings-toggle-on", "settings-toggle-off");
        tglNotifications.getStyleClass().add(enabled ? "settings-toggle-on" : "settings-toggle-off");
    }

    private void showUsernameError(String message) {
        if (lblUsernameError == null)
            return;
        lblUsernameError.setText(message);
        lblUsernameError.setVisible(true);
        lblUsernameError.setManaged(true);
    }

    private void hideUsernameError() {
        if (lblUsernameError == null)
            return;
        lblUsernameError.setVisible(false);
        lblUsernameError.setManaged(false);
    }

    private void showFocusError(String message) {
        if (lblFocusError == null)
            return;
        lblFocusError.setText(message);
        lblFocusError.setVisible(true);
        lblFocusError.setManaged(true);
    }

    private void hideFocusError() {
        if (lblFocusError == null)
            return;
        lblFocusError.setVisible(false);
        lblFocusError.setManaged(false);
    }

    private void showSavedFeedback(Button btn, String originalText) {
        btn.setText("✓ Salvo");
        btn.setDisable(true);
        PauseTransition pause = new PauseTransition(Duration.seconds(1.8));
        pause.setOnFinished(e -> {
            btn.setText(originalText);
            btn.setDisable(false);
        });
        pause.play();
    }
}