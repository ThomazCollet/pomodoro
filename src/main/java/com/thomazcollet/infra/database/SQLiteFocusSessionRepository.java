package com.thomazcollet.infra.database;

import com.thomazcollet.domain.model.FocusSession;
import com.thomazcollet.domain.model.SessionType;
import com.thomazcollet.domain.repository.FocusSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SQLiteFocusSessionRepository implements FocusSessionRepository {

    private static final Logger logger = LoggerFactory.getLogger(SQLiteFocusSessionRepository.class);

    @Override
    public void save(FocusSession session) {
        String sql = """
                INSERT INTO focus_sessions (profile_id, type, start_timestamp, end_timestamp, duration_seconds, completed)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setLong(1, session.getProfileId());
            pstmt.setString(2, session.getType().name());
            pstmt.setString(3, session.getStartTimestamp().toString());
            pstmt.setString(4, session.getEndTimestamp() != null ? session.getEndTimestamp().toString() : null);
            pstmt.setInt(5, session.getDurationSeconds());
            pstmt.setBoolean(6, session.isCompleted());

            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    session.setId(generatedKeys.getLong(1));
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao persistir FocusSession", e);
            throw new RuntimeException("Falha na persistência da sessão", e);
        }
    }

    @Override
    public List<FocusSession> findByProfileId(Long profileId) {
        List<FocusSession> sessions = new ArrayList<>();
        String sql = "SELECT * FROM focus_sessions WHERE profile_id = ? ORDER BY start_timestamp DESC";

        try (Connection conn = DatabaseInitializer.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, profileId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    sessions.add(mapResultSetToFocusSession(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao buscar sessões por perfil ID: {}", profileId, e);
        }
        return sessions;
    }

    @Override
    public List<FocusSession> findRecent(int limit) {
        List<FocusSession> sessions = new ArrayList<>();
        String sql = "SELECT * FROM focus_sessions ORDER BY start_timestamp DESC LIMIT ?";

        try (Connection conn = DatabaseInitializer.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    sessions.add(mapResultSetToFocusSession(rs));
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao buscar sessões recentes", e);
        }
        return sessions;
    }

    private FocusSession mapResultSetToFocusSession(ResultSet rs) throws SQLException {
        return new FocusSession(
                rs.getLong("id"),
                rs.getLong("profile_id"),
                SessionType.valueOf(rs.getString("type")),
                LocalDateTime.parse(rs.getString("start_timestamp")),
                rs.getString("end_timestamp") != null ? LocalDateTime.parse(rs.getString("end_timestamp")) : null,
                rs.getInt("duration_seconds"),
                rs.getBoolean("completed")
        );
    }
}