package com.baogemusic.tv.data.store

import android.content.Context
import com.baogemusic.tv.data.model.UserScript
import com.baogemusic.tv.source.SourceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** Stores imported custom-source scripts and (re)loads enabled ones into [SourceManager]. */
class ScriptStore(private val context: Context, private val sourceManager: SourceManager) {
    private val store = JsonStore(
        File(context.filesDir, "scripts.json"),
        ListSerializer(UserScript.serializer()),
        emptyList(),
    )

    private val _scripts = MutableStateFlow<List<UserScript>>(emptyList())
    val scripts: StateFlow<List<UserScript>> = _scripts.asStateFlow()

    /** Load persisted scripts and start the enabled ones, then make sure every bundled
     *  built-in source (the .js files under assets/builtin_scripts) is present — so a fresh
     *  install is playable without hunting for a custom source first. */
    suspend fun loadAll() {
        val list = withContext(Dispatchers.IO) { store.load() }
        _scripts.value = list
        list.filter { it.enabled }.forEach { sourceManager.load(it) }
        ensureBuiltInScripts()
    }

    /** Import the lx v4 scripts shipped in `assets/builtin_scripts`.
     *
     *  Rules: a bundled script is imported when it isn't there yet, or when the installed copy
     *  is itself a built-in one whose source text has changed (i.e. the app shipped an update).
     *  A same-named script the *user* imported by hand is left alone.
     */
    suspend fun ensureBuiltInScripts() {
        val names = withContext(Dispatchers.IO) {
            runCatching { context.assets.list(BUILTIN_DIR)?.toList() }.getOrNull()
        } ?: return
        for (fileName in names.filter { it.endsWith(".js") }.sorted()) {
            val raw = withContext(Dispatchers.IO) {
                runCatching {
                    context.assets.open("$BUILTIN_DIR/$fileName").bufferedReader().use { it.readText() }
                }.getOrNull()
            } ?: continue
            val meta = parseHeader(raw)
            val scriptName = meta["name"]?.takeIf { it.isNotBlank() } ?: fileName.removeSuffix(".js")
            val author = meta["author"] ?: ""
            val existing = _scripts.value.firstOrNull { it.name == scriptName && it.author == author }
            when {
                // 已装且内容一致 → 无事可做
                existing != null && existing.rawScript == raw -> continue
                // 同名但是用户自己导入的（非内置）→ 尊重用户，不覆盖
                existing != null && !existing.builtin -> continue
                else -> runCatching { import(raw, builtin = true) }
            }
        }
    }

    /** Result of [import]: [replaced] is the older copy of the same script (same name + author)
     *  that this import updated in place, or null when it was added as a new source. */
    data class Imported(val script: UserScript, val replaced: UserScript?) {
        val message: String
            get() = if (replaced == null) "已添加音源：${script.name}"
            else "已更新音源：${script.name}（替换了同名旧版本${replaced.version.takeIf { it.isNotBlank() }?.let { " v$it" } ?: ""}）"
    }

    /**
     * Import a raw script: parse its header, load it, persist. Several sources can coexist; only
     * a script with the same name AND author counts as a new version of an existing one and
     * replaces it (keeping its place in the list). The old runtime is unloaded — previously it
     * stayed loaded in [SourceManager] invisibly — and same-named scripts by different authors
     * are kept side by side instead of silently overwriting each other.
     */
    suspend fun import(raw: String, builtin: Boolean = false): Result<Imported> {
        val meta = parseHeader(raw)
        val script = UserScript(
            name = meta["name"] ?: "未命名脚本",
            description = meta["description"] ?: "",
            version = meta["version"] ?: "",
            author = meta["author"] ?: "",
            homepage = meta["homepage"] ?: "",
            rawScript = raw,
            enabled = true,
            builtin = builtin,
        )
        val old = _scripts.value.firstOrNull { it.name == script.name && it.author == script.author }
        val result = sourceManager.load(script)
        return result.fold(
            onSuccess = {
                if (old != null) {
                    sourceManager.unload(old.id)
                    _scripts.value = _scripts.value.map { if (it.id == old.id) script else it }
                } else {
                    _scripts.value = _scripts.value + script
                }
                persist()
                Result.success(Imported(script, old))
            },
            onFailure = { Result.failure(it) },
        )
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val script = _scripts.value.firstOrNull { it.id == id } ?: return
        _scripts.value = _scripts.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        if (enabled) sourceManager.load(script.copy(enabled = true)) else sourceManager.unload(id)
        persist()
    }

    suspend fun remove(id: String) {
        sourceManager.unload(id)
        _scripts.value = _scripts.value.filter { it.id != id }
        persist()
    }

    private suspend fun persist() = withContext(Dispatchers.IO) { store.save(_scripts.value) }

    companion object {
        /** assets 下存放随包内置音源脚本的目录。 */
        private const val BUILTIN_DIR = "builtin_scripts"
        private val headerBlock = Regex("""/\*[\s\S]*?\*/""")
        private val tagLine = Regex("""@(\w+)\s+(.+)""")

        /** Parse the leading KDoc-style header of an lx v4 script (@name / @version / etc). */
        fun parseHeader(raw: String): Map<String, String> {
            val block = headerBlock.find(raw)?.value ?: return emptyMap()
            val out = mutableMapOf<String, String>()
            tagLine.findAll(block).forEach { m ->
                out[m.groupValues[1].lowercase()] = m.groupValues[2].trim().removeSuffix("*").trim()
            }
            return out
        }
    }
}
