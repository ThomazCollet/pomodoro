/* INSTRUÇÕES DE USO:
   1. Certifique-se de que a aplicação Java rodou pelo menos uma vez para criar as tabelas.
   2. No terminal, com o pomodoro.db na pasta:
      .\sqlite3.exe pomodoro.db
      .read seed.sql
*/

DELETE FROM focus_sessions;

WITH RECURSIVE dates(date_val) AS (
    SELECT date('2026-04-15')
    UNION ALL
    SELECT date(date_val, '+1 day')
    FROM dates
    LIMIT 45
),
generated_data AS (
    SELECT 
        1 as profile_id, 
        'FOCUS' as type,
        date_val || ' 09:00:00' as start_timestamp,
        date_val || ' 09:00:00' as end_timestamp,
        CASE 
            WHEN (ABS(RANDOM()) % 10) = 0 THEN 0
            WHEN (ABS(RANDOM()) % 5) = 1 THEN 600
            WHEN (ABS(RANDOM()) % 5) = 2 THEN 3600
            WHEN (ABS(RANDOM()) % 5) = 3 THEN 5400
            ELSE 9000
        END as calculated_duration,
        1 as completed
    FROM dates
)
INSERT INTO focus_sessions (profile_id, type, start_timestamp, end_timestamp, duration_seconds, completed)
SELECT profile_id, type, start_timestamp, end_timestamp, calculated_duration, completed
FROM generated_data
WHERE calculated_duration > 0;

-- Adiciona sessões extras para teste
INSERT INTO focus_sessions (profile_id, type, start_timestamp, end_timestamp, duration_seconds, completed)
VALUES 
(1, 'FOCUS', '2026-05-20 14:00:00', '2026-05-20 15:00:00', 3600, 1),
(1, 'FOCUS', '2026-05-20 16:00:00', '2026-05-20 17:00:00', 3600, 1);

SELECT 'Banco de dados preenchido com sucesso!' AS status;

-- =========================================================
-- TESTE DE HISTÓRICO DE DESAFIOS (UI)
-- =========================================================

-- Limpa desafios de teste (ajuste o profile_id conforme necessário)
DELETE FROM challenges WHERE profile_id = 1;

-- 1. Desafio de Intensidade CONCLUÍDO (Barra cheia, verde)
INSERT INTO challenges (
    profile_id, title, type, duration_days, min_focus_minutes_per_day, 
    target_total_minutes, accumulated_minutes, lives_total, lives_remaining, 
    status, start_date, progress_days
) VALUES (
    1, 'Maratona de Java', 'MILESTONE_CHALLENGE', 30, 0, 
    600, 600, 0, 0, 
    'COMPLETED', '2026-04-01', 30
);

-- 2. Desafio de Constância FALHOU (Barra parcial, vermelho)
INSERT INTO challenges (
    profile_id, title, type, duration_days, min_focus_minutes_per_day, 
    target_total_minutes, accumulated_minutes, lives_total, lives_remaining, 
    status, start_date, progress_days
) VALUES (
    1, 'Acordar às 06h', 'STREAK_CHALLENGE', 14, 0, 
    0, 0, 3, -1, 
    'FAILED', '2026-05-10', 5
);

-- 3. Desafio de Constância EM ANDAMENTO (Para manter o contraste)
INSERT INTO challenges (
    profile_id, title, type, duration_days, min_focus_minutes_per_day, 
    target_total_minutes, accumulated_minutes, lives_total, lives_remaining, 
    status, start_date, progress_days
) VALUES (
    1, 'Leitura Diária', 'STREAK_CHALLENGE', 20, 30, 
    0, 0, 3, 2, 
    'ACTIVE', '2026-05-25', 5
);

SELECT 'Desafios de teste (concluído, falho e ativo) injetados com sucesso!' AS status;