package com.thomazcollet.ui.controller;

import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.model.StreakRule;
import com.thomazcollet.service.AudioService;
import com.thomazcollet.service.ProfileService;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller da tela de Configurações.
 *
 * <p>
 * <b>Fase 1 — esqueleto:</b> todos os campos carregam os dados reais do
 * perfil ativo e a navegação funciona. Os botões "Salvar" de cada seção ainda
 * não persistem nada — cada seção será fiada nas fases seguintes, conforme
 * o roteiro de implementação definido.
 *
 * <p>
 * Métodos marcados com {@code // TODO Fase N} indicam onde a lógica de
 * persistência de cada seção deverá ser adicionada.
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
    private Button btnChangeAvatar;
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

    // Mapa ordenado: rótulo visível → código interno
    private static final Map<String, String> LANGUAGE_OPTIONS = new LinkedHashMap<>();
    static {
        LANGUAGE_OPTIONS.put("Português (Brasil)", "pt_BR");
        LANGUAGE_OPTIONS.put("English (US)", "en_US");
        LANGUAGE_OPTIONS.put("Español", "es_ES");
    }

    // ==========================================================================
    // INICIALIZAÇÃO
    // ==========================================================================

    /**
     * Chamado pelo MainController após carregar o FXML, passando as dependências
     * necessárias e disparando o preenchimento dos campos.
     */
    public void initData(ProfileService profileService, AudioService audioService) {
        this.profileService = profileService;
        this.audioService = audioService;
        setupStaticComponents();
        loadProfileData();
    }

    /**
     * Configura componentes que não dependem do perfil (spinners, rádios, etc.).
     */
    private void setupStaticComponents() {
        // Spinners de metas
        spnDailyGoalHours.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 23, 1));
        spnDailyGoalMinutes.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 59, 30, 5));
        spnWeeklyGoalHours.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 168, 7));
        spnMonthlyGoalHours.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 744, 30));

        // Spinners de foco
        spnWorkDuration.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 60, 25));
        spnShortBreak.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 30, 5));
        spnLongBreak.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 60, 15));

        // Grupo de rádio para modo de foco
        ToggleGroup focusGroup = new ToggleGroup();
        rdoFocusDefault.setToggleGroup(focusGroup);
        rdoFocusCustom.setToggleGroup(focusGroup);

        // Slider de volume — atualiza label em tempo real
        sliderVolume.valueProperty()
                .addListener((obs, oldVal, newVal) -> lblVolumeValue.setText(newVal.intValue() + "%"));

        // ComboBox de idioma
        cmbLanguage.getItems().addAll(LANGUAGE_OPTIONS.keySet());
    }

    /** Preenche todos os campos com os valores reais do perfil ativo. */
    private void loadProfileData() {
        if (profileService == null)
            return;

        Profile p = profileService.getActiveProfile();

        // ── Perfil ──
        txtUsername.setText(p.getUsername());
        settingsAvatarCircle.setFill(Paint.valueOf(profileService.getAvatarColor()));
        settingsAvatarInitial.setText(profileService.getProfileInitial());

        // ── Metas ──
        int dailyTotalSeconds = p.getDailyGoalSeconds();
        spnDailyGoalHours.getValueFactory().setValue(dailyTotalSeconds / 3600);
        spnDailyGoalMinutes.getValueFactory().setValue((dailyTotalSeconds % 3600) / 60);
        spnWeeklyGoalHours.getValueFactory().setValue(p.getWeeklyGoalSeconds() / 3600);
        spnMonthlyGoalHours.getValueFactory().setValue(p.getMonthlyGoalSeconds() / 3600);

        // ── Foco ──
        boolean isDefault = p.getWorkDuration() == 25
                && p.getShortBreak() == 5
                && p.getLongBreak() == 15;
        rdoFocusDefault.setSelected(isDefault);
        rdoFocusCustom.setSelected(!isDefault);
        boxFocusCustomFields.setDisable(isDefault);

        spnWorkDuration.getValueFactory().setValue(p.getWorkDuration());
        spnShortBreak.getValueFactory().setValue(p.getShortBreak());
        spnLongBreak.getValueFactory().setValue(p.getLongBreak());

        // ── Áudio ──
        sliderVolume.setValue(p.getAudioVolume());
        lblVolumeValue.setText(p.getAudioVolume() + "%");

        // ── Notificações ──
        tglNotifications.setSelected(p.isNotificationsEnabled());
        updateNotificationsToggleText(p.isNotificationsEnabled());

        // ── Idioma ──
        String currentLanguage = p.getLanguage() != null ? p.getLanguage() : "pt_BR";
        LANGUAGE_OPTIONS.entrySet().stream()
                .filter(e -> e.getValue().equals(currentLanguage))
                .findFirst()
                .ifPresentOrElse(
                        e -> cmbLanguage.setValue(e.getKey()),
                        () -> cmbLanguage.setValue("Português (Brasil)"));
    }

    // ==========================================================================
    // HANDLERS — SEÇÃO PERFIL
    // ==========================================================================

    @FXML
    private void handleChangeAvatar() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Escolher foto de perfil");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Imagens", "*.png", "*.jpg", "*.jpeg", "*.gif"));

        File chosen = chooser.showOpenDialog(btnChangeAvatar.getScene().getWindow());
        if (chosen != null) {
            logger.info("Avatar selecionado: {}", chosen.getAbsolutePath());
            // TODO Fase 5: copiar arquivo para data/avatars/, atualizar preview e persistir
        }
    }

    @FXML
    private void handleSaveProfile() {
        lblUsernameError.setVisible(false);
        lblUsernameError.setManaged(false);
        // TODO Fase 5: validar txtUsername, chamar profileService.saveProfileInfo(),
        // atualizar avatar/inicial na sidebar
        logger.debug("handleSaveProfile() chamado — persistência será implementada na Fase 5.");
        showSavedFeedback(btnSaveProfile, "Salvar Perfil");
    }

    // ==========================================================================
    // HANDLERS — SEÇÃO METAS
    // ==========================================================================

    @FXML
    private void handleSaveGoals() {
        // TODO Fase 2: ler spinners, converter para segundos, chamar
        // profileService.saveGoals()
        logger.debug("handleSaveGoals() chamado — persistência será implementada na Fase 2.");
        showSavedFeedback(btnSaveGoals, "Salvar Metas");
    }

    // ==========================================================================
    // HANDLERS — SEÇÃO FOCO
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
    }

    @FXML
    private void handleSaveFocus() {
        // TODO Fase 4: validar regra longBreak > shortBreak, chamar
        // profileService.saveDurations(),
        // notificar PomodoroService via pomodoroService.updateProfile()
        logger.debug("handleSaveFocus() chamado — persistência será implementada na Fase 4.");
        showSavedFeedback(btnSaveFocus, "Salvar Configurações de Foco");
    }

    // ==========================================================================
    // HANDLERS — SEÇÃO ÁUDIO
    // ==========================================================================

    @FXML
    private void handleSaveAudio() {
        // TODO Fase 3: chamar audioService.setVolume(),
        // profileService.saveAudioVolume()
        logger.debug("handleSaveAudio() chamado — persistência será implementada na Fase 3.");
        showSavedFeedback(btnSaveAudio, "Salvar Áudio");
    }

    // ==========================================================================
    // HANDLERS — SEÇÃO NOTIFICAÇÕES
    // ==========================================================================

    @FXML
    private void handleNotificationsToggle() {
        updateNotificationsToggleText(tglNotifications.isSelected());
    }

    @FXML
    private void handleSaveNotifications() {
        // TODO Fase 3: chamar profileService.saveNotificationsEnabled(),
        // notificationService.setEnabled()
        logger.debug("handleSaveNotifications() chamado — persistência será implementada na Fase 3.");
        showSavedFeedback(btnSaveNotifications, "Salvar Notificações");
    }

    // ==========================================================================
    // HANDLERS — SEÇÃO IDIOMA
    // ==========================================================================

    @FXML
    private void handleSaveLanguage() {
        // TODO Fase 3: resolver código do idioma via LANGUAGE_OPTIONS, chamar
        // profileService.saveLanguage()
        logger.debug("handleSaveLanguage() chamado — persistência será implementada na Fase 3.");
        showSavedFeedback(btnSaveLanguage, "Salvar Idioma");
    }

    // ==========================================================================
    // HANDLERS — ZONA DE RISCO
    // ==========================================================================

    @FXML
    private void handleClearHistory() {
        // TODO Fase final: confirmar com diálogo, chamar focusSessionService.clearAll()
        logger.debug("handleClearHistory() chamado — persistência será implementada na fase final.");
    }

    @FXML
    private void handleResetProgress() {
        // TODO Fase final: confirmar dupla (diálogo + campo de digitação), resetar tudo
        logger.debug("handleResetProgress() chamado — persistência será implementada na fase final.");
    }

    // ==========================================================================
    // HELPERS
    // ==========================================================================

    private void updateNotificationsToggleText(boolean enabled) {
        tglNotifications.setText(enabled ? "Ativadas ✓" : "Desativadas");
        tglNotifications.getStyleClass().removeAll("settings-toggle-on", "settings-toggle-off");
        tglNotifications.getStyleClass().add(enabled ? "settings-toggle-on" : "settings-toggle-off");
    }

    /**
     * Feedback visual temporário de "salvo" no botão — sem toast (já é uma tela
     * de configurações, não uma ação de domínio).
     */
    private void showSavedFeedback(Button btn, String originalText) {
        btn.setText("✓ Salvo");
        btn.setDisable(true);
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(
                javafx.util.Duration.seconds(1.8));
        pause.setOnFinished(e -> {
            btn.setText(originalText);
            btn.setDisable(false);
        });
        pause.play();
    }
}