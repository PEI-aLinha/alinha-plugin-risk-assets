package pt.alinha.riskassets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RiskAssetsPluginTest {
    @Test
    fun `manifest has the plugin id`() {
        assertEquals("risk-assets", RiskAssetsPlugin().manifest.id)
    }
}
