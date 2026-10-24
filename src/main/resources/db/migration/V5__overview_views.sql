CREATE OR REPLACE VIEW public.v_applications_overview AS
SELECT
    a.id AS application_id,
    c.inn,
    c.name AS company,
    p.name AS product,
    a.requested_amount,
    a.requested_term_months,
    a.status,
    a.risk_score,
    CASE
        WHEN a.risk_score IS NULL THEN NULL
        WHEN a.risk_score >= 700 THEN 'A'
        WHEN a.risk_score >= 600 THEN 'B'
        WHEN a.risk_score >= 500 THEN 'C'
        ELSE 'D'
    END AS grade,
    a.approved_amount,
    a.rejection_reason,
    (SELECT count(*) FROM public.offers o WHERE o.application_id = a.id) AS offers,
    a.created_at,
    a.updated_at
FROM public.applications a
JOIN public.companies c ON c.id = a.company_id
JOIN public.products p ON p.id = a.product_id;

CREATE OR REPLACE VIEW public.v_application_timeline AS
SELECT
    e.application_id,
    c.inn,
    c.name AS company,
    e."timestamp",
    e.event_type,
    e.actor,
    e.description
FROM public.application_events e
JOIN public.applications a ON a.id = e.application_id
JOIN public.companies c ON c.id = a.company_id;

CREATE OR REPLACE VIEW public.v_companies_risk AS
SELECT
    c.inn,
    c.name,
    c.category,
    c.status,
    c.okved,
    c.registration_date,
    c.annual_revenue,
    c.total_debt,
    round(c.total_debt / NULLIF(c.annual_revenue, 0) * 100, 1) AS debt_to_revenue_pct,
    c.tax_debt,
    c.bank_relationship_months
FROM public.companies c;
