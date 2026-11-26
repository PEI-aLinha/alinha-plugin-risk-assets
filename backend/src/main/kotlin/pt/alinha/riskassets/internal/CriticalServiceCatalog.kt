package pt.alinha.riskassets.internal

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pt.alinha.core.audit.AuditTrail
import pt.alinha.riskassets.internal.domain.CriticalServiceData
import pt.alinha.riskassets.internal.domain.CriticalServiceInput
import pt.alinha.riskassets.internal.domain.FieldError
import pt.alinha.riskassets.internal.domain.InventoryValidator
import pt.alinha.riskassets.internal.domain.Validation
import pt.alinha.riskassets.internal.persistence.AssetRepository
import pt.alinha.riskassets.internal.persistence.CriticalServiceEntity
import pt.alinha.riskassets.internal.persistence.CriticalServiceRepository
import java.time.Instant
import java.util.UUID

internal const val SERVICE_ENTITY_TYPE = "critical_service"
internal const val SERVICE_NAME_CONSTRAINT = "critical_service_name_uq"

/** Critical services of the current organization (tenant filtering is Hibernate's). Audit details hold no free text. */
@Service
class CriticalServiceCatalog(
    private val services: CriticalServiceRepository,
    private val assets: AssetRepository,
    private val audit: AuditTrail,
) {

    @Transactional(readOnly = true)
    fun list(page: Int, pageSize: Int): CriticalServicePage {
        val request = pageRequest(page, pageSize, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id")))
        val result = services.findAllBy(request)
        return CriticalServicePage(result.content.map { it.toView() }, request.pageNumber, request.pageSize, result.totalElements)
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): CriticalServiceView = (services.findOneById(id) ?: throw CriticalServiceNotFoundException()).toView()

    @Transactional
    fun create(input: CriticalServiceInput): CriticalServiceView {
        val data = validated(input)
        val saved = save(CriticalServiceEntity(UUID.randomUUID(), Instant.now(), data))
        audit.record(
            "critical_service.created", SERVICE_ENTITY_TYPE, saved.id.toString(),
            mapOf("criticality" to data.criticality.json),
        )
        return saved.toView()
    }

    @Transactional
    fun update(id: UUID, input: CriticalServiceInput, version: Long?): CriticalServiceView {
        val entity = services.findOneById(id) ?: throw CriticalServiceNotFoundException()
        val missingVersion = if (version == null) {
            listOf(FieldError("version", "Recarregue a página e volte a fazer as alterações."))
        } else {
            emptyList()
        }
        val data = validated(input, missingVersion)
        if (version != entity.version) throw StaleVersionException()
        val changed = entity.update(data, Instant.now())
        if (changed.isEmpty()) return entity.toView()
        val saved = save(entity)
        audit.record("critical_service.updated", SERVICE_ENTITY_TYPE, saved.id.toString(), mapOf("changed" to changed))
        return saved.toView()
    }

    @Transactional
    fun delete(id: UUID) {
        val entity = services.findOneById(id) ?: throw CriticalServiceNotFoundException()
        // Each linked asset changes (serviceIds): version and updatedAt go up and it gets its own audit entry, so a
        // PUT still holding the old version is a 409, not a 422 about a service that no longer exists.
        val now = Instant.now()
        val linked = assets.findAllByServicesId(entity.id).sortedBy { it.id }
        linked.forEach { asset ->
            asset.unlink(entity.id, now)
            assets.saveAndFlush(asset)
            audit.record("asset.updated", ASSET_ENTITY_TYPE, asset.id.toString(), mapOf("changed" to listOf("serviceIds")))
        }
        services.delete(entity)
        audit.record(
            "critical_service.deleted", SERVICE_ENTITY_TYPE, id.toString(), mapOf("unlinkedAssets" to linked.size),
        )
    }

    private fun validated(input: CriticalServiceInput, extra: List<FieldError> = emptyList()): CriticalServiceData =
        when (val result = InventoryValidator.service(input, extra)) {
            is Validation.Valid -> result.value
            is Validation.Invalid -> throw InvalidInventoryDataException(result.errors)
        }

    private fun save(entity: CriticalServiceEntity): CriticalServiceEntity =
        try {
            services.saveAndFlush(entity)
        } catch (e: DataIntegrityViolationException) {
            if (violatedConstraint(e) == SERVICE_NAME_CONSTRAINT) throw ServiceNameTakenException()
            throw e
        }
}
