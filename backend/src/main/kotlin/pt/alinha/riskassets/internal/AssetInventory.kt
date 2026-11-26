package pt.alinha.riskassets.internal

import jakarta.persistence.EntityManager
import jakarta.persistence.criteria.Predicate
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pt.alinha.core.audit.AuditTrail
import pt.alinha.core.tenant.CorrelationContext
import pt.alinha.core.tenant.TenantContext
import pt.alinha.riskassets.events.AssetRegistered
import pt.alinha.riskassets.internal.domain.AssetData
import pt.alinha.riskassets.internal.domain.AssetInput
import pt.alinha.riskassets.internal.domain.AssetType
import pt.alinha.riskassets.internal.domain.Criticality
import pt.alinha.riskassets.internal.domain.FieldError
import pt.alinha.riskassets.internal.domain.InventoryValidator
import pt.alinha.riskassets.internal.domain.Validation
import pt.alinha.riskassets.internal.persistence.AssetEntity
import pt.alinha.riskassets.internal.persistence.AssetRepository
import pt.alinha.riskassets.internal.persistence.CriticalServiceEntity
import pt.alinha.riskassets.internal.persistence.CriticalServiceRepository
import java.time.Instant
import java.util.UUID

internal const val ASSET_ENTITY_TYPE = "asset"

/** The list filters; they combine with AND. */
data class AssetFilter(val type: AssetType? = null, val criticality: Criticality? = null, val serviceId: UUID? = null)

/** Assets of the current organization (tenant filtering is Hibernate's). Audit details hold no free text. */
@Service
class AssetInventory(
    private val assets: AssetRepository,
    private val services: CriticalServiceRepository,
    private val audit: AuditTrail,
    private val tenants: TenantContext,
    private val correlation: CorrelationContext,
    private val events: ApplicationEventPublisher,
    private val entityManager: EntityManager,
) {

    @Transactional(readOnly = true)
    fun list(page: Int, pageSize: Int, filter: AssetFilter): AssetPage {
        val request = pageRequest(page, pageSize, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")))
        val result = assets.findAll(matching(filter), request)
        return AssetPage(result.content.map { it.toView() }, request.pageNumber, request.pageSize, result.totalElements)
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): AssetView = (assets.findOneById(id) ?: throw AssetNotFoundException()).toView()

    @Transactional
    fun create(input: AssetInput): AssetView {
        val data = validated(input)
        val linked = loadServices(data.serviceIds)
        val now = Instant.now()
        // persist, not save(): the id is assigned, so save() would merge, and merging an entity with a collection
        // bumps its version on the first flush (a new asset must start at version 0).
        val saved = AssetEntity(UUID.randomUUID(), now, data, linked)
        entityManager.persist(saved)
        entityManager.flush()
        audit.record(
            "asset.created", ASSET_ENTITY_TYPE, saved.id.toString(),
            mapOf(
                "type" to data.type.json,
                "criticality" to data.criticality.json,
                "serviceIds" to data.serviceIds.map { it.toString() }.sorted(),
            ),
        )
        events.publishEvent(AssetRegistered(tenants.current(), correlation.current(), saved.id, now))
        return saved.toView()
    }

    @Transactional
    fun update(id: UUID, input: AssetInput, version: Long?): AssetView {
        val entity = assets.findOneById(id) ?: throw AssetNotFoundException()
        // Required on update (unlike create), so that a client that forgets serviceIds does not unlink everything.
        val missing = listOfNotNull(
            FieldError("version", "Recarregue a página e volte a fazer as alterações.").takeIf { version == null },
            FieldError("serviceIds", "Indique os serviços que o ativo suporta (lista vazia se nenhum).")
                .takeIf { input.serviceIds == null },
        )
        val data = validated(input, missing)
        if (version != entity.version) throw StaleVersionException()
        val changed = entity.update(data, loadServices(data.serviceIds), Instant.now())
        if (changed.isEmpty()) return entity.toView()
        val saved = assets.saveAndFlush(entity)
        audit.record("asset.updated", ASSET_ENTITY_TYPE, saved.id.toString(), mapOf("changed" to changed))
        return saved.toView()
    }

    @Transactional
    fun delete(id: UUID) {
        val entity = assets.findOneById(id) ?: throw AssetNotFoundException()
        assets.delete(entity)
        audit.record("asset.deleted", ASSET_ENTITY_TYPE, id.toString(), emptyMap())
    }

    /**
     * The tenant-filtered load is the guard against linking another organization's service (the join table has no
     * tenant column): a missing id, foreign or deleted, is a validation error.
     */
    private fun loadServices(ids: Set<UUID>): Set<CriticalServiceEntity> {
        if (ids.isEmpty()) return emptySet()
        val found = services.findAllByIdIn(ids).toSet()
        if (found.size != ids.size) {
            throw InvalidInventoryDataException(
                listOf(FieldError("serviceIds", "Um dos serviços escolhidos já não existe. Recarregue a página.")),
            )
        }
        return found
    }

    private fun validated(input: AssetInput, extra: List<FieldError> = emptyList()): AssetData =
        when (val result = InventoryValidator.asset(input, extra)) {
            is Validation.Valid -> result.value
            is Validation.Invalid -> throw InvalidInventoryDataException(result.errors)
        }

    private fun matching(filter: AssetFilter): Specification<AssetEntity> = Specification { root, query, cb ->
        val predicates = mutableListOf<Predicate>()
        filter.type?.let { predicates += cb.equal(root.get<AssetType>("type"), it) }
        filter.criticality?.let { predicates += cb.equal(root.get<Criticality>("criticality"), it) }
        filter.serviceId?.let {
            query?.distinct(true)
            predicates += cb.equal(root.join<AssetEntity, CriticalServiceEntity>("services").get<UUID>("id"), it)
        }
        cb.and(*predicates.toTypedArray())
    }
}
