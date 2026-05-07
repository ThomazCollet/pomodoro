package com.thomazcollet.service;

import com.thomazcollet.domain.model.FocusSession;
import com.thomazcollet.domain.model.SessionType;
import com.thomazcollet.domain.repository.FocusSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FocusSessionServiceTest {

    @Mock
    private FocusSessionRepository repository;

    private FocusSessionService service;
    private final Long testProfileId = 1L;

    @BeforeEach
    void setUp() {
        service = new FocusSessionService(repository);
    }

    @Test
    @DisplayName("Deve criar e salvar uma nova sessão ao iniciar")
    void shouldCreateAndSaveNewSessionOnStart() {
        service.startSession(testProfileId, SessionType.FOCUS);

        // Verifica se o repositório foi chamado para salvar
        verify(repository, times(1)).save(any(FocusSession.class));
    }

    @Test
    @DisplayName("Deve atualizar a sessão existente com os dados de finalização")
    void shouldUpdateExistingSessionWithFinalizationData() {
        // 1. Inicia a sessão
        service.startSession(testProfileId, SessionType.FOCUS);

        // 2. Finaliza a sessão
        int duration = 1500;
        service.finalizeCurrentSession(duration, true);

        // Capturamos o objeto enviado ao repositório no segundo save (o de finalização)
        ArgumentCaptor<FocusSession> sessionCaptor = ArgumentCaptor.forClass(FocusSession.class);
        verify(repository, times(2)).save(sessionCaptor.capture());

        FocusSession finalizedSession = sessionCaptor.getValue();

        assertNotNull(finalizedSession.getEndTimestamp());
        assertEquals(duration, finalizedSession.getDurationSeconds());
        assertTrue(finalizedSession.isCompleted());
    }

    @Test
    @DisplayName("Não deve chamar o repositório se tentar finalizar sem uma sessão ativa")
    void shouldNotCallRepositoryWhenFinalizingWithoutActiveSession() {
        service.finalizeCurrentSession(1500, true);

        // O save nunca deve ter sido chamado se startSession não foi executado
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Deve limpar a sessão ativa após a finalização")
    void shouldClearActiveSessionAfterFinalization() {
        service.startSession(testProfileId, SessionType.FOCUS);
        service.finalizeCurrentSession(1500, true);
        
        // Se tentarmos finalizar de novo, não deve haver interação nova com o repositório
        service.finalizeCurrentSession(600, false);
        verify(repository, times(2)).save(any()); // Permanece com as 2 chamadas do primeiro ciclo
    }
}