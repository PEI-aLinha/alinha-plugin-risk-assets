package pt.alinha.riskassets.internal.domain

import com.fasterxml.jackson.annotation.JsonProperty

enum class AssetType {
    @JsonProperty("server") SERVER,
    @JsonProperty("network") NETWORK,
    @JsonProperty("application") APPLICATION,
    @JsonProperty("database") DATABASE,
    @JsonProperty("equipment") EQUIPMENT,
    @JsonProperty("cloud-service") CLOUD_SERVICE,
    @JsonProperty("storage") STORAGE,
    @JsonProperty("ot-system") OT_SYSTEM,
    @JsonProperty("other") OTHER,
    ;

    /** The contract's value (`"high"`), read from the `@JsonProperty` on the constant: one source of truth. */
    val json: String by lazy { declaringJavaClass.getField(name).getAnnotation(JsonProperty::class.java).value }

    companion object {
        /** Maps the kebab-case JSON name (`"cloud-service"`) to the constant; anything else is null. */
        fun fromJson(value: String): AssetType? = entries.firstOrNull { it.json == value }
    }
}
