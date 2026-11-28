package pt.alinha.riskassets.internal.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Lock
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

    /**
     * The service locked for deletion (`FOR NO KEY UPDATE`): an asset write that links it waits in [findAllByIdIn]
     * until the delete commits and then no longer finds it, and the delete sees every link committed before it.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findLockedById(id: UUID): CriticalServiceEntity?

    /**
     * Tenant-filtered load of the services an asset links to: the guard against another organization's ids.
     * `FOR SHARE`, so a concurrent delete waits until the links are committed instead of breaking their foreign key.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    fun findAllByIdIn(ids: Collection<UUID>): List<CriticalServiceEntity>

    fun findAllBy(pageable: Pageable): Page<CriticalServiceEntity>
}
