package com.sally.launcher.data.model

data class MinecraftVersion(
    val id: String,
    val type: String,
    val url: String,
    val releaseTime: String,
) {
    fun displayLabel(): String = "$id  ·  ${type.replaceFirstChar { it.uppercase() }}"
}

data class VersionCatalog(
    val versions: List<MinecraftVersion>,
    val latestReleaseId: String?,
    val latestSnapshotId: String?,
    val loadedFromCache: Boolean,
)
