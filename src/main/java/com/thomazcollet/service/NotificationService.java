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

    // Propriedade reativa do JavaFX que a Sidebar/Sino irá escutar para atualizar
    // o contador automaticamente
    private final IntegerProperty unreadCountProperty = new SimpleIntegerProperty(0);

    private AudioClip notificationSound;

    // -----------------------------------------------------------------------
    // Despachante de mutações de propriedades JavaFX (unreadCountProperty).
    // Em produção, sempre Platform::runLater — garante que o bind() com o
    // badge do sino na UI só seja tocado na FX Application Thread, mesmo
    // quando send()/loadUnreadCount()/markAllAsRead() são chamados de uma
    // thread de fundo (ex: a tarefa agendada do PomodoroService).
    // Injetável via construtor de pacote para permitir testes unitários
    // síncronos sem precisar inicializar o toolkit do JavaFX.
    // -----------------------------------------------------------------------
    private final Consumer<Runnable> uiDispatcher;

    // -----------------------------------------------------------------------
    // Callback para a UI exibir toast/snackbar em tempo real.
    // Configurado pelo MainController após a criação do serviço.
    // Recebe o objeto Notification completo para que a UI possa formatar
    // título + mensagem da forma que desejar.
    // -----------------------------------------------------------------------
    private Consumer<Notification> toastCallback;

    // -----------------------------------------------------------------------
    // Proteção anti-spam para metas de período (diária, semanal, mensal).
    // Armazena a data da última vez que cada tipo de meta foi notificado.
    // Chaves: "daily_<profileId>", "weekly_<profileId>", "monthly_<profileId>"
    // Resetado automaticamente quando a data muda (verificação lazy).
    // -----------------------------------------------------------------------
    private final Set<String> goalNotifiedToday = new HashSet<>();
    private LocalDate lastGoalCheckDate = LocalDate.now();

    public NotificationService(NotificationRepository notificationRepository) {
        this(notificationRepository, Platform::runLater);
    }

    /**
     * Construtor de visibilidade de pacote — permite injetar um despachante
     * síncrono (ex: {@code Runnable::run}) em testes unitários, evitando a
     * necessidade de inicializar o toolkit do JavaFX apenas para validar a
     * lógica de negócio do serviço.
     */
    NotificationService(NotificationRepository notificationRepository, Consumer<Runnable> uiDispatcher) {
        this.notificationRepository = notificationRepository;
        this.uiDispatcher = uiDispatcher;
        initializeSound();
    }

    // ==========================================================================
    // CONFIGURAÇÃO
    // ==========================================================================

    /**
     * Registra o callback que a UI (MainController) usa para exibir o toast.
     * Deve ser chamado uma única vez durante a inicialização do controller.
     *
     * @param callback Consumer que recebe a notificação e exibe o toast na tela.
     */
    public void setToastCallback(Consumer<Notification> callback) {
        this.toastCallback = callback;
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

    /**
     * Envia uma nova notificação: persiste no banco, incrementa o contador
     * reativo, dispara o toast na UI e toca o som.
     *
     * @param profileId ID do perfil que receberá a notificação.
     * @param title     Título curto (ex: "🏆 Conquista desbloqueada!").
     * @param message   Mensagem amigável com detalhes.
     */
    public void send(int profileId, String title, String message) {
        Notification notification = new Notification(profileId, title, message);
        notificationRepository.save(notification);

        uiDispatcher.accept(() -> unreadCountProperty.set(unreadCountProperty.get() + 1));

        playSound();
        fireToast(notification);

        logger.info("Notificação enviada: [{}] — {}", title, message);
    }

    /**
     * Versão protegida contra spam para notificações de metas de período.
     * Garante que a mesma meta (diária/semanal/mensal) só seja notificada
     * uma vez por dia por perfil.
     *
     * @param profileId ID do perfil.
     * @param goalType  Identificador do tipo de meta: "daily", "weekly" ou
     *                  "monthly".
     * @param title     Título da notificação.
     * @param message   Mensagem da notificação.
     * @return {@code true} se a notificação foi enviada; {@code false} se já foi
     *         enviada hoje para este tipo de meta.
     */
    public boolean sendGoalNotification(int profileId, String goalType, String title, String message) {
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

    /**
     * Reseta o conjunto de metas notificadas quando a data do dia muda.
     * Chamado de forma lazy antes de qualquer verificação de meta.
     */
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

    /**
     * Inicializa o contador de não lidas com base no banco de dados.
     * Deve ser chamado assim que o perfil for carregado na inicialização do app.
     */
    public void loadUnreadCount(int profileId) {
        int count = notificationRepository.findUnreadByProfileId(profileId).size();
        uiDispatcher.accept(() -> unreadCountProperty.set(count));
        logger.debug("Contador de não lidas carregado: {} para o perfil ID {}.", count, profileId);
    }

    /**
     * Busca o histórico completo de notificações do usuário (para o popup do sino).
     */
    public List<Notification> getHistory(int profileId) {
        return notificationRepository.findByProfileId(profileId);
    }

    /**
     * Marca todas as notificações do usuário como lidas e zera o contador da UI.
     */
    public void markAllAsRead(int profileId) {
        notificationRepository.markAllAsRead(profileId);
        uiDispatcher.accept(() -> unreadCountProperty.set(0));
        logger.debug("Todas as notificações do perfil {} marcadas como lidas.", profileId);
    }

    /**
     * Remove notificações antigas para evitar acúmulo no SQLite.
     */
    public void clearOldNotifications(int days) {
        notificationRepository.deleteOlderThanDays(days);
    }

    // ==========================================================================
    // INTERNOS
    // ==========================================================================

    private void playSound() {
        if (notificationSound != null) {
            notificationSound.play();
        }
    }

    private void fireToast(Notification notification) {
        if (toastCallback != null) {
            toastCallback.accept(notification);
        }
    }

    // ==========================================================================
    // GETTERS PARA A VIEW (JavaFX Properties)
    // ==========================================================================

    public IntegerProperty unreadCountProperty() {
        return unreadCountProperty;
    }

    public int getUnreadCount() {
        return unreadCountProperty.get();
    }
}