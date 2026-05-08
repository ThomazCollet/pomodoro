package com.thomazcollet.infra.database;

import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.repository.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SQLiteProfileRepository implements ProfileRepository {

    private static final Logger logger = LoggerFactory.getLogger(SQLiteProfileRepository.class);

    @Override
    public void save(Profile profile) {
        String sql = """
                INSERT INTO profiles (username, image_path, work_duration, short_break, long_break,
                                    max_streak, max_focus_day_seconds, total_focus_sessions)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, profile.getUsername());
            pstmt.setString(2, profile.getImagePath());
            pstmt.setInt(3, profile.getWorkDuration());
            pstmt.setInt(4, profile.getShortBreak());
            pstmt.setInt(5, profile.getLongBreak());
            pstmt.setInt(6, profile.getMaxStreak());
            pstmt.setInt(7, profile.getMaxFocusDaySeconds());
            pstmt.setInt(8, profile.getTotalFocusSessions());

            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    profile.setId(generatedKeys.getLong(1));
                }
            }
            logger.info("Perfil '{}' salvo com sucesso (ID: {}).", profile.getUsername(), profile.getId());
        } catch (SQLException e) {
            logger.error("Erro ao persistir perfil", e);
            throw new RuntimeException("Falha na persistência do perfil", e);
        }
    }

    @Override
    public void updateStats(Long profileId, int streak, int maxSeconds, int totalSessions) {
        String sql = """
                UPDATE profiles
                SET max_streak = ?, max_focus_day_seconds = ?, total_focus_sessions = ?
                WHERE id = ?
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, streak);
            pstmt.setInt(2, maxSeconds);
            pstmt.setInt(3, totalSessions);
            pstmt.setLong(4, profileId);

            pstmt.executeUpdate();
            logger.debug("Marcos do perfil ID {} atualizados: Streak: {}, Recorde: {}s", profileId, streak, maxSeconds);
        } catch (SQLException e) {
            logger.error("Erro ao atualizar marcos do perfil ID: {}", profileId, e);
            throw new RuntimeException("Falha ao atualizar estatísticas no banco", e);
        }
    }

    @Override
    public Optional<Profile> findById(Long id) {
        String sql = "SELECT * FROM profiles WHERE id = ?";

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToProfile(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao buscar perfil por ID: {}", id, e);
            throw new RuntimeException("Erro na consulta de perfil", e);
        }
        return Optional.empty();
    }

    @Override
    public List<Profile> findAll() {
        List<Profile> profiles = new ArrayList<>();
        String sql = "SELECT * FROM profiles ORDER BY created_at DESC";

        try (Connection conn = DatabaseInitializer.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                profiles.add(mapResultSetToProfile(rs));
            }
        } catch (SQLException e) {
            logger.error("Erro ao listar todos os perfis", e);
            throw new RuntimeException("Erro na listagem de perfis", e);
        }
        return profiles;
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM profiles WHERE id = ?";
        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
            logger.info("Perfil ID {} removido.", id);
        } catch (SQLException e) {
            logger.error("Erro ao deletar perfil ID: {}", id, e);
            throw new RuntimeException("Erro na remoção do perfil", e);
        }
    }

    private Profile mapResultSetToProfile(ResultSet rs) throws SQLException {
        return new Profile(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("image_path"),
                rs.getInt("work_duration"),
                rs.getInt("short_break"),
                rs.getInt("long_break"),
                rs.getInt("max_streak"),
                rs.getInt("max_focus_day_seconds"),
                rs.getInt("total_focus_sessions"),
                rs.getTimestamp("created_at").toLocalDateTime().withNano(0));
    }
}