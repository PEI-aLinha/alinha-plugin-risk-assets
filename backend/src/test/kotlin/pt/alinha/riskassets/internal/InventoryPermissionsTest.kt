package pt.alinha.riskassets.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request
import java.util.UUID

/** Every endpoint of the inventory against every role (docs/rbac.md). A 403 writes no audit row. */
@SpringBootTest(classes = [RiskAssetsTestApplication::class])
@AutoConfigureMockMvc
class InventoryPermissionsTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = RiskAssetsDatabase.register(registry)
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate

    private fun auditCount(tenant: UUID) =
        jdbc.queryForObject("select count(*) from audit.audit_event where tenant_id = ?", Long::class.java, tenant)

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @CsvSource(
        "responsible,GET,/services,200", "responsible,POST,/services,201", "responsible,GET,/services/{id},200",
        "responsible,PUT,/services/{id},200", "responsible,DELETE,/services/{id},204",
        "responsible,GET,/assets,200", "responsible,POST,/assets,201", "responsible,GET,/assets/{id},200",
        "responsible,PUT,/assets/{id},200", "responsible,DELETE,/assets/{id},204",

        "analyst,GET,/services,200", "analyst,POST,/services,403", "analyst,GET,/services/{id},200",
        "analyst,PUT,/services/{id},403", "analyst,DELETE,/services/{id},403",
        "analyst,GET,/assets,200", "analyst,POST,/assets,201", "analyst,GET,/assets/{id},200",
        "analyst,PUT,/assets/{id},200", "analyst,DELETE,/assets/{id},403",

        "consultant,GET,/services,200", "consultant,POST,/services,403", "consultant,GET,/services/{id},200",
        "consultant,PUT,/services/{id},403", "consultant,DELETE,/services/{id},403",
        "consultant,GET,/assets,200", "consultant,POST,/assets,403", "consultant,GET,/assets/{id},200",
        "consultant,PUT,/assets/{id},403", "consultant,DELETE,/assets/{id},403",

        "registrant,GET,/services,403", "registrant,POST,/services,403", "registrant,GET,/services/{id},403",
        "registrant,PUT,/services/{id},403", "registrant,DELETE,/services/{id},403",
        "registrant,GET,/assets,403", "registrant,POST,/assets,403", "registrant,GET,/assets/{id},403",
        "registrant,PUT,/assets/{id},403", "registrant,DELETE,/assets/{id},403",
    )
    fun `the role matrix`(role: String, method: String, path: String, expected: Int) {
        val tenant = UUID.randomUUID()
        val owner = bearer("responsible", tenant)
        // Fresh target data per case, created by the responsible so every role can be tested against it.
        val id = if (path.startsWith("/services")) mvc.createService(owner) else mvc.createAsset(owner)
        val body = when {
            method == "POST" && path == "/services" ->
                """{"name":"Novo","description":null,"owner":"Ana","criticality":"low"}"""
            method == "POST" -> """{"name":"Novo","type":"server","owner":"Rui","criticality":"low"}"""
            method == "PUT" && path.startsWith("/services") ->
                """{"version":0,"name":"Faturação","description":null,"owner":"Outro","criticality":"high"}"""
            method == "PUT" -> """{"version":0,"name":"Servidor ERP","type":"server","owner":"Outro","criticality":"high"}"""
            else -> null
        }
        val before = auditCount(tenant)
        val token = if (role == "consultant") "consultant.${UUID.randomUUID()}.$tenant" else bearer(role, tenant)

        mvc.request(HttpMethod.valueOf(method), "/api/risk-assets" + path.replace("{id}", id)) {
            header("Authorization", "Bearer $token")
            if (role == "consultant") header("X-Alinha-Tenant", tenant.toString())
            if (body != null) {
                contentType = MediaType.APPLICATION_JSON
                content = body
            }
        }.andExpect { status { isEqualTo(expected) } }

        if (expected == 403) assertThat(auditCount(tenant)).isEqualTo(before)
    }
}
