package com.thomazcollet.service;

import com.thomazcollet.domain.exception.ProfileInitializationException;
import com.thomazcollet.domain.exception.ProfileNotFoundException;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.repository.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class ProfileService {
    private static final Logger logger = LoggerFactory.getLogger(ProfileService.class);
    private final ProfileRepository repository;
    private Profile activeProfile;

    public ProfileService(ProfileRepository repository) {
        this.repository = repository;
    }

    /**
     * Inicializa o sistema de perfis. 
     * Se não houver perfis, cria o primeiro automaticamente.
     * @throws ProfileInitializationException se houver falha na persistência do perfil inicial.
     */
    public Profile ensureProfileExists() {
        try {
            List<Profile> profiles = repository.findAll();
            
            if (profiles.isEmpty()) {
                logger.info("Nenhum perfil encontrado. Gerando perfil inicial...");
                Profile newProfile = generateDefaultProfile();
                repository.save(newProfile);
                
                // Validação Fail-Fast: se o ID for nulo, a persistência falhou silenciosamente
                if (newProfile.getId() == null) {
                    throw new ProfileInitializationException("Falha crítica: Perfil gerado mas não persistido corretamente.");
                }

                this.activeProfile = newProfile;
                return newProfile;
            }

            this.activeProfile = profiles.get(0); // Assume o perfil mais recente como ativo
            logger.info("Perfil carregado: {}", activeProfile.getUsername());
            return activeProfile;
            
        } catch (Exception e) {
            logger.error("Erro durante a inicialização do perfil de usuário", e);
            throw new ProfileInitializationException("Não foi possível carregar ou criar um perfil de usuário.", e);
        }
    }

    /**
     * Retorna o perfil ativo em memória.
     * @throws ProfileNotFoundException se o perfil ainda não tiver sido inicializado.
     */
    public Profile getActiveProfile() {
        if (activeProfile == null) {
            throw new ProfileNotFoundException("Nenhum perfil ativo encontrado. Chame ensureProfileExists() primeiro.");
        }
        return activeProfile;
    }

    private Profile generateDefaultProfile() {
        String randomName = "User_" + UUID.randomUUID().toString().substring(0, 5);
        // Atributos padrão: nome aleatório, 25min foco, 5min pausa curta, 15min pausa longa
        return new Profile(randomName, 25, 5, 15);
    }

    /**
     * Permite atualizar o perfil ativo.
     */
    public void updateActiveProfile(Profile profile) {
        if (profile == null || profile.getId() == null) {
            throw new IllegalArgumentException("Perfil inválido para atualização.");
        }
        repository.save(profile);
        this.activeProfile = profile;
        logger.info("Perfil '{}' atualizado com sucesso.", profile.getUsername());
    }
}