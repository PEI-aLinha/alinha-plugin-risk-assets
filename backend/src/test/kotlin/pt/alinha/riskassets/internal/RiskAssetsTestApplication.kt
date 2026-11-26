package pt.alinha.riskassets.internal

import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.postgresql.PostgreSQLContainer
import pt.alinha.core.access.internal.AccessWebConfig
import pt.alinha.core.access.internal.RequiresPermissionInterceptor
import pt.alinha.core.access.internal.RolePermissionRegistry
import pt.alinha.core.access.internal.SecurityConfig
import pt.alinha.core.audit.internal.AuditActor
import pt.alinha.core.audit.internal.JpaAuditTrail
import pt.alinha.core.registry.PluginRegistry
import pt.alinha.core.tenant.internal.CorrelationIdFilter
import pt.alinha.core.tenant.internal.HibernateTenantResolver
import pt.alinha.core.tenant.internal.JwtTenantContext
import pt.alinha.core.tenant.internal.ScopedCorrelationContext
import pt.alinha.core.tenant.internal.TenantResolutionInterceptor
import pt.alinha.core.tenant.internal.TenantWebConfig
import pt.alinha.riskassets.RiskAssetsPlugin
import pt.alinha.riskassets.internal.web.AssetController
import pt.alinha.riskassets.internal.web.CriticalServiceController
import pt.alinha.riskassets.internal.web.RiskAssetsProblems

/**
 * The plugin with the core's real RBAC, tenant resolution and audit trail on PostgreSQL. Only the JWT decoder is a
 * fake. Services and controllers are added to the `@Import` as they are created.
 * Token value: `<role>.<sub>.<tenant>` (a colon is not allowed in a Bearer token); a consultant's tenant goes to
 * `tenants`, a client user's to `tenant_id`.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan(basePackages = ["pt.alinha.riskassets", "pt.alinha.core.audit"])
@EnableJpaRepositories(basePackages = ["pt.alinha.riskassets"])
@Import(
    SecurityConfig::class, AccessWebConfig::class, RequiresPermissionInterceptor::class,
    RolePermissionRegistry::class, PluginRegistry::class,
    TenantWebConfig::class, TenantResolutionInterceptor::class, JwtTenantContext::class,
    HibernateTenantResolver::class, CorrelationIdFilter::class, ScopedCorrelationContext::class,
    AuditActor::class, JpaAuditTrail::class,
    RiskAssetsPlugin::class,
    CriticalServiceCatalog::class, CriticalServiceController::class, RiskAssetsProblems::class,
    AssetInventory::class, AssetController::class,
)
class RiskAssetsTestApplication {
    @Bean
    @Primary
    fun testJwtDecoder(): JwtDecoder = JwtDecoder { token ->
        val (role, sub, tenant) = (token.split(".") + listOf("", "")).take(3)
        val builder = Jwt.withTokenValue(token).header("alg", "none").subject(sub).claim("email_verified", true)
            .claim("realm_access", mapOf("roles" to listOf(role)))
        when {
            tenant.isEmpty() -> Unit
            role == "consultant" -> builder.claim("tenants", listOf(tenant))
            else -> builder.claim("tenant_id", tenant)
        }
        builder.build()
    }
}

object RiskAssetsDatabase {
    val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:17").apply { start() }

    fun register(registry: DynamicPropertyRegistry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl)
        registry.add("spring.datasource.username", postgres::getUsername)
        registry.add("spring.datasource.password", postgres::getPassword)
        registry.add("spring.jpa.hibernate.ddl-auto") { "validate" }
        // As in the application: a session opened before the tenant is known would keep "no tenant".
        registry.add("spring.jpa.open-in-view") { "false" }
        registry.add("spring.flyway.locations") { "classpath:db/migration" }
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri") { "https://keycloak.test/realms/alinha" }
        registry.add("spring.security.oauth2.resourceserver.jwt.audiences") { "alinha-api" }
    }
}
