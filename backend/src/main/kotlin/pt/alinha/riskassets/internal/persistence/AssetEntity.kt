package pt.alinha.riskassets.internal.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import org.hibernate.annotations.BatchSize
import pt.alinha.core.tenant.TenantOwnedEntity
import pt.alinha.riskassets.internal.domain.AssetData
import pt.alinha.riskassets.internal.domain.AssetType
import pt.alinha.riskassets.internal.domain.Criticality
import java.time.Instant
import java.util.UUID

@Entity
@Table(schema = "riskassets", name = "asset")
class AssetEntity(
    @Id val id: UUID,
    @Column(name = "created_at", nullable = false, updatable = false) val createdAt: Instant,
    data: AssetData,
    services: Set<CriticalServiceEntity>,
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
    @Enumerated(EnumType.STRING)
    var type: AssetType = data.type
        protected set
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    @ManyToMany
    @JoinTable(
        schema = "riskassets",
        name = "service_asset",
        joinColumns = [JoinColumn(name = "asset_id")],
        inverseJoinColumns = [JoinColumn(name = "service_id")],
    )
    @BatchSize(size = 100)
    var services: MutableSet<CriticalServiceEntity> = services.toMutableSet()
        protected set

    fun toData() = AssetData(name, type, description, owner, criticality, services.map { it.id }.toSet())

    /**
     * Applies [data] and the already loaded [services], and returns the names of the fields that changed
     * (`"serviceIds"` when the set of linked services changes). [data]'s own `serviceIds` is not read here.
     */
    fun update(data: AssetData, services: Set<CriticalServiceEntity>, at: Instant): List<String> {
        val changed = listOfNotNull(
            "name".takeIf { name != data.name },
            "type".takeIf { type != data.type },
            "description".takeIf { description != data.description },
            "owner".takeIf { owner != data.owner },
            "criticality".takeIf { criticality != data.criticality },
            "serviceIds".takeIf { this.services.map { it.id }.toSet() != services.map { it.id }.toSet() },
        )
        if (changed.isEmpty()) return changed
        name = data.name
        type = data.type
        description = data.description
        owner = data.owner
        criticality = data.criticality
        if ("serviceIds" in changed) {
            this.services.clear()
            this.services.addAll(services)
        }
        updatedAt = at
        return changed
    }

    /** Drops the link to a service that is being deleted; the collection change bumps [version] on flush. */
    fun unlink(serviceId: UUID, at: Instant) {
        if (services.removeIf { it.id == serviceId }) updatedAt = at
    }
}
