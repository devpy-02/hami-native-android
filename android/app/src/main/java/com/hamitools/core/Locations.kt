package com.hamitools.core

// موجودی به تفکیک مکان (همان منطق نسخه‌ی وب)
object Locations {
    /** تغییر موجودی یک مکان. کاهش اول از همان مکان و بعد از بقیه کم می‌شود. */
    fun applyDelta(dist: Map<String, Long>, locationId: String, delta: Long): Map<String, Long> {
        val out = dist.toMutableMap()
        if (delta == 0L) return out
        if (delta > 0) {
            out[locationId] = (out[locationId] ?: 0L) + delta
            return out
        }
        var need = -delta
        val order = listOf(locationId) + out.keys.filter { it != locationId }
        for (k in order) {
            if (need <= 0) break
            val have = out[k] ?: 0L
            val take = minOf(have, need)
            val left = have - take
            need -= take
            if (left <= 0) out.remove(k) else out[k] = left
        }
        return out
    }
}
