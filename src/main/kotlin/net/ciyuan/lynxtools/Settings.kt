package net.ciyuan.lynxtools

import com.electronwill.nightconfig.core.UnmodifiableConfig
import com.electronwill.nightconfig.toml.TomlParser
import java.nio.file.Files
import java.nio.file.Path

data class Settings(val values: Map<String, Any>, val language: Language) {
    fun bool(key: String) = values.getValue(key) as Boolean
    fun int(key: String) = (values.getValue(key) as Number).toInt()
    fun double(key: String) = (values.getValue(key) as Number).toDouble()
    fun string(key: String) = values.getValue(key) as String
    @Suppress("UNCHECKED_CAST") fun strings(key: String) = values.getValue(key) as List<String>
    fun module(command: String) = bool("modules." + when(command) { "setwarp" -> "warp"; "sethome" -> "home"; "tpr", "tpa", "tpd" -> "requests"; else -> command })
    companion object {
        val restartKeys = setOf("storage.database", "storage.busy_timeout_ms", "storage.queue_capacity", "storage.shutdown_timeout_seconds", "warp.max_name_length", "logging.startup_banner")
        private val ranges = mapOf("storage.busy_timeout_ms" to 0..30000, "storage.queue_capacity" to 16..4096,
            "storage.shutdown_timeout_seconds" to 1..120, "warp.max_name_length" to 1..64,
            "teleport.search_radius" to 0..32, "teleport.max_candidates" to 1..65536,
            "teleport.checks_per_tick" to 1..1024, "teleport.chunk_timeout_seconds" to 1..120,
            "teleport.max_pending" to 1..256, "teleport.chunk_loads_per_tick" to 1..8, "teleport.max_chunk_loads" to 1..32, "teleport.cooldown_seconds" to 0..86400,
            "teleport.wait_seconds" to 0..300, "fly.recheck_ticks" to 1..1200, "fly.fall_protection_ticks" to 1..600,
            "nametags.refresh_ticks" to 1..1200, "nametags.updates_per_tick" to 1..64, "nametags.max_characters" to 16..256,
            "requests.timeout_seconds" to 5..300, "requests.cooldown_seconds" to 0..3600, "requests.max_incoming" to 1..32, "requests.max_pending" to 1..1024)
        fun load(root: Path): Settings {
            val dir = root.resolve("plugins/LynxTools")
            Files.createDirectories(dir.resolve("Lang"))
            install(dir.resolve("Config.toml"), "Config.toml")
            install(dir.resolve("Lang/zh_cn.toml"), "zh_cn.toml")
            val defaults = resource("Config.toml")
            val provided = parse(dir.resolve("Config.toml"))
            require(provided.keys.all { it in defaults }) { "Config.toml: unknown keys ${provided.keys - defaults.keys}" }
            val v = defaults + provided
            v.forEach { (k, value) ->
                val d = defaults.getValue(k)
                require(when(d) { is Boolean -> value is Boolean; is String -> value is String; is Number -> value is Number; is List<*> -> value is List<*> && value.all { it is String }; else -> false }) { "$k: invalid type" }
            }
            ranges.forEach { (k,r) -> val n = (v.getValue(k) as Number).toDouble(); require(n.isFinite() && n % 1.0 == 0.0 && n >= r.first && n <= r.last) { "$k: expected $r" } }
            mapOf("warp.default_yaw" to -180.0..180.0, "warp.default_pitch" to -90.0..90.0, "teleport.move_tolerance" to 0.0..10.0).forEach { (k,r) -> require((v.getValue(k) as Number).toDouble() in r) { "$k: expected $r" } }
            require(v["teleport.strategy"] in setOf("safe", "exact")) { "teleport.strategy: safe/exact" }
            require(v["fly.disable_in_air"] in setOf("protect", "immediate")) { "fly.disable_in_air: protect/immediate" }
            val code = v.getValue("language.code") as String
            require(code.matches(Regex("[a-zA-Z0-9_-]{1,32}"))) { "language.code: invalid code" }
            require((v["storage.database"] as String).isNotBlank()) { "storage.database: empty path" }
            require((v["nametags.name_format"] as String).length <= 2048) { "nametags.name_format: maximum 2048 characters" }
            net.ciyuan.lynxtools.tags.TagFormatting.format("", "Player", "", v["nametags.name_format"] as String, 256)
            listOf("teleport.blocked_source_dimensions", "teleport.blocked_target_dimensions", "teleport.dangerous_blocks").forEach { key ->
                (v[key] as List<*>).forEach { require((it as String).matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+"))) { "$key: invalid resource key" } }
            }
            return Settings(v, Language.load(dir.resolve("Lang/$code.toml")))
        }
        private fun install(path: Path, name: String) {
            if (!Files.exists(path)) Settings::class.java.getResourceAsStream("/defaults/$name")!!.use { Files.copy(it, path) }
        }
        internal fun resource(name: String): Map<String, Any> = Settings::class.java.getResourceAsStream("/defaults/$name")!!.bufferedReader().use { flatten(TomlParser().parse(it)) }
        internal fun parse(path: Path): Map<String, Any> = Files.newBufferedReader(path).use { flatten(TomlParser().parse(it)) }
        private fun flatten(config: UnmodifiableConfig, prefix: String = ""): Map<String, Any> = buildMap {
            config.entrySet().forEach { e -> val v = e.getValue<Any>(); val k = prefix + e.key
                if (v is UnmodifiableConfig) putAll(flatten(v, "$k.")) else put(k, v)
            }
        }
    }
}

data class Language(val values: Map<String, String>) {
    fun text(key: String, args: Map<String, String> = emptyMap()): String = PLACEHOLDER.replace(values.getValue("messages.$key")) { args[it.groupValues[1]] ?: it.value }
    companion object {
        val PLACEHOLDER = Regex("\\{([a-z_]+)}")
        fun load(path: Path): Language {
            val defaults = Settings.resource("zh_cn.toml")
            val input = Settings.parse(path)
            require(input.keys.all { it in defaults }) { "Language: unknown keys ${input.keys - defaults.keys}" }
            val values = (defaults + input).mapValues { (k,v) -> require(v is String) { "Language $k: expected string" }; v }
            values.forEach { (k,v) ->
                if(k.endsWith("_color") || k.startsWith("prefix_") ) require(v.matches(Regex("#[0-9a-fA-F]{6}"))) { "Language $k: expected #RRGGBB" }
                if(k.startsWith("messages.")) {
                    val allowed = PLACEHOLDER.findAll(defaults.getValue(k) as String).map { it.value }.toSet()
                    require(PLACEHOLDER.findAll(v).all { it.value in allowed }) { "Language $k: unknown placeholder" }
                }
            }
            return Language(values)
        }
    }
}
