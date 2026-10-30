package com.baogemusic.tv.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** An imported lx-music v4 user source script, mirroring iOS `UserScript`. */
@Serializable
data class UserScript(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val version: String = "",
    val author: String = "",
    val homepage: String = "",
    val rawScript: String,
    val importedAt: Long = System.currentTimeMillis(),
    val enabled: Boolean = true,
    /** true = 随包内置（assets/builtin_scripts）的音源。内置音源升级时会被新版覆盖；用户自己
     *  导入的同名音源不会被内置版本顶掉。旧数据缺该字段时默认 false（即用户导入）。 */
    val builtin: Boolean = false,
)

/** Capabilities a script reports via `lx.send('inited', { sources })`. */
@Serializable
data class ScriptCapabilities(
    val sources: Map<SourceID, SourceCapability> = emptyMap(),
)

@Serializable
data class SourceCapability(
    val type: String = "music",
    val actions: List<String> = emptyList(),
    val qualities: List<Quality> = emptyList(),
)
