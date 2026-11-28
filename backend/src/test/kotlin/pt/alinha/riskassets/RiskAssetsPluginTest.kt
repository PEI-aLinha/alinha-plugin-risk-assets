package pt.alinha.riskassets

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
        assertThat(manifest.rolePermissions["analyst"].orEmpty()).isEmpty()
    }
}
