package com.thomazcollet.service;

import com.thomazcollet.domain.model.FocusSession;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.model.SessionType;
import com.thomazcollet.domain.repository.FocusSessionRepository;
import com.thomazcollet.domain.repository.ProfileRepository; // Adicionado
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FocusSessionServiceTest {

    @Mock
    private FocusSessionRepository repository;

    @Mock
    private ProfileRepository profileRepository; // Adicionado para suportar o motor de XP diário

    private FocusSessionService service;
    private final Long testProfileId = 1L;
    private Profile fakeProfile;

    @BeforeEach
    void setUp() {
        // Atualizado para passar os dois mocks exigidos pelo novo construtor
        service = new FocusSessionService(repository, profileRepository);

        // Prepara um perfil padrão mockado para os testes de ganho de XP
        fakeProfile = new Profile();
        fakeProfile.setId(testProfileId);
        fakeProfile.setXp(100);
    }

    @Test
    @DisplayName("Deve criar e salvar uma nova sessão ao iniciar")
    void shouldCreateAndSaveNewSessionOnStart() {
        service.startSession(testProfileId, SessionType.FOCUS);

        // Verifica se o repositório foi chamado para salvar
        verify(repository, times(1)).save(any(FocusSession.class));
    }

    @Test
    @DisplayName("Deve atualizar a sessão existente com os dados de finalização e conceder as migalhinhas de XP")
    void shouldUpdateExistingSessionWithFinalizationData() {
        // CONFIGURAÇÃO: Mockar o comportamento de busca do perfil que receberá o XP
        when(profileRepository.findById(testProfileId)).thenReturn(Optional.of(fakeProfile));

        // 1. Inicia a sessão
        service.startSession(testProfileId, SessionType.FOCUS);

        // 2. Finaliza a sessão com 1500 segundos (25 minutos = 25 XP)
        int duration = 1500;
        service.finalizeCurrentSession(duration, true);

        // Capturamos o objeto enviado ao repositório no segundo save (o de finalização)
        ArgumentCaptor<FocusSession> sessionCaptor = ArgumentCaptor.forClass(FocusSession.class);
        verify(repository, times(2)).save(sessionCaptor.capture());

        FocusSession finalizedSession = sessionCaptor.getValue();

        assertNotNull(finalizedSession.getEndTimestamp());
        assertEquals(duration, finalizedSession.getDurationSeconds());
        assertTrue(finalizedSession.isCompleted());

        // VERIFICAÇÃO DO XP: 100 XP antigos + 25 XP do Pomodoro = 125 XP salvos no
        // banco
        verify(profileRepository, times(1)).updateXp(testProfileId, 125);
    }

    @Test
    @DisplayName("Não deve chamar o repositório se tentar finalizar sem uma sessão activa")
    void shouldNotCallRepositoryWhenFinalizingWithoutActiveSession() {
        service.finalizeCurrentSession(1500, true);

        // O save nunca deve ter sido chamado se startSession não foi executado
        verify(repository, never()).save(any());
        verify(profileRepository, never()).updateXp(anyLong(), anyInt());
    }

    @Test
    @DisplayName("Deve limpar a sessão ativa após a finalização")
    void shouldClearActiveSessionAfterFinalization() {
        // CONFIGURAÇÃO: Mockar o perfil para o primeiro encerramento com sucesso
        when(profileRepository.findById(testProfileId)).thenReturn(Optional.of(fakeProfile));

        service.startSession(testProfileId, SessionType.FOCUS);
        service.finalizeCurrentSession(1500, true); // Primeiro encerramento (chama repository e profileRepository)

        // Se tentarmos finalizar de novo, não deve haver interação nova com o
        // repositório
        service.finalizeCurrentSession(600, false);

        verify(repository, times(2)).save(any()); // Permanece com as 2 chamadas do primeiro ciclo
        verify(profileRepository, times(1)).updateXp(anyLong(), anyInt()); // XP distribuído apenas 1 vez
    }

    @Test
    @DisplayName("Não deve conceder XP se a sessão finalizada for do tipo BREAK")
    void shouldNotAwardXpForBreakSessions() {
        // 1. Inicia uma sessão de intervalo curto
        service.startSession(testProfileId, SessionType.SHORT_BREAK);

        // 2. Finaliza o intervalo (5 minutos)
        service.finalizeCurrentSession(300, true);

        // Verifica que salvou no banco a sessão de break normalmente
        verify(repository, times(2)).save(any(FocusSession.class));

        // Garante que o repositório de perfis NUNCA foi acionado para dar XP em
        // descansos
        verify(profileRepository, never()).findById(anyLong());
        verify(profileRepository, never()).updateXp(anyLong(), anyInt());
    }
}