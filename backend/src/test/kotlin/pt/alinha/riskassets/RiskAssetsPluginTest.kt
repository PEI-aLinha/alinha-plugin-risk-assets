package pt.alinha.riskassets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RiskAssetsPluginTest {
    @Test
    fun `manifest tem o id do plugin`() {
        assertEquals("risk-assets", RiskAssetsPlugin().manifest.id)
    }
}
