package pt.alinha.riskassets.internal

import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pt.alinha.core.audit.AuditTrail
import pt.alinha.core.tenant.CorrelationContext
import pt.alinha.core.tenant.TenantContext
import pt.alinha.riskassets.events.RiskRegistered
import pt.alinha.riskassets.internal.domain.FieldError
import pt.alinha.riskassets.internal.domain.RiskData
import pt.alinha.riskassets.internal.domain.RiskInput
import pt.alinha.riskassets.internal.domain.RiskMatrix
import pt.alinha.riskassets.internal.domain.RiskValidator
import pt.alinha.riskassets.internal.domain.Validation
import pt.alinha.riskassets.internal.persistence.RiskEntity
import pt.alinha.riskassets.internal.persistence.RiskRepository
import java.time.Instant
import java.util.UUID

internal const val RISK_ENTITY_TYPE = "risk"

/** Highest score first; among equal scores, the one changed most recently. `id` keeps pages stable. */
private val REGISTER_ORDER = Sort.by(Sort.Order.desc("score"), Sort.Order.desc("updatedAt"), Sort.Order.asc("id"))

/** The organization's ISO/IEC 27005 risk register (risk-assets#4, FR36). */
@Service
@EnableConfigurationProperties(RiskMatrix::class)
class RiskService(
    private val risks: RiskRepository,
    private val matrix: RiskMatrix,
    private val audit: AuditTrail,
    private val events: ApplicationEventPublisher,
    private val tenants: TenantContext,
    private val correlation: CorrelationContext,
) {
    fun matrix(): RiskMatrixView = matrix.toView()

    @Transactional(readOnly = true)
    fun list(page: Int, pageSize: Int): RiskPageView {
        val result = risks.findAllBy(PageRequest.of(page, pageSize, REGISTER_ORDER))
        return RiskPageView(result.content.map { it.toView(matrix) }, page, pageSize, result.totalElements)
    }

    @Transactional(readOnly = true)
    fun get(id: UUID): RiskView = (risks.findOneById(id) ?: throw RiskNotFoundException()).toView(matrix)

    /** Adds a risk, audits it and publishes [RiskRegistered], all in one transaction. */
    @Transactional
    fun register(input: RiskInput): RiskView {
        val risk = validated(input)
        val now = Instant.now()
        val entity = risks.saveAndFlush(RiskEntity(UUID.randomUUID(), now, risk))
        val level = matrix.levelOf(entity.score)
        audit.record(
            "risk.registered", RISK_ENTITY_TYPE, entity.id.toString(),
            mapOf("assetId" to entity.assetId?.toString(), "level" to level.name.lowercase()),
        )
        events.publishEvent(RiskRegistered(tenants.current(), correlation.current(), entity.id, now))
        return entity.toView(matrix)
    }

    /**
     * Replaces a risk's data. [version] must match, so a stale form cannot overwrite changes. Sending the saved data
     * again changes nothing and writes no audit entry.
     */
    @Transactional
    fun update(id: UUID, input: RiskInput, version: Long?): RiskView {
        // Looked up first: another organization's id is a 404 whatever the data.
        val entity = risks.findOneById(id) ?: throw RiskNotFoundException()
        // Then the version: a stale form is a 409 even if it also has invalid fields, since its data is outdated anyway.
        if (version != null && version != entity.version) throw StaleRiskVersionException()
        val missingVersion = if (version == null) {
            listOf(FieldError("version", "Recarregue a página e volte a fazer as alterações."))
        } else {
            emptyList()
        }
        // A value saved under an older, larger scale may be kept as it is; only a new value must be on the scale.
        val risk = validated(input, missingVersion, stored = entity.toData())
        val levelBefore = matrix.levelOf(entity.score)
        val changed = entity.update(risk, Instant.now())
        if (changed.isEmpty()) return entity.toView(matrix)
        val saved = risks.saveAndFlush(entity)
        val levelAfter = matrix.levelOf(saved.score)
        val details = buildMap {
            put("changed", changed)
            if (levelAfter != levelBefore) {
                put("levelBefore", levelBefore.name.lowercase())
                put("level", levelAfter.name.lowercase())
            }
        }
        audit.record("risk.updated", RISK_ENTITY_TYPE, saved.id.toString(), details)
        return saved.toView(matrix)
    }

    private fun validated(input: RiskInput, extraErrors: List<FieldError> = emptyList(), stored: RiskData? = null): RiskData =
        when (val result = RiskValidator.validate(input, matrix, extraErrors, stored)) {
            is Validation.Valid -> result.value
            is Validation.Invalid -> throw InvalidRiskException(result.errors)
        }
}
