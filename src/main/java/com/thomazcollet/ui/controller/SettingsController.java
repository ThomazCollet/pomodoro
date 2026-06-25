package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.model.StreakRule;
import com.thomazcollet.service.AudioService;
import com.thomazcollet.service.NotificationService;
import com.thomazcollet.service.PomodoroService;
import com.thomazcollet.service.ProfileService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller da tela de Configurações.
 *
 * <p>
 * Fases implementadas:
 * <ul>
 * <li>Fase 1 — esqueleto e carregamento de dados reais.</li>
 * <li>Fase 2 — seção de Metas (diária, semanal, mensal).</li>
 * <li>Fase 3 — volume de áudio, notificações e idioma.</li>
 * <li>Fase 4 — durações de foco e pausas.</li>
 * </ul>
 *
 * <p>
 * Pendente:
 * <ul>
 * <li>Fase 5 — avatar e username (FileChooser + cópia gerenciada).</li>
 * <li>Fase final — Zona de Risco (limpar histórico / resetar progresso).</li>
 * </ul>
 */
public class SettingsController {

    private static final Logger logger = LoggerFactory.getLogger(SettingsController.class);

    // ── Seção Perfil ────────────────────────────────────────────────────────
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
    private PomodoroService pomodoroService; // nullable — pode ser nulo se o timer nunca foi aberto

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
            PomodoroService pomodoroService) {
        this.profileService = profileService;
        this.audioService = audioService;
        this.notificationService = notificationService;
        this.pomodoroService = pomodoroService; // pode ser nulo
        setupStaticComponents();
        loadProfileData();
    }

    private void setupStaticComponents() {
        // Metas
        spnDailyGoalHours.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 1));
        spnDailyGoalMinutes.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 30, 5));
        spnWeeklyGoalHours.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 168, 7));
        spnMonthlyGoalHours.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 744, 30));

        // Foco
        spnWorkDuration.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 60, 25));
        spnShortBreak.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 30, 5));
        spnLongBreak.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 60, 15));

        ToggleGroup focusGroup = new ToggleGroup();
        rdoFocusDefault.setToggleGroup(focusGroup);
        rdoFocusCustom.setToggleGroup(focusGroup);

        // Áudio
        sliderVolume.valueProperty()
                .addListener((obs, oldVal, newVal) -> lblVolumeValue.setText(newVal.intValue() + "%"));

        // Idioma
        cmbLanguage.getItems().addAll(LANGUAGE_OPTIONS.keySet());
    }

    private void loadProfileData() {
        if (profileService == null)
            return;
        Profile p = profileService.getActiveProfile();

        // Perfil
        txtUsername.setText(p.getUsername());
        settingsAvatarCircle.setFill(Paint.valueOf(profileService.getAvatarColor()));
        settingsAvatarInitial.setText(profileService.getProfileInitial());

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
            logger.info("Metas salvas pelo usuário.");
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

        // Aplica imediatamente no serviço de áudio
        audioService.setVolume(volume);

        // Persiste apenas o volume; os demais campos de settings ficam inalterados
        profileService.saveSettings(
                volume,
                p.isNotificationsEnabled(),
                p.getLanguage(),
                p.getStreakRule());

        showSavedFeedback(btnSaveAudio, "Salvar Áudio");
        logger.info("Volume salvo: {}%.", volume);
    }

    @FXML
    private void handleNotificationsToggle() {
        updateNotificationsToggleText(tglNotifications.isSelected());
    }

    @FXML
    private void handleSaveNotifications() {
        boolean enabled = tglNotifications.isSelected();
        Profile p = profileService.getActiveProfile();

        // Aplica imediatamente no serviço de notificações
        if (notificationService != null) {
            notificationService.setEnabled(enabled);
        }

        // Persiste; os demais campos ficam inalterados
        profileService.saveSettings(
                p.getAudioVolume(),
                enabled,
                p.getLanguage(),
                p.getStreakRule());

        showSavedFeedback(btnSaveNotifications, "Salvar Notificações");
        logger.info("Notificações {}.", enabled ? "ativadas" : "desativadas");
    }

    @FXML
    private void handleSaveLanguage() {
        String selectedLabel = cmbLanguage.getValue();
        if (selectedLabel == null)
            return;

        String langCode = LANGUAGE_OPTIONS.getOrDefault(selectedLabel, "pt_BR");
        Profile p = profileService.getActiveProfile();

        profileService.saveSettings(
                p.getAudioVolume(),
                p.isNotificationsEnabled(),
                langCode,
                p.getStreakRule());

        showSavedFeedback(btnSaveLanguage, "Salvar Idioma");
        logger.info("Idioma salvo: {}.", langCode);
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
            // Ex: longBreak <= shortBreak — mostra o erro inline sem diálogo
            showFocusError(e.getMessage());
            return;
        }

        // Notifica o PomodoroService para que ele recarregue as durações em memória.
        // Se o timer nunca foi aberto (pomodoroService == null), as novas durações
        // serão lidas do Profile quando o PomodoroService for criado.
        if (pomodoroService != null) {
            pomodoroService.updateProfile(profileService.getActiveProfile());
        }

        showSavedFeedback(btnSaveFocus, "Salvar Configurações de Foco");
        logger.info("Durações salvas: foco={}m, curta={}m, longa={}m.", work, small, large);
    }

    // ==========================================================================
    // HANDLERS — PERFIL (Fase 5 — TODO)
    // ==========================================================================

    @FXML
    private void handleChangeAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Escolher foto de perfil");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imagens", "*.png", "*.jpg", "*.jpeg", "*.gif"));
        File chosen = chooser.showOpenDialog(btnSaveProfile.getScene().getWindow());
        if (chosen != null) {
            logger.info("Avatar selecionado (pendente Fase 5): {}", chosen.getAbsolutePath());
            // TODO Fase 5: copiar para data/avatars/, atualizar preview, persistir
        }
    }

    @FXML
    private void handleSaveProfile() {
        lblUsernameError.setVisible(false);
        lblUsernameError.setManaged(false);
        // TODO Fase 5: validar txtUsername, persistir e atualizar avatar/inicial na
        // sidebar
        showSavedFeedback(btnSaveProfile, "Salvar Perfil");
    }

    // ==========================================================================
    // HANDLERS — ZONA DE RISCO (Fase final — TODO)
    // ==========================================================================

    @FXML
    private void handleClearHistory() {
        // TODO Fase final: confirmar via DialogHelper, chamar
        // focusSessionService.clearAll()
        logger.debug("handleClearHistory() — pendente fase final.");
    }

    @FXML
    private void handleResetProgress() {
        // TODO Fase final: confirmar dupla, resetar tudo
        logger.debug("handleResetProgress() — pendente fase final.");
    }

    // ==========================================================================
    // HELPERS
    // ==========================================================================

    private void updateNotificationsToggleText(boolean enabled) {
        tglNotifications.setText(enabled ? "Ativadas ✓" : "Desativadas");
        tglNotifications.getStyleClass().removeAll("settings-toggle-on", "settings-toggle-off");
        tglNotifications.getStyleClass().add(enabled ? "settings-toggle-on" : "settings-toggle-off");
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