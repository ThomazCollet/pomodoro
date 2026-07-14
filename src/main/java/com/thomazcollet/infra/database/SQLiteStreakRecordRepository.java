package com.thomazcollet.infra.database;

import com.thomazcollet.domain.dto.StreakRecord;
import com.thomazcollet.domain.repository.StreakRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class SQLiteStreakRecordRepository implements StreakRecordRepository {

    private static final Logger logger = LoggerFactory.getLogger(SQLiteStreakRecordRepository.class);

    @Override
    public void save(StreakRecord record) {
        String sql = "INSERT INTO streak_records (profile_id, duration_days, start_date, end_date) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, record.profileId());
            stmt.setInt(2, record.durationDays());
            stmt.setString(3, record.startDate().toString());
            stmt.setString(4, record.endDate().toString());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar streak no banco de dados", e);
        }
    }

    @Override
    public void delete(Long id) {
        String sql = "DELETE FROM streak_records WHERE id = ?";
        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao remover streak com ID: " + id, e);
        }
    }

    @Override
    public StreakRecord getMinStreak(Long profileId) {
        String sql = "SELECT id, profile_id, duration_days, start_date, end_date FROM streak_records WHERE profile_id = ? ORDER BY duration_days ASC LIMIT 1";
        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, profileId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar a menor streak do perfil: " + profileId, e);
        }
        return null;
    }

    @Override
    public List<StreakRecord> getTopStreaks(Long profileId, int limit) {
        String sql = "SELECT id, profile_id, duration_days, start_date, end_date FROM streak_records WHERE profile_id = ? ORDER BY duration_days DESC LIMIT ?";
        List<StreakRecord> topStreaks = new ArrayList<>();
        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, profileId);
            stmt.setInt(2, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    topStreaks.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao buscar as melhores streaks do perfil: " + profileId, e);
        }
        return topStreaks;
    }

    @Override
    public int countRecords(Long profileId) {
        String sql = "SELECT COUNT(*) FROM streak_records WHERE profile_id = ?";
        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, profileId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao contar registros de streak do perfil: " + profileId, e);
        }
        return 0;
    }

    /**
     * Helper privado para centralizar a conversão de uma linha do ResultSet para o
     * record StreakRecord.
     */
    private StreakRecord mapRow(ResultSet rs) throws SQLException {
        return new StreakRecord(
                rs.getLong("id"),
                rs.getLong("profile_id"),
                rs.getInt("duration_days"),
                LocalDate.parse(rs.getString("start_date")),
                LocalDate.parse(rs.getString("end_date")));
    }

    @Override
    public void deleteAllByProfileId(Long profileId) {
        String sql = "DELETE FROM streak_records WHERE profile_id = ?";
        try (Connection conn = DatabaseInitializer.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, profileId);
            int rows = stmt.executeUpdate();
            logger.info("{} registros de streak removidos para o perfil ID {}.", rows, profileId);
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao remover streaks do perfil: " + profileId, e);
        }
    }
}