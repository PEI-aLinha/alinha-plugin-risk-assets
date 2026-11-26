package pt.alinha.riskassets.internal

import com.jayway.jsonpath.JsonPath
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import java.util.UUID

internal const val SERVICES = "/api/risk-assets/services"

fun bearer(role: String, tenant: UUID): String = "$role.${UUID.randomUUID()}.$tenant"

/** Registers a critical service and returns its id. */
fun MockMvc.createService(token: String, name: String = "Faturação", criticality: String = "high"): String {
    val body = post(SERVICES) {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = """{"name":"$name","description":null,"owner":"Ana Costa","criticality":"$criticality"}"""
    }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
    return JsonPath.read(body, "$.id")
}

internal const val ASSETS = "/api/risk-assets/assets"

/** Registers an asset linked to [serviceIds] and returns its id. */
fun MockMvc.createAsset(
    token: String,
    name: String = "Servidor ERP",
    type: String = "server",
    criticality: String = "high",
    serviceIds: List<String> = emptyList(),
): String {
    val ids = serviceIds.joinToString(",") { "\"$it\"" }
    val body = post(ASSETS) {
        header("Authorization", "Bearer $token")
        contentType = MediaType.APPLICATION_JSON
        content = """{"name":"$name","type":"$type","owner":"Rui","criticality":"$criticality","serviceIds":[$ids]}"""
    }.andExpect { status { isCreated() } }.andReturn().response.contentAsString
    return JsonPath.read(body, "$.id")
}
