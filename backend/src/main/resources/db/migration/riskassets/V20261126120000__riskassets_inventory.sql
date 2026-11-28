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
    updated_at  timestamptz not null,
    constraint critical_service_tenant_id_uq unique (tenant_id, id)
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
    updated_at  timestamptz not null,
    -- Target of tenant-safe foreign keys (service_asset here, risk.asset_id in risk-assets#4).
    constraint asset_tenant_id_uq unique (tenant_id, id)
);
create index asset_tenant_name_idx on riskassets.asset (tenant_id, name);

-- tenant_id lets the database refuse a link between two organizations (the composite foreign keys), on top of
-- the application's tenant-filtered load. JPA does not map it: the trigger copies it from the asset.
create table riskassets.service_asset (
    tenant_id  uuid not null,
    service_id uuid not null,
    asset_id   uuid not null,
    primary key (service_id, asset_id),
    constraint service_asset_service_fk foreign key (tenant_id, service_id)
        references riskassets.critical_service (tenant_id, id) on delete cascade,
    constraint service_asset_asset_fk foreign key (tenant_id, asset_id)
        references riskassets.asset (tenant_id, id) on delete cascade
);
create index service_asset_asset_idx on riskassets.service_asset (tenant_id, asset_id);

create function riskassets.service_asset_tenant() returns trigger language plpgsql as $$
begin
    new.tenant_id := (select a.tenant_id from riskassets.asset a where a.id = new.asset_id);
    return new;
end
$$;
create trigger service_asset_tenant before insert or update on riskassets.service_asset
    for each row execute function riskassets.service_asset_tenant();
