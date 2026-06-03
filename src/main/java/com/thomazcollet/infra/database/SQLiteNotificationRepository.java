package com.thomazcollet.infra.database;

import com.thomazcollet.domain.model.Notification;
import com.thomazcollet.domain.repository.NotificationRepository;
import com.thomazcollet.infra.database.DatabaseInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SQLiteNotificationRepository implements NotificationRepository {

    private static final Logger logger = LoggerFactory.getLogger(SQLiteNotificationRepository.class);

    @Override
    public void save(Notification notification) {
        String sql = """
                INSERT INTO notifications (profile_id, title, message, is_read)
                VALUES (?, ?, ?, ?);
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, notification.getProfileId());
            pstmt.setString(2, notification.getTitle());
            pstmt.setString(3, notification.getMessage());
            pstmt.setBoolean(4, notification.isRead());

            pstmt.executeUpdate();
            logger.debug("Notificação salva com sucesso para o perfil ID: {}", notification.getProfileId());

        } catch (SQLException e) {
            logger.error("Erro ao salvar notificação no banco de dados.", e);
        }
    }

    @Override
    public List<Notification> findByProfileId(int profileId) {
        String sql = "SELECT * FROM notifications WHERE profile_id = ? ORDER BY created_at DESC;";
        List<Notification> list = new ArrayList<>();

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, profileId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToNotification(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao buscar notificações do perfil ID: {}", profileId, e);
        }
        return list;
    }

    @Override
    public List<Notification> findUnreadByProfileId(int profileId) {
        String sql = "SELECT * FROM notifications WHERE profile_id = ? AND is_read = 0 ORDER BY created_at DESC;";
        List<Notification> list = new ArrayList<>();

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, profileId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToNotification(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao buscar notificações não lidas do perfil ID: {}", profileId, e);
        }
        return list;
    }

    @Override
    public void markAllAsRead(int profileId) {
        String sql = "UPDATE notifications SET is_read = 1 WHERE profile_id = ? AND is_read = 0;";

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, profileId);
            int rowsAffected = pstmt.executeUpdate();
            logger.debug("{} notificações marcadas como lidas para o perfil ID: {}", rowsAffected, profileId);

        } catch (SQLException e) {
            logger.error("Erro ao marcar notificações como lidas para o perfil ID: {}", profileId, e);
        }
    }

    @Override
    public void deleteOlderThanDays(int days) {
        String sql = "DELETE FROM notifications WHERE created_at < datetime('now', 'localtime', ?);";

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, "-" + days + " days");
            int deletedRows = pstmt.executeUpdate();
            if (deletedRows > 0) {
                logger.info("Limpeza automática: {} notificações antigas foram removidas.", deletedRows);
            }

        } catch (SQLException e) {
            logger.error("Erro ao executar limpeza automática de notificações antigas.", e);
        }
    }

    /**
     * Método auxiliar para converter uma linha do banco SQLite em um objeto de
     * Domínio Notification.
     */
    private Notification mapResultSetToNotification(ResultSet rs) throws SQLException {
        int id = rs.getInt("id");
        int profileId = rs.getInt("profile_id");
        String title = rs.getString("title");
        String message = rs.getString("message");
        boolean isRead = rs.getBoolean("is_read");

        // Conversão segura da data do SQLite para o LocalDateTime do Java
        String createdAtStr = rs.getString("created_at");
        LocalDateTime createdAt = createdAtStr != null ? LocalDateTime.parse(createdAtStr.replace(" ", "T"))
                : LocalDateTime.now();

        return new Notification(id, profileId, title, message, isRead, createdAt);
    }
}