/* ============================================================
   SEED DE DEMONSTRAÇÃO — ABA DE ESTATÍSTICAS
   ============================================================
   Popula ~13 meses de sessões de foco com padrões realistas
   (mais intensidade em dias de semana, quedas no fim de semana),
   garante:
     - Heatmap anual completo e variado (todas as 6 faixas de cor)
     - Streak atual "quente" de 13 dias terminando hoje
     - Pódio de Dias Recordistas com 3 valores de destaque
     - Pódio de Meses Recordistas com 3 meses reforçados
     - Pódio de Maiores Streaks com histórico de 5 sequências
     - Perfil com recordes/metas redondos e Rank A
     - Desafios (ativo, concluído e falho), conquistas variadas
       (incluindo Platina) e notificações no sino

   COMO USAR:
     1. Rode a aplicação Java ao menos uma vez para as tabelas existirem.
     2. No terminal, na pasta do projeto, com o pomodoro.db presente:
          sqlite3 pomodoro.db
          .read seed_stats_demo.sql
     3. Abra o app e vá até a aba Estatísticas (ou Conquistas/Desafios).

   ⚠️ Assume que o perfil de destino tem id = 1 (o primeiro perfil
      criado automaticamente pelo app). Se seu perfil tiver outro id,
      troque as ocorrências de "profile_id = 1" / "1," abaixo.

   💡 Como as datas são geradas relativas a date('now'), você pode
      rodar este script de novo mais tarde e o heatmap/streak sempre
      vão parecer "atuais" na hora do print ou GIF.
   ============================================================ */

PRAGMA foreign_keys = ON;

-- ------------------------------------------------------------
-- 0. LIMPEZA — remove dados de teste anteriores do perfil alvo
-- ------------------------------------------------------------
DELETE FROM focus_sessions WHERE profile_id = 1;
DELETE FROM streak_records WHERE profile_id = 1;
DELETE FROM challenges     WHERE profile_id = 1;
DELETE FROM achievements   WHERE profile_id = 1;
DELETE FROM notifications  WHERE profile_id = 1;

-- ------------------------------------------------------------
-- 1. HISTÓRICO DE FOCO — ~13 meses de dados (heatmap completo)
-- ------------------------------------------------------------
-- Um dia por offset (13 = ~2 semanas atrás até 399 = ~13 meses atrás).
-- Os últimos 13 dias (offset 0-12) ficam de fora daqui de propósito:
-- eles são preenchidos à parte na seção 2, garantindo uma streak
-- atual limpa e ininterrupta.
WITH RECURSIVE dates(day_offset, date_val, weekday) AS (
    SELECT 13, date('now', '-13 day'), CAST(strftime('%w', date('now', '-13 day')) AS INTEGER)
    UNION ALL
    SELECT day_offset + 1,
           date('now', '-' || (day_offset + 1) || ' day'),
           CAST(strftime('%w', date('now', '-' || (day_offset + 1) || ' day')) AS INTEGER)
    FROM dates
    WHERE day_offset < 399
),
rolled AS (
    SELECT date_val, weekday, ABS(RANDOM() % 100) AS roll
    FROM dates
),
sized AS (
    SELECT
        date_val,
        CASE
            -- Fim de semana (0 = domingo, 6 = sábado): mais dias vazios/curtos
            WHEN weekday IN (0, 6) THEN
                CASE
                    WHEN roll < 35 THEN 0
                    WHEN roll < 55 THEN 300   + ABS(RANDOM() % 1400)  -- rastro
                    WHEN roll < 80 THEN 1800  + ABS(RANDOM() % 5300)  -- leve
                    WHEN roll < 95 THEN 7200  + ABS(RANDOM() % 7100)  -- bom
                    WHEN roll < 99 THEN 14400 + ABS(RANDOM() % 7000)  -- ótimo
                    ELSE               21600 + ABS(RANDOM() % 8000)  -- supremo
                END
            -- Dia de semana: mais intensidade e consistência
            ELSE
                CASE
                    WHEN roll < 10 THEN 0
                    WHEN roll < 25 THEN 300   + ABS(RANDOM() % 1400)
                    WHEN roll < 55 THEN 1800  + ABS(RANDOM() % 5300)
                    WHEN roll < 85 THEN 7200  + ABS(RANDOM() % 7100)
                    WHEN roll < 95 THEN 14400 + ABS(RANDOM() % 7000)
                    ELSE               21600 + ABS(RANDOM() % 8000)
                END
        END AS total_seconds
    FROM rolled
)
INSERT INTO focus_sessions (profile_id, type, start_timestamp, end_timestamp, duration_seconds, completed)
SELECT 1, 'FOCUS', date_val || ' 09:00:00', date_val || ' 09:00:00', total_seconds, 1
FROM sized
WHERE total_seconds > 0;

-- ------------------------------------------------------------
-- 2. STREAK ATUAL "QUENTE" — últimos 13 dias sempre com foco
-- ------------------------------------------------------------
WITH RECURSIVE last_days(day_offset, date_val) AS (
    SELECT 0, date('now')
    UNION ALL
    SELECT day_offset + 1, date('now', '-' || (day_offset + 1) || ' day')
    FROM last_days
    WHERE day_offset < 12
)
INSERT INTO focus_sessions (profile_id, type, start_timestamp, end_timestamp, duration_seconds, completed)
SELECT 1, 'FOCUS', date_val || ' 09:00:00', date_val || ' 09:00:00',
       7200 + ABS(RANDOM() % 12600), -- entre 2h e 5h30
       1
FROM last_days;

-- ------------------------------------------------------------
-- 3. DIAS RECORDISTAS GARANTIDOS — 🏆 pódio diário
-- ------------------------------------------------------------
INSERT INTO focus_sessions (profile_id, type, start_timestamp, end_timestamp, duration_seconds, completed)
VALUES
    (1, 'FOCUS', date('now', '-42 day')  || ' 08:30:00', date('now', '-42 day')  || ' 08:30:00', 32400, 1), -- 9h00m
    (1, 'FOCUS', date('now', '-77 day')  || ' 08:30:00', date('now', '-77 day')  || ' 08:30:00', 29700, 1), -- 8h15m
    (1, 'FOCUS', date('now', '-108 day') || ' 08:30:00', date('now', '-108 day') || ' 08:30:00', 27000, 1); -- 7h30m

-- ------------------------------------------------------------
-- 4. MESES RECORDISTAS GARANTIDOS — 📅 pódio mensal
-- ------------------------------------------------------------
-- Reforça 3 meses específicos do histórico com sessões extras para
-- que fiquem claramente à frente na comparação mensal.
WITH RECURSIVE boost_month_1(n, date_val) AS (
    SELECT 0, date('now', '-95 day')
    UNION ALL
    SELECT n + 1, date('now', '-95 day', '+' || (n + 1) || ' day')
    FROM boost_month_1
    WHERE n < 9
)
INSERT INTO focus_sessions (profile_id, type, start_timestamp, end_timestamp, duration_seconds, completed)
SELECT 1, 'FOCUS', date_val || ' 10:00:00', date_val || ' 10:00:00', 9000 + ABS(RANDOM() % 3600), 1
FROM boost_month_1;

WITH RECURSIVE boost_month_2(n, date_val) AS (
    SELECT 0, date('now', '-190 day')
    UNION ALL
    SELECT n + 1, date('now', '-190 day', '+' || (n + 1) || ' day')
    FROM boost_month_2
    WHERE n < 9
)
INSERT INTO focus_sessions (profile_id, type, start_timestamp, end_timestamp, duration_seconds, completed)
SELECT 1, 'FOCUS', date_val || ' 10:00:00', date_val || ' 10:00:00', 8400 + ABS(RANDOM() % 3600), 1
FROM boost_month_2;

