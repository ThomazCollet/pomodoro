package com.thomazcollet.service; // ajuste o pacote se necessário

import com.thomazcollet.domain.model.Notification;
import com.thomazcollet.domain.repository.NotificationRepository;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.media.AudioClip;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.util.List;

/**
 * Serviço centralizador que gerencia a lógica de negócios, eventos de interface
 * e efeitos sonoros do sistema de notificações.
 */
public class NotificationService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    // Propriedade reativa do JavaFX que a Sidebar/Sino irá escutar para atualizar o
    // contador automaticamente
    private final IntegerProperty unreadCountProperty = new SimpleIntegerProperty(0);

    private AudioClip notificationSound;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
        initializeSound();
    }

    /**
     * Carrega o efeito sonoro a partir da pasta de recursos (assets).
     */
    private void initializeSound() {
        try {
            URL soundUrl = getClass().getResource("/assets/sounds/start_notification.wav");
            if (soundUrl != null) {
                this.notificationSound = new AudioClip(soundUrl.toExternalForm());
                logger.info("Efeito sonoro de notificação carregado com sucesso.");
            } else {
                logger.warn("AVISO: Arquivo de som '/assets/sounds/start_notification.wav' não foi encontrado.");
            }
        } catch (Exception e) {
            logger.error("Erro ao carregar o arquivo de áudio da notificação.", e);
        }
    }

    /**
     * Inicializa ou atualiza o contador de mensagens não lidas com base no banco de
     * dados.
     * Deve ser chamado assim que o perfil do usuário for carregado na inicialização
     * do app.
     */
    public void loadUnreadCount(int profileId) {
        int unreadCount = notificationRepository.findUnreadByProfileId(profileId).size();
        unreadCountProperty.set(unreadCount);
        logger.debug("Contador de notificações não lidas atualizado para o perfil ID {}: {}", profileId, unreadCount);
    }

    /**
     * Envia uma nova notificação: salva no banco, atualiza a UI e reproduz o som.
     */
    public void send(int profileId, String title, String message) {
        // 1. Instancia e salva no banco de dados
        Notification notification = new Notification(profileId, title, message);
        notificationRepository.save(notification);

        // 2. Incrementa o contador reativo da interface
        unreadCountProperty.set(unreadCountProperty.get() + 1);

        // 3. Reproduz o som em segundo plano (sem travar a aplicação)
        if (notificationSound != null) {
            notificationSound.play();
        }

        logger.info("Notificação enviada com sucesso: [{}] - {}", title, message);
    }

    /**
     * Busca o histórico de todas as notificações do usuário (para preencher o popup
     * do sino).
     */
    public List<Notification> getHistory(int profileId) {
        return notificationRepository.findByProfileId(profileId);
    }

    /**
     * Marca todas as notificações do usuário como lidas e zera o contador da UI.
     */
    public void markAllAsRead(int profileId) {
        notificationRepository.markAllAsRead(profileId);
        unreadCountProperty.set(0);
        logger.debug("Todas as notificações do perfil ID {} foram marcadas como lidas.", profileId);
    }

    /**
     * Executa a limpeza de mensagens antigas para evitar acúmulo no SQLite.
     */
    public void clearOldNotifications(int days) {
        notificationRepository.deleteOlderThanDays(days);
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