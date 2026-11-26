package pt.alinha.riskassets.internal

import pt.alinha.riskassets.internal.domain.RiskBand
import pt.alinha.riskassets.internal.domain.RiskMatrix
import pt.alinha.riskassets.internal.domain.ScaleStep
import pt.alinha.riskassets.internal.persistence.RiskEntity
import java.time.Instant
import java.util.UUID

/** `Risk` in the contract. Enum values go out in lowercase, as the contract lists them. */
data class RiskView(
    val id: UUID,
    val version: Long,
    val assetId: UUID?,
    val threat: String,
    val vulnerability: String?,
    val likelihood: Int,
    val impact: Int,
    val score: Int,
    val level: String,
    val treatment: String?,
    val ownerId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class RiskPageView(val items: List<RiskView>, val page: Int, val pageSize: Int, val total: Long)

data class RiskBandView(val level: String, val minScore: Int, val maxScore: Int)

data class RiskMatrixView(val likelihood: List<ScaleStep>, val impact: List<ScaleStep>, val bands: List<RiskBandView>)

internal fun RiskEntity.toView(matrix: RiskMatrix) = RiskView(
    id = id,
    version = checkNotNull(version) { "Risk $id was not saved yet" },
    assetId = assetId,
    threat = threat,
    vulnerability = vulnerability,
    likelihood = likelihood,
    impact = impact,
    score = score,
    level = matrix.levelOf(score).name.lowercase(),
    treatment = treatment?.name?.lowercase(),
    ownerId = ownerId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun RiskMatrix.toView() = RiskMatrixView(
    likelihood = likelihood,
    impact = impact,
    bands = bands.sortedBy(RiskBand::minScore).map { RiskBandView(it.level.name.lowercase(), it.minScore, it.maxScore) },
)
