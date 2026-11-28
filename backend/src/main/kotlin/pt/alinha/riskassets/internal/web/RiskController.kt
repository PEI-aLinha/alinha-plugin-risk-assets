package pt.alinha.riskassets.internal.web

import org.springframework.http.HttpStatus
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
import pt.alinha.riskassets.internal.RiskMatrixView
import pt.alinha.riskassets.internal.RiskPageView
import pt.alinha.riskassets.internal.RiskService
import pt.alinha.riskassets.internal.RiskView
import pt.alinha.riskassets.internal.domain.RiskInput
import java.util.UUID

/** Body of `PUT /risks/{id}` (`RiskUpdate`): the risk's data plus the version the form was loaded with. */
data class RiskUpdate(
    val version: Long? = null,
    val assetId: UUID? = null,
    val threat: String? = null,
    val vulnerability: String? = null,
    val likelihood: Int? = null,
    val impact: Int? = null,
    val treatment: String? = null,
    val ownerId: UUID? = null,
) {
    fun toInput() = RiskInput(assetId, threat, vulnerability, likelihood, impact, treatment, ownerId)
}

@RestController
@RequestMapping("/api/risk-assets")
class RiskController(private val risks: RiskService) {

    @RequiresPermission("risk", "read")
    @GetMapping("/risk-matrix")
    fun matrix() = risks.matrix()

    @RequiresPermission("risk", "read")
    @GetMapping("/risks")
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
    ): RiskPageView {
        if (page < 0 || pageSize !in 1..MAX_PAGE_SIZE) throw InvalidPageException()
        return risks.list(page, pageSize)
    }

    @RequiresPermission("risk", "edit")
    @PostMapping("/risks")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@RequestBody input: RiskInput): RiskView = risks.register(input)

    @RequiresPermission("risk", "read")
    @GetMapping("/risks/{riskId}")
    fun get(@PathVariable riskId: UUID): RiskView = risks.get(riskId)

    @RequiresPermission("risk", "edit")
    @PutMapping("/risks/{riskId}")
    fun update(@PathVariable riskId: UUID, @RequestBody body: RiskUpdate): RiskView =
        risks.update(riskId, body.toInput(), body.version)
}

internal const val MAX_PAGE_SIZE = 100

class InvalidPageException : RuntimeException("Invalid page parameters")
