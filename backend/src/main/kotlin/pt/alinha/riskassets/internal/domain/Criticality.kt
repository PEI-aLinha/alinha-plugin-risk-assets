package pt.alinha.riskassets.internal.domain

import com.fasterxml.jackson.annotation.JsonProperty

enum class Criticality {
    @JsonProperty("low") LOW,
    @JsonProperty("medium") MEDIUM,
    @JsonProperty("high") HIGH,
    ;

    companion object {
        /** Maps the lowercase JSON name (`"high"`) to the constant; anything else is null. */
        fun fromJson(value: String): Criticality? = entries.firstOrNull { it.name.lowercase() == value }
    }
}
