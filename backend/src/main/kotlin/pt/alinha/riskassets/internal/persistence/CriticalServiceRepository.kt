package pt.alinha.riskassets.internal.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

/** Derived queries (not `findById`) so every lookup carries Hibernate's tenant restriction. */
interface CriticalServiceRepository : JpaRepository<CriticalServiceEntity, UUID> {
    fun findOneById(id: UUID): CriticalServiceEntity?

    fun findAllBy(pageable: Pageable): Page<CriticalServiceEntity>

    /** Assets linked to a service (JPQL, so the tenant restriction applies). */
    @Query("select count(a) from AssetEntity a join a.services s where s.id = :id")
    fun countLinkedAssets(@Param("id") id: UUID): Long
}
