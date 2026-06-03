package com.thomazcollet.domain.repository; 

import com.thomazcollet.domain.model.Notification; // vamos criar essa model logo em seguida
import java.util.List;

public interface NotificationRepository {

    /**
     * Salva uma nova notificação no banco de dados.
     */
    void save(Notification notification);

    /**
     * Retorna todas as notificações de um perfil específico, ordenadas pelas mais
     * recentes.
     */
    List<Notification> findByProfileId(int profileId);

    /**
     * Retorna apenas as notificações que ainda não foram lidas por um perfil.
     */
    List<Notification> findUnreadByProfileId(int profileId);

    /**
     * Marca todas as notificações de um perfil como lidas de uma só vez.
     */
    void markAllAsRead(int profileId);

    /**
     * Opcional: Remove notificações muito antigas para não acumular lixo no banco.
     */
    void deleteOlderThanDays(int days);
}