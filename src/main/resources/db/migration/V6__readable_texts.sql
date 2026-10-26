UPDATE public.applications
SET rejection_reason = 'Сумма заявки больше 10 000 000 ₽. Нужна ручная проверка.'
WHERE rejection_reason = 'Сумма заявки больше 10000000 ₽. Нужна ручная проверка.';

UPDATE public.application_events
SET description = CASE description
    WHEN 'Клиент принял предложение REQUESTED' THEN 'Клиент принял предложение: на запрошенных условиях'
    WHEN 'Клиент принял предложение LIMIT' THEN 'Клиент принял предложение: меньшая сумма'
    WHEN 'Клиент принял предложение GUARANTEE' THEN 'Клиент принял предложение: с поручительством Корпорации МСП'
    WHEN 'Клиент принял предложение COLLATERAL' THEN 'Клиент принял предложение: с залогом'
    ELSE description
END
WHERE description LIKE 'Клиент принял предложение %';

DELETE FROM public.application_events e
USING public.applications a
WHERE e.application_id = a.id
  AND e.event_type = 'DATA_ENRICHED'
  AND a.rejection_reason = 'Не все источники данных ответили';
