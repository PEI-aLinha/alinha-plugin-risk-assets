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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActionsDsl
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.util.UUID

@SpringBootTest(classes = [RiskAssetsTestApplication::class])
@AutoConfigureMockMvc
class CriticalServiceApiTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = RiskAssetsDatabase.register(registry)
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate

    private lateinit var tenant: UUID
    private lateinit var token: String

    @BeforeEach
    fun newOrganization() {
        tenant = UUID.randomUUID()
        token = bearer("responsible", tenant)
    }

    private fun post(json: String): ResultActionsDsl = mvc.post(SERVICES) {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = json
    }

    private fun put(id: String, json: String): ResultActionsDsl = mvc.put("$SERVICES/$id") {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = json
    }

    private fun update(version: Long?, owner: String = "Rui Dias", name: String = "Faturação"): String {
        val v = version?.let { """"version":$it,""" }.orEmpty()
        return """{$v"name":"$name","description":null,"owner":"$owner","criticality":"high"}"""
    }

    private fun auditRows(action: String) = jdbc.queryForList(
        "select entity_id, entity_type, details::text as details from audit.audit_event where tenant_id = ? and action = ?",
        tenant, action,
    )

    @Test
    fun `the Designated Officer registers a service and it is audited without its name or owner`() {
        val body = post(
            """{"name":"Faturação","description":"Emissão de faturas","owner":"Ana Costa","criticality":"high"}""",
        ).andExpect {
            status { isCreated() }
            jsonPath("$.criticality") { value("high") }
            jsonPath("$.version") { value(0) }
        }.andReturn().response.contentAsString
        val id: String = JsonPath.read(body, "$.id")

        val rows = auditRows("critical_service.created")
        assertThat(rows).hasSize(1)
        assertThat(rows[0]["entity_type"]).isEqualTo("critical_service")
        assertThat(rows[0]["entity_id"].toString()).isEqualTo(id)
        val details = rows[0]["details"].toString()
        assertThat(details).contains("HIGH").doesNotContain("Ana Costa").doesNotContain("Faturação")
    }

    @Test
    fun `the list is sorted by name and paged`() {
        listOf("Gama", "Alfa", "Beta").forEach { mvc.createService(token, it) }

        val body = mvc.get(SERVICES) { header("Authorization", "Bearer $token") }
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(body, "$.items[*].name")).containsExactly("Alfa", "Beta", "Gama")
        mvc.get("$SERVICES?pageSize=2") { header("Authorization", "Bearer $token") }.andExpect {
            jsonPath("$.total") { value(3) }
            jsonPath("$.items.length()") { value(2) }
        }
    }

    @Test
    fun `page -1 or pageSize 101 is a 400`() {
        mvc.get("$SERVICES?page=-1") { header("Authorization", "Bearer $token") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.code") { value("VALIDATION_FAILED") }
            jsonPath("$.errors[0].field") { value("page") }
        }
        mvc.get("$SERVICES?pageSize=101") { header("Authorization", "Bearer $token") }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errors[0].field") { value("pageSize") }
        }
    }

    @Test
    fun `an unknown criticality is a 422 naming the field`() {
        for (value in listOf("alta", "HIGH")) {
            post("""{"name":"X","owner":"Y","criticality":"$value"}""").andExpect {
                status { isUnprocessableContent() }
                jsonPath("$.code") { value("VALIDATION_FAILED") }
                jsonPath("$.errors[0].field") { value("criticality") }
            }
        }
    }

    @Test
    fun `a name already used, in any case or spacing, is a 409`() {
        mvc.createService(token, "ERP")
        post("""{"name":" erp ","owner":"Y","criticality":"low"}""").andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("SERVICE_NAME_TAKEN") }
            jsonPath("$.detail") { value("Já existe um serviço com este nome.") }
        }
    }

    @Test
    fun `renaming to an existing name is a 409`() {
        mvc.createService(token, "ERP")
        val id = mvc.createService(token, "CRM")
        put(id, update(0, name = "erp ")).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("SERVICE_NAME_TAKEN") }
        }
    }

    @Test
    fun `updating with the current version saves and audits the changed fields`() {
        val id = mvc.createService(token)
        put(id, update(0)).andExpect {
            status { isOk() }
            jsonPath("$.version") { value(1) }
            jsonPath("$.owner") { value("Rui Dias") }
        }
        val rows = auditRows("critical_service.updated")
        assertThat(rows).hasSize(1)
        assertThat(JsonPath.read<List<String>>(rows[0]["details"].toString(), "$.changed")).containsExactly("owner")
        assertThat(rows[0]["details"].toString()).doesNotContain("Rui Dias")
    }

    @Test
    fun `updating with the same data writes no audit entry`() {
        val id = mvc.createService(token)
        put(id, """{"version":0,"name":"Faturação","description":null,"owner":"Ana Costa","criticality":"high"}""")
            .andExpect {
                status { isOk() }
                jsonPath("$.version") { value(0) }
            }
        assertThat(auditRows("critical_service.updated")).isEmpty()
    }

    @Test
    fun `a stale version is a 409 and changes nothing`() {
        val id = mvc.createService(token)
        put(id, update(0, owner = "Rui Dias")).andExpect { status { isOk() } }
        put(id, update(0, owner = "Outra Pessoa")).andExpect {
            status { isConflict() }
            jsonPath("$.code") { value("VERSION_CONFLICT") }
        }
        mvc.get("$SERVICES/$id") { header("Authorization", "Bearer $token") }.andExpect {
            jsonPath("$.owner") { value("Rui Dias") }
        }
    }

    @Test
    fun `a missing version is a 422`() {
        val id = mvc.createService(token)
        put(id, update(null)).andExpect {
            status { isUnprocessableContent() }
            jsonPath("$.errors[0].field") { value("version") }
            jsonPath("$.errors[0].message") { value("Recarregue a página e volte a fazer as alterações.") }
        }
    }

    @Test
    fun `deleting returns 204, audits, then the service is a 404`() {
        val id = mvc.createService(token)
        mvc.delete("$SERVICES/$id") { header("Authorization", "Bearer $token") }.andExpect {
            status { isNoContent() }
            content { string("") }
        }
        val rows = auditRows("critical_service.deleted")
        assertThat(rows).hasSize(1)
        assertThat(JsonPath.read<Int>(rows[0]["details"].toString(), "$.unlinkedAssets")).isZero()
        mvc.get("$SERVICES/$id") { header("Authorization", "Bearer $token") }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SERVICE_NOT_FOUND") }
            jsonPath("$.detail") { value("Este serviço não existe ou foi apagado.") }
        }
    }

    @Test
    fun `deleting a linked service audits how many assets were unlinked`() {
        val id = mvc.createService(token)
        val assetId = UUID.randomUUID()
        jdbc.update(
            "insert into riskassets.asset (id, tenant_id, version, name, owner, criticality, type, created_at, updated_at) " +
                "values (?, ?, 0, 'Servidor', 'Ana', 'LOW', 'SERVER', now(), now())",
            assetId, tenant,
        )
        jdbc.update("insert into riskassets.service_asset (service_id, asset_id) values (?::uuid, ?)", id, assetId)

        mvc.delete("$SERVICES/$id") { header("Authorization", "Bearer $token") }.andExpect { status { isNoContent() } }

        val details = auditRows("critical_service.deleted")[0]["details"].toString()
        assertThat(JsonPath.read<Int>(details, "$.unlinkedAssets")).isEqualTo(1)
        assertThat(jdbc.queryForObject("select count(*) from riskassets.asset where id = ?", Long::class.java, assetId)).isEqualTo(1L)
    }

    @Test
    fun `another organization's service is a 404`() {
        val id = mvc.createService(token)
        val other = bearer("responsible", UUID.randomUUID())
        mvc.get("$SERVICES/$id") { header("Authorization", "Bearer $other") }.andExpect {
            status { isNotFound() }
            jsonPath("$.code") { value("SERVICE_NOT_FOUND") }
        }
    }
}
