package com.thomazcollet.service;

import com.thomazcollet.domain.exception.ProfileInitializationException;
import com.thomazcollet.domain.exception.ProfileNotFoundException;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private ProfileRepository repository;

    @InjectMocks
    private ProfileService profileService;

    private Profile existingProfile;

    @BeforeEach
    void setUp() {
        existingProfile = new Profile("Estudos Java", 25, 5, 15);
        existingProfile.setId(1L);
    }

    @Test
    @DisplayName("Should load existing profile when database is not empty")
    void shouldLoadExistingProfile() {
        // Arrange
        when(repository.findAll()).thenReturn(List.of(existingProfile));

        // Act
        Profile result = profileService.ensureProfileExists();

        // Assert
        assertNotNull(result);
        assertEquals("Estudos Java", result.getUsername());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Should create new profile when database is empty")
    void shouldCreateNewProfileWhenEmpty() {
        // Arrange
        when(repository.findAll()).thenReturn(Collections.emptyList());
        
        // Simula o comportamento do banco atribuindo ID ao salvar
        doAnswer(invocation -> {
            Profile p = invocation.getArgument(0);
            p.setId(2L);
            return null;
        }).when(repository).save(any(Profile.class));

        // Act
        Profile result = profileService.ensureProfileExists();

        // Assert
        assertNotNull(result);
        assertTrue(result.getUsername().startsWith("User_"));
        assertEquals(2L, result.getId());
        verify(repository, times(1)).save(any(Profile.class));
    }

    @Test
    @DisplayName("Should throw exception when profile persistence fails")
    void shouldThrowExceptionOnPersistenceFailure() {
        // Arrange
        when(repository.findAll()).thenReturn(Collections.emptyList());
        // Simulamos que o repository não conseguiu atribuir um ID (falha de banco)

        // Act & Assert
        assertThrows(ProfileInitializationException.class, () -> profileService.ensureProfileExists());
    }

    @Test
    @DisplayName("Should throw ProfileNotFoundException when activeProfile is null")
    void shouldThrowExceptionWhenNoProfileActive() {
        assertThrows(ProfileNotFoundException.class, () -> profileService.getActiveProfile());
    }

    @Test
    @DisplayName("Should return correct initial for avatar")
    void shouldReturnCorrectInitial() {
        // Arrange
        when(repository.findAll()).thenReturn(List.of(existingProfile));
        profileService.ensureProfileExists();

        // Act
        String initial = profileService.getProfileInitial();

        // Assert
        assertEquals("E", initial);
    }
}