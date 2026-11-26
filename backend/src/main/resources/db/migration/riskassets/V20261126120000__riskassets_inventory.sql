-- Critical services and assets of the organization (FR35, risk-assets#2). Enum values as the Kotlin names.
create table riskassets.critical_service (
    id          uuid primary key,
    tenant_id   uuid        not null,
    version     bigint      not null,
    name        text        not null check (char_length(name) between 1 and 200),
    description text        check (char_length(description) <= 2000),
    owner       text        not null check (char_length(owner) between 1 and 120),
    criticality text        not null check (criticality in ('LOW', 'MEDIUM', 'HIGH')),
    created_at  timestamptz not null,
    updated_at  timestamptz not null
);
create unique index critical_service_name_uq on riskassets.critical_service (tenant_id, lower(name));

create table riskassets.asset (
    id          uuid primary key,
    tenant_id   uuid        not null,
    version     bigint      not null,
    name        text        not null check (char_length(name) between 1 and 200),
    description text        check (char_length(description) <= 2000),
    owner       text        not null check (char_length(owner) between 1 and 120),
    criticality text        not null check (criticality in ('LOW', 'MEDIUM', 'HIGH')),
    type        text        not null check (type in ('SERVER','NETWORK','APPLICATION','DATABASE','EQUIPMENT','CLOUD_SERVICE','STORAGE','OT_SYSTEM','OTHER')),
    created_at  timestamptz not null,
    updated_at  timestamptz not null
);
create index asset_tenant_name_idx on riskassets.asset (tenant_id, name);

create table riskassets.service_asset (
    service_id uuid not null references riskassets.critical_service (id) on delete cascade,
    asset_id   uuid not null references riskassets.asset (id) on delete cascade,
    primary key (service_id, asset_id)
);
create index service_asset_asset_idx on riskassets.service_asset (asset_id);
