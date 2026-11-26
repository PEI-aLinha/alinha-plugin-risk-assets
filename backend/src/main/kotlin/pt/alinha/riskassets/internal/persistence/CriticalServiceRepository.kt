package pt.alinha.riskassets.internal.persistence

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface CriticalServiceRepository : JpaRepository<CriticalServiceEntity, UUID>
