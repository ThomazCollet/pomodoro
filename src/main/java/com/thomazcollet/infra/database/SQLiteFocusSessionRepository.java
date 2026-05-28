package com.thomazcollet.infra.database;

import com.thomazcollet.domain.model.FocusSession;
import com.thomazcollet.domain.model.SessionType;
import com.thomazcollet.domain.repository.FocusSessionRepository;
import com.thomazcollet.domain.repository.FocusSessionRepository.DailyPodiumEntry;
import com.thomazcollet.domain.repository.FocusSessionRepository.MonthlyPodiumEntry;
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

    @Override
    public int findMaxFocusMinutesInAGivenDay(Long profileId) {
        String sql = """
                SELECT MAX(total_daily_seconds) FROM (
                    SELECT SUM(duration_seconds) as total_daily_seconds
                    FROM focus_sessions
                    WHERE profile_id = ? AND type = 'FOCUS' AND completed = 1
                    GROUP BY date(start_timestamp)
                );
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, profileId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    long maxSeconds = rs.getLong(1);
                    return (int) (maxSeconds / 60);
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao buscar o recorde diário de minutos focados para o perfil: {}", profileId, e);
        }
        return 0;
    }

    @Override
    public int countCompletedSessionsByProfileId(Long profileId) {
        String sql = "SELECT COUNT(*) FROM focus_sessions WHERE profile_id = ? AND type = 'FOCUS' AND completed = 1;";

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, profileId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Erro ao contar ciclos pomodoro completados para o perfil: {}", profileId, e);
        }
        return 0;
    }

    @Override
    public int countDistinctDaysWithCompletedFocus(Long profileId) {
        String sql = "SELECT COUNT(DISTINCT date(start_timestamp)) FROM focus_sessions WHERE profile_id = ? AND type = 'FOCUS' AND completed = 1;";

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, profileId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }
        } catch (SQLException e) {
            logger.error("Erro ao contar dias distintos de foco para o perfil: {}", profileId, e);
        }
        return 0;
    }

    @Override
    public int sumTotalFocusMinutesByProfileId(Long profileId) {
        String sql = "SELECT SUM(duration_seconds) FROM focus_sessions WHERE profile_id = ? AND type = 'FOCUS' AND completed = 1;";

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, profileId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    long totalSeconds = rs.getLong(1);
                    return (int) (totalSeconds / 60);
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao somar minutos históricos totais de foco para o perfil: {}", profileId, e);
        }
        return 0;
    }

    @Override
    public int findCurrentStreakDaysByProfileId(Long profileId) {
        String sql = """
                WITH DistinctDays AS (
                    SELECT DISTINCT date(start_timestamp) as focus_date
                    FROM focus_sessions
                    WHERE profile_id = ? AND type = 'FOCUS' AND completed = 1
                ),
                OrderedDays AS (
                    SELECT focus_date,
                           date(focus_date, '-' || (ROW_NUMBER() OVER (ORDER BY focus_date)) || ' day') as base_island
                    FROM DistinctDays
                ),
                IslandLengths AS (
                    SELECT COUNT(*) as streak_days,
                           MAX(focus_date) as last_focus_date
                    FROM OrderedDays
                    GROUP BY base_island
                )
                SELECT streak_days FROM IslandLengths
                WHERE last_focus_date >= date('now', '-1 day')
                ORDER BY last_focus_date DESC LIMIT 1;
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, profileId);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("streak_days");
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao buscar streak atual do perfil: {}", profileId, e);
        }
        return 0;
    }

    @Override
    public int countTimesStreakTargetWasReached(Long profileId, int streakTarget) {
        String sql = """
                WITH DistinctDays AS (
                    SELECT DISTINCT date(start_timestamp) as focus_date
                    FROM focus_sessions
                    WHERE profile_id = ? AND type = 'FOCUS' AND completed = 1
                ),
                OrderedDays AS (
                    SELECT focus_date,
                           date(focus_date, '-' || (ROW_NUMBER() OVER (ORDER BY focus_date)) || ' day') as base_island
                    FROM DistinctDays
                ),
                HistoricalIslands AS (
                    SELECT COUNT(*) as streak_length
                    FROM OrderedDays
                    GROUP BY base_island
                )
                SELECT COUNT(*) FROM HistoricalIslands WHERE streak_length >= ?;
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, profileId);
            pstmt.setInt(2, streakTarget);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao contar recorrência do streak de {} dias para o perfil: {}", streakTarget, profileId,
                    e);
        }
        return 0;
    }

    @Override
    public List<DailyPodiumEntry> getTop3DailyFocusRecords(Long profileId) {
        List<DailyPodiumEntry> podium = new ArrayList<>();
        String sql = """
                SELECT date(start_timestamp) as focus_day, SUM(duration_seconds) as total_seconds
                FROM focus_sessions
                WHERE profile_id = ? AND type = 'FOCUS' AND completed = 1
                GROUP BY focus_day
                ORDER BY total_seconds DESC
                LIMIT 3
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, profileId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    podium.add(new DailyPodiumEntry(
                            rs.getString("focus_day"),
                            rs.getLong("total_seconds")));
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao buscar o pódio de recordes diários para o perfil: {}", profileId, e);
        }
        return podium;
    }

    @Override
    public List<MonthlyPodiumEntry> getTop3MonthlyFocusRecords(Long profileId) {
        List<MonthlyPodiumEntry> podium = new ArrayList<>();
        String sql = """
                SELECT strftime('%Y-%m', start_timestamp) as focus_month, SUM(duration_seconds) as total_seconds
                FROM focus_sessions
                WHERE profile_id = ? AND type = 'FOCUS' AND completed = 1
                GROUP BY focus_month
                ORDER BY total_seconds DESC
                LIMIT 3
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, profileId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    podium.add(new MonthlyPodiumEntry(
                            rs.getString("focus_month"),
                            rs.getLong("total_seconds")));
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao buscar o pódio de recordes mensais para o perfil: {}", profileId, e);
        }
        return podium;
    }
}