-- ISO/IEC 27005 risk register (FR36, risk-assets#4). The level is not stored: it comes from the configurable risk
-- matrix on every read. The score is stored only to sort the register by it.
create table riskassets.risk (
    id            uuid primary key,
    tenant_id     uuid        not null,
    version       bigint      not null,
    -- No foreign key yet: the asset inventory (risk-assets#2) has no table. When it does, the key must include the
    -- tenant, (tenant_id, asset_id) -> asset (tenant_id, id), or a risk could point at another organization's asset.
    -- owner_id is an account `sub`: checked against the organization's members once they exist (organization#6).
    asset_id      uuid,
    threat        text        not null check (char_length(threat) between 1 and 200),
    vulnerability text        check (char_length(vulnerability) between 1 and 500),
    likelihood    integer     not null check (likelihood between 1 and 10), -- RiskMatrix.MAX_SCALE_VALUE
    impact        integer     not null check (impact between 1 and 10),
    score         integer     not null,
    treatment     text        check (treatment in ('MITIGATE', 'ACCEPT', 'TRANSFER', 'AVOID')),
    owner_id      uuid,
    created_at    timestamptz not null,
    updated_at    timestamptz not null,
    constraint risk_score_is_product check (score = likelihood * impact)
);

-- The register's order: highest score first, then most recently changed.
create index risk_tenant_order_idx on riskassets.risk (tenant_id, score desc, updated_at desc);
