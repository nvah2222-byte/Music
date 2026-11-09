package com.example.music.data

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Резервная копия SharedPreferences: ключи и типы сохраняются как есть,
 * поэтому новые настройки не нужно добавлять в сериализацию вручную.
 * Long и Float помечаем: JSON не различает их и Int/Double при чтении.
 */
fun dumpPrefs(prefs: SharedPreferences): JSONObject = JSONObject().apply {
    prefs.all.forEach { (key, value) ->
        when (value) {
            is Boolean, is Int, is String -> put(key, value)
            is Long -> put(key, JSONObject().put("long", value))
            is Float -> put(key, JSONObject().put("float", value))
            is Set<*> -> put(key, JSONArray().apply {
                value.filterIsInstance<String>().forEach { put(it) }
            })
        }
    }
}

fun restorePrefs(prefs: SharedPreferences, json: JSONObject) {
    // Сначала проверяем, что из файла вообще есть что восстанавливать:
    // иначе пустой/битый бэкап затрёт текущие данные.
    var supportedCount = 0
    json.keys().forEach { key ->
        when (val value = json.opt(key)) {
            is Boolean, is Int, is String, is JSONArray -> supportedCount++
            is JSONObject -> if (value.has("long") || value.has("float")) supportedCount++
        }
    }
    require(supportedCount > 0) { "Пустая резервная копия" }

    val editor = prefs.edit().clear()
    json.keys().forEach { key ->
        when (val value = json.opt(key)) {
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is String -> editor.putString(key, value)
            is JSONObject -> {
                if (value.has("long")) {
                    editor.putLong(key, value.optLong("long"))
                } else if (value.has("float")) {
                    editor.putFloat(key, value.optDouble("float").toFloat())
                }
            }
            is JSONArray -> {
                val set = buildSet {
                    for (index in 0 until value.length()) {
                        value.optString(index).takeIf { it.isNotEmpty() }?.let(::add)
                    }
                }
                editor.putStringSet(key, set)
            }
        }
    }
    editor.commit()
}
