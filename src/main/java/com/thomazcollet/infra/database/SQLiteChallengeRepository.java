package com.thomazcollet.infra.database;

import com.thomazcollet.domain.model.Challenge;
import com.thomazcollet.domain.model.ChallengeStatus;
import com.thomazcollet.domain.model.ChallengeType;
import com.thomazcollet.domain.repository.ChallengeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SQLiteChallengeRepository implements ChallengeRepository {

    private static final Logger logger = LoggerFactory.getLogger(SQLiteChallengeRepository.class);

    @Override
    public void save(Challenge challenge) {
        String sql = """
                INSERT INTO challenges (
                    profile_id, title, type, duration_days, min_focus_minutes_per_day,
                    target_total_minutes, accumulated_minutes, today_focus_minutes,
                    lives_total, lives_remaining, status, start_date, progress_days
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setLong(1, challenge.getProfileId());
            pstmt.setString(2, challenge.getTitle());
            pstmt.setString(3, challenge.getType().name());
            pstmt.setInt(4, challenge.getDurationDays());
            pstmt.setInt(5, challenge.getMinFocusMinutesPerDay());
            pstmt.setInt(6, challenge.getTargetTotalMinutes());
            pstmt.setInt(7, challenge.getAccumulatedMinutes());
            pstmt.setInt(8, challenge.getTodayFocusMinutes());
            pstmt.setInt(9, challenge.getLivesTotal());
            pstmt.setInt(10, challenge.getLivesRemaining());
            pstmt.setString(11, challenge.getStatus().name());
            pstmt.setString(12, challenge.getStartDate().toString());
            pstmt.setInt(13, challenge.getProgressDays());

            pstmt.executeUpdate();

            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    challenge.setId(generatedKeys.getLong(1));
                }
            }
            logger.info("Desafio '{}' ({}) criado com sucesso.", challenge.getTitle(), challenge.getType());
        } catch (SQLException e) {
            logger.error("Erro ao salvar desafio", e);
            throw new RuntimeException("Falha na persistência do desafio", e);
        }
    }

    @Override
    public void updateProgress(Long challengeId, int progressDays, int livesRemaining, String status) {
        String sql = "UPDATE challenges SET progress_days = ?, lives_remaining = ?, status = ? WHERE id = ?";

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, progressDays);
            pstmt.setInt(2, livesRemaining);
            pstmt.setString(3, status);
            pstmt.setLong(4, challengeId);

            pstmt.executeUpdate();
            logger.debug("Progresso do desafio ID {} atualizado.", challengeId);
        } catch (SQLException e) {
            logger.error("Erro ao atualizar progresso do desafio", e);
            throw new RuntimeException("Falha ao atualizar desafio no banco", e);
        }
    }

    /**
     * Novo método para suportar o desafio de Intensidade (Milestone)
     */
    public void updateMilestoneProgress(Long challengeId, int accumulatedMinutes, String status) {
        String sql = "UPDATE challenges SET accumulated_minutes = ?, status = ? WHERE id = ?";

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, accumulatedMinutes);
            pstmt.setString(2, status);
            pstmt.setLong(3, challengeId);

            pstmt.executeUpdate();
            logger.debug("Acumulado do desafio Milestone ID {} atualizado para {} min.", challengeId,
                    accumulatedMinutes);
        } catch (SQLException e) {
            logger.error("Erro ao atualizar acumulado do desafio", e);
            throw new RuntimeException("Falha ao atualizar milestone no banco", e);
        }
    }

    @Override
    public void updateDailyFocus(Long challengeId, int minutes) {
        String sql = "UPDATE challenges SET today_focus_minutes = ? WHERE id = ?";

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, minutes);
            pstmt.setLong(2, challengeId);

            pstmt.executeUpdate();
            logger.debug("Minutos de foco diário do desafio ID {} atualizados para {}.", challengeId, minutes);
        } catch (SQLException e) {
            logger.error("Erro ao atualizar minutos de foco", e);
            throw new RuntimeException("Falha ao atualizar foco no banco", e);
        }
    }

    @Override
    public List<Challenge> findActiveByProfile(Long profileId) {
        String sql = "SELECT * FROM challenges WHERE profile_id = ? AND status = 'ACTIVE'";
        return findListByQuery(sql, profileId);
    }

    @Override
    public List<Challenge> findAllByProfile(Long profileId) {
        String sql = "SELECT * FROM challenges WHERE profile_id = ? ORDER BY start_date DESC";
        return findListByQuery(sql, profileId);
    }

    @Override
    public Optional<Challenge> findById(Long id) {
        String sql = "SELECT * FROM challenges WHERE id = ?";
        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next())
                    return Optional.of(mapResultSetToChallenge(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar desafio", e);
        }
        return Optional.empty();
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM challenges WHERE id = ?";
        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao deletar desafio", e);
        }
    }

    @Override
    public int countCompletedChallengesByTypeAndMinDuration(Long profileId, String type, int minDays) {
        String sql = """
                SELECT COUNT(*) FROM challenges
                WHERE profile_id = ?
                  AND status = 'COMPLETED'
                  AND type = ?
                  AND duration_days >= ?
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, profileId);
            pstmt.setString(2, type);
            pstmt.setInt(3, minDays);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao contar desafios concluídos por tipo e duração mínima", e);
        }
        return 0;
    }

    @Override
    public int countPerfectCompletedChallenges(Long profileId, int minDays) {
        String sql = """
                SELECT COUNT(*) FROM challenges
                WHERE profile_id = ?
                  AND status = 'COMPLETED'
                  AND type = 'STREAK_CHALLENGE'
                  AND duration_days >= ?
                  AND lives_remaining = lives_total
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, profileId);
            pstmt.setInt(2, minDays);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            logger.error("Erro ao contar desafios perfeitos concluídos", e);
        }
        return 0;
    }

    @Override
    public boolean hasCompletedIntensityChallenge(Long profileId, int minDays, int minTargetHours) {
        // Converte horas da planilha para minutos (já que seu banco armazena
        // target_total_minutes)
        int minTargetMinutes = minTargetHours * 60;

        String sql = """
                SELECT 1 FROM challenges
                WHERE profile_id = ?
                  AND status = 'COMPLETED'
                  AND type = 'MILESTONE_CHALLENGE'
                  AND duration_days >= ?
                  AND target_total_minutes >= ?
                LIMIT 1
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, profileId);
            pstmt.setInt(2, minDays);
            pstmt.setInt(3, minTargetMinutes);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next(); // Retorna true se encontrar pelo menos um registro correspondente
            }
        } catch (SQLException e) {
            logger.error("Erro ao verificar existência de desafio de intensidade específico", e);
        }
        return false;
    }

    private List<Challenge> findListByQuery(String sql, Long profileId) {
        List<Challenge> list = new ArrayList<>();
        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, profileId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToChallenge(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao listar desafios", e);
        }
        return list;
    }

    private Challenge mapResultSetToChallenge(ResultSet rs) throws SQLException {
        Challenge c = new Challenge();
        c.setId(rs.getLong("id"));
        c.setProfileId(rs.getLong("profile_id"));
        c.setTitle(rs.getString("title"));

        // Mapeamento dos novos campos Enum e Inteiros
        c.setType(ChallengeType.valueOf(rs.getString("type")));
        c.setTargetTotalMinutes(rs.getInt("target_total_minutes"));
        c.setAccumulatedMinutes(rs.getInt("accumulated_minutes"));
        c.setTodayFocusMinutes(rs.getInt("today_focus_minutes"));

        c.setDurationDays(rs.getInt("duration_days"));
        c.setMinFocusMinutesPerDay(rs.getInt("min_focus_minutes_per_day"));
        c.setLivesTotal(rs.getInt("lives_total"));
        c.setLivesRemaining(rs.getInt("lives_remaining"));
        c.setStatus(ChallengeStatus.valueOf(rs.getString("status")));
        c.setStartDate(LocalDate.parse(rs.getString("start_date")));
        c.setProgressDays(rs.getInt("progress_days"));

        return c;
    }

    @Override
    public void deleteAllByProfileId(Long profileId) {
        String sql = "DELETE FROM challenges WHERE profile_id = ?";
        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, profileId);
            int rows = pstmt.executeUpdate();
            logger.info("{} desafios removidos para o perfil ID {}.", rows, profileId);
        } catch (SQLException e) {
            logger.error("Erro ao deletar desafios do perfil ID: {}", profileId, e);
            throw new RuntimeException("Falha ao limpar desafios", e);
        }
    }
}