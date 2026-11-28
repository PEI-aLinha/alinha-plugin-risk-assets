package pt.alinha.riskassets.internal.domain

import java.util.UUID

/** ISO/IEC 27005 risk treatment options. */
enum class RiskTreatment { MITIGATE, ACCEPT, TRANSFER, AVOID }

/** A risk's data as the client sends it (`RiskInput` in the contract), before validation. */
data class RiskInput(
    val assetId: UUID? = null,
    val threat: String? = null,
    val vulnerability: String? = null,
    val likelihood: Int? = null,
    val impact: Int? = null,
    val treatment: String? = null,
    val ownerId: UUID? = null,
)

/** A valid risk: trimmed text, values of the matrix's scales. */
data class RiskData(
    val assetId: UUID?,
    val threat: String,
    val vulnerability: String?,
    val likelihood: Int,
    val impact: Int,
    val treatment: RiskTreatment?,
    val ownerId: UUID?,
) {
    val score: Int get() = likelihood * impact
}

const val THREAT_MAX = 200
const val VULNERABILITY_MAX = 500

object RiskValidator {
    /**
     * [stored] is the risk being updated, if any. Its likelihood and impact are accepted as they are even when the
     * current scale no longer has them (the matrix was made smaller), so the risk's other fields can still be changed;
     * a new value must be on the current scale.
     */
    fun validate(
        input: RiskInput,
        matrix: RiskMatrix,
        extraErrors: List<FieldError> = emptyList(),
        stored: RiskData? = null,
    ): Validation<RiskData> {
        val errors = extraErrors.toMutableList()

        val threat = input.threat?.trim().orEmpty()
        when {
            threat.isEmpty() -> errors += FieldError("threat", "Descreva o que pode acontecer.")
            threat.length > THREAT_MAX -> errors += FieldError("threat", "Use no máximo $THREAT_MAX caracteres.")
        }

        val vulnerability = input.vulnerability?.trim()?.ifEmpty { null }
        if (vulnerability != null && vulnerability.length > VULNERABILITY_MAX) {
            errors += FieldError("vulnerability", "Use no máximo $VULNERABILITY_MAX caracteres.")
        }

        when {
            input.likelihood == null -> errors += FieldError("likelihood", "Escolha a probabilidade.")
            !matrix.isLikelihood(input.likelihood) && input.likelihood != stored?.likelihood ->
                errors += FieldError("likelihood", NOT_ON_SCALE)
        }
        when {
            input.impact == null -> errors += FieldError("impact", "Escolha o impacto.")
            !matrix.isImpact(input.impact) && input.impact != stored?.impact -> errors += FieldError("impact", NOT_ON_SCALE)
        }

        val treatment = input.treatment?.let { wire ->
            RiskTreatment.entries.firstOrNull { it.name.lowercase() == wire }
                ?: null.also { errors += FieldError("treatment", "Escolha uma das opções de tratamento.") }
        }

        if (errors.isNotEmpty()) return Validation.Invalid(errors)
        return Validation.Valid(
            RiskData(input.assetId, threat, vulnerability, input.likelihood!!, input.impact!!, treatment, input.ownerId),
        )
    }

    private const val NOT_ON_SCALE = "Escolha um dos valores da escala."
}
