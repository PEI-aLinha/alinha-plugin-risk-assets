package pt.alinha.riskassets.internal

import pt.alinha.riskassets.internal.domain.FieldError

class InvalidInventoryDataException(val errors: List<FieldError>) : RuntimeException("Invalid inventory data")

class CriticalServiceNotFoundException : RuntimeException("Critical service not found")

class AssetNotFoundException : RuntimeException("Asset not found")

class StaleVersionException : RuntimeException("Stale version")

class ServiceNameTakenException : RuntimeException("Critical service name already used")

/** A bad query parameter (`page`, `pageSize` or a malformed filter); [field] names it. */
class InvalidQueryException(val field: String) : RuntimeException("Invalid query parameter $field")
