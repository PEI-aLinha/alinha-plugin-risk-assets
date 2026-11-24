package pt.alinha.riskassets.internal.domain

import org.springframework.boot.context.properties.ConfigurationProperties

enum class RiskLevel { LOW, MEDIUM, HIGH }

data class ScaleStep(val value: Int, val label: String, val description: String)

data class RiskBand(val level: RiskLevel, val minScore: Int, val maxScore: Int)

/**
 * ISO/IEC 27005 risk matrix (risk-assets#4). A risk's score is `likelihood × impact`, and its level is the band that
 * contains the score. The scale is configuration (`alinha.risk-assets.matrix`), so moving to 5×5 needs no code
 * change. The default is the prototype's 3×3 (decision D2). The level is computed on every read and never stored,
 * so changing the bands reclassifies every risk at once.
 *
 * Invalid configuration fails at startup: the scales must be ascending positive values, and the bands must give
 * every possible score exactly one level, never a lower level to a higher score.
 */
@ConfigurationProperties("alinha.risk-assets.matrix")
data class RiskMatrix(
    val likelihood: List<ScaleStep> = DEFAULT_LIKELIHOOD,
    val impact: List<ScaleStep> = DEFAULT_IMPACT,
    val bands: List<RiskBand> = DEFAULT_BANDS,
) {
    init {
        requireScale("likelihood", likelihood)
        requireScale("impact", impact)
        val scores = likelihood.flatMap { l -> impact.map { i -> l.value * i.value } }.toSortedSet()
        scores.forEach { score ->
            val matching = bands.filter { score in it.minScore..it.maxScore }
            require(matching.size == 1) {
                "Risk matrix: score $score is in ${matching.size} bands, it must be in exactly one"
            }
        }
        val levelsByScore = scores.map { levelOf(it) }
        require(levelsByScore.zipWithNext().all { (a, b) -> a <= b }) {
            "Risk matrix: a higher score must never get a lower level"
        }
    }

    fun isLikelihood(value: Int) = likelihood.any { it.value == value }

    fun isImpact(value: Int) = impact.any { it.value == value }

    /** The level of a score the scales can produce. */
    fun levelOf(score: Int): RiskLevel =
        bands.single { score in it.minScore..it.maxScore }.level

    private fun requireScale(name: String, steps: List<ScaleStep>) {
        require(steps.isNotEmpty()) { "Risk matrix: the $name scale is empty" }
        require(steps.all { it.value >= 1 }) { "Risk matrix: $name values start at 1" }
        require(steps.zipWithNext().all { (a, b) -> a.value < b.value }) {
            "Risk matrix: $name values must be ascending and distinct"
        }
    }

    companion object {
        val DEFAULT_LIKELIHOOD = listOf(
            ScaleStep(1, "Baixa", "Pouco provável: não aconteceu nos últimos anos."),
            ScaleStep(2, "Média", "Pode acontecer: já aconteceu a empresas parecidas."),
            ScaleStep(3, "Alta", "Provável: há sinais ou já aconteceu cá."),
        )
        val DEFAULT_IMPACT = listOf(
            ScaleStep(1, "Baixo", "Incómodo, resolve-se no próprio dia."),
            ScaleStep(2, "Médio", "Atrasa o trabalho ou custa dinheiro, sem parar a empresa."),
            ScaleStep(3, "Alto", "Para a produção ou a faturação, ou expõe dados pessoais."),
        )
        val DEFAULT_BANDS = listOf(
            RiskBand(RiskLevel.LOW, 1, 2),
            RiskBand(RiskLevel.MEDIUM, 3, 4),
            RiskBand(RiskLevel.HIGH, 6, 9),
        )
    }
}