WITH RECURSIVE boost_month_3(n, date_val) AS (
    SELECT 0, date('now', '-290 day')
    UNION ALL
    SELECT n + 1, date('now', '-290 day', '+' || (n + 1) || ' day')
    FROM boost_month_3
    WHERE n < 9
)
INSERT INTO focus_sessions (profile_id, type, start_timestamp, end_timestamp, duration_seconds, completed)
SELECT 1, 'FOCUS', date_val || ' 10:00:00', date_val || ' 10:00:00', 7800 + ABS(RANDOM() % 3600), 1
FROM boost_month_3;

-- ------------------------------------------------------------
-- 5. PÓDIO DE STREAKS — 🔥 histórico de sequências
-- ------------------------------------------------------------
INSERT INTO streak_records (profile_id, duration_days, start_date, end_date)
VALUES
    (1, 34, date('now', '-160 day'), date('now', '-127 day')),
    (1, 27, date('now', '-230 day'), date('now', '-204 day')),
    (1, 21, date('now', '-95 day'),  date('now', '-75 day')),
    (1, 18, date('now', '-310 day'), date('now', '-293 day')),
    (1, 14, date('now', '-55 day'),  date('now', '-42 day'));

-- ------------------------------------------------------------
-- 6. PERFIL — recordes e metas redondos, Rank A (18.500 XP)
-- ------------------------------------------------------------
UPDATE profiles
SET
    max_focus_day_seconds = 32400,   -- 9h00m (bate com o dia recordista acima)
    total_focus_sessions  = 268,
    xp                    = 18500,   -- Rank A (entre 15.000 e 40.000)
    daily_goal_seconds    = 7200,    -- meta diária: 2h
    weekly_goal_seconds   = 32400,   -- meta semanal: 9h
    monthly_goal_seconds  = 129600   -- meta mensal: 36h
WHERE id = 1;

-- ------------------------------------------------------------
-- 7. DESAFIOS — mistura de ativos, concluídos e falhos
-- ------------------------------------------------------------
INSERT INTO challenges (
    profile_id, title, type, duration_days, min_focus_minutes_per_day,
    target_total_minutes, accumulated_minutes, today_focus_minutes,
    lives_total, lives_remaining, status, start_date, progress_days
) VALUES
    -- Concluído (Intensidade)
    (1, 'Maratona de Java', 'MILESTONE_CHALLENGE', 30, 0, 1800, 1800, 0, 0, 0,
     'COMPLETED', date('now', '-60 day'), 30),
    -- Concluído (Constância)
    (1, 'Rotina Matinal de Estudos', 'STREAK_CHALLENGE', 21, 45, 0, 0, 0, 3, 3,
     'COMPLETED', date('now', '-50 day'), 21),
    -- Falho (Constância)
    (1, 'Acordar às 06h', 'STREAK_CHALLENGE', 14, 30, 0, 0, 0, 3, -1,
     'FAILED', date('now', '-35 day'), 6),
    -- Ativo (Constância) — em bom ritmo
    (1, 'Leitura Técnica Diária', 'STREAK_CHALLENGE', 20, 30, 0, 0, 45, 3, 2,
     'ACTIVE', date('now', '-8 day'), 8),
    -- Ativo (Intensidade) — bem avançado
    (1, 'Domínio em Arquitetura de Software', 'MILESTONE_CHALLENGE', 45, 0, 3000, 2100, 90, 0, 0,
     'ACTIVE', date('now', '-30 day'), 0);

