package pt.alinha.riskassets.internal

import pt.alinha.riskassets.internal.domain.FieldError

class InvalidRiskException(val errors: List<FieldError>) : RuntimeException("Invalid risk data")
class RiskNotFoundException : RuntimeException("Risk not found")
class StaleRiskVersionException : RuntimeException("Stale risk version")
