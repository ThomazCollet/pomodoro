package com.thomazcollet.service;

import com.thomazcollet.domain.model.Notification;
import com.thomazcollet.domain.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class NotificationServiceTest {

    private NotificationRepository repositoryMock;
    private NotificationService notificationService;
    private final int TEST_PROFILE_ID = 1;

    @BeforeEach
    public void setUp() {
        // Criamos um mock do repositório para isolar o teste do banco SQLite real
        repositoryMock = mock(NotificationRepository.class);

        // Instanciamos o service injetando o mock
        notificationService = new NotificationService(repositoryMock);
    }

    @Test
    public void deveEnviarNotificacaoComSucessoESalvarNoBanco() {
        // Arrange
        String titulo = "Desafio Concluído!";
        String mensagem = "Você completou o desafio de constância.";

        // Act
        notificationService.send(TEST_PROFILE_ID, titulo, mensagem);

        // Assert
        // Captura o objeto que foi enviado para o método save do repositório
        ArgumentCaptor<Notification> notificationCaptor = ArgumentCaptor.forClass(Notification.class);
        verify(repositoryMock, times(1)).save(notificationCaptor.capture());

        Notification notificacaoSalva = notificationCaptor.getValue();
        assertEquals(TEST_PROFILE_ID, notificacaoSalva.getProfileId());
        assertEquals(titulo, notificacaoSalva.getTitle());
        assertEquals(mensagem, notificacaoSalva.getMessage());
        assertFalse(notificacaoSalva.isRead()); // Deve nascer como não lida
        assertNotNull(notificacaoSalva.getCreatedAt());

        // Verifica se o contador reativo do JavaFX incrementou para 1
        assertEquals(1, notificationService.getUnreadCount());
    }

    @Test
    public void deveCarregarContadorDeNaoLidasCorretamente() {
        // Arrange
        // Simulamos que o banco possui 3 notificações não lidas para o usuário
        List<Notification> notificacoesNaoLidasFake = List.of(
                new Notification(TEST_PROFILE_ID, "T1", "M1"),
                new Notification(TEST_PROFILE_ID, "T2", "M2"),
                new Notification(TEST_PROFILE_ID, "T3", "M3"));
        when(repositoryMock.findUnreadByProfileId(TEST_PROFILE_ID)).thenReturn(notificacoesNaoLidasFake);

        // Act
        notificationService.loadUnreadCount(TEST_PROFILE_ID);

        // Assert
        assertEquals(3, notificationService.getUnreadCount());
        verify(repositoryMock, times(1)).findUnreadByProfileId(TEST_PROFILE_ID);
    }

    @Test
    public void deveZerarContadorAoMarcarTodasComoLidas() {
        // Arrange
        // Forçamos o contador a estar em 5 simulando recepções anteriores
        notificationService.unreadCountProperty().set(5);

        // Act
        notificationService.markAllAsRead(TEST_PROFILE_ID);

        // Assert
        assertEquals(0, notificationService.getUnreadCount());
        verify(repositoryMock, times(1)).markAllAsRead(TEST_PROFILE_ID);
    }
}