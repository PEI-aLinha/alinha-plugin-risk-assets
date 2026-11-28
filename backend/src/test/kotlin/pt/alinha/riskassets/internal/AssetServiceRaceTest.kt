package pt.alinha.riskassets.internal

import com.jayway.jsonpath.JsonPath
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import pt.alinha.riskassets.internal.persistence.CriticalServiceEntity
import pt.alinha.riskassets.internal.persistence.CriticalServiceRepository
import java.sql.SQLException
import java.util.UUID

/**
 * A service deleted while an asset links it. The asset write holds the service `FOR SHARE` from its load until it
 * commits, and the delete holds it `FOR NO KEY UPDATE` from before it reads the links, so neither can slip past the
 * other: no foreign key 500, and no link dropped by the cascade without the asset's version bump and audit entry.
 * Each test tries the other side on another connection, with a short `lock_timeout`, while the request holds the lock.
 */
@SpringBootTest(classes = [RiskAssetsTestApplication::class])
@AutoConfigureMockMvc
@Import(AssetServiceRaceTest.RaceConfig::class)
class AssetServiceRaceTest {
    companion object {
        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) = RiskAssetsDatabase.register(registry)
    }

    /** Runs a hook once, right after the asset write's load of its services, or after the delete's lock. */
    class RacingServices(private val real: CriticalServiceRepository) : CriticalServiceRepository by real {
        @Volatile var afterLoad: (() -> Unit)? = null
        @Volatile var afterLock: (() -> Unit)? = null

        override fun findAllByIdIn(ids: Collection<UUID>): List<CriticalServiceEntity> =
            real.findAllByIdIn(ids).also { afterLoad?.also { afterLoad = null }?.invoke() }

        override fun findLockedById(id: UUID): CriticalServiceEntity? =
            real.findLockedById(id).also { afterLock?.also { afterLock = null }?.invoke() }
    }

    @TestConfiguration
    class RaceConfig {
        @Bean
        @Primary
        fun racingServices(@Qualifier("criticalServiceRepository") real: CriticalServiceRepository) = RacingServices(real)
    }

    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jdbc: JdbcTemplate
    @Autowired private lateinit var services: RacingServices
    @Autowired private lateinit var transactions: PlatformTransactionManager

    private val tenant = UUID.randomUUID()
    private val responsible = bearer("responsible", tenant)
    private val analyst = bearer("analyst", tenant)

    @AfterEach
    fun noHooks() {
        services.afterLoad = null
        services.afterLock = null
    }

    /** Runs [sql] in its own transaction and connection, failing after 200 ms if it has to wait for a lock. */
    private fun otherConnection(sql: String, serviceId: String): () -> Unit = {
        TransactionTemplate(transactions).apply {
            propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        }.executeWithoutResult {
            jdbc.execute("set local lock_timeout = '200ms'")
            if (sql.startsWith("select")) jdbc.queryForList(sql, serviceId) else jdbc.update(sql, serviceId)
        }
    }

    /** The hook's statement must time out on the request's lock; the request itself then goes on normally. */
    private fun mustWait(sql: String, serviceId: String): () -> Unit {
        val run = otherConnection(sql, serviceId)
        // 55P03 is lock_not_available (the lock_timeout); Spring leaves it untranslated.
        return {
            assertThatThrownBy { run() }.rootCause()
                .isInstanceOf(SQLException::class.java)
                .extracting { (it as SQLException).sqlState }.isEqualTo("55P03")
        }
    }

    private fun createAsset(serviceIds: String = ""): String =
        JsonPath.read(
            mvc.post(ASSETS) {
                header("Authorization", "Bearer $analyst")
                contentType = MediaType.APPLICATION_JSON
                content = """{"name":"Servidor ERP","type":"server","owner":"Rui","criticality":"high","serviceIds":[$serviceIds]}"""
            }.andExpect { status { isCreated() } }.andReturn().response.contentAsString,
            "$.id",
        )

    private fun links(serviceId: String): Long =
        jdbc.queryForObject("select count(*) from riskassets.service_asset where service_id = ?::uuid", Long::class.java, serviceId)!!

    @Test
    fun `a service cannot be deleted while an asset is being created with a link to it`() {
        val service = mvc.createService(responsible, "Faturação")
        services.afterLoad = mustWait("delete from riskassets.critical_service where id = ?::uuid", service)
        createAsset("\"$service\"")
        assertThat(services.afterLoad == null).describedAs("the hook ran").isTrue()
        assertThat(links(service)).isEqualTo(1)
    }

    @Test
    fun `a service cannot be deleted while an asset is being linked to it`() {
        val service = mvc.createService(responsible, "Faturação")
        val id = createAsset()
        services.afterLoad = mustWait("delete from riskassets.critical_service where id = ?::uuid", service)
        mvc.put("$ASSETS/$id") {
            header("Authorization", "Bearer $analyst")
            contentType = MediaType.APPLICATION_JSON
            content = """{"name":"Servidor ERP","type":"server","owner":"Rui","criticality":"high","serviceIds":["$service"],"version":0}"""
        }.andExpect { status { isOk() } }
        assertThat(services.afterLoad == null).describedAs("the hook ran").isTrue()
        assertThat(links(service)).isEqualTo(1)
    }

    @Test
    fun `an asset cannot link a service while it is being deleted`() {
        val service = mvc.createService(responsible, "Faturação")
        // The same lock the asset write takes in findAllByIdIn.
        services.afterLock = mustWait("select 1 from riskassets.critical_service where id = ?::uuid for share", service)
        mvc.delete("$SERVICES/$service") {
            header("Authorization", "Bearer $responsible")
        }.andExpect { status { isNoContent() } }
        assertThat(services.afterLock == null).describedAs("the hook ran").isTrue()
    }
}
