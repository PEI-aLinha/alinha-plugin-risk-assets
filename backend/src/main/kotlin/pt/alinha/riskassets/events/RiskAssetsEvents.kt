package pt.alinha.riskassets.events

import pt.alinha.pluginapi.DomainEvent
import pt.alinha.pluginapi.TenantId
import java.time.Instant
import java.util.UUID

/**
 * A risk was added to the organization's register (risk-assets#4, FR36). Carries the id only: consumers such as the
 * dashboard read models (dashboard-reports#2) ask this plugin for the data inside `TenantContext.runAs(event)`.
 */
data class RiskRegistered(
    override val tenantId: TenantId,
    override val correlationId: UUID,
    val riskId: UUID,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent
