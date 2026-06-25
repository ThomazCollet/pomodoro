package com.thomazcollet.service;

import com.thomazcollet.domain.model.Notification;
import com.thomazcollet.domain.repository.NotificationRepository;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.media.AudioClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Serviço centralizador que gerencia a lógica de negócios, eventos de interface
 * e efeitos sonoros do sistema de notificações.
 *
 * <p>
 * Responsabilidades:
 * <ul>
 * <li>Persistir notificações no banco via {@link NotificationRepository}.</li>
 * <li>Manter o contador reativo de não lidas para a sidebar.</li>
 * <li>Reproduzir o som de notificação.</li>
 * <li>Notificar a UI via callback para exibir toast/snackbar em tempo
 * real.</li>
 * <li>Proteger contra spam de metas repetindo a mesma data.</li>
 * </ul>
 */
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    // Propriedade reativa — o badge do sino a escuta para atualizar automaticamente
    private final IntegerProperty unreadCountProperty = new SimpleIntegerProperty(0);

    private AudioClip notificationSound;

    // -----------------------------------------------------------------------
    // Despachante de mutações de propriedades JavaFX (unreadCountProperty).
    // Em produção: Platform::runLater — garante que o bind() com o badge do
    // sino só seja tocado na FX Application Thread, mesmo quando send() é
    // chamado de uma thread de fundo (ex: tarefa agendada do PomodoroService).
    // Injetável via construtor de pacote para testes unitários síncronos sem
    // inicializar o toolkit do JavaFX.
    // -----------------------------------------------------------------------
    private final Consumer<Runnable> uiDispatcher;

    // -----------------------------------------------------------------------
    // Quando false, send() persiste no banco e atualiza o badge normalmente,
    // mas suprime toast e som em tempo real.
    // Controlado pela preferência "Notificações" na tela de Configurações.
    // -----------------------------------------------------------------------
    private boolean enabled = true;

    // -----------------------------------------------------------------------
    // Callback para a UI exibir toast/snackbar em tempo real.
    // Configurado pelo MainController após a criação do serviço.
    // -----------------------------------------------------------------------
    private Consumer<Notification> toastCallback;

    // -----------------------------------------------------------------------
    // Proteção anti-spam para metas de período (diária, semanal, mensal).
    // -----------------------------------------------------------------------
    private final Set<String> goalNotifiedToday = new HashSet<>();
    private LocalDate lastGoalCheckDate = LocalDate.now();

    // ==========================================================================
    // CONSTRUTORES
    // ==========================================================================

    public NotificationService(NotificationRepository notificationRepository) {
        this(notificationRepository, Platform::runLater);
    }

    /**
     * Construtor de visibilidade de pacote — permite injetar um despachante
     * síncrono (ex: {@code Runnable::run}) em testes unitários, evitando a
     * necessidade de inicializar o toolkit do JavaFX.
     */
    NotificationService(NotificationRepository notificationRepository,
            Consumer<Runnable> uiDispatcher) {
        this.notificationRepository = notificationRepository;
        this.uiDispatcher = uiDispatcher;
        initializeSound();
    }

    // ==========================================================================
    // CONFIGURAÇÃO
    // ==========================================================================

    public void setToastCallback(Consumer<Notification> callback) {
        this.toastCallback = callback;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Ativa ou desativa toasts e sons em tempo real.
     * A persistência no banco e o badge do sino continuam funcionando normalmente.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        logger.info("Notificações em tempo real {}.", enabled ? "ativadas" : "desativadas");
    }

    // ==========================================================================
    // INICIALIZAÇÃO DE SOM
    // ==========================================================================

    private void initializeSound() {
        try {
            URL soundUrl = getClass().getResource("/assets/sounds/start_notification.wav");
            if (soundUrl != null) {
                this.notificationSound = new AudioClip(soundUrl.toExternalForm());
                logger.info("Efeito sonoro de notificação carregado com sucesso.");
            } else {
                logger.warn("AVISO: Arquivo de som '/assets/sounds/start_notification.wav' não encontrado.");
            }
        } catch (Exception e) {
            logger.error("Erro ao carregar o arquivo de áudio da notificação.", e);
        }
    }

    // ==========================================================================
    // ENVIO DE NOTIFICAÇÕES
    // ==========================================================================

    public void send(int profileId, String title, String message) {
        Notification notification = new Notification(profileId, title, message);
        notificationRepository.save(notification);

        uiDispatcher.accept(() -> unreadCountProperty.set(unreadCountProperty.get() + 1));

        if (enabled) {
            playSound();
            fireToast(notification);
        }

        logger.info("Notificação enviada: [{}] — {}", title, message);
    }

    public boolean sendGoalNotification(int profileId, String goalType,
            String title, String message) {
        refreshGoalGuardIfNeeded();

        String guardKey = goalType + "_" + profileId;
        if (goalNotifiedToday.contains(guardKey)) {
            logger.debug("Meta '{}' do perfil {} já foi notificada hoje — ignorando.", goalType, profileId);
            return false;
        }

        goalNotifiedToday.add(guardKey);
        send(profileId, title, message);
        return true;
    }

    private void refreshGoalGuardIfNeeded() {
        LocalDate today = LocalDate.now();
        if (!today.equals(lastGoalCheckDate)) {
            goalNotifiedToday.clear();
            lastGoalCheckDate = today;
            logger.debug("Guard de metas diárias resetado para o novo dia: {}", today);
        }
    }

    // ==========================================================================
    // CARREGAMENTO E GERENCIAMENTO
    // ==========================================================================

    public void loadUnreadCount(int profileId) {
        int count = notificationRepository.findUnreadByProfileId(profileId).size();
        uiDispatcher.accept(() -> unreadCountProperty.set(count));
        logger.debug("Contador de não lidas carregado: {} para o perfil ID {}.", count, profileId);
    }

    public List<Notification> getHistory(int profileId) {
        return notificationRepository.findByProfileId(profileId);
    }

    public void markAllAsRead(int profileId) {
        notificationRepository.markAllAsRead(profileId);
        uiDispatcher.accept(() -> unreadCountProperty.set(0));
        logger.debug("Todas as notificações do perfil {} marcadas como lidas.", profileId);
    }

    public void clearOldNotifications(int days) {
        notificationRepository.deleteOlderThanDays(days);
    }

    // ==========================================================================
    // INTERNOS
    // ==========================================================================

    private void playSound() {
        if (notificationSound != null)
            notificationSound.play();
    }

    private void fireToast(Notification notification) {
        if (toastCallback != null)
            toastCallback.accept(notification);
    }

    // ==========================================================================
    // GETTERS PARA A VIEW
    // ==========================================================================

    public IntegerProperty unreadCountProperty() {
        return unreadCountProperty;
    }

    public int getUnreadCount() {
        return unreadCountProperty.get();
    }
}