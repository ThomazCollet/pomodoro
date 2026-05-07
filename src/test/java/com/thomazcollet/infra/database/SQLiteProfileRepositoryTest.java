package com.thomazcollet.infra.database;

import com.thomazcollet.domain.model.Profile;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SQLiteProfileRepositoryTest {

    private SQLiteProfileRepository repository;
    private static final String DB_PATH = "pomodoro.db";

    @BeforeEach
    void setUp() {
        // Garantimos um banco limpo e inicializado antes de cada teste
        File dbFile = new File(DB_PATH);
        if (dbFile.exists()) dbFile.delete();
        
        DatabaseInitializer.initialize();
        repository = new SQLiteProfileRepository();
    }

    @Test
    @DisplayName("Deve salvar um perfil e recuperar o ID gerado")
    void shouldSaveProfileAndGenerateId() {
        Profile profile = new Profile("Estudos Java", 50, 10, 20);
        
        repository.save(profile);

        assertNotNull(profile.getId(), "O ID não deve ser nulo após salvar.");
        
        Optional<Profile> savedProfile = repository.findById(profile.getId());
        assertTrue(savedProfile.isPresent());
        assertEquals("Estudos Java", savedProfile.get().getUsername());
    }

    @Test
    @DisplayName("Deve listar todos os perfis cadastrados")
    void shouldFindAllProfiles() {
        repository.save(new Profile("Perfil 1", 25, 5, 15));
        repository.save(new Profile("Perfil 2", 30, 5, 20));

        List<Profile> profiles = repository.findAll();

        assertEquals(2, profiles.size(), "Deveriam existir 2 perfis no banco.");
    }

    @Test
    @DisplayName("Deve deletar um perfil com sucesso")
    void shouldDeleteProfile() {
        Profile profile = new Profile("Temporário", 25, 5, 10);
        repository.save(profile);
        Long id = profile.getId();

        repository.delete(id);

        Optional<Profile> deleted = repository.findById(id);
        assertFalse(deleted.isPresent(), "O perfil não deveria mais existir no banco.");
    }
}