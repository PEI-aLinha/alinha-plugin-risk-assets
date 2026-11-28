package pt.alinha.riskassets.internal.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import pt.alinha.riskassets.internal.AssetNotFoundException
import pt.alinha.riskassets.internal.CriticalServiceNotFoundException
import pt.alinha.riskassets.internal.InvalidInventoryDataException
import pt.alinha.riskassets.internal.InvalidQueryException
import pt.alinha.riskassets.internal.ServiceNameTakenException
import pt.alinha.riskassets.internal.StaleVersionException
import tools.jackson.core.JacksonException

/** RFC 9457 Problem Details with `code` and `errors` (ADR 0003). `detail` is shown to the user, in Portuguese. */
@RestControllerAdvice(basePackageClasses = [CriticalServiceController::class])
class RiskAssetsProblems {

    @ExceptionHandler
    fun invalid(e: InvalidInventoryDataException) =
        problem(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Há dados por corrigir.").apply {
            setProperty("errors", e.errors.map { mapOf("field" to it.field, "message" to it.message) })
        }

    /** A body Jackson cannot read (wrong type, unknown enum value, malformed JSON, no body), naming the field if known. */
    @ExceptionHandler
    fun unreadable(e: HttpMessageNotReadableException): ProblemDetail {
        val field = unreadableField(e)
        val detail = if (field == null) UNREADABLE_BODY else "Há dados por corrigir."
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", detail).apply {
            setProperty("errors", listOfNotNull(field?.let { mapOf("field" to it, "message" to INVALID_VALUE) }))
        }
    }

    @ExceptionHandler
    fun badQuery(e: InvalidQueryException) = badParameter(e.field)

    @ExceptionHandler
    fun badParameter(e: MethodArgumentTypeMismatchException) = badParameter(e.name)

    @ExceptionHandler
    fun serviceNotFound(e: CriticalServiceNotFoundException) =
        problem(HttpStatus.NOT_FOUND, "SERVICE_NOT_FOUND", "Este serviço não existe ou foi apagado.")

    @ExceptionHandler
    fun assetNotFound(e: AssetNotFoundException) =
        problem(HttpStatus.NOT_FOUND, "ASSET_NOT_FOUND", "Este ativo não existe ou foi apagado.")

    @ExceptionHandler(StaleVersionException::class, ObjectOptimisticLockingFailureException::class)
    fun stale() = problem(
        HttpStatus.CONFLICT, "VERSION_CONFLICT",
        "Os dados foram alterados entretanto. Recarregue a página e volte a fazer as alterações.",
    )

    @ExceptionHandler
    fun nameTaken(e: ServiceNameTakenException) =
        problem(HttpStatus.CONFLICT, "SERVICE_NAME_TAKEN", "Já existe um serviço com este nome.")

    private fun badParameter(field: String) =
        problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Há dados por corrigir.").apply {
            setProperty("errors", listOf(mapOf("field" to field, "message" to INVALID_VALUE)))
        }

    private fun problem(status: HttpStatus, code: String, detail: String) =
        ProblemDetail.forStatusAndDetail(status, detail).apply { setProperty("code", code) }
}

private const val INVALID_VALUE = "Este valor não é válido."
private const val UNREADABLE_BODY = "Não foi possível ler os dados enviados. Recarregue a página e tente de novo."
private const val MAX_CAUSE_DEPTH = 20

/** The body's field Jackson failed on, in dotted notation, or null if unknown. */
internal fun unreadableField(e: Throwable): String? =
    generateSequence(e) { it.cause }.take(MAX_CAUSE_DEPTH)
        .filterIsInstance<JacksonException>().firstOrNull()
        ?.path?.mapNotNull { it.propertyName }?.joinToString(".")?.ifEmpty { null }
