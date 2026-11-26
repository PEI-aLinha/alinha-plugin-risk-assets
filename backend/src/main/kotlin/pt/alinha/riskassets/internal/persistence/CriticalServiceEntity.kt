package pt.alinha.riskassets.internal.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import pt.alinha.core.tenant.TenantOwnedEntity
import pt.alinha.riskassets.internal.domain.Criticality
import pt.alinha.riskassets.internal.domain.CriticalServiceData
import java.time.Instant
import java.util.UUID

@Entity
@Table(schema = "riskassets", name = "critical_service")
class CriticalServiceEntity(
    @Id val id: UUID,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant,
    data: CriticalServiceData,
) : TenantOwnedEntity() {

    @Version
    var version: Long = 0
        protected set

    var name: String = data.name
        protected set
    var description: String? = data.description
        protected set
    var owner: String = data.owner
        protected set
    @Enumerated(EnumType.STRING)
    var criticality: Criticality = data.criticality
        protected set
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    fun toData() = CriticalServiceData(name, description, owner, criticality)

    /** Applies [data] and returns the names of the fields that changed (for the audit trail, no values). */
    fun update(data: CriticalServiceData, at: Instant): List<String> {
        val changed = listOfNotNull(
            "name".takeIf { name != data.name },
            "description".takeIf { description != data.description },
            "owner".takeIf { owner != data.owner },
            "criticality".takeIf { criticality != data.criticality },
        )
        if (changed.isEmpty()) return changed
        name = data.name
        description = data.description
        owner = data.owner
        criticality = data.criticality
        updatedAt = at
        return changed
    }
}
