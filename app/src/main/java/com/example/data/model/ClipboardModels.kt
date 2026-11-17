package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

enum class ClipboardCategory(val wireValue: String, val label: String) {
    LINK("link", "Links"),
    CODE("code", "Code"),
    ADDRESS("address", "Addresses"),
    TEXT("text", "Plain Text");

    companion object {
        fun fromWire(value: String?): ClipboardCategory =
            entries.firstOrNull { it.wireValue == value } ?: TEXT
    }
}

enum class RetentionPolicy(val wireValue: String, val label: String, val durationMillis: Long?) {
    ONE_HOUR("1h", "1 Hour", 3_600_000L),
    TWENTY_FOUR_HOURS("24h", "24 Hours", 86_400_000L),
    SEVEN_DAYS("7d", "7 Days", 604_800_000L),
    NEVER("never", "Never", null);

    companion object {
        fun fromWire(value: String?): RetentionPolicy =
            entries.firstOrNull { it.wireValue == value } ?: TWENTY_FOUR_HOURS
    }
}

enum class DeviceType(val wireValue: String, val label: String) {
    PHONE("phone", "Phone"),
    TABLET("tablet", "Tablet");

    companion object {
        fun fromWire(value: String?): DeviceType =
            entries.firstOrNull { it.wireValue == value } ?: PHONE
    }
}

enum class SnippetCategory(val wireValue: String, val label: String) {
    MACRO("macro", "Macro"),
    REPLY("reply", "Quick Reply"),
    CODE("code", "Code"),
    TEMPLATE("template", "Template");

    companion object {
        fun fromWire(value: String?): SnippetCategory =
            entries.firstOrNull { it.wireValue == value } ?: MACRO
    }
}

enum class ThemeMode(val wireValue: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromWire(value: String?): ThemeMode =
            entries.firstOrNull { it.wireValue == value } ?: SYSTEM
    }
}

enum class KeyboardLayoutMode(val wireValue: String, val label: String) {
    STANDARD("standard", "Full Width"),
    ONE_HANDED_LEFT("one_handed_left", "One-Handed Left (75%)"),
    ONE_HANDED_RIGHT("one_handed_right", "One-Handed Right (75%)"),
    FLOATING("floating", "Floating Resizable");

    companion object {
        fun fromWire(value: String?): KeyboardLayoutMode =
            entries.firstOrNull { it.wireValue == value } ?: STANDARD
    }
}

@IgnoreExtraProperties
data class FirestoreClipboardEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val content: String = "",
    val category: String = ClipboardCategory.TEXT.wireValue,
    @get:PropertyName("isPinned") @set:PropertyName("isPinned")
    var isPinned: Boolean = false,
    val deviceName: String = "",
    val deviceType: String = DeviceType.PHONE.wireValue,
    val retentionPolicy: String = RetentionPolicy.TWENTY_FOUR_HOURS.wireValue,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreSnippet(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val shortcut: String = "",
    val body: String = "",
    val category: String = SnippetCategory.MACRO.wireValue,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class FirestoreDevice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceName: String = "",
    val deviceType: String = DeviceType.PHONE.wireValue,
    val modelName: String = "",
    val syncEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
