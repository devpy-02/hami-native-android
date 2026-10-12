package com.hamitools.data

import androidx.room.TypeConverter
import org.json.JSONArray
import org.json.JSONObject

// تبدیل فهرست و نقشه به متن JSON برای ذخیره در دیتابیس
class Converters {

    @TypeConverter
    fun listToJson(list: List<String>): String = JSONArray(list).toString()

    @TypeConverter
    fun jsonToList(s: String): List<String> {
        val arr = JSONArray(s)
        return (0 until arr.length()).map { arr.getString(it) }
    }

    @TypeConverter
    fun mapToJson(map: Map<String, Long>): String = JSONObject(map as Map<*, *>).toString()

    @TypeConverter
    fun jsonToMap(s: String): Map<String, Long> {
        val o = JSONObject(s)
        return o.keys().asSequence().associateWith { o.getLong(it) }
    }
}
