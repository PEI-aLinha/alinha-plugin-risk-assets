package pt.alinha.riskassets.internal.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class RiskValidatorTest {
    private val matrix = RiskMatrix()
    private val valid = RiskInput(threat = "  Ransomware cifra o servidor  ", vulnerability = "  ", likelihood = 3, impact = 2)

    private fun errorsOf(input: RiskInput) =
        (RiskValidator.validate(input, matrix) as Validation.Invalid).errors.map { it.field }

    @Test
    fun `trims text and drops a blank vulnerability`() {
        val risk = (RiskValidator.validate(valid, matrix) as Validation.Valid).risk
        assertThat(risk.threat).isEqualTo("Ransomware cifra o servidor")
        assertThat(risk.vulnerability).isNull()
        assertThat(risk.score).isEqualTo(6)
    }

    @Test
    fun `reads the treatment in the contract's lowercase`() {
        val risk = (RiskValidator.validate(valid.copy(treatment = "transfer"), matrix) as Validation.Valid).risk
        assertThat(risk.treatment).isEqualTo(RiskTreatment.TRANSFER)
    }

    @Test
    fun `keeps the asset and the owner`() {
        val asset = UUID.randomUUID()
        val owner = UUID.randomUUID()
        val risk = (RiskValidator.validate(valid.copy(assetId = asset, ownerId = owner), matrix) as Validation.Valid).risk
        assertThat(risk.assetId).isEqualTo(asset)
        assertThat(risk.ownerId).isEqualTo(owner)
    }

    @Test
    fun `reports every missing field at once`() {
        assertThat(errorsOf(RiskInput())).containsExactly("threat", "likelihood", "impact")
    }

    @Test
    fun `refuses values off the scale`() {
        assertThat(errorsOf(valid.copy(likelihood = 4, impact = 0))).containsExactly("likelihood", "impact")
    }

    @Test
    fun `refuses an unknown treatment, and the enum name in uppercase`() {
        assertThat(errorsOf(valid.copy(treatment = "ignore"))).containsExactly("treatment")
        assertThat(errorsOf(valid.copy(treatment = "MITIGATE"))).containsExactly("treatment")
    }

    @Test
    fun `refuses text that is too long`() {
        assertThat(errorsOf(valid.copy(threat = "a".repeat(THREAT_MAX + 1), vulnerability = "b".repeat(VULNERABILITY_MAX + 1))))
            .containsExactly("threat", "vulnerability")
    }
}
