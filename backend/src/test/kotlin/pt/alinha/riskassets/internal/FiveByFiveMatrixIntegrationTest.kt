package pt.alinha.riskassets.internal

import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.util.UUID

/** The matrix comes from configuration: a 5x5 with its own bands, and no code change. */
@SpringBootTest(classes = [RiskAssetsTestApplication::class])
@AutoConfigureMockMvc
class FiveByFiveMatrixIntegrationTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            RiskAssetsDatabase.register(registry)
            for (scale in listOf("likelihood", "impact")) {
                (1..5).forEachIndexed { i, value ->
                    registry.add("alinha.risk-assets.matrix.$scale[$i].value") { value }
                    registry.add("alinha.risk-assets.matrix.$scale[$i].label") { "Nível $value" }
                    registry.add("alinha.risk-assets.matrix.$scale[$i].description") { "" }
                }
            }
            listOf(Triple("LOW", 1, 4), Triple("MEDIUM", 5, 12), Triple("HIGH", 13, 25)).forEachIndexed { i, (level, min, max) ->
                registry.add("alinha.risk-assets.matrix.bands[$i].level") { level }
                registry.add("alinha.risk-assets.matrix.bands[$i].min-score") { min }
                registry.add("alinha.risk-assets.matrix.bands[$i].max-score") { max }
            }
        }
    }

    @Autowired private lateinit var mvc: MockMvc

    private val tenant = UUID.randomUUID()

    @Test
    fun `the 5x5 scale is served and used to classify`() {
        val body = mvc.get("/api/risk-assets/risk-matrix") { header("Authorization", "Bearer responsible.${UUID.randomUUID()}.$tenant") }
            .andExpect { status { isOk() } }.andReturn().response.contentAsString
        assertThat(JsonPath.read<List<Int>>(body, "$.likelihood[*].value")).containsExactly(1, 2, 3, 4, 5)

        mvc.post("/api/risk-assets/risks") {
            header("Authorization", "Bearer consultant.${UUID.randomUUID()}.$tenant")
            header("X-Alinha-Tenant", tenant)
            contentType = MediaType.APPLICATION_JSON
            content = """{"threat": "Inundação do bastidor", "likelihood": 4, "impact": 3}"""
        }.andExpect {
            status { isCreated() }
            jsonPath("$.score") { value(12) }
            jsonPath("$.level") { value("medium") }
        }
    }
}
