package pt.alinha.riskassets.internal.domain

import com.fasterxml.jackson.annotation.JsonProperty

enum class AssetType {
    @JsonProperty("server") SERVER,
    @JsonProperty("network") NETWORK,
    @JsonProperty("application") APPLICATION,
    @JsonProperty("database") DATABASE,
    @JsonProperty("equipment") EQUIPMENT,
    @JsonProperty("cloud_service") CLOUD_SERVICE,
    @JsonProperty("storage") STORAGE,
    @JsonProperty("ot_system") OT_SYSTEM,
    @JsonProperty("other") OTHER,
    ;

    companion object {
        /** Maps the lowercase JSON name (`"cloud_service"`) to the constant; anything else is null. */
        fun fromJson(value: String): AssetType? = entries.firstOrNull { it.name.lowercase() == value }
    }
}
