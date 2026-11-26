package pt.alinha.riskassets.internal.persistence

import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.repository.Repository
import java.util.UUID

/**
 * A plain Repository, not JpaRepository, so findById/getReferenceById/existsById/deleteById do not exist: every
 * lookup is a derived query or a criteria query (JpaSpecificationExecutor), which always carry Hibernate's tenant
 * restriction.
 */
interface AssetRepository : Repository<AssetEntity, UUID>, JpaSpecificationExecutor<AssetEntity> {
    fun saveAndFlush(entity: AssetEntity): AssetEntity

    fun delete(entity: AssetEntity)

    fun findOneById(id: UUID): AssetEntity?
}
