package pt.alinha.riskassets.internal.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface AssetRepository : JpaRepository<AssetEntity, UUID>, JpaSpecificationExecutor<AssetEntity>
