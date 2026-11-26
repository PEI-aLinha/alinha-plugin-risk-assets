package pt.alinha.riskassets.internal.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.UUID

class InventoryValidatorTest {

    @Test
    fun `trims name and owner and turns a blank description into null`() {
        val v = InventoryValidator.service(CriticalServiceInput("  ERP  ", "   ", " Diretor de TI ", Criticality.HIGH))
        assertThat((v as Validation.Valid).value).isEqualTo(CriticalServiceData("ERP", null, "Diretor de TI", Criticality.HIGH))
    }

    @Test
    fun `inner whitespace collapses and control characters are dropped from single-line fields`() {
        val v = InventoryValidator.service(CriticalServiceInput("ERP  Vendas", null, "Ana\n\u0007  Costa", Criticality.LOW))
        assertThat((v as Validation.Valid).value.name).isEqualTo("ERP Vendas")
        assertThat(v.value.owner).isEqualTo("Ana Costa")
        val w = InventoryValidator.service(CriticalServiceInput("ERP\nVendas\u0000", null, "Ana", Criticality.LOW))
        assertThat((w as Validation.Valid).value.name).isEqualTo("ERP Vendas")
    }

    @Test
    fun `description keeps its line breaks`() {
        val v = InventoryValidator.service(CriticalServiceInput("A", " linha 1\r\nlinha\u0007 2 ", "Ana", Criticality.LOW))
        assertThat((v as Validation.Valid).value.description).isEqualTo("linha 1\nlinha 2")
    }

    @Test
    fun `json values come from the same names as the contract`() {
        assertThat(Criticality.HIGH.json).isEqualTo("high")
        assertThat(AssetType.CLOUD_SERVICE.json).isEqualTo("cloud_service")
    }

    @Test
    fun `missing or blank fields get one message each`() {
        val v = InventoryValidator.asset(AssetInput(name = "  ", owner = null)) as Validation.Invalid
        assertThat(v.errors).containsExactlyInAnyOrder(
            FieldError("name", "Indique o nome."),
            FieldError("type", "Escolha o tipo de ativo."),
            FieldError("owner", "Indique quem é o responsável."),
            FieldError("criticality", "Escolha a criticidade."),
        )
    }

    @ParameterizedTest
    @CsvSource(
        "name,201,O nome pode ter até 200 caracteres.",
        "owner,121,O responsável pode ter até 120 caracteres.",
        "description,2001,A descrição pode ter até 2000 caracteres.",
    )
    fun `too long`(field: String, length: Int, message: String) {
        val long = "a".repeat(length)
        val input = AssetInput(
            name = if (field == "name") long else "Servidor",
            description = if (field == "description") long else "ok",
            owner = if (field == "owner") long else "Ana",
            criticality = Criticality.LOW,
            type = AssetType.SERVER,
        )
        val v = InventoryValidator.asset(input) as Validation.Invalid
        assertThat(v.errors).containsExactly(FieldError(field, message))
    }

    @Test
    fun `at the limit is valid`() {
        val input = CriticalServiceInput("a".repeat(200), "a".repeat(2000), "a".repeat(120), Criticality.LOW)
        assertThat(InventoryValidator.service(input)).isInstanceOf(Validation.Valid::class.java)
    }

    @Test
    fun `duplicate serviceIds collapse, more than 100 is an error`() {
        val id = UUID.randomUUID()
        val base = AssetInput("A", null, "Ana", Criticality.LOW, AssetType.SERVER)
        val ok = InventoryValidator.asset(base.copy(serviceIds = listOf(id, id))) as Validation.Valid
        assertThat(ok.value.serviceIds).isEqualTo(setOf(id))
        val many = List(101) { UUID.randomUUID() }
        val bad = InventoryValidator.asset(base.copy(serviceIds = many)) as Validation.Invalid
        assertThat(bad.errors).containsExactly(FieldError("serviceIds", "Pode ligar até 100 serviços."))
        val repeated = InventoryValidator.asset(base.copy(serviceIds = List(101) { id })) as Validation.Invalid
        assertThat(repeated.errors).containsExactly(FieldError("serviceIds", "Pode ligar até 100 serviços."))
        val hundred = InventoryValidator.asset(base.copy(serviceIds = List(100) { id })) as Validation.Valid
        assertThat(hundred.value.serviceIds).isEqualTo(setOf(id))
        assertThat(InventoryValidator.asset(base.copy(serviceIds = null))).isInstanceOf(Validation.Valid::class.java)
    }

    @Test
    fun `extra errors are kept and make the result invalid`() {
        val extra = FieldError("serviceIds", "Algum serviço não existe.")
        val v = InventoryValidator.service(CriticalServiceInput("ERP", null, "Ana", Criticality.LOW), listOf(extra))
        assertThat((v as Validation.Invalid).errors).containsExactly(extra)
    }

    @Test
    fun `fromJson maps lowercase names only`() {
        assertThat(Criticality.fromJson("high")).isEqualTo(Criticality.HIGH)
        assertThat(Criticality.fromJson("HIGH")).isNull()
        assertThat(Criticality.fromJson("x")).isNull()
        assertThat(AssetType.fromJson("cloud_service")).isEqualTo(AssetType.CLOUD_SERVICE)
        assertThat(AssetType.fromJson("ot_system")).isEqualTo(AssetType.OT_SYSTEM)
        assertThat(AssetType.fromJson("OT_SYSTEM")).isNull()
    }
}
