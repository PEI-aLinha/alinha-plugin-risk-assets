package pt.alinha.riskassets.internal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID

/** Another organization's service or asset id never reads, writes, links or audits anything. */
@SpringBootTest(classes = [RiskAssetsTestApplication::class])
@AutoConfigureMockMvc
class InventoryTenantIsolationTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = RiskAssetsDatabase.register(registry)
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate

    private lateinit var tenantA: UUID
    private lateinit var tenantB: UUID
    private lateinit var tokenA: String
    private lateinit var tokenB: String
    private lateinit var serviceA: String
    private lateinit var assetA: String

    @BeforeEach
    fun twoOrganizations() {
        tenantA = UUID.randomUUID()
        tenantB = UUID.randomUUID()
        tokenA = bearer("responsible", tenantA)
        tokenB = bearer("responsible", tenantB)
        serviceA = mvc.createService(tokenA)
        assetA = mvc.createAsset(tokenA, serviceIds = listOf(serviceA))
    }

    private fun auditCount(tenant: UUID) =
        jdbc.queryForObject("select count(*) from audit.audit_event where tenant_id = ?", Long::class.java, tenant)

    private fun row(table: String, id: String) =
        jdbc.queryForMap("select * from riskassets.$table where id = ?::uuid", id)

    @Test
    fun `B cannot read, change or delete A's service or asset by id`() {
        val serviceBefore = row("critical_service", serviceA)
        val assetBefore = row("asset", assetA)
        val auditA = auditCount(tenantA)
        val svcBody = """{"version":0,"name":"X","description":null,"owner":"Y","criticality":"low"}"""
        val assetBody = """{"version":0,"name":"X","type":"server","owner":"Y","criticality":"low","serviceIds":[]}"""

        mvc.get("$SERVICES/$serviceA") { header("Authorization", "Bearer $tokenB") }.andExpect { status { isNotFound() } }
        mvc.put("$SERVICES/$serviceA") {
            header("Authorization", "Bearer $tokenB"); contentType = MediaType.APPLICATION_JSON; content = svcBody
        }.andExpect { status { isNotFound() } }
        mvc.delete("$SERVICES/$serviceA") { header("Authorization", "Bearer $tokenB") }.andExpect { status { isNotFound() } }
        mvc.get("$ASSETS/$assetA") { header("Authorization", "Bearer $tokenB") }.andExpect { status { isNotFound() } }
        mvc.put("$ASSETS/$assetA") {
            header("Authorization", "Bearer $tokenB"); contentType = MediaType.APPLICATION_JSON; content = assetBody
        }.andExpect { status { isNotFound() } }
        mvc.delete("$ASSETS/$assetA") { header("Authorization", "Bearer $tokenB") }.andExpect { status { isNotFound() } }

        assertThat(row("critical_service", serviceA)).isEqualTo(serviceBefore)
        assertThat(row("asset", assetA)).isEqualTo(assetBefore)
        assertThat(jdbc.queryForObject("select count(*) from riskassets.service_asset where asset_id = ?::uuid", Long::class.java, assetA)).isEqualTo(1)
        assertThat(auditCount(tenantB)).isZero()
        assertThat(auditCount(tenantA)).isEqualTo(auditA)
    }

    @Test
    fun `B cannot link A's service to its own asset`() {
        mvc.post(ASSETS) {
            header("Authorization", "Bearer $tokenB")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"X","type":"server","owner":"Y","criticality":"low","serviceIds":["$serviceA"]}"""
        }.andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.errors[0].field") { value("serviceIds") }
        }
        assertThat(jdbc.queryForObject("select count(*) from riskassets.asset where tenant_id = ?", Long::class.java, tenantB)).isZero()
        // The only link to A's service is A's own asset.
        assertThat(jdbc.queryForObject("select count(*) from riskassets.service_asset where service_id = ?::uuid", Long::class.java, serviceA)).isEqualTo(1)
        assertThat(auditCount(tenantB)).isZero()
    }

    @Test
    fun `the lists show only the caller's data`() {
        for (path in listOf(SERVICES, ASSETS)) {
            mvc.get(path) { header("Authorization", "Bearer $tokenB") }.andExpect {
                status { isOk() }
                jsonPath("$.total") { value(0) }
            }
            mvc.get(path) { header("Authorization", "Bearer $tokenA") }.andExpect { jsonPath("$.total") { value(1) } }
        }
    }

    @Test
    fun `the same service name can exist in two organizations`() {
        mvc.createService(tokenB, "Faturação")
        mvc.get(SERVICES) { header("Authorization", "Bearer $tokenB") }.andExpect { jsonPath("$.total") { value(1) } }
    }

    @Test
    fun `a consultant sees only the organization in X-Alinha-Tenant`() {
        val consultant = "consultant.${UUID.randomUUID()}.$tenantA"
        mvc.get(SERVICES) {
            header("Authorization", "Bearer $consultant"); header("X-Alinha-Tenant", tenantA.toString())
        }.andExpect { status { isOk() }; jsonPath("$.total") { value(1) } }
        mvc.get(SERVICES) {
            header("Authorization", "Bearer $consultant"); header("X-Alinha-Tenant", tenantB.toString())
        }.andExpect { status { isForbidden() } }
        mvc.get("$ASSETS/$assetA") {
            header("Authorization", "Bearer $consultant"); header("X-Alinha-Tenant", tenantB.toString())
        }.andExpect { status { isForbidden() } }
    }
}
