package pt.alinha.riskassets.internal.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.Repository
import java.util.UUID

/**
 * A plain Repository, not JpaRepository, so findById/getReferenceById/existsById/deleteById do not exist: every
 * lookup is a derived or JPQL query, which always carries Hibernate's tenant restriction.
 */
interface CriticalServiceRepository : Repository<CriticalServiceEntity, UUID> {
    fun saveAndFlush(entity: CriticalServiceEntity): CriticalServiceEntity

    fun delete(entity: CriticalServiceEntity)

    fun findOneById(id: UUID): CriticalServiceEntity?

    /** Tenant-filtered load of the services an asset links to: the guard against another organization's ids. */
    fun findAllByIdIn(ids: Collection<UUID>): List<CriticalServiceEntity>

    fun findAllBy(pageable: Pageable): Page<CriticalServiceEntity>
}
