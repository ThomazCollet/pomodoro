package com.thomazcollet.infra.database;

import com.thomazcollet.domain.model.FocusSession;
import com.thomazcollet.domain.model.SessionType;
import com.thomazcollet.domain.repository.FocusSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SQLiteFocusSessionRepository implements FocusSessionRepository {

    private static final Logger logger = LoggerFactory.getLogger(SQLiteFocusSessionRepository.class);

    // Formatador para compatibilidade total com o SQLite (evita o erro do "T" nas
    // datas)
    private static final DateTimeFormatter SQLITE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

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
            pstmt.setString(3, session.getStartTimestamp().format(SQLITE_FORMATTER));
            pstmt.setString(4,
                    session.getEndTimestamp() != null ? session.getEndTimestamp().format(SQLITE_FORMATTER) : null);
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

    @Override
    public long sumDurationSecondsByProfileIdAndPeriod(Long profileId, LocalDateTime start, LocalDateTime end) {
        String sql = """
                SELECT SUM(duration_seconds) FROM focus_sessions
                WHERE profile_id = ? AND type = 'FOCUS' AND completed = 1
                AND start_timestamp BETWEEN ? AND ?
                """;
        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, profileId);
            pstmt.setString(2, start.format(SQLITE_FORMATTER));
            pstmt.setString(3, end.format(SQLITE_FORMATTER));

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next())
                    return rs.getLong(1);
            }
        } catch (SQLException e) {
            logger.error("Erro ao somar duração por período", e);
        }
        return 0;
    }

    @Override
    public Map<String, Integer> getDailyFocusSummary(Long profileId, int daysToLookBack) {
        Map<String, Integer> summary = new HashMap<>();
        String sql = """
                SELECT date(start_timestamp) as day, SUM(duration_seconds) as total
                FROM focus_sessions
                WHERE profile_id = ? AND type = 'FOCUS' AND completed = 1
                GROUP BY day
                ORDER BY day DESC LIMIT ?
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, profileId);
            pstmt.setInt(2, daysToLookBack);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    summary.put(rs.getString("day"), rs.getInt("total"));
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao buscar resumo diário de foco", e);
        }
        return summary;
    }

    @Override
    public Map<LocalDate, Long> getDailyFocusTime(Long profileId, LocalDateTime since) {
        Map<LocalDate, Long> summary = new HashMap<>();
        String sql = """
                SELECT date(start_timestamp) as day, SUM(duration_seconds) as total
                FROM focus_sessions
                WHERE profile_id = ? AND type = 'FOCUS' AND completed = 1
                AND start_timestamp >= ?
                GROUP BY day
                ORDER BY day ASC
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, profileId);
            pstmt.setString(2, since.format(SQLITE_FORMATTER));

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    LocalDate date = LocalDate.parse(rs.getString("day"));
                    summary.put(date, rs.getLong("total"));
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao buscar dados do Heatmap", e);
        }
        return summary;
    }

    private FocusSession mapResultSetToFocusSession(ResultSet rs) throws SQLException {
        return new FocusSession(
                rs.getLong("id"),
                rs.getLong("profile_id"),
                SessionType.valueOf(rs.getString("type")),
                LocalDateTime.parse(rs.getString("start_timestamp").replace(" ", "T")),
                rs.getString("end_timestamp") != null
                        ? LocalDateTime.parse(rs.getString("end_timestamp").replace(" ", "T"))
                        : null,
                rs.getInt("duration_seconds"),
                rs.getBoolean("completed"));
    }
}