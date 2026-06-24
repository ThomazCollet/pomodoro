package com.thomazcollet.infra.database;

import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.model.StreakRule;
import com.thomazcollet.domain.repository.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SQLiteProfileRepository implements ProfileRepository {

    private static final Logger logger = LoggerFactory.getLogger(SQLiteProfileRepository.class);

    /**
     * Persiste um perfil NOVO (sem ID) no banco e atribui o ID gerado de volta
     * ao objeto. Para atualizar um perfil existente, use os métodos granulares
     * updateProfileInfo, updateDurations, updateGoals e updateSettings.
     *
     * @throws IllegalStateException se chamado com um perfil que já possui ID.
     */
    @Override
    public void save(Profile profile) {
        if (profile.getId() != null) {
            throw new IllegalStateException(
                    "save() só deve ser usado para novos perfis. " +
                            "Use os métodos update* para atualizar um perfil existente. Profile ID: "
                            + profile.getId());
        }

        String sql = """
                INSERT INTO profiles (username, image_path, work_duration, short_break, long_break,
                                    max_focus_day_seconds, total_focus_sessions, xp,
                                    daily_goal_seconds, weekly_goal_seconds, monthly_goal_seconds,
                                    audio_volume, notifications_enabled, language, streak_rule)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            pstmt.setString(1, profile.getUsername());
            pstmt.setString(2, profile.getImagePath());
            pstmt.setInt(3, profile.getWorkDuration());
            pstmt.setInt(4, profile.getShortBreak());
            pstmt.setInt(5, profile.getLongBreak());
            pstmt.setInt(6, profile.getMaxFocusDaySeconds());
            pstmt.setInt(7, profile.getTotalFocusSessions());
            pstmt.setInt(8, profile.getXp());
            pstmt.setInt(9, profile.getDailyGoalSeconds());
            pstmt.setInt(10, profile.getWeeklyGoalSeconds());
            pstmt.setInt(11, profile.getMonthlyGoalSeconds());
            pstmt.setInt(12, profile.getAudioVolume());
            pstmt.setBoolean(13, profile.isNotificationsEnabled());
            pstmt.setString(14, profile.getLanguage());
            pstmt.setString(15, profile.getStreakRule() != null
                    ? profile.getStreakRule().name()
                    : "ALL_DAYS");

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
    public void updateStats(Long profileId, int maxFocusDaySeconds, int totalSessions) {
        String sql = """
                UPDATE profiles
                SET max_focus_day_seconds = ?, total_focus_sessions = ?
                WHERE id = ?
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, maxFocusDaySeconds);
            pstmt.setInt(2, totalSessions);
            pstmt.setLong(3, profileId);

            pstmt.executeUpdate();
            logger.debug("Marcos do perfil ID {} atualizados: Recorde: {}s", profileId, maxFocusDaySeconds);
        } catch (SQLException e) {
            logger.error("Erro ao atualizar marcos do perfil ID: {}", profileId, e);
            throw new RuntimeException("Falha ao atualizar estatísticas no banco", e);
        }
    }

    @Override
    public void updateXp(Long profileId, int newXp) {
        String sql = "UPDATE profiles SET xp = ? WHERE id = ?";

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, newXp);
            pstmt.setLong(2, profileId);

            pstmt.executeUpdate();
            logger.debug("XP do perfil ID {} atualizado para: {}", profileId, newXp);
        } catch (SQLException e) {
            logger.error("Erro ao atualizar XP do perfil ID: {}", profileId, e);
            throw new RuntimeException("Falha ao atualizar XP no banco", e);
        }
    }

    /**
     * 🆕 Atualiza cirurgicamente as metas de foco personalizadas pelo usuário.
     * Útil para ser utilizado quando a tela de configurações alterar os objetivos.
     */
    @Override
    public void updateGoals(Long profileId, int daily, int weekly, int monthly) {
        String sql = """
                UPDATE profiles
                SET daily_goal_seconds = ?, weekly_goal_seconds = ?, monthly_goal_seconds = ?
                WHERE id = ?
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, daily);
            pstmt.setInt(2, weekly);
            pstmt.setInt(3, monthly);
            pstmt.setLong(4, profileId);

            pstmt.executeUpdate();
            logger.info("Metas do perfil ID {} atualizadas com sucesso.", profileId);
        } catch (SQLException e) {
            logger.error("Erro ao atualizar metas do perfil ID: {}", profileId, e);
            throw new RuntimeException("Falha ao atualizar metas no banco", e);
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
    public void updateProfileInfo(Long profileId, String username, String imagePath) {
        String sql = "UPDATE profiles SET username = ?, image_path = ? WHERE id = ?";
        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, imagePath);
            pstmt.setLong(3, profileId);
            pstmt.executeUpdate();
            logger.info("Informações do perfil ID {} atualizadas.", profileId);
        } catch (SQLException e) {
            logger.error("Erro ao atualizar informações do perfil ID: {}", profileId, e);
            throw new RuntimeException("Falha ao atualizar informações do perfil", e);
        }
    }

    @Override
    public void updateDurations(Long profileId, int workDuration, int shortBreak, int longBreak) {
        String sql = """
                UPDATE profiles
                SET work_duration = ?, short_break = ?, long_break = ?
                WHERE id = ?
                """;
        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, workDuration);
            pstmt.setInt(2, shortBreak);
            pstmt.setInt(3, longBreak);
            pstmt.setLong(4, profileId);
            pstmt.executeUpdate();
            logger.info("Durações do perfil ID {} atualizadas: foco={}m, curta={}m, longa={}m.",
                    profileId, workDuration, shortBreak, longBreak);
        } catch (SQLException e) {
            logger.error("Erro ao atualizar durações do perfil ID: {}", profileId, e);
            throw new RuntimeException("Falha ao atualizar durações do perfil", e);
        }
    }

    @Override
    public void updateSettings(Long profileId, int audioVolume, boolean notificationsEnabled,
            String language, String streakRule) {
        String sql = """
                UPDATE profiles
                SET audio_volume = ?, notifications_enabled = ?, language = ?, streak_rule = ?
                WHERE id = ?
                """;
        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, audioVolume);
            pstmt.setBoolean(2, notificationsEnabled);
            pstmt.setString(3, language);
            pstmt.setString(4, streakRule);
            pstmt.setLong(5, profileId);
            pstmt.executeUpdate();
            logger.info("Configurações do perfil ID {} atualizadas.", profileId);
        } catch (SQLException e) {
            logger.error("Erro ao atualizar configurações do perfil ID: {}", profileId, e);
            throw new RuntimeException("Falha ao atualizar configurações do perfil", e);
        }
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
        String streakRuleStr = rs.getString("streak_rule");
        StreakRule streakRule;
        try {
            streakRule = (streakRuleStr != null) ? StreakRule.valueOf(streakRuleStr) : StreakRule.ALL_DAYS;
        } catch (IllegalArgumentException e) {
            logger.warn("Valor desconhecido de streak_rule '{}', usando ALL_DAYS como fallback.", streakRuleStr);
            streakRule = StreakRule.ALL_DAYS;
        }

        return new Profile(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("image_path"),
                rs.getInt("work_duration"),
                rs.getInt("short_break"),
                rs.getInt("long_break"),
                rs.getInt("max_focus_day_seconds"),
                rs.getInt("total_focus_sessions"),
                rs.getInt("xp"),
                rs.getInt("daily_goal_seconds"),
                rs.getInt("weekly_goal_seconds"),
                rs.getInt("monthly_goal_seconds"),
                rs.getInt("audio_volume"),
                rs.getBoolean("notifications_enabled"),
                rs.getString("language") != null ? rs.getString("language") : "pt_BR",
                streakRule,
                rs.getTimestamp("created_at").toLocalDateTime().withNano(0));
    }
}