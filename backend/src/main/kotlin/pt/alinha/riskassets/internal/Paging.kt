package pt.alinha.riskassets.internal

import org.postgresql.util.PSQLException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

private const val MAX_PAGE_SIZE = 100
private const val MAX_CAUSE_DEPTH = 20

internal fun pageRequest(page: Int, pageSize: Int, sort: Sort): PageRequest {
    if (page < 0) throw InvalidQueryException("page")
    if (pageSize !in 1..MAX_PAGE_SIZE) throw InvalidQueryException("pageSize")
    return PageRequest.of(page, pageSize, sort)
}

/**
 * The constraint PostgreSQL names in its error fields. Not parsed from the message, whose wording depends on the
 * server's `lc_messages` (and so does Hibernate's own extraction).
 */
internal fun violatedConstraint(e: Throwable): String? =
    generateSequence(e) { it.cause }.take(MAX_CAUSE_DEPTH)
        .filterIsInstance<PSQLException>().firstOrNull()?.serverErrorMessage?.constraint
