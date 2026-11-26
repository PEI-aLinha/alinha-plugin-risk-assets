package pt.alinha.riskassets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import pt.alinha.pluginapi.NavArea
import pt.alinha.pluginapi.NavEntry

class RiskAssetsPluginTest {
    @Test
    fun `manifest has the plugin id`() {
        assertEquals("risk-assets", RiskAssetsPlugin().manifest.id)
    }

    @Test
    fun `roles follow the prototype's matrix`() {
        val m = RiskAssetsPlugin().manifest
        assertEquals(setOf("AssetRegistered"), m.publishes)
        assertEquals(m.permissions, m.rolePermissions.getValue("responsible"))
        assertEquals(setOf("asset:read", "asset:create", "asset:edit", "critical-service:read"), m.rolePermissions.getValue("analyst"))
        assertEquals(setOf("asset:read", "critical-service:read"), m.rolePermissions.getValue("consultant"))
        assertEquals(listOf(NavEntry("Ativos", "assets", NavArea.CLIENT, resource = "asset", order = 50)), m.nav)
    }
}
