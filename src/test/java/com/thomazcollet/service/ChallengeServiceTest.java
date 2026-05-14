package com.thomazcollet.service;

import com.thomazcollet.domain.exception.ChallengeNotFoundException;
import com.thomazcollet.domain.model.Challenge;
import com.thomazcollet.domain.model.ChallengeStatus;
import com.thomazcollet.domain.repository.ChallengeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChallengeServiceTest {

    @Mock
    private ChallengeRepository repository;

    @InjectMocks
    private ChallengeService service;

    private Challenge challenge;

    @BeforeEach
    void setUp() {
        challenge = new Challenge();
        challenge.setId(1L);
        challenge.setProfileId(10L);
        challenge.setTitle("Foco Intenso");
        challenge.setDurationDays(5);
        challenge.setMinFocusMinutesPerDay(30);
        challenge.setLivesTotal(2);
    }

    @Test
    @DisplayName("Deve criar desafio com estado inicial correto")
    void shouldCreateChallengeWithInitialState() {
        service.createChallenge(challenge);

        assertEquals(ChallengeStatus.ACTIVE, challenge.getStatus());
        assertEquals(0, challenge.getProgressDays());
        assertEquals(2, challenge.getLivesRemaining());
        verify(repository, times(1)).save(challenge);
    }

    @Test
    @DisplayName("Deve lançar exceção ao criar desafio com duração inválida")
    void shouldThrowExceptionForInvalidDuration() {
        challenge.setDurationDays(0);
        assertThrows(IllegalArgumentException.class, () -> service.createChallenge(challenge));
    }

    @Test
    @DisplayName("Deve lançar ChallengeNotFoundException ao deletar ID inexistente")
    void shouldThrowExceptionWhenDeletingNonExistentId() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ChallengeNotFoundException.class, () -> service.deleteChallenge(1L));
    }

    @Test
    @DisplayName("Deve progredir dia quando meta de foco é atingida")
    void shouldIncrementProgressWhenGoalIsReached() {
        challenge.setProgressDays(1);
        challenge.setLivesRemaining(2);
        challenge.setStatus(ChallengeStatus.ACTIVE);

        when(repository.findActiveByProfile(10L)).thenReturn(List.of(challenge));

        service.processDailyProgress(10L, 40); // 40 min > 30 min meta

        verify(repository).updateProgress(1L, 2, 2, "ACTIVE");
    }

    @Test
    @DisplayName("Deve concluir desafio ao atingir meta no último dia")
    void shouldCompleteChallengeOnLastDay() {
        // Configurando o cenário específico para este teste
        challenge.setProgressDays(4);
        challenge.setLivesRemaining(2); // Garante que as vidas atuais sejam 2
        challenge.setStatus(ChallengeStatus.ACTIVE);

        when(repository.findActiveByProfile(10L)).thenReturn(List.of(challenge));

        service.processDailyProgress(10L, 30);

        // Agora o verify vai bater: 2 vidas entrando, 2 vidas saindo (já que ele bateu
        // a meta)
        verify(repository).updateProgress(1L, 5, 2, "COMPLETED");
    }

    @Test
    @DisplayName("Deve perder vida quando meta de foco não é atingida")
    void shouldDecrementLivesWhenGoalIsNotReached() {
        challenge.setProgressDays(1);
        challenge.setLivesRemaining(2);
        challenge.setStatus(ChallengeStatus.ACTIVE);

        when(repository.findActiveByProfile(10L)).thenReturn(List.of(challenge));

        service.processDailyProgress(10L, 10); // 10 min < 30 min meta

        verify(repository).updateProgress(1L, 1, 1, "ACTIVE");
    }

    @Test
    @DisplayName("Deve falhar desafio quando vidas acabam")
    void shouldFailChallengeWhenLivesAreExhausted() {
        challenge.setProgressDays(2);
        challenge.setLivesRemaining(0); // Última vida
        challenge.setStatus(ChallengeStatus.ACTIVE);

        when(repository.findActiveByProfile(10L)).thenReturn(List.of(challenge));

        service.processDailyProgress(10L, 5);

        verify(repository).updateProgress(1L, 2, -1, "FAILED");
    }
}