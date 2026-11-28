package pt.alinha.riskassets.internal

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID

@SpringBootTest(classes = [RiskAssetsTestApplication::class])
class InventorySchemaTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = RiskAssetsDatabase.register(registry)
    }

    @Autowired lateinit var jdbc: JdbcTemplate

    private fun insertService(tenant: UUID, name: String): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "insert into riskassets.critical_service (id, tenant_id, version, name, owner, criticality, created_at, updated_at) " +
                "values (?, ?, 0, ?, 'Ana', 'HIGH', now(), now())",
            id, tenant, name,
        )
        return id
    }

    private fun insertAsset(tenant: UUID, name: String): UUID {
        val id = UUID.randomUUID()
        jdbc.update(
            "insert into riskassets.asset (id, tenant_id, version, name, owner, criticality, type, created_at, updated_at) " +
                "values (?, ?, 0, ?, 'Ana', 'LOW', 'SERVER', now(), now())",
            id, tenant, name,
        )
        return id
    }

    @Test
    fun `the entities match the migration`() {
        // The context started: Hibernate validated the entities against the migrated schema.
        assertThat(jdbc.queryForObject("select count(*) from riskassets.asset", Long::class.java)).isNotNull
    }

    @Test
    fun `deleting a service removes its links and keeps the asset`() {
        val tenant = UUID.randomUUID()
        val s = insertService(tenant, "ERP")
        val a = insertAsset(tenant, "Servidor")
        jdbc.update("insert into riskassets.service_asset (service_id, asset_id) values (?, ?)", s, a)
        jdbc.update("delete from riskassets.critical_service where id = ?", s)
        assertThat(jdbc.queryForObject("select count(*) from riskassets.service_asset where asset_id = ?", Long::class.java, a)).isZero()
        assertThat(jdbc.queryForObject("select count(*) from riskassets.asset where id = ?", Long::class.java, a)).isEqualTo(1)
    }

    @Test
    fun `service names are unique per tenant ignoring case`() {
        val t1 = UUID.randomUUID()
        val t2 = UUID.randomUUID()
        insertService(t1, "ERP")
        assertThatThrownBy { insertService(t1, "erp") }.isInstanceOf(DataIntegrityViolationException::class.java)
        insertService(t2, "erp")
        assertThat(jdbc.queryForObject("select count(*) from riskassets.critical_service where lower(name) = 'erp' and tenant_id in (?, ?)", Long::class.java, t1, t2)).isEqualTo(2)
    }

    @Test
    fun `a link takes the asset's tenant and cannot join two organizations`() {
        val t1 = UUID.randomUUID()
        val s1 = insertService(t1, "ERP")
        val a1 = insertAsset(t1, "Servidor")
        jdbc.update("insert into riskassets.service_asset (service_id, asset_id) values (?, ?)", s1, a1)
        assertThat(jdbc.queryForObject("select tenant_id from riskassets.service_asset where asset_id = ?", UUID::class.java, a1)).isEqualTo(t1)

        val foreignAsset = insertAsset(UUID.randomUUID(), "Portátil")
        assertThatThrownBy {
            jdbc.update("insert into riskassets.service_asset (tenant_id, service_id, asset_id) values (?, ?, ?)", t1, s1, foreignAsset)
        }.isInstanceOf(DataIntegrityViolationException::class.java).satisfies({
            assertThat(violatedConstraint(it)).isEqualTo("service_asset_service_fk")
        })
    }
}
