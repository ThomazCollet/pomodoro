package com.thomazcollet.infra.database;

import com.thomazcollet.domain.model.Achievement;
import com.thomazcollet.domain.model.AchievementCategory;
import com.thomazcollet.domain.model.AchievementTier;
import com.thomazcollet.domain.repository.AchievementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SQLiteAchievementRepository implements AchievementRepository {

    private static final Logger logger = LoggerFactory.getLogger(SQLiteAchievementRepository.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void save(Achievement achievement) {
        String sql = """
                INSERT INTO achievements (profile_id, achievement_key, category, tier)
                VALUES (?, ?, ?, ?);
                """;

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, achievement.getProfileId());
            pstmt.setString(2, achievement.getAchievementKey());
            pstmt.setString(3, achievement.getCategory().name());
            pstmt.setString(4, achievement.getTier().name());

            pstmt.executeUpdate();
            logger.info("Conquista '{}' salva com sucesso para o perfil id: {}",
                    achievement.getAchievementKey(), achievement.getProfileId());

        } catch (SQLException e) {
            logger.error("Erro ao salvar conquista no banco SQLite: ", e);
            throw new RuntimeException("Falha ao persistir conquista no banco de dados", e);
        }
    }

    @Override
    public List<Achievement> findByProfileId(Long profileId) {
        String sql = "SELECT id, profile_id, achievement_key, category, tier, unlocked_at FROM achievements WHERE profile_id = ?;";
        List<Achievement> list = new ArrayList<>();

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, profileId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapResultSetToAchievement(rs));
                }
            }

        } catch (SQLException e) {
            logger.error("Erro ao buscar conquistas por perfil no banco SQLite: ", e);
            throw new RuntimeException("Falha ao consultar conquistas", e);
        }
        return list;
    }

    @Override
    public boolean isUnlocked(Long profileId, String achievementKey) {
        String sql = "SELECT 1 FROM achievements WHERE profile_id = ? AND achievement_key = ? LIMIT 1;";

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, profileId);
            pstmt.setString(2, achievementKey);

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }

        } catch (SQLException e) {
            logger.error("Erro ao verificar status da conquista '{}': ", achievementKey, e);
            return false;
        }
    }

    @Override
    public int countByProfileAndTier(Long profileId, String tier) {
        String sql = "SELECT COUNT(*) FROM achievements WHERE profile_id = ? AND tier = ?;";

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, profileId);
            pstmt.setString(2, tier);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next())
                    return rs.getInt(1);
            }

        } catch (SQLException e) {
            logger.error("Erro ao contar conquistas por tier '{}': ", tier, e);
        }
        return 0;
    }

    @Override
    public Set<String> findUnlockedKeysByProfileId(Long profileId) {
        String sql = "SELECT achievement_key FROM achievements WHERE profile_id = ?;";
        Set<String> unlockedKeys = new HashSet<>();

        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, profileId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    unlockedKeys.add(rs.getString("achievement_key"));
                }
            }

        } catch (SQLException e) {
            logger.error("Erro ao buscar chaves de conquistas desbloqueadas para o perfil ID: {}", profileId, e);
            throw new RuntimeException("Falha ao consultar chaves de conquistas no banco", e);
        }

        return unlockedKeys;
    }

    /**
     * Retorna o total de conquistas definidas no sistema para um dado tier,
     * excluindo meta-conquistas (prefixo "meta_").
     *
     * Como o projeto não possui tabela de definições no banco (as conquistas
     * são definidas em código no AchievementService), optamos por um Map
     * hardcoded e documentado, espelhando exatamente o
     * getDefinitionsFromPlanilha().
     *
     * MANUTENÇÃO: atualizar estes valores sempre que conquistas forem
     * adicionadas ou removidas do AchievementService.
     *
     * Contagem atual (excluindo meta_* e platinas das trilhas normais):
     * GOLD:
     * DAILY_FOCUS : focus_daily(1) + focus_cycles(1) + focus_total_days(1) +
     * focus_accumulated(1) = 4
     * STREAK : streak_current(1) + streak_count_x3(1) + streak_count_x5(1) = 3
     * CHALLENGE : constancy_days trilha curta(1) + trilha longa(1) +
     * perfect_days(1)
     * + count_constancy_min_7(1) + min_15(1) + min_30(1)
     * + intensity_15d(1) + intensity_30d(1) + intensity_90d(1)
     * + count_intensity_min_15(1) = 10
     * RANKING : ranking_tier_s(1) = 1
     * Total GOLD = 18
     *
     * BRONZE: contar analogamente se necessário no futuro.
     * SILVER: idem.
     */
    private static final java.util.Map<String, Integer> TOTAL_DEFINED_BY_TIER = java.util.Map.of(
            "GOLD", 18,
            "SILVER", 18, // mesma estrutura de trilhas, 1 prata por trilha
            "BRONZE", 18 // mesma estrutura de trilhas, 1 bronze por trilha
    );

    @Override
    public int countTotalDefinedByTier(String tier) {
        return TOTAL_DEFINED_BY_TIER.getOrDefault(tier.toUpperCase(), 0);
    }

    private Achievement mapResultSetToAchievement(ResultSet rs) throws SQLException {
        Achievement achievement = new Achievement();
        achievement.setId(rs.getLong("id"));
        achievement.setProfileId(rs.getLong("profile_id"));
        achievement.setAchievementKey(rs.getString("achievement_key"));
        achievement.setCategory(AchievementCategory.valueOf(rs.getString("category")));
        achievement.setTier(AchievementTier.valueOf(rs.getString("tier")));

        String unlockedAtStr = rs.getString("unlocked_at");
        if (unlockedAtStr != null) {
            achievement.setUnlockedAt(LocalDateTime.parse(unlockedAtStr, formatter));
        }

        return achievement;
    }

    @Override
    public void deleteAllByProfileId(Long profileId) {
        String sql = "DELETE FROM achievements WHERE profile_id = ?";
        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, profileId);
            int rows = pstmt.executeUpdate();
            logger.info("{} conquistas removidas para o perfil ID {}.", rows, profileId);
        } catch (SQLException e) {
            logger.error("Erro ao deletar conquistas do perfil ID: {}", profileId, e);
            throw new RuntimeException("Falha ao limpar conquistas", e);
        }
    }
}