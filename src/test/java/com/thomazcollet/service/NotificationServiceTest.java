package com.thomazcollet.service;

import com.thomazcollet.domain.model.Notification;
import com.thomazcollet.domain.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class NotificationServiceTest {

    private NotificationRepository repositoryMock;
    private NotificationService notificationService;
    private final int TEST_PROFILE_ID = 1;

    @BeforeEach
    public void setUp() {
        repositoryMock = mock(NotificationRepository.class);
        // Construtor de pacote com despachante síncrono — evita a necessidade de
        // inicializar o toolkit do JavaFX para testar a lógica de negócio.
        notificationService = new NotificationService(repositoryMock, Runnable::run);
    }

    // ==========================================================================
    // ENVIO BÁSICO
    // ==========================================================================

    @Test
    @DisplayName("Deve enviar notificação, salvar no banco e incrementar o contador de não lidas")
    public void deveEnviarNotificacaoComSucessoESalvarNoBanco() {
        String titulo = "Desafio Concluído!";
        String mensagem = "Você completou o desafio de constância.";

        notificationService.send(TEST_PROFILE_ID, titulo, mensagem);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repositoryMock, times(1)).save(captor.capture());

        Notification salva = captor.getValue();
        assertEquals(TEST_PROFILE_ID, salva.getProfileId());
        assertEquals(titulo, salva.getTitle());
        assertEquals(mensagem, salva.getMessage());
        assertFalse(salva.isRead());
        assertNotNull(salva.getCreatedAt());
        assertEquals(1, notificationService.getUnreadCount());
    }

    @Test
    @DisplayName("Deve acumular o contador corretamente após múltiplos envios")
    public void deveAcumularContadorAposMultiplosEnvios() {
        notificationService.send(TEST_PROFILE_ID, "T1", "M1");
        notificationService.send(TEST_PROFILE_ID, "T2", "M2");
        notificationService.send(TEST_PROFILE_ID, "T3", "M3");

        assertEquals(3, notificationService.getUnreadCount());
        verify(repositoryMock, times(3)).save(any(Notification.class));
    }

    // ==========================================================================
    // CARREGAMENTO DO CONTADOR
    // ==========================================================================

    @Test
    @DisplayName("Deve carregar o contador de não lidas corretamente a partir do banco")
    public void deveCarregarContadorDeNaoLidasCorretamente() {
        List<Notification> naoLidas = List.of(
                new Notification(TEST_PROFILE_ID, "T1", "M1"),
                new Notification(TEST_PROFILE_ID, "T2", "M2"),
                new Notification(TEST_PROFILE_ID, "T3", "M3"));
        when(repositoryMock.findUnreadByProfileId(TEST_PROFILE_ID)).thenReturn(naoLidas);

        notificationService.loadUnreadCount(TEST_PROFILE_ID);

        assertEquals(3, notificationService.getUnreadCount());
        verify(repositoryMock, times(1)).findUnreadByProfileId(TEST_PROFILE_ID);
    }

    // ==========================================================================
    // MARCAR COMO LIDAS
    // ==========================================================================

    @Test
    @DisplayName("Deve zerar o contador ao marcar todas as notificações como lidas")
    public void deveZerarContadorAoMarcarTodasComoLidas() {
        notificationService.unreadCountProperty().set(5);

        notificationService.markAllAsRead(TEST_PROFILE_ID);

        assertEquals(0, notificationService.getUnreadCount());
        verify(repositoryMock, times(1)).markAllAsRead(TEST_PROFILE_ID);
    }

    // ==========================================================================
    // PROTEÇÃO ANTI-SPAM (sendGoalNotification)
    // ==========================================================================

    @Test
    @DisplayName("sendGoalNotification deve enviar e retornar true na primeira chamada do dia")
    public void deveEnviarMetaNaPrimeiraChamada() {
        boolean enviado = notificationService.sendGoalNotification(
                TEST_PROFILE_ID, "daily", "☀️ Meta Diária!", "Você bateu a meta.");

        assertTrue(enviado, "Deveria ter enviado na primeira chamada.");
        verify(repositoryMock, times(1)).save(any(Notification.class));
        assertEquals(1, notificationService.getUnreadCount());
    }

    @Test
    @DisplayName("sendGoalNotification NÃO deve reenviar a mesma meta no mesmo dia")
    public void naoDeveReenviarMesmaMeta() {
        notificationService.sendGoalNotification(TEST_PROFILE_ID, "daily", "T", "M");
        boolean segundaVez = notificationService.sendGoalNotification(
                TEST_PROFILE_ID, "daily", "T", "M");

        assertFalse(segundaVez, "Segunda chamada no mesmo dia deveria ser bloqueada.");
        // save chamado apenas 1 vez (primeira chamada)
        verify(repositoryMock, times(1)).save(any(Notification.class));
        assertEquals(1, notificationService.getUnreadCount());
    }

    @Test
    @DisplayName("sendGoalNotification deve permitir tipos de meta diferentes no mesmo dia")
    public void devePermitirDiferentesMetasNomesmoDia() {
        boolean daily = notificationService.sendGoalNotification(TEST_PROFILE_ID, "daily", "T", "M");
        boolean weekly = notificationService.sendGoalNotification(TEST_PROFILE_ID, "weekly", "T", "M");
        boolean monthly = notificationService.sendGoalNotification(TEST_PROFILE_ID, "monthly", "T", "M");

        assertTrue(daily, "Meta diária deveria passar.");
        assertTrue(weekly, "Meta semanal deveria passar.");
        assertTrue(monthly, "Meta mensal deveria passar.");
        verify(repositoryMock, times(3)).save(any(Notification.class));
    }

    @Test
    @DisplayName("sendGoalNotification deve separar anti-spam por profileId")
    public void deveIsolarAntiSpamPorPerfil() {
        boolean perfil1 = notificationService.sendGoalNotification(1, "daily", "T", "M");
        boolean perfil2 = notificationService.sendGoalNotification(2, "daily", "T", "M");

        assertTrue(perfil1, "Perfil 1 deveria enviar.");
        assertTrue(perfil2, "Perfil 2 (diferente) também deveria enviar.");
        verify(repositoryMock, times(2)).save(any(Notification.class));
    }

    // ==========================================================================
    // TOAST CALLBACK
    // ==========================================================================

    @Test
    @DisplayName("Deve acionar o toastCallback com a notificação enviada")
    public void deveAcionarToastCallback() {
        AtomicReference<Notification> capturada = new AtomicReference<>();
        notificationService.setToastCallback(capturada::set);

        notificationService.send(TEST_PROFILE_ID, "Título", "Mensagem");

        assertNotNull(capturada.get(), "O callback deveria ter sido chamado.");
        assertEquals("Título", capturada.get().getTitle());
        assertEquals("Mensagem", capturada.get().getMessage());
    }

    @Test
    @DisplayName("Não deve lançar exceção se toastCallback não estiver configurado (null-safe)")
    public void deveSuportarCallbackNulo() {
        // sem setToastCallback → callback é null
        assertDoesNotThrow(() -> notificationService.send(TEST_PROFILE_ID, "T", "M"));
    }

    // ==========================================================================
    // HISTÓRICO E LIMPEZA
    // ==========================================================================

    @Test
    @DisplayName("getHistory deve delegar ao repositório e retornar a lista")
    public void deveRetornarHistorico() {
        List<Notification> historico = List.of(
                new Notification(TEST_PROFILE_ID, "T1", "M1"),
                new Notification(TEST_PROFILE_ID, "T2", "M2"));
        when(repositoryMock.findByProfileId(TEST_PROFILE_ID)).thenReturn(historico);

        List<Notification> resultado = notificationService.getHistory(TEST_PROFILE_ID);

        assertEquals(2, resultado.size());
        verify(repositoryMock, times(1)).findByProfileId(TEST_PROFILE_ID);
    }

    @Test
    @DisplayName("clearOldNotifications deve delegar ao repositório com o número de dias correto")
    public void deveLimparNotificacoesAntigas() {
        notificationService.clearOldNotifications(30);
        verify(repositoryMock, times(1)).deleteOlderThanDays(30);
    }
}