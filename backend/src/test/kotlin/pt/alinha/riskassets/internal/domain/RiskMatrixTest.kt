package pt.alinha.riskassets.internal.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class RiskMatrixTest {
    private val matrix = RiskMatrix()

    @ParameterizedTest(name = "likelihood {0} × impact {1} is {2}")
    @CsvSource(
        "1, 1, LOW", "1, 2, LOW", "2, 1, LOW",
        "1, 3, MEDIUM", "3, 1, MEDIUM", "2, 2, MEDIUM",
        "2, 3, HIGH", "3, 2, HIGH", "3, 3, HIGH",
    )
    fun `the default 3x3 matrix gives every cell a level`(likelihood: Int, impact: Int, level: RiskLevel) {
        assertThat(matrix.levelOf(likelihood * impact)).isEqualTo(level)
    }

    @Test
    fun `a 5x5 scale only needs new configuration`() {
        val five = (1..5).map { ScaleStep(it, "$it", "") }
        val bigger = RiskMatrix(
            likelihood = five, impact = five,
            bands = listOf(RiskBand(RiskLevel.LOW, 1, 4), RiskBand(RiskLevel.MEDIUM, 5, 12), RiskBand(RiskLevel.HIGH, 13, 25)),
        )
        assertThat(bigger.levelOf(4 * 4)).isEqualTo(RiskLevel.HIGH)
        assertThat(bigger.isLikelihood(5)).isTrue()
    }

    @Test
    fun `risks saved under a larger scale still get a level after the scale shrinks`() {
        // Scores a 5x5 matrix produced, read with the default 3x3: none of them may fail.
        assertThat(matrix.levelOf(25)).isEqualTo(RiskLevel.HIGH)
        assertThat(matrix.levelOf(12)).isEqualTo(RiskLevel.HIGH)
        assertThat(matrix.levelOf(5)).isEqualTo(RiskLevel.MEDIUM)
        assertThat(matrix.levelOf(0)).isEqualTo(RiskLevel.LOW)
    }

    @Test
    fun `a score with no band fails at startup`() {
        assertThatThrownBy { RiskMatrix(bands = listOf(RiskBand(RiskLevel.LOW, 1, 2), RiskBand(RiskLevel.HIGH, 6, 9))) }
            .hasMessageContaining("score 3 is in 0 bands")
    }

    @Test
    fun `overlapping bands fail at startup`() {
        assertThatThrownBy {
            RiskMatrix(bands = listOf(RiskBand(RiskLevel.LOW, 1, 3), RiskBand(RiskLevel.MEDIUM, 3, 4), RiskBand(RiskLevel.HIGH, 6, 9)))
        }.hasMessageContaining("score 3 is in 2 bands")
    }

    @Test
    fun `a higher score with a lower level fails at startup`() {
        assertThatThrownBy {
            RiskMatrix(bands = listOf(RiskBand(RiskLevel.HIGH, 1, 2), RiskBand(RiskLevel.MEDIUM, 3, 4), RiskBand(RiskLevel.LOW, 6, 9)))
        }.hasMessageContaining("never get a lower level")
    }

    @Test
    fun `a scale value above the cap fails at startup`() {
        assertThatThrownBy { RiskMatrix(likelihood = listOf(ScaleStep(1, "", ""), ScaleStep(RiskMatrix.MAX_SCALE_VALUE + 1, "", ""))) }
            .hasMessageContaining("must be 1..${RiskMatrix.MAX_SCALE_VALUE}")
    }

    @Test
    fun `a scale out of order fails at startup`() {
        assertThatThrownBy { RiskMatrix(likelihood = listOf(ScaleStep(2, "", ""), ScaleStep(1, "", ""))) }
            .hasMessageContaining("ascending")
    }
}
