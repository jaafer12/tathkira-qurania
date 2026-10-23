package com.jaafer.tvremote

import android.content.Context
import com.jaafer.tvremote.tv.TvDevice

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("remote", Context.MODE_PRIVATE)

    var lastDevice: TvDevice?
        get() = sp.getString("host", null)?.let { TvDevice(sp.getString("name", null) ?: it, it) }
        set(value) {
            sp.edit().putString("host", value?.host).putString("name", value?.name).apply()
        }

    var touchpad: Boolean
        get() = sp.getBoolean("touchpad", false)
        set(value) = sp.edit().putBoolean("touchpad", value).apply()

    val savedDevices: List<TvDevice>
        get() = sp.getStringSet("saved", emptySet()).orEmpty()
            .mapNotNull { entry -> entry.split(SEP).takeIf { it.size == 2 }?.let { TvDevice(it[0], it[1]) } }
            .sortedBy { it.name }

    fun save(device: TvDevice) = writeSaved(savedDevices.filterNot { it.host == device.host } + device)

    fun forget(device: TvDevice) {
        writeSaved(savedDevices.filterNot { it.host == device.host })
        if (lastDevice?.host == device.host) lastDevice = null
    }

    private fun writeSaved(list: List<TvDevice>) {
        sp.edit().putStringSet("saved", list.map { it.name + SEP + it.host }.toSet()).apply()
    }

    private companion object {
        const val SEP = "\u0001"
    }
}
