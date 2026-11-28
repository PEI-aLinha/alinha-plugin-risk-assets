package pt.alinha.riskassets

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import pt.alinha.pluginapi.NavArea
import pt.alinha.pluginapi.NavEntry
import java.io.File

class RiskAssetsPluginTest {
    private val manifest = RiskAssetsPlugin().manifest
    private val pluginYaml = File("../plugin.yaml").readText()

    @Test
    fun `manifest has the plugin id`() {
        assertThat(manifest.id).isEqualTo("risk-assets")
    }

    @Test
    fun `plugin yaml lists the same events and permissions as the manifest`() {
        manifest.publishes.forEach { assertThat(pluginYaml).contains(it) }
        manifest.permissions.forEach { assertThat(pluginYaml).contains("- $it") }
    }

    @Test
    fun `only consultants edit risks and analysts cannot see them`() {
        manifest.rolePermissions.filterKeys { it != "consultant" }.values
            .forEach { assertThat(it).doesNotContain(RiskAssetsPlugin.RISK_EDIT) }
        assertThat(manifest.rolePermissions["analyst"].orEmpty())
            .doesNotContain(RiskAssetsPlugin.RISK_READ, RiskAssetsPlugin.RISK_EDIT)
    }

    @Test
    fun `roles follow the prototype's matrix`() {
        assertThat(manifest.publishes).isEqualTo(setOf("AssetRegistered", "RiskRegistered"))
        assertThat(manifest.rolePermissions.getValue("responsible"))
            .isEqualTo(manifest.permissions - RiskAssetsPlugin.RISK_EDIT)
        assertThat(manifest.rolePermissions.getValue("analyst"))
            .isEqualTo(setOf("asset:read", "asset:create", "asset:edit", "critical-service:read"))
        assertThat(manifest.rolePermissions.getValue("consultant"))
            .isEqualTo(setOf("asset:read", "critical-service:read", "risk:read", "risk:edit"))
        assertThat(manifest.nav)
            .isEqualTo(listOf(NavEntry("Ativos", "assets", NavArea.CLIENT, resource = "asset", order = 50)))
    }
}
