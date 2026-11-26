package pt.alinha.riskassets.internal.domain

import java.util.UUID

data class FieldError(val field: String, val message: String)

sealed interface Validation<out T> {
    data class Valid<T>(val value: T) : Validation<T>
    data class Invalid(val errors: List<FieldError>) : Validation<Nothing>
}

data class CriticalServiceInput(
    val name: String? = null,
    val description: String? = null,
    val owner: String? = null,
    val criticality: Criticality? = null,
)

data class CriticalServiceData(
    val name: String,
    val description: String?,
    val owner: String,
    val criticality: Criticality,
)

data class AssetInput(
    val name: String? = null,
    val description: String? = null,
    val owner: String? = null,
    val criticality: Criticality? = null,
    val type: AssetType? = null,
    val serviceIds: List<UUID>? = null,
)

data class AssetData(
    val name: String,
    val type: AssetType,
    val description: String?,
    val owner: String,
    val criticality: Criticality,
    val serviceIds: Set<UUID>,
)

/** Maximum lengths of the free-text fields (also enforced by the database). */
internal object InventoryLimits {
    const val NAME = 200
    const val OWNER = 120
    const val DESCRIPTION = 2000
    const val SERVICES_PER_ASSET = 100
}

/** Validates and normalises critical services and assets. Messages are plain European Portuguese. */
object InventoryValidator {

    fun service(input: CriticalServiceInput, extra: List<FieldError> = emptyList()): Validation<CriticalServiceData> {
        val errors = extra.toMutableList()
        val common = common(input.name, input.description, input.owner, input.criticality, errors)
        if (errors.isNotEmpty()) return Validation.Invalid(errors)
        return Validation.Valid(CriticalServiceData(common.name!!, common.description, common.owner!!, common.criticality!!))
    }

    fun asset(input: AssetInput, extra: List<FieldError> = emptyList()): Validation<AssetData> {
        val errors = extra.toMutableList()
        val common = common(input.name, input.description, input.owner, input.criticality, errors)
        if (input.type == null) errors += FieldError("type", "Escolha o tipo de ativo.")
        val serviceIds = input.serviceIds.orEmpty().toSet()
        if (serviceIds.size > InventoryLimits.SERVICES_PER_ASSET) {
            errors += FieldError("serviceIds", "Pode ligar até ${InventoryLimits.SERVICES_PER_ASSET} serviços.")
        }
        if (errors.isNotEmpty()) return Validation.Invalid(errors)
        return Validation.Valid(
            AssetData(common.name!!, input.type!!, common.description, common.owner!!, common.criticality!!, serviceIds),
        )
    }

    private class Common(val name: String?, val description: String?, val owner: String?, val criticality: Criticality?)

    private fun common(
        rawName: String?,
        rawDescription: String?,
        rawOwner: String?,
        criticality: Criticality?,
        errors: MutableList<FieldError>,
    ): Common {
        val name = rawName?.trim().orEmpty()
        when {
            name.isEmpty() -> errors += FieldError("name", "Indique o nome.")
            name.length > InventoryLimits.NAME -> errors += FieldError("name", "O nome pode ter até ${InventoryLimits.NAME} caracteres.")
        }
        val owner = rawOwner?.trim().orEmpty()
        when {
            owner.isEmpty() -> errors += FieldError("owner", "Indique quem é o responsável.")
            owner.length > InventoryLimits.OWNER ->
                errors += FieldError("owner", "O responsável pode ter até ${InventoryLimits.OWNER} caracteres.")
        }
        val description = rawDescription?.trim()?.ifEmpty { null }
        if (description != null && description.length > InventoryLimits.DESCRIPTION) {
            errors += FieldError("description", "A descrição pode ter até ${InventoryLimits.DESCRIPTION} caracteres.")
        }
        if (criticality == null) errors += FieldError("criticality", "Escolha a criticidade.")
        return Common(name, description, owner, criticality)
    }
}
