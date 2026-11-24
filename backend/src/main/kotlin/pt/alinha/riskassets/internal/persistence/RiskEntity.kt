package pt.alinha.riskassets.internal.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import pt.alinha.core.tenant.TenantOwnedEntity
import pt.alinha.riskassets.internal.domain.RiskData
import pt.alinha.riskassets.internal.domain.RiskTreatment
import java.time.Instant
import java.util.UUID

@Entity
@Table(schema = "riskassets", name = "risk")
class RiskEntity(
    @Id val id: UUID,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant,
    risk: RiskData,
) : TenantOwnedEntity() {

    @Version
    var version: Long = 0
        protected set

    @Column(name = "asset_id")
    var assetId: UUID? = risk.assetId
        protected set
    var threat: String = risk.threat
        protected set
    var vulnerability: String? = risk.vulnerability
        protected set
    var likelihood: Int = risk.likelihood
        protected set
    var impact: Int = risk.impact
        protected set
    /** Always `likelihood × impact` (checked by the table); stored so the register can be sorted by it. */
    var score: Int = risk.score
        protected set
    @Enumerated(EnumType.STRING)
    var treatment: RiskTreatment? = risk.treatment
        protected set
    @Column(name = "owner_id")
    var ownerId: UUID? = risk.ownerId
        protected set
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    fun toData() = RiskData(assetId, threat, vulnerability, likelihood, impact, treatment, ownerId)

    /** Applies [risk] and returns the names of the fields that changed (for the audit trail, no values). */
    fun update(risk: RiskData, at: Instant): List<String> {
        val before = toData()
        val changed = listOfNotNull(
            "assetId".takeIf { before.assetId != risk.assetId },
            "threat".takeIf { before.threat != risk.threat },
            "vulnerability".takeIf { before.vulnerability != risk.vulnerability },
            "likelihood".takeIf { before.likelihood != risk.likelihood },
            "impact".takeIf { before.impact != risk.impact },
            "treatment".takeIf { before.treatment != risk.treatment },
            "ownerId".takeIf { before.ownerId != risk.ownerId },
        )
        if (changed.isEmpty()) return changed
        assetId = risk.assetId
        threat = risk.threat
        vulnerability = risk.vulnerability
        likelihood = risk.likelihood
        impact = risk.impact
        score = risk.score
        treatment = risk.treatment
        ownerId = risk.ownerId
        updatedAt = at
        return changed
    }
}
