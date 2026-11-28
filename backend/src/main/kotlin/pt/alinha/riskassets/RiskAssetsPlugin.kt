package pt.alinha.riskassets

import org.springframework.stereotype.Component
import pt.alinha.pluginapi.AlinhaPlugin
import pt.alinha.pluginapi.NavArea
import pt.alinha.pluginapi.NavEntry
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
        publishes = setOf("AssetRegistered", "RiskRegistered"),
        consumes = setOf(),
        permissions = setOf(
            ASSET_READ, ASSET_CREATE, ASSET_EDIT, ASSET_DELETE,
            SERVICE_READ, SERVICE_CREATE, SERVICE_EDIT, SERVICE_DELETE,
            RISK_READ, RISK_EDIT,
        ),
        rolePermissions = mapOf(
            // The consultant runs the risk analysis; the Designated Officer follows it. Analysts get no risk
            // access: the register shows the organization's weaknesses (least privilege, docs/rbac.md).
            "responsible" to setOf(
                ASSET_READ, ASSET_CREATE, ASSET_EDIT, ASSET_DELETE,
                SERVICE_READ, SERVICE_CREATE, SERVICE_EDIT, SERVICE_DELETE,
                RISK_READ,
            ),
            "analyst" to setOf(ASSET_READ, ASSET_CREATE, ASSET_EDIT, SERVICE_READ),
            "consultant" to setOf(ASSET_READ, SERVICE_READ, RISK_READ, RISK_EDIT),
        ),
        nav = listOf(NavEntry("Ativos", "assets", NavArea.CLIENT, resource = "asset", order = 50)),
    )

    companion object {
        const val ASSET_READ = "asset:read"
        const val ASSET_CREATE = "asset:create"
        const val ASSET_EDIT = "asset:edit"
        const val ASSET_DELETE = "asset:delete"
        const val SERVICE_READ = "critical-service:read"
        const val SERVICE_CREATE = "critical-service:create"
        const val SERVICE_EDIT = "critical-service:edit"
        const val SERVICE_DELETE = "critical-service:delete"
        const val RISK_READ = "risk:read"
        const val RISK_EDIT = "risk:edit"
    }
}
