package pt.alinha.riskassets.internal.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import pt.alinha.core.access.RequiresPermission
import pt.alinha.riskassets.internal.AssetFilter
import pt.alinha.riskassets.internal.AssetInventory
import pt.alinha.riskassets.internal.AssetPage
import pt.alinha.riskassets.internal.AssetView
import pt.alinha.riskassets.internal.InvalidQueryException
import pt.alinha.riskassets.internal.domain.AssetInput
import pt.alinha.riskassets.internal.domain.AssetType
import pt.alinha.riskassets.internal.domain.Criticality
import java.util.UUID

/** Body of `PUT /assets/{id}`: the input plus the `version` that was read. */
data class AssetUpdate(
    val version: Long? = null,
    val name: String? = null,
    val description: String? = null,
    val owner: String? = null,
    val criticality: Criticality? = null,
    val type: AssetType? = null,
    val serviceIds: List<UUID>? = null,
) {
    fun toInput() = AssetInput(name, description, owner, criticality, type, serviceIds)
}

@RestController
@RequestMapping("/api/risk-assets/assets")
class AssetController(private val inventory: AssetInventory) {

    @RequiresPermission("asset", "read")
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(required = false) type: String?,
        @RequestParam(required = false) criticality: String?,
        @RequestParam(required = false) serviceId: UUID?,
    ): AssetPage {
        val filter = AssetFilter(
            type = type?.let { AssetType.fromJson(it) ?: throw InvalidQueryException("type") },
            criticality = criticality?.let { Criticality.fromJson(it) ?: throw InvalidQueryException("criticality") },
            serviceId = serviceId,
        )
        return inventory.list(page, pageSize, filter)
    }

    @RequiresPermission("asset", "read")
    @GetMapping("/{assetId}")
    fun get(@PathVariable("assetId") assetId: UUID): AssetView = inventory.get(assetId)

    @RequiresPermission("asset", "create")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody body: AssetInput): AssetView = inventory.create(body)

    @RequiresPermission("asset", "edit")
    @PutMapping("/{assetId}")
    fun update(@PathVariable("assetId") assetId: UUID, @RequestBody body: AssetUpdate): AssetView =
        inventory.update(assetId, body.toInput(), body.version)

    @RequiresPermission("asset", "delete")
    @DeleteMapping("/{assetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable("assetId") assetId: UUID) = inventory.delete(assetId)
}
