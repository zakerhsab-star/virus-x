package com.virusx.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AppConfigDto(
    val id: Int = 1,
    @SerialName("latest_version_code") val latestVersionCode: Int = 1,
    @SerialName("latest_version_name") val latestVersionName: String = "1.0.0",
    @SerialName("apk_download_url") val apkDownloadUrl: String = "",
    @SerialName("release_notes") val releaseNotes: String = "",
    @SerialName("force_update") val forceUpdate: Boolean = false,
    @SerialName("maintenance_mode") val maintenanceMode: Boolean = false,
    @SerialName("maintenance_message") val maintenanceMessage: String = ""
)
