package com.thomazcollet.service;

import com.thomazcollet.domain.exception.ProfileInitializationException;
import com.thomazcollet.domain.exception.ProfileNotFoundException;
import com.thomazcollet.domain.model.Profile;
import com.thomazcollet.domain.model.StreakRule;
import com.thomazcollet.domain.repository.ProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;

public class ProfileService {

    private static final Logger logger = LoggerFactory.getLogger(ProfileService.class);
    private final ProfileRepository repository;
    private Profile activeProfile;

    private static final String[] AVATAR_COLORS = {
            "#FF5733", "#33FF57", "#3357FF", "#F333FF",
            "#FF33A1", "#33FFF6", "#F6FF33", "#A133FF"
    };

    public ProfileService(ProfileRepository repository) {
        this.repository = repository;
    }

    // ==========================================================================
    // INICIALIZAÇÃO
    // ==========================================================================

    public Profile ensureProfileExists() {
        try {
            List<Profile> profiles = repository.findAll();

            if (profiles.isEmpty()) {
                logger.info("Nenhum perfil encontrado. Gerando perfil inicial...");
                Profile newProfile = generateDefaultProfile();
                repository.save(newProfile);

                if (newProfile.getId() == null) {
                    throw new ProfileInitializationException(
                            "Falha crítica: Perfil gerado mas não persistido corretamente.");
                }

                this.activeProfile = newProfile;
                return newProfile;
            }

            this.activeProfile = profiles.get(0);
            logger.info("Perfil carregado: {}", activeProfile.getUsername());
            return activeProfile;

        } catch (Exception e) {
            logger.error("Erro durante a inicialização do perfil de usuário", e);
            throw new ProfileInitializationException(
                    "Não foi possível carregar ou criar um perfil de usuário.", e);
        }
    }

    public Profile getActiveProfile() {
        if (activeProfile == null) {
            throw new ProfileNotFoundException(
                    "Nenhum perfil ativo encontrado. Chame ensureProfileExists() primeiro.");
        }
        return activeProfile;
    }

    private Profile generateDefaultProfile() {
        String randomName = "User_" + UUID.randomUUID().toString().substring(0, 5);
        return new Profile(randomName, 25, 5, 15);
    }

    // ==========================================================================
    // MÉTODOS DE ATUALIZAÇÃO GRANULAR
    // ==========================================================================

    /**
     * Persiste e aplica em memória o nome de exibição e o caminho do avatar.
     * Valida o username via setter do domínio antes de persistir.
     *
     * @throws IllegalArgumentException se o username for inválido (vazio, > 20
     *                                  chars).
     */
    public void saveProfileInfo(String username, String imagePath) {
        Profile p = getActiveProfile();
        p.setUsername(username.trim());
        if (imagePath != null)
            p.setImagePath(imagePath);
        repository.updateProfileInfo(p.getId(), p.getUsername(), p.getImagePath());
        logger.info("Informações do perfil atualizadas: username='{}'.", p.getUsername());
    }

    /**
     * Persiste e aplica em memória as metas de foco do perfil ativo.
     */
    public void saveGoals(int dailySeconds, int weeklySeconds, int monthlySeconds) {
        Profile p = getActiveProfile();
        p.setDailyGoalSeconds(dailySeconds);
        p.setWeeklyGoalSeconds(weeklySeconds);
        p.setMonthlyGoalSeconds(monthlySeconds);
        repository.updateGoals(p.getId(), dailySeconds, weeklySeconds, monthlySeconds);
        logger.info("Metas atualizadas: diária={}s, semanal={}s, mensal={}s.",
                dailySeconds, weeklySeconds, monthlySeconds);
    }

    /**
     * Persiste e aplica em memória as durações de foco e pausas do perfil ativo.
     * A validação (ex: longBreak > shortBreak) é aplicada pelos setters de
     * {@link Profile} — lança {@link IllegalArgumentException} se inválido.
     */
    public void saveDurations(int workDuration, int shortBreak, int longBreak) {
        Profile p = getActiveProfile();
        p.setWorkDuration(workDuration);
        p.setShortBreak(shortBreak);
        p.setLongBreak(longBreak);
        repository.updateDurations(p.getId(), workDuration, shortBreak, longBreak);
        logger.info("Durações atualizadas: foco={}m, curta={}m, longa={}m.",
                workDuration, shortBreak, longBreak);
    }

    /**
     * Persiste e aplica em memória as preferências de configuração do perfil ativo.
     */
    public void saveSettings(int audioVolume, boolean notificationsEnabled,
            String language, StreakRule streakRule) {
        Profile p = getActiveProfile();
        p.setAudioVolume(audioVolume);
        p.setNotificationsEnabled(notificationsEnabled);
        p.setLanguage(language);
        p.setStreakRule(streakRule);
        repository.updateSettings(p.getId(), audioVolume, notificationsEnabled,
                language, streakRule != null ? streakRule.name() : "ALL_DAYS");
        logger.info("Configurações atualizadas: volume={}, notif={}, lang={}.",
                audioVolume, notificationsEnabled, language);
    }

    /**
     * Persiste todas as alterações do perfil ativo de forma granular e segura.
     * Cada aspecto do perfil é atualizado pelo seu método dedicado no repositório.
     */
    public void updateActiveProfile(Profile profile) {
        if (profile == null || profile.getId() == null) {
            throw new IllegalArgumentException("Perfil inválido para atualização.");
        }

        repository.updateProfileInfo(profile.getId(), profile.getUsername(), profile.getImagePath());
        repository.updateDurations(profile.getId(),
                profile.getWorkDuration(), profile.getShortBreak(), profile.getLongBreak());
        repository.updateGoals(profile.getId(),
                profile.getDailyGoalSeconds(), profile.getWeeklyGoalSeconds(),
                profile.getMonthlyGoalSeconds());
        repository.updateSettings(profile.getId(),
                profile.getAudioVolume(), profile.isNotificationsEnabled(),
                profile.getLanguage(),
                profile.getStreakRule() != null ? profile.getStreakRule().name() : "ALL_DAYS");

        this.activeProfile = profile;
        logger.info("Perfil '{}' (ID: {}) atualizado com sucesso.",
                profile.getUsername(), profile.getId());
    }

    // ==========================================================================
    // HELPERS DE UI
    // ==========================================================================

    public String getAvatarColor() {
        Profile profile = getActiveProfile();
        int hash = profile.getUsername().hashCode();
        int index = Math.abs(hash) % AVATAR_COLORS.length;
        return AVATAR_COLORS[index];
    }

    public String getProfileInitial() {
        String name = getActiveProfile().getUsername();
        return name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
    }
}