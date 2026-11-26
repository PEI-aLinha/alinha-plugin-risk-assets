package pt.alinha.riskassets.internal.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.Repository
import org.springframework.data.repository.query.Param
import java.util.UUID

/**
 * A plain Repository, not JpaRepository, so findById/getReferenceById/existsById/deleteById do not exist: every
 * lookup is a derived or JPQL query, which always carries Hibernate's tenant restriction.
 */
interface CriticalServiceRepository : Repository<CriticalServiceEntity, UUID> {
    fun saveAndFlush(entity: CriticalServiceEntity): CriticalServiceEntity

    fun delete(entity: CriticalServiceEntity)

    fun findOneById(id: UUID): CriticalServiceEntity?

    fun findAllBy(pageable: Pageable): Page<CriticalServiceEntity>

    /** Assets linked to a service (JPQL, so the tenant restriction applies). */
    @Query("select count(a) from AssetEntity a join a.services s where s.id = :id")
    fun countLinkedAssets(@Param("id") id: UUID): Long
}
