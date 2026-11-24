package pt.alinha.riskassets.internal.persistence

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.Repository
import java.util.UUID

/**
 * A plain Repository, not JpaRepository, so findById/getReferenceById do not exist: every lookup is a derived
 * query, which always carries Hibernate's tenant restriction. All queries see only the current tenant.
 */
interface RiskRepository : Repository<RiskEntity, UUID> {
    fun saveAndFlush(entity: RiskEntity): RiskEntity

    fun findOneById(id: UUID): RiskEntity?

    fun findAllBy(pageable: Pageable): Page<RiskEntity>
}
