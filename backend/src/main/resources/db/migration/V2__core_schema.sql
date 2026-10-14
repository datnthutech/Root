CREATE OR REPLACE FUNCTION f_unaccent(text) RETURNS text AS
$$ SELECT public.unaccent('public.unaccent', $1) $$ LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT;

CREATE TABLE app_user (
  id               bigserial PRIMARY KEY,
  username         varchar(80)  NOT NULL UNIQUE,
  password_hash    varchar(100) NOT NULL,
  display_name     varchar(120),
  role             varchar(10)  NOT NULL DEFAULT 'VIEWER' CHECK (role IN ('ADMIN','EDITOR','VIEWER')),
  linked_person_id bigint,
  status           varchar(10)  NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACTIVE','LOCKED')),
  failed_attempts  int          NOT NULL DEFAULT 0,
  locked_until     timestamptz,
  created_at       timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE person (
  id                        bigserial PRIMARY KEY,
  full_name                 varchar(200) NOT NULL,
  nickname                  varchar(100),
  gender                    varchar(1)   NOT NULL DEFAULT 'U' CHECK (gender IN ('M','F','U')),
  birth_date                date,
  birth_precision           varchar(10)  NOT NULL DEFAULT 'UNKNOWN' CHECK (birth_precision IN ('DAY','MONTH','YEAR','UNKNOWN')),
  birth_lunar               varchar(40),
  death_date                date,
  death_precision           varchar(10)  NOT NULL DEFAULT 'UNKNOWN' CHECK (death_precision IN ('DAY','MONTH','YEAR','UNKNOWN')),
  death_anniversary_lunar   varchar(40),
  is_alive                  boolean      NOT NULL DEFAULT true,
  origin_place              varchar(200),
  home_place                varchar(200),
  burial_place              varchar(200),
  biography                 text,
  photo_key                 varchar(255),
  birth_order_override      int CHECK (birth_order_override IS NULL OR birth_order_override >= 2),
  version                   bigint       NOT NULL DEFAULT 0,
  created_by                bigint REFERENCES app_user(id),
  created_at                timestamptz  NOT NULL DEFAULT now(),
  updated_at                timestamptz  NOT NULL DEFAULT now(),
  deleted_at                timestamptz
);
CREATE INDEX idx_person_name_trgm ON person USING gin (lower(f_unaccent(full_name)) gin_trgm_ops);

ALTER TABLE app_user ADD CONSTRAINT fk_user_person FOREIGN KEY (linked_person_id) REFERENCES person(id);

CREATE TABLE union_rel (
  id           bigserial PRIMARY KEY,
  partner_a_id bigint NOT NULL REFERENCES person(id),
  partner_b_id bigint NOT NULL REFERENCES person(id),
  union_order  int    NOT NULL DEFAULT 1 CHECK (union_order >= 1),
  status       varchar(10) NOT NULL DEFAULT 'MARRIED' CHECK (status IN ('MARRIED','DIVORCED','WIDOWED')),
  start_date   date,
  end_date     date,
  CHECK (partner_a_id <> partner_b_id)
);
CREATE INDEX idx_union_partners ON union_rel(partner_a_id, partner_b_id);
CREATE INDEX idx_union_partner_b ON union_rel(partner_b_id);
CREATE UNIQUE INDEX uq_union_active_pair
  ON union_rel (LEAST(partner_a_id, partner_b_id), GREATEST(partner_a_id, partner_b_id))
  WHERE status = 'MARRIED';

CREATE TABLE parent_child (
  id              bigserial PRIMARY KEY,
  parent_id       bigint NOT NULL REFERENCES person(id),
  child_id        bigint NOT NULL REFERENCES person(id),
  kind            varchar(12) NOT NULL DEFAULT 'BIOLOGICAL' CHECK (kind IN ('BIOLOGICAL','ADOPTED','STEP')),
  union_id        bigint REFERENCES union_rel(id) ON DELETE SET NULL,
  counts_in_order boolean NOT NULL DEFAULT true,
  UNIQUE (parent_id, child_id),
  CHECK (parent_id <> child_id)
);
CREATE INDEX idx_pc_parent ON parent_child(parent_id);
CREATE INDEX idx_pc_child ON parent_child(child_id);

CREATE TABLE clan (
  id                 bigserial PRIMARY KEY,
  name               varchar(150) NOT NULL,
  founder_person_id  bigint REFERENCES person(id),
  description        text,
  sibling_count_mode varchar(12) NOT NULL DEFAULT 'PER_UNION' CHECK (sibling_count_mode IN ('PER_UNION','PER_FATHER')),
  use_ut_label       boolean NOT NULL DEFAULT true
);

CREATE TABLE clan_member (
  id         bigserial PRIMARY KEY,
  clan_id    bigint NOT NULL REFERENCES clan(id) ON DELETE CASCADE,
  person_id  bigint NOT NULL REFERENCES person(id),
  membership varchar(10) NOT NULL CHECK (membership IN ('BLOOD','IN_LAW')),
  generation int NOT NULL,
  UNIQUE (clan_id, person_id)
);
CREATE INDEX idx_clan_member_clan ON clan_member(clan_id);

CREATE TABLE refresh_token (
  id         bigserial PRIMARY KEY,
  user_id    bigint NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
  token_hash varchar(100) NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  revoked    boolean NOT NULL DEFAULT false
);

CREATE TABLE audit_log (
  id          bigserial PRIMARY KEY,
  user_id     bigint REFERENCES app_user(id),
  entity_type varchar(30) NOT NULL,
  entity_id   bigint NOT NULL,
  action      varchar(20) NOT NULL,
  before_data jsonb,
  after_data  jsonb,
  created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id, created_at DESC);
