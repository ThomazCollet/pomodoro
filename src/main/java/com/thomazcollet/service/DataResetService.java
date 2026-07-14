package com.thomazcollet.service;

import com.thomazcollet.domain.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orquestra as operações destrutivas da Zona de Risco nas Configurações.
 *
 * <p>
 * Separado do {@link ProfileService} para manter o foco de cada serviço:
 * o ProfileService cuida de identidade e preferências; este serviço cuida de
 * limpeza de dados de progresso, que envolve múltiplos repositórios.
 */
public class DataResetService {

    private static final Logger logger = LoggerFactory.getLogger(DataResetService.class);

    private final FocusSessionRepository sessionRepository;
    private final AchievementRepository achievementRepository;
    private final ChallengeRepository challengeRepository;
    private final StreakRecordRepository streakRepository;
    private final NotificationRepository notificationRepository;
    private final ProfileRepository profileRepository;

    public DataResetService(
            FocusSessionRepository sessionRepository,
            AchievementRepository achievementRepository,
            ChallengeRepository challengeRepository,
            StreakRecordRepository streakRepository,
            NotificationRepository notificationRepository,
            ProfileRepository profileRepository) {

        this.sessionRepository = sessionRepository;
        this.achievementRepository = achievementRepository;
        this.challengeRepository = challengeRepository;
        this.streakRepository = streakRepository;
        this.notificationRepository = notificationRepository;
        this.profileRepository = profileRepository;
    }

    /**
     * Apaga todo o histórico de foco e streaks do perfil.
     * Reseta as estatísticas do perfil (recorde diário e total de sessões) para
     * zero.
     * Preserva conquistas, desafios, notificações e XP.
     */
    public void clearFocusHistory(Long profileId) {
        logger.warn("Iniciando limpeza de histórico de foco para o perfil ID {}.", profileId);
        sessionRepository.deleteAllByProfileId(profileId);
        streakRepository.deleteAllByProfileId(profileId);
        profileRepository.updateStats(profileId, 0, 0);
        logger.info("Histórico de foco apagado com sucesso para o perfil ID {}.", profileId);
    }

    /**
     * Apaga TODO o progresso do perfil: histórico de foco, streak records,
     * conquistas, desafios, notificações. Reseta XP e estatísticas para zero.
     *
     * <p>
     * O perfil em si (username, avatar, preferências de configuração)
     * é preservado — apenas os dados de progresso são apagados.
     */
    public void resetAllProgress(Long profileId) {
        logger.warn("Iniciando reset completo de progresso para o perfil ID {}.", profileId);
        clearFocusHistory(profileId);
        achievementRepository.deleteAllByProfileId(profileId);
        challengeRepository.deleteAllByProfileId(profileId);
        notificationRepository.deleteAllByProfileId(profileId.intValue());
        profileRepository.updateXp(profileId, 0);
        logger.info("Reset completo de progresso concluído para o perfil ID {}.", profileId);
    }
}