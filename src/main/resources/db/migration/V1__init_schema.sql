--
-- PostgreSQL database dump (Cleaned for Flyway)
--

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';
SET default_table_access_method = heap;

-- 1. Companies
CREATE TABLE public.companies (
    id uuid NOT NULL,
    annual_revenue numeric(38,2) NOT NULL,
    bank_relationship_months integer NOT NULL,
    category character varying(255) NOT NULL,
    director_name character varying(255) NOT NULL,
    inn character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    ogrn character varying(255) NOT NULL,
    okved character varying(255) NOT NULL,
    okved_description character varying(255) NOT NULL,
    registration_date date NOT NULL,
    status character varying(255) NOT NULL,
    tax_debt numeric(38,2) NOT NULL,
    total_debt numeric(38,2) NOT NULL
);

-- 2. Products
CREATE TABLE public.products (
    id uuid NOT NULL,
    base_rate double precision NOT NULL,
    description character varying(255) NOT NULL,
    max_amount numeric(38,2) NOT NULL,
    max_term integer NOT NULL,
    min_amount numeric(38,2) NOT NULL,
    min_term integer NOT NULL,
    name character varying(255) NOT NULL,
    tags character varying(1000) NOT NULL
);

-- 3. Managers
CREATE TABLE public.managers (
    id uuid NOT NULL,
    name character varying(255) NOT NULL,
    role character varying(255) NOT NULL
);

-- 4. Applications
CREATE TABLE public.applications (
    id uuid NOT NULL,
    approved_amount numeric(38,2),
    approved_term_months integer,
    assigned_manager_id character varying(255),
    created_at timestamp(6) with time zone NOT NULL,
    idempotency_key character varying(255),
    purpose character varying(255),
    rejection_reason character varying(255),
    requested_amount numeric(38,2) NOT NULL,
    requested_term_months integer NOT NULL,
    risk_score integer,
    scoring_details character varying(4000),
    status character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    company_id uuid NOT NULL,
    product_id uuid NOT NULL,
    has_collateral boolean DEFAULT false NOT NULL,
    owner_username character varying(255),
    version bigint
);

-- 5. Application Events
CREATE TABLE public.application_events (
    id uuid NOT NULL,
    actor character varying(255),
    description character varying(1000) NOT NULL,
    event_type character varying(255) NOT NULL,
    "timestamp" timestamp(6) with time zone NOT NULL,
    application_id uuid NOT NULL
);

-- 6. Consents
CREATE TABLE public.consents (
    id uuid NOT NULL,
    consent_type character varying(255) NOT NULL,
    signed_at timestamp(6) with time zone NOT NULL,
    application_id uuid NOT NULL
);

-- 7. Documents
CREATE TABLE public.documents (
    id uuid NOT NULL,
    file_name character varying(255) NOT NULL,
    upload_date timestamp(6) with time zone NOT NULL,
    application_id uuid NOT NULL,
    content_type character varying(255),
    original_file_name character varying(255),
    sha256 character varying(64),
    size_bytes bigint
);

-- 8. Offers
CREATE TABLE public.offers (
    id uuid NOT NULL,
    amount numeric(38,2) NOT NULL,
    interest_rate double precision NOT NULL,
    term_months integer NOT NULL,
    type character varying(255) NOT NULL,
    application_id uuid NOT NULL
);

-- 9. Processing Jobs
CREATE TABLE public.processing_jobs (
    id uuid NOT NULL,
    application_id uuid NOT NULL,
    attempts integer NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    last_error character varying(4000),
    max_attempts integer NOT NULL,
    next_attempt_at timestamp(6) with time zone NOT NULL,
    status character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL
);

-- 10. Event Publication (Spring Modulith)
CREATE TABLE public.event_publication (
    id uuid NOT NULL,
    completion_date timestamp(6) with time zone,
    event_type character varying(255),
    listener_id character varying(255),
    publication_date timestamp(6) with time zone,
    serialized_event character varying(255)
);

CREATE TABLE public.event_publication_archive (
    id uuid NOT NULL,
    completion_date timestamp(6) with time zone,
    event_type character varying(255),
    listener_id character varying(255),
    publication_date timestamp(6) with time zone,
    serialized_event character varying(255)
);

-- PRIMARY KEYS
ALTER TABLE ONLY public.application_events ADD CONSTRAINT application_events_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.applications ADD CONSTRAINT applications_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.companies ADD CONSTRAINT companies_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.consents ADD CONSTRAINT consents_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.documents ADD CONSTRAINT documents_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.event_publication_archive ADD CONSTRAINT event_publication_archive_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.event_publication ADD CONSTRAINT event_publication_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.managers ADD CONSTRAINT managers_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.offers ADD CONSTRAINT offers_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.processing_jobs ADD CONSTRAINT processing_jobs_pkey PRIMARY KEY (id);
ALTER TABLE ONLY public.products ADD CONSTRAINT products_pkey PRIMARY KEY (id);

-- UNIQUE CONSTRAINTS
ALTER TABLE ONLY public.processing_jobs ADD CONSTRAINT uk_processing_jobs_application UNIQUE (application_id);
ALTER TABLE ONLY public.companies ADD CONSTRAINT uk_companies_inn UNIQUE (inn);
ALTER TABLE ONLY public.applications ADD CONSTRAINT uk_applications_idempotency_key UNIQUE (idempotency_key);

-- FOREIGN KEYS
ALTER TABLE ONLY public.application_events ADD CONSTRAINT fk_application_events_application FOREIGN KEY (application_id) REFERENCES public.applications(id);
ALTER TABLE ONLY public.documents ADD CONSTRAINT fk_documents_application FOREIGN KEY (application_id) REFERENCES public.applications(id);
ALTER TABLE ONLY public.consents ADD CONSTRAINT fk_consents_application FOREIGN KEY (application_id) REFERENCES public.applications(id);
ALTER TABLE ONLY public.applications ADD CONSTRAINT fk_applications_company FOREIGN KEY (company_id) REFERENCES public.companies(id);
ALTER TABLE ONLY public.offers ADD CONSTRAINT fk_offers_application FOREIGN KEY (application_id) REFERENCES public.applications(id);
ALTER TABLE ONLY public.applications ADD CONSTRAINT fk_applications_product FOREIGN KEY (product_id) REFERENCES public.products(id);

--
-- PostgreSQL database dump complete
--