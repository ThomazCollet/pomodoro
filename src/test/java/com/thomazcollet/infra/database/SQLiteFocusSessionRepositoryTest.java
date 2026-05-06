package com.thomazcollet.infra.database;

import com.thomazcollet.domain.model.FocusSession;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.model.SessionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SQLiteFocusSessionRepositoryTest {

    private SQLiteFocusSessionRepository sessionRepository;
    private SQLiteProfileRepository profileRepository;
    private Long testProfileId;

    @BeforeEach
    void setUp() {
        DatabaseInitializer.initialize();
        sessionRepository = new SQLiteFocusSessionRepository();
        profileRepository = new SQLiteProfileRepository();

        // Criamos um perfil real, pois a focus_session tem uma Foreign Key para
        // profiles
        Profile profile = new Profile("Test User", 25, 5, 15);
        profileRepository.save(profile);
        testProfileId = profile.getId();
    }

    @Test
    @DisplayName("Deve salvar uma sessão de foco e recuperar o ID gerado")
    void shouldSaveFocusSessionAndGenerateId() {
        FocusSession session = new FocusSession(testProfileId, SessionType.FOCUS, LocalDateTime.now());
        session.setEndTimestamp(LocalDateTime.now().plusMinutes(25));
        session.setDurationSeconds(1500);
        session.setCompleted(true);

        sessionRepository.save(session);

        assertNotNull(session.getId(), "O ID da sessão deve ter sido gerado pelo banco.");
    }

    @Test
    @DisplayName("Deve buscar sessões filtradas pelo ID do perfil")
    void shouldFindSessionsByProfileId() {
        // Salva duas sessões para o perfil de teste
        FocusSession s1 = new FocusSession(testProfileId, SessionType.FOCUS, LocalDateTime.now());
        FocusSession s2 = new FocusSession(testProfileId, SessionType.SHORT_BREAK, LocalDateTime.now().plusMinutes(30));

        sessionRepository.save(s1);
        sessionRepository.save(s2);

        List<FocusSession> sessions = sessionRepository.findByProfileId(testProfileId);

        assertEquals(2, sessions.size(), "Deveria encontrar exatamente 2 sessões para este perfil.");
        assertEquals(SessionType.SHORT_BREAK, sessions.get(0).getType(),
                "A lista deve vir ordenada pela data (mais recente primeiro).");
    }

    @Test
    @DisplayName("Deve converter corretamente as datas ao ler do banco")
    void shouldMapDatesCorrectly() {
        // Truncamos para segundos para evitar divergência de nanossegundos no parse da
        // String
        LocalDateTime fixedDate = LocalDateTime.of(2026, 5, 6, 20, 0, 0);

        FocusSession session = new FocusSession(testProfileId, SessionType.FOCUS, fixedDate);
        session.setEndTimestamp(fixedDate.plusMinutes(25));
        session.setDurationSeconds(1500);
        session.setCompleted(true);

        sessionRepository.save(session);

        List<FocusSession> result = sessionRepository.findByProfileId(testProfileId);
        FocusSession recovered = result.get(0);

        assertNotNull(recovered);
        assertEquals(fixedDate, recovered.getStartTimestamp());
    }
}