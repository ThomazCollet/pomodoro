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
        // Atualizado para incluir image_path e usar o nome correto username
        String sql = """
            INSERT INTO profiles (username, image_path, work_duration, short_break, long_break)
            VALUES (?, ?, ?, ?, ?)
            """;
            
        try (Connection conn = DatabaseInitializer.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, profile.getUsername());
            pstmt.setString(2, profile.getImagePath());
            pstmt.setInt(3, profile.getWorkDuration());
            pstmt.setInt(4, profile.getShortBreak());
            pstmt.setInt(5, profile.getLongBreak());
            
            pstmt.executeUpdate();

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

    // Mapeia os novos campos do ResultSet para o objeto Profile
    private Profile mapResultSetToProfile(ResultSet rs) throws SQLException {
        Profile profile = new Profile();
        profile.setId(rs.getLong("id"));
        profile.setUsername(rs.getString("username")); // Coluna alterada
        profile.setImagePath(rs.getString("image_path")); // Nova coluna
        profile.setWorkDuration(rs.getInt("work_duration"));
        profile.setShortBreak(rs.getInt("short_break"));
        profile.setLongBreak(rs.getInt("long_break"));
        profile.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime().withNano(0));
        
        return profile;
    }
}