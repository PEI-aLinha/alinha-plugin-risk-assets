package pt.alinha.riskassets.internal

import com.jayway.jsonpath.JsonPath
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
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.context.request.RequestContextHolder
import pt.alinha.core.tenant.TenantContext
import pt.alinha.pluginapi.TenantId
import pt.alinha.riskassets.events.RiskRegistered
import pt.alinha.riskassets.internal.persistence.RiskRepository
import java.util.UUID

private const val RISKS = "/api/risk-assets/risks"
private const val MATRIX = "/api/risk-assets/risk-matrix"

private fun riskBody(
    threat: String = "Ransomware cifra o servidor de ficheiros",
    likelihood: Any = 2,
    impact: Any = 3,
    treatment: String? = null,
    version: Long? = null,
) = """
    {${version?.let { "\"version\": $it," } ?: ""}
     "threat": "$threat", "vulnerability": "Cópias de segurança no mesmo servidor",
     "likelihood": $likelihood, "impact": $impact, "treatment": ${treatment?.let { "\"$it\"" } ?: "null"}}
""".trimIndent()

@SpringBootTest(classes = [RiskAssetsTestApplication::class])
@AutoConfigureMockMvc
@RecordApplicationEvents
class RiskRegisterIntegrationTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = RiskAssetsDatabase.register(registry)
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var events: ApplicationEvents
    @Autowired private lateinit var tx: TransactionTemplate
    @Autowired private lateinit var repository: RiskRepository
    @Autowired private lateinit var tenants: TenantContext

    private lateinit var tenant: UUID

    @BeforeEach
    fun newOrganization() {
        tenant = UUID.randomUUID()
    }

    private fun token(role: String, tenant: UUID = this.tenant) = "Bearer $role.${UUID.randomUUID()}.$tenant"

    private fun register(body: String = riskBody(), role: String = "consultant", tenant: UUID = this.tenant): ResultActionsDsl =
        mvc.post(RISKS) {
            header("Authorization", token(role, tenant))
            if (role == "consultant") header("X-Alinha-Tenant", tenant)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun update(id: String, body: String, tenant: UUID = this.tenant): ResultActionsDsl =
        mvc.put("$RISKS/$id") {
            header("Authorization", token("consultant", tenant))
            header("X-Alinha-Tenant", tenant)
            contentType = MediaType.APPLICATION_JSON
            content = body
        }

    private fun read(path: String, role: String = "responsible", tenant: UUID = this.tenant): ResultActionsDsl =
        mvc.get(path) {
            header("Authorization", token(role, tenant))
            if (role == "consultant") header("X-Alinha-Tenant", tenant)
        }

    private fun registeredId(body: String = riskBody()): String =
        JsonPath.read(register(body).andExpect { status { isCreated() } }.andReturn().response.contentAsString, "$.id")

    private fun audit(action: String): List<Map<String, Any?>> = jdbc.queryForList(
        "select entity_id, details::text as details from audit.audit_event where tenant_id = ? and action = ?",
        tenant, action,
    )

    @Test
    fun `a consultant registers a risk and the server computes its level`() {
        register(riskBody(likelihood = 2, impact = 3, treatment = "mitigate")).andExpect {
            status { isCreated() }
            jsonPath("$.score") { value(6) }
            jsonPath("$.level") { value("high") }
            jsonPath("$.treatment") { value("mitigate") }
            jsonPath("$.version") { value(0) }
            jsonPath("$.assetId") { value(null) }
        }
    }

    @Test
    fun `registering is audited and publishes RiskRegistered`() {
        val id = registeredId()

        val entry = audit("risk.registered").single()
        assertThat(entry["entity_id"]).isEqualTo(id)
        assertThat(entry["details"] as String).contains("\"level\": \"high\"")
        val event = events.stream(RiskRegistered::class.java).toList().single { it.riskId.toString() == id }
        assertThat(event.tenantId.value).isEqualTo(tenant)
    }

    @Test
    fun `the Designated Officer reads the register but cannot change it`() {
        val id = registeredId()

        read(RISKS).andExpect { status { isOk() }; jsonPath("$.total") { value(1) } }
        read("$RISKS/$id").andExpect { status { isOk() } }
        register(role = "responsible").andExpect { status { isForbidden() } }
    }

    @Test
    fun `an analyst cannot see the register`() {
        registeredId()

        read(RISKS, role = "analyst").andExpect { status { isForbidden() } }
        read(MATRIX, role = "analyst").andExpect { status { isForbidden() } }
    }

    @Test
    fun `the register lists the highest score first`() {
        registeredId(riskBody(threat = "Baixo", likelihood = 1, impact = 1))
        registeredId(riskBody(threat = "Alto", likelihood = 3, impact = 3))
        registeredId(riskBody(threat = "Médio", likelihood = 2, impact = 2))

        read("$RISKS?pageSize=2").andExpect {
            status { isOk() }
            jsonPath("$.total") { value(3) }
            jsonPath("$.items.length()") { value(2) }
            jsonPath("$.items[0].threat") { value("Alto") }
            jsonPath("$.items[1].threat") { value("Médio") }
        }
    }

    @Test
    fun `another organization's risk is not found and does not change`() {
        val id = registeredId()
        val other = UUID.randomUUID()

        read("$RISKS/$id", tenant = other).andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("RISK_NOT_FOUND") }
        }
        update(id, riskBody(threat = "Alterado", version = 0), tenant = other).andExpect { status { isNotFound() } }
        read(RISKS, tenant = other).andExpect { jsonPath("$.total") { value(0) } }
        read("$RISKS/$id").andExpect { jsonPath("$.threat") { value("Ransomware cifra o servidor de ficheiros") } }
    }

    @Test
    fun `reassessing a risk audits the changed fields and the new level`() {
        val id = registeredId(riskBody(likelihood = 2, impact = 3))

        update(id, riskBody(likelihood = 1, impact = 3, treatment = "accept", version = 0)).andExpect {
            status { isOk() }
            jsonPath("$.level") { value("medium") }
            jsonPath("$.version") { value(1) }
        }
        val details = audit("risk.updated").single()["details"] as String
        assertThat(details).contains("likelihood", "treatment", "\"levelBefore\": \"high\"", "\"level\": \"medium\"")
        assertThat(details).doesNotContain("impact")
    }

    @Test
    fun `sending the saved data again writes no audit entry`() {
        val id = registeredId()

        update(id, riskBody(version = 0)).andExpect { status { isOk() }; jsonPath("$.version") { value(0) } }
        assertThat(audit("risk.updated")).isEmpty()
    }

    @Test
    fun `a stale form is a conflict even when it also has invalid fields`() {
        val id = registeredId()
        update(id, riskBody(threat = "Primeira", version = 0)).andExpect { status { isOk() } }

        update(id, riskBody(threat = " ", likelihood = 9, version = 0)).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("VERSION_CONFLICT") }
        }
    }

    @Test
    fun `two saves of the same version cannot both win`() {
        val id = UUID.fromString(registeredId())
        // Two requests read version 0; the first commits, the second's flush must fail instead of overwriting it.
        // The test context binds a mock request to this thread, and runAs refuses to run inside a request.
        RequestContextHolder.resetRequestAttributes()
        val failure = runCatching {
            tenants.runAs(TenantId(tenant)) {
                tx.execute {
                    val stale = repository.findOneById(id)!!
                    // The other request, in its own transaction: it commits first.
                    TransactionTemplate(tx.transactionManager!!).apply {
                        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
                    }.executeWithoutResult {
                        jdbc.update("update riskassets.risk set version = version + 1, threat = 'Outro pedido' where id = ?", id)
                    }
                    stale.update(stale.toData().copy(threat = "Este pedido"), java.time.Instant.now())
                    repository.saveAndFlush(stale)
                }
            }
        }.exceptionOrNull()

        assertThat(failure).isInstanceOf(ObjectOptimisticLockingFailureException::class.java)
        read("$RISKS/$id").andExpect { jsonPath("$.threat") { value("Outro pedido") } }
    }

    @Test
    fun `the audit entries hold no free text`() {
        val secret = "Servidor sem palavra-passe no armazem"
        val id = registeredId(riskBody(threat = secret))
        update(id, riskBody(threat = "$secret 2", version = 0)).andExpect { status { isOk() } }

        val details = jdbc.queryForList("select details::text from audit.audit_event where tenant_id = ?", String::class.java, tenant)
        assertThat(details).hasSize(2).noneMatch { it.contains("armazem") || it.contains("palavra-passe") || it.contains("Cópias") }
    }

    @Test
    fun `a stale version is a conflict and changes nothing`() {
        val id = registeredId()
        update(id, riskBody(threat = "Primeira", version = 0)).andExpect { status { isOk() } }

        update(id, riskBody(threat = "Segunda", version = 0)).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("VERSION_CONFLICT") }
        }
        read("$RISKS/$id").andExpect { jsonPath("$.threat") { value("Primeira") } }
    }

    @Test
    fun `invalid data lists every field to fix`() {
        val body = register(riskBody(threat = " ", likelihood = 4, impact = 3, treatment = "ignore")).andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
        }.andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(body, "$.errors[*].field")).containsExactly("threat", "likelihood", "treatment")
        assertThat(audit("risk.registered")).isEmpty()
    }

    @Test
    fun `a value of the wrong type names its field`() {
        register(riskBody(likelihood = "\"alta\"")).andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.errors[0].field") { value("likelihood") }
        }
    }

    @Test
    fun `an update without a version is refused`() {
        val id = registeredId()

        update(id, riskBody()).andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.errors[0].field") { value("version") }
        }
    }

    @Test
    fun `a page size over the limit is a bad request`() {
        read("$RISKS?pageSize=101").andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("INVALID_PAGE") }
        }
    }

    @Test
    fun `the UI reads the risk matrix from the server`() {
        val body = read(MATRIX).andExpect {
            status { isOk() }
            jsonPath("$.likelihood.length()") { value(3) }
            jsonPath("$.impact[2].label") { value("Alto") }
        }.andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(body, "$.bands[*].level")).containsExactly("low", "medium", "high")
    }
}
