package pt.alinha.riskassets.internal.domain

import com.fasterxml.jackson.annotation.JsonProperty

enum class Criticality {
    @JsonProperty("low") LOW,
    @JsonProperty("medium") MEDIUM,
    @JsonProperty("high") HIGH,
    ;

    /** The contract's value (`"high"`), read from the `@JsonProperty` on the constant: one source of truth. */
    val json: String by lazy { declaringJavaClass.getField(name).getAnnotation(JsonProperty::class.java).value }

    companion object {
        /** Maps the lowercase JSON name (`"high"`) to the constant; anything else is null. */
        fun fromJson(value: String): Criticality? = entries.firstOrNull { it.json == value }
    }
}
