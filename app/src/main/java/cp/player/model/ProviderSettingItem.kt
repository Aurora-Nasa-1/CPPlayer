package cp.player.model

import com.google.gson.annotations.SerializedName

/**
 * Represents a single setting item defined by a BackendProvider via `settings/schema`.
 */
data class ProviderSettingItem(
    @SerializedName("key") val key: String,
    @SerializedName("name") val name: String,
    @SerializedName("type") val type: String, // "switch", "input", "select"
    @SerializedName("description") val description: String? = null,
    @SerializedName("defaultValue") val defaultValue: Any? = null,
    @SerializedName("options") val options: List<ProviderSettingOption>? = null
)

data class ProviderSettingOption(
    @SerializedName("label") val label: String,
    @SerializedName("value") val value: Any
)
