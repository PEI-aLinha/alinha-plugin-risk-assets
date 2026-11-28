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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import pt.alinha.riskassets.events.AssetRegistered
import java.util.UUID

@SpringBootTest(classes = [RiskAssetsTestApplication::class])
@AutoConfigureMockMvc
@RecordApplicationEvents
class AssetApiTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = RiskAssetsDatabase.register(registry)
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var events: ApplicationEvents

    private lateinit var tenant: UUID
    private lateinit var responsible: String
    private lateinit var analyst: String

    @BeforeEach
    fun newOrganization() {
        tenant = UUID.randomUUID()
        responsible = bearer("responsible", tenant)
        analyst = bearer("analyst", tenant)
    }

    private fun post(json: String, correlation: UUID? = null): ResultActionsDsl = mvc.post(ASSETS) {
        header("Authorization", "Bearer $analyst")
        correlation?.let { header("X-Correlation-Id", it.toString()) }
        contentType = MediaType.APPLICATION_JSON
        content = json
    }

    private fun put(id: String, json: String): ResultActionsDsl = mvc.put("$ASSETS/$id") {
        header("Authorization", "Bearer $analyst")
        contentType = MediaType.APPLICATION_JSON
        content = json
    }

    private fun list(query: String = ""): ResultActionsDsl =
        mvc.get("$ASSETS$query") { header("Authorization", "Bearer $analyst") }

    private fun auditRows(action: String) = jdbc.queryForList(
        "select entity_id, entity_type, details::text as details from audit.audit_event where tenant_id = ? and action = ?",
        tenant, action,
    )

    private fun names(query: String): List<String> =
        JsonPath.read(list(query).andExpect { status { isOk() } }.andReturn().response.contentAsString, "$.items[*].name")

    @Test
    fun `an analyst registers an asset linked to two services and AssetRegistered is published`() {
        val s1 = mvc.createService(responsible, "Faturação")
        val s2 = mvc.createService(responsible, "Vendas")
        val c = UUID.randomUUID()
        val body = post(
            """{"name":"Servidor ERP","type":"server","owner":"Rui","criticality":"high","serviceIds":["$s2","$s1","$s1"]}""", c,
        ).andExpect {
            status { isCreated() }
            jsonPath("$.type") { value("server") }
            jsonPath("$.description") { value(null) }
            jsonPath("$.version") { value(0) }
        }.andReturn().response.contentAsString
        val id: String = JsonPath.read(body, "$.id")
        assertThat(JsonPath.read<List<String>>(body, "$.serviceIds")).containsExactlyElementsOf(listOf(s1, s2).sorted())

        assertThat(events.stream(AssetRegistered::class.java)).singleElement().satisfies({
            assertThat(it.tenantId.value).isEqualTo(tenant)
            assertThat(it.correlationId).isEqualTo(c)
            assertThat(it.assetId.toString()).isEqualTo(id)
        })
        val rows = auditRows("asset.created")
        assertThat(rows).hasSize(1)
        assertThat(rows[0]["entity_type"]).isEqualTo("asset")
        val details = rows[0]["details"].toString()
        assertThat(details).contains("\"server\"").contains("\"high\"").doesNotContain("SERVER").doesNotContain("HIGH").contains(s1).contains(s2)
            .doesNotContain("Rui").doesNotContain("Servidor ERP")
    }

    @Test
    fun `without serviceIds an asset has none`() {
        post("""{"name":"Switch","type":"network","owner":"Rui","criticality":"low"}""").andExpect {
            status { isCreated() }
            jsonPath("$.serviceIds.length()") { value(0) }
        }
    }

    @Test
    fun `a type or criticality the contract does not offer is a 422 naming the field`() {
        post("""{"name":"X","type":"laptop","owner":"Y","criticality":"high"}""").andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("type") }
        }
        post("""{"name":"X","type":"server","owner":"Y","criticality":"HIGH"}""").andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.errors[0].field") { value("criticality") }
        }
    }

    @Test
    fun `a null service id is a 422 naming serviceIds`() {
        post("""{"name":"X","type":"server","owner":"Y","criticality":"low","serviceIds":[null]}""").andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.errors[0].field") { value("serviceIds") }
        }
    }

    @Test
    fun `an unknown service id is a 422 and nothing is saved, audited or published`() {
        val s1 = mvc.createService(responsible)
        val other = mvc.createService(bearer("responsible", UUID.randomUUID()), "Alheio")
        for (bad in listOf(UUID.randomUUID().toString(), other)) {
            post("""{"name":"X","type":"server","owner":"Y","criticality":"low","serviceIds":["$s1","$bad"]}""").andExpect {
                status { isUnprocessableContent() }
                jsonPath("$.errors[0].field") { value("serviceIds") }
                jsonPath("$.errors[0].message") { value("Um dos serviços escolhidos já não existe. Recarregue a página.") }
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from riskassets.asset where tenant_id = ?", Long::class.java, tenant)).isZero()
        assertThat(auditRows("asset.created")).isEmpty()
        assertThat(events.stream(AssetRegistered::class.java)).isEmpty()
    }

    @Test
    fun `filters by type, criticality and service combine`() {
        val s1 = mvc.createService(responsible, "Faturação")
        val s2 = mvc.createService(responsible, "Vendas")
        mvc.createAsset(analyst, "A", "server", "high", listOf(s1, s2))
        mvc.createAsset(analyst, "B", "server", "low", listOf(s1))
        mvc.createAsset(analyst, "C", "network", "high")

        assertThat(names("?type=server&criticality=high")).containsExactly("A")
        assertThat(names("?serviceId=$s1")).containsExactly("A", "B")
        assertThat(names("?serviceId=$s2&type=network")).isEmpty()
        assertThat(names("?criticality=high")).containsExactly("A", "C")
        // distinct + count: A is linked to two services and must count once
        list("?serviceId=$s1&pageSize=1").andExpect { jsonPath("$.total") { value(2) } }
        list("?serviceId=$s2").andExpect { jsonPath("$.total") { value(1) } }
        list().andExpect { jsonPath("$.total") { value(3) } }
        assertThat(names("")).containsExactly("A", "B", "C")
    }

    @Test
    fun `an invalid filter value is a 400 naming it`() {
        for ((q, field) in listOf("type=laptop" to "type", "criticality=HIGH" to "criticality", "serviceId=abc" to "serviceId")) {
            list("?$q").andExpect {
                status { isBadRequest() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[0].field") { value(field) }
            }
        }
    }

    @Test
    fun `changing the services audits serviceIds as changed`() {
        val s1 = mvc.createService(responsible, "Faturação")
        val s2 = mvc.createService(responsible, "Vendas")
        val id = mvc.createAsset(analyst, serviceIds = listOf(s1))
        put(id, """{"version":0,"name":"Servidor ERP","type":"server","owner":"Rui","criticality":"high","serviceIds":["$s2"]}""")
            .andExpect {
                status { isOk() }
                jsonPath("$.version") { value(1) }
                jsonPath("$.serviceIds[0]") { value(s2) }
            }
        val rows = auditRows("asset.updated")
        assertThat(rows).hasSize(1)
        assertThat(JsonPath.read<List<String>>(rows[0]["details"].toString(), "$.changed")).containsExactly("serviceIds")
    }

    @Test
    fun `updating with the same data writes no audit and does not bump the version`() {
        val id = mvc.createAsset(analyst)
        put(id, """{"version":0,"name":"Servidor ERP","type":"server","owner":"Rui","criticality":"high","serviceIds":[]}""").andExpect {
            status { isOk() }
            jsonPath("$.version") { value(0) }
        }
        assertThat(auditRows("asset.updated")).isEmpty()
    }

    @Test
    fun `updating with an unknown service id is a 422 and changes nothing`() {
        val id = mvc.createAsset(analyst)
        put(id, """{"version":0,"name":"Novo","type":"server","owner":"Rui","criticality":"high","serviceIds":["${UUID.randomUUID()}"]}""")
            .andExpect {
                status { isUnprocessableContent() }
                jsonPath("$.errors[0].field") { value("serviceIds") }
            }
        assertThat(auditRows("asset.updated")).isEmpty()
    }

    @Test
    fun `a PUT without serviceIds is a 422 and keeps the links`() {
        val s1 = mvc.createService(responsible, "Faturação")
        val id = mvc.createAsset(analyst, serviceIds = listOf(s1))
        put(id, """{"version":0,"name":"Servidor ERP","type":"server","owner":"Rui","criticality":"high"}""").andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.errors[0].field") { value("serviceIds") }
        }
        mvc.get("$ASSETS/$id") { header("Authorization", "Bearer $analyst") }.andExpect {
            jsonPath("$.version") { value(0) }
            jsonPath("$.serviceIds[0]") { value(s1) }
        }
        assertThat(auditRows("asset.updated")).isEmpty()
    }

    @Test
    fun `deleting a service drops only that link, bumps the asset's version and audits it`() {
        val s1 = mvc.createService(responsible, "Faturação")
        val s2 = mvc.createService(responsible, "Vendas")
        val id = mvc.createAsset(analyst, serviceIds = listOf(s1, s2))
        val other = mvc.createAsset(analyst, name = "Switch", serviceIds = listOf(s2))
        val before = JsonPath.read<String>(
            mvc.get("$ASSETS/$id") { header("Authorization", "Bearer $analyst") }.andReturn().response.contentAsString,
            "$.updatedAt",
        )
        mvc.delete("$SERVICES/$s1") { header("Authorization", "Bearer $responsible") }.andExpect { status { isNoContent() } }
        mvc.get("$ASSETS/$id") { header("Authorization", "Bearer $analyst") }.andExpect {
            status { isOk() }
            jsonPath("$.version") { value(1) }
            jsonPath("$.serviceIds.length()") { value(1) }
            jsonPath("$.serviceIds[0]") { value(s2) }
            jsonPath("$.updatedAt") { value(org.hamcrest.Matchers.not(before)) }
        }
        mvc.get("$ASSETS/$other") { header("Authorization", "Bearer $analyst") }.andExpect { jsonPath("$.version") { value(0) } }
        val rows = auditRows("asset.updated")
        assertThat(rows.map { it["entity_id"].toString() }).containsExactly(id)
        assertThat(JsonPath.read<List<String>>(rows[0]["details"].toString(), "$.changed")).containsExactly("serviceIds")

        // A form opened before the deletion still holds version 0 and the deleted id: a 409, not a 422.
        put(id, """{"version":0,"name":"Servidor ERP","type":"server","owner":"Rui","criticality":"high","serviceIds":["$s1","$s2"]}""")
            .andExpect {
                status { isConflict() }
                jsonPath("$.code") { value("VERSION_CONFLICT") }
            }
    }

    @Test
    fun `deleting an asset is a 204, audited, then a 404`() {
        val id = mvc.createAsset(analyst)
        mvc.delete("$ASSETS/$id") { header("Authorization", "Bearer $responsible") }.andExpect {
            status { isNoContent() }
            content { string("") }
        }
        assertThat(auditRows("asset.deleted")).hasSize(1)
        mvc.get("$ASSETS/$id") { header("Authorization", "Bearer $analyst") }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("ASSET_NOT_FOUND") }
            jsonPath("$.detail") { value("Este ativo não existe ou foi apagado.") }
        }
    }

    @Test
    fun `a stale version is a 409`() {
        val id = mvc.createAsset(analyst)
        put(id, """{"version":0,"name":"Novo nome","type":"server","owner":"Rui","criticality":"high","serviceIds":[]}""").andExpect { status { isOk() } }
        put(id, """{"version":0,"name":"Outro","type":"server","owner":"Rui","criticality":"high","serviceIds":[]}""").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("VERSION_CONFLICT") }
        }
    }

    @Test
    fun `a missing version is a 422 and another organization's asset is a 404`() {
        val id = mvc.createAsset(analyst)
        put(id, """{"name":"Novo","type":"server","owner":"Rui","criticality":"high","serviceIds":[]}""").andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.errors[0].field") { value("version") }
        }
        mvc.get("$ASSETS/$id") { header("Authorization", "Bearer ${bearer("analyst", UUID.randomUUID())}") }
            .andExpect { status { isNotFound() } }
    }
}
