package com.thomazcollet.infra.database;

import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.repository.ProfileRepository;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SQLiteProfileRepository implements ProfileRepository {

    @Override
    public void save(Profile profile) {
        String sql = """
            INSERT INTO profiles (name, work_duration, short_break, long_break)
            VALUES (?, ?, ?, ?)
            """;
            
        try (Connection conn = DatabaseInitializer.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, profile.getName());
            pstmt.setInt(2, profile.getWorkDuration());
            pstmt.setInt(3, profile.getShortBreak());
            pstmt.setInt(4, profile.getLongBreak());
            
            pstmt.executeUpdate();

            // Recupera o ID gerado pelo SQLite e atualiza o objeto
            try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    profile.setId(generatedKeys.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao salvar perfil: " + e.getMessage(), e);
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
            throw new RuntimeException("Erro ao buscar perfil por ID", e);
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
            throw new RuntimeException("Erro ao listar perfis", e);
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
        } catch (SQLException e) {
            throw new RuntimeException("Erro ao deletar perfil", e);
        }
    }

    // Helper para converter a linha do banco no nosso objeto de domínio
    private Profile mapResultSetToProfile(ResultSet rs) throws SQLException {
        return new Profile(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getInt("work_duration"),
            rs.getInt("short_break"),
            rs.getInt("long_break"),
            rs.getTimestamp("created_at").toLocalDateTime()
        );
    }
}