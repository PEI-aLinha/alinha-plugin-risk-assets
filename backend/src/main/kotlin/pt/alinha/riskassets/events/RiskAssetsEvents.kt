package pt.alinha.riskassets.events

import pt.alinha.pluginapi.DomainEvent
import pt.alinha.pluginapi.TenantId
import java.time.Instant
import java.util.UUID

/**
 * An asset was registered in the inventory. The event carries ids only; consumers read the data through
 * `RiskAssetsApi` inside `TenantContext.runAs(tenantId)`.
 *
 * `runAs` must start outside a transaction, so do not use `@ApplicationModuleListener` (it opens one first). Use
 * `@Async @TransactionalEventListener` with no `@Transactional`, and open the transaction inside `runAs`. Pass the
 * event itself, so the work also carries its [correlationId] (docs/events.md in the main repo):
 * ```
 * @Async @TransactionalEventListener
 * fun on(event: AssetRegistered) = tenants.runAs(event) { tx.execute { ... } }
 * ```
 */
data class AssetRegistered(
    override val tenantId: TenantId,
    override val correlationId: UUID,
    val assetId: UUID,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent
