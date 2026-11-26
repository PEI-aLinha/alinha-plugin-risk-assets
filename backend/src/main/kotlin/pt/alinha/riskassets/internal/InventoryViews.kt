package pt.alinha.riskassets.internal

import pt.alinha.riskassets.internal.domain.Criticality
import pt.alinha.riskassets.internal.persistence.CriticalServiceEntity
import java.time.Instant
import java.util.UUID

data class CriticalServiceView(
    val id: UUID,
    val version: Long,
    val name: String,
    val description: String?,
    val owner: String,
    val criticality: Criticality,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class CriticalServicePage(val items: List<CriticalServiceView>, val page: Int, val pageSize: Int, val total: Long)

internal fun CriticalServiceEntity.toView() =
    CriticalServiceView(id, version, name, description, owner, criticality, createdAt, updatedAt)
