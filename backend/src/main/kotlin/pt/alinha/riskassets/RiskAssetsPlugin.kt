package pt.alinha.riskassets

import org.springframework.stereotype.Component
import pt.alinha.pluginapi.AlinhaPlugin
import pt.alinha.pluginapi.PluginKind
import pt.alinha.pluginapi.PluginManifest

/** Registers the plugin in the core's Module Registry. Keep in sync with plugin.yaml. */
@Component
class RiskAssetsPlugin : AlinhaPlugin {
    override val manifest = PluginManifest(
        id = "risk-assets",
        name = "Risk & Assets",
        kind = PluginKind.FEATURE,
        version = "0.1.0",
        publishes = setOf("RiskRegistered"),
        consumes = setOf(),
        permissions = setOf(RISK_READ, RISK_EDIT),
        rolePermissions = mapOf(
            // The consultant runs the risk analysis; the Designated Officer follows it. Analysts get no access:
            // the register shows the organization's weaknesses (least privilege, docs/rbac.md).
            "consultant" to setOf(RISK_READ, RISK_EDIT),
            "responsible" to setOf(RISK_READ),
        ),
    )

    companion object {
        const val RISK_READ = "risk:read"
        const val RISK_EDIT = "risk:edit"
    }
}