-- ------------------------------------------------------------
-- 8. CONQUISTAS — variedade de tiers (incluindo Platina)
-- ------------------------------------------------------------
INSERT INTO achievements (profile_id, achievement_key, category, tier, unlocked_at) VALUES
    (1, 'focus_daily_2h_hours',        'DAILY_FOCUS',  'BRONZE',   datetime('now', '-100 day')),
    (1, 'focus_daily_3h_hours',        'DAILY_FOCUS',  'SILVER',   datetime('now', '-80 day')),
    (1, 'focus_daily_4h_hours',        'DAILY_FOCUS',  'GOLD',     datetime('now', '-42 day')),
    (1, 'focus_cycles_1_bronze',       'DAILY_FOCUS',  'BRONZE',   datetime('now', '-200 day')),
    (1, 'focus_cycles_10_silver',      'DAILY_FOCUS',  'SILVER',   datetime('now', '-150 day')),
    (1, 'focus_cycles_25_gold',        'DAILY_FOCUS',  'GOLD',     datetime('now', '-60 day')),
    (1, 'focus_cycles_100_platinum',   'DAILY_FOCUS',  'PLATINUM', datetime('now', '-5 day')),
    (1, 'focus_total_days_15_bronze',  'DAILY_FOCUS',  'BRONZE',   datetime('now', '-180 day')),
    (1, 'focus_total_days_30_silver',  'DAILY_FOCUS',  'SILVER',   datetime('now', '-140 day')),
    (1, 'focus_accumulated_12h_hours', 'DAILY_FOCUS',  'BRONZE',   datetime('now', '-190 day')),
    (1, 'focus_accumulated_24h_hours', 'DAILY_FOCUS',  'SILVER',   datetime('now', '-160 day')),
    (1, 'streak_current_5',            'STREAK',       'BRONZE',   datetime('now', '-9 day')),
    (1, 'streak_current_12',           'STREAK',       'SILVER',   datetime('now', '-1 day')),
    (1, 'streak_current_15',           'STREAK',       'GOLD',     datetime('now', '-70 day')),
    (1, 'streak_current_30',           'STREAK',       'PLATINUM', datetime('now', '-130 day')),
    (1, 'streak_count_5_x3',           'STREAK',       'BRONZE',   datetime('now', '-30 day')),
    (1, 'challenge_constancy_days_7',  'CHALLENGE',    'BRONZE',   datetime('now', '-50 day')),
    (1, 'challenge_constancy_days_15', 'CHALLENGE',    'SILVER',   datetime('now', '-49 day')),
    (1, 'challenge_perfect_days_5',    'CHALLENGE',    'BRONZE',   datetime('now', '-49 day')),
    (1, 'ranking_tier_c',              'RANKING',      'BRONZE',   datetime('now', '-90 day')),
    (1, 'ranking_tier_a',              'RANKING',      'SILVER',   datetime('now', '-20 day')),
    (1, 'meta_total_5',                'ACHIEVEMENTS', 'BRONZE',   datetime('now', '-45 day')),
    (1, 'meta_total_15',               'ACHIEVEMENTS', 'SILVER',   datetime('now', '-10 day'));

-- ------------------------------------------------------------
-- 9. NOTIFICAÇÕES — histórico para o sino não ficar vazio
-- ------------------------------------------------------------
INSERT INTO notifications (profile_id, title, message, is_read, created_at) VALUES
    (1, '🔥 Streak em alta!', 'Você está há 13 dias seguidos focando. Continue assim!', 0, datetime('now', '-1 hour')),
    (1, '🥇 Conquista Desbloqueada!', 'Parabéns! Você desbloqueou: "Centenário do Foco — 100 ciclos". Continue assim! 🎉', 0, datetime('now', '-1 day')),
    (1, '☀️ Meta Diária Batida!', 'Você atingiu sua meta de foco do dia (02h 00m). Dia bem aproveitado! 🎯', 1, datetime('now', '-2 day')),
    (1, '🏆 Desafio Concluído!', 'Parabéns! Você finalizou o desafio de constância "Rotina Matinal de Estudos" com sucesso! Continue assim — cada vitória conta. 🎉', 1, datetime('now', '-50 day')),
    (1, '🌟 Subiu de Rank!', 'Incrível! Você subiu do Rank B para o Rank A! Sua dedicação está valendo muito. Continue focado! 🚀', 1, datetime('now', '-20 day'));

SELECT '✅ Seed de demonstração da aba Estatísticas aplicado com sucesso!' AS status;