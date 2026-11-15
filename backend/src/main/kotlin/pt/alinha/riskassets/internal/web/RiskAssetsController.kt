package pt.alinha.riskassets.internal.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import pt.alinha.core.access.RequiresPermission

@RestController
@RequestMapping("/api/risk-assets")
class RiskAssetsController {

    /** Walking skeleton: proves the plugin is wired end to end. Every role may call it (`plugins:read`). */
    @RequiresPermission("plugins")
    @GetMapping("/ping")
    fun ping() = mapOf("plugin" to "risk-assets", "status" to "ok")
}
