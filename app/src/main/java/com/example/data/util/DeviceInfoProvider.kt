package com.example.data.util

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.example.data.model.DeviceType

data class LocalDeviceProfile(
    val deviceId: String,
    val deviceName: String,
    val modelName: String,
    val deviceType: DeviceType
)

object DeviceInfoProvider {
    fun getProfile(context: Context): LocalDeviceProfile {
        val rawId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: "android_local"
        val sanitizedId = "dev_${rawId.replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(24).ifEmpty { "default" }}"
        val swDp = context.resources.configuration.smallestScreenWidthDp
        val deviceType = if (swDp >= 600) DeviceType.TABLET else DeviceType.PHONE
        val model = Build.MODEL?.trim().orEmpty().ifEmpty { "Android Device" }
        val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
        val friendlyName = when {
            model.contains("Pixel", ignoreCase = true) -> model
            model.contains("Pad", ignoreCase = true) -> model
            manufacturer.isNotBlank() && !model.startsWith(manufacturer, ignoreCase = true) ->
                "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
            else -> model
        }.take(80)

        return LocalDeviceProfile(
            deviceId = sanitizedId,
            deviceName = friendlyName,
            modelName = model.take(80),
            deviceType = deviceType
        )
    }
}
