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
import pt.alinha.riskassets.internal.CriticalServiceCatalog
import pt.alinha.riskassets.internal.CriticalServicePage
import pt.alinha.riskassets.internal.CriticalServiceView
import pt.alinha.riskassets.internal.domain.CriticalServiceInput
import pt.alinha.riskassets.internal.domain.Criticality
import java.util.UUID

/** Body of `PUT /services/{id}`: the input plus the `version` that was read. */
data class CriticalServiceUpdate(
    val version: Long? = null,
    val name: String? = null,
    val description: String? = null,
    val owner: String? = null,
    val criticality: Criticality? = null,
) {
    fun toInput() = CriticalServiceInput(name, description, owner, criticality)
}

@RestController
@RequestMapping("/api/risk-assets/services")
class CriticalServiceController(private val catalog: CriticalServiceCatalog) {

    @RequiresPermission("critical-service", "read")
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
    ): CriticalServicePage = catalog.list(page, pageSize)

    @RequiresPermission("critical-service", "read")
    @GetMapping("/{serviceId}")
    fun get(@PathVariable("serviceId") serviceId: UUID): CriticalServiceView = catalog.get(serviceId)

    @RequiresPermission("critical-service", "create")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@RequestBody body: CriticalServiceInput): CriticalServiceView = catalog.create(body)

    @RequiresPermission("critical-service", "edit")
    @PutMapping("/{serviceId}")
    fun update(
        @PathVariable("serviceId") serviceId: UUID,
        @RequestBody body: CriticalServiceUpdate,
    ): CriticalServiceView = catalog.update(serviceId, body.toInput(), body.version)

    @RequiresPermission("critical-service", "delete")
    @DeleteMapping("/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable("serviceId") serviceId: UUID) = catalog.delete(serviceId)
}
