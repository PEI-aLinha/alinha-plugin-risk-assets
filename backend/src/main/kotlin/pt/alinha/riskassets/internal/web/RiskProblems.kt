package pt.alinha.riskassets.internal.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import pt.alinha.riskassets.internal.InvalidRiskException
import pt.alinha.riskassets.internal.RiskNotFoundException
import pt.alinha.riskassets.internal.StaleRiskVersionException

/** RFC 9457 Problem Details with `code` and `errors` (ADR 0003). `detail` is shown to the user, in Portuguese. */
@RestControllerAdvice(basePackageClasses = [RiskController::class])
class RiskProblems {

    @ExceptionHandler
    fun invalid(e: InvalidRiskException) =
        problem(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", "Há dados por corrigir.").apply {
            setProperty("errors", e.errors.map { mapOf("field" to it.field, "message" to it.message) })
        }

    /** A body Jackson cannot read (`"likelihood": "alta"`, an id that is not a UUID, malformed JSON, no body). */
    @ExceptionHandler
    fun unreadable(e: HttpMessageNotReadableException): ProblemDetail {
        val field = unreadableField(e)
        val detail = if (field == null) UNREADABLE_BODY else "Há dados por corrigir."
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_FAILED", detail).apply {
            setProperty("errors", listOfNotNull(field?.let { mapOf("field" to it, "message" to INVALID_VALUE) }))
        }
    }

    /** A risk id in the path that is not a UUID cannot exist: same answer as an unknown one. */
    @ExceptionHandler
    fun badPathOrQuery(e: MethodArgumentTypeMismatchException): ProblemDetail =
        if (e.name == "riskId") notFound(RiskNotFoundException()) else badPage(InvalidPageException())

    @ExceptionHandler
    fun badPage(e: InvalidPageException) = problem(
        HttpStatus.BAD_REQUEST, "INVALID_PAGE", "A página pedida não é válida.",
    )

    @ExceptionHandler
    fun notFound(e: RiskNotFoundException) =
        problem(HttpStatus.NOT_FOUND, "RISK_NOT_FOUND", "Este risco não existe ou foi removido.")

    @ExceptionHandler(StaleRiskVersionException::class, ObjectOptimisticLockingFailureException::class)
    fun stale() = problem(
        HttpStatus.CONFLICT, "VERSION_CONFLICT",
        "O risco foi alterado entretanto. Recarregue a página e volte a fazer as alterações.",
    )

    private fun problem(status: HttpStatus, code: String, detail: String) =
        ProblemDetail.forStatusAndDetail(status, detail).apply { setProperty("code", code) }
}

private const val INVALID_VALUE = "Este valor não é válido."
private const val UNREADABLE_BODY = "Não foi possível ler os dados enviados. Recarregue a página e tente de novo."
