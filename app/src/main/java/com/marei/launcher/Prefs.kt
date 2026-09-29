package com.marei.launcher

import android.content.Context

object Prefs {
    const val DESKTOP = "desktop"
    const val START = "start"
    const val TASKBAR = "taskbar"
    const val TASKBAR_LIMIT = 4

    private val FAVORITES = listOf(
        "com.samsung.android.dialer", "com.google.android.dialer",
        "com.samsung.android.messaging", "com.google.android.apps.messaging",
        "com.sec.android.app.camera", "com.sec.android.gallery3d",
        "com.google.android.apps.photos", "com.android.chrome",
        "com.sec.android.app.sbrowser", "com.google.android.gm",
        "com.microsoft.office.outlook", "com.whatsapp",
        "com.google.android.apps.maps", "com.google.android.youtube",
        "com.samsung.android.calendar", "com.google.android.calendar",
        "com.sec.android.app.myfiles", "com.anthropic.claude",
        "com.android.vending", "com.android.settings",
    )
    private val TASKBAR_DEFAULTS = listOf(
        "com.samsung.android.dialer", "com.google.android.dialer",
        "com.samsung.android.messaging", "com.google.android.apps.messaging",
        "com.android.chrome", "com.sec.android.app.sbrowser",
        "com.sec.android.app.camera",
    )

    private fun sp(c: Context) = c.getSharedPreferences("launcher", Context.MODE_PRIVATE)

    fun list(c: Context, key: String): MutableList<String> =
        sp(c).getString(key, null)?.split("\n")?.filter { it.isNotBlank() }?.toMutableList()
            ?: mutableListOf()

    private fun save(c: Context, key: String, items: List<String>) =
        sp(c).edit().putString(key, items.joinToString("\n")).apply()

    fun contains(c: Context, key: String, app: String) = app in list(c, key)

    /** Returns false if the list is full. */
    fun toggle(c: Context, key: String, app: String, limit: Int = Int.MAX_VALUE): Boolean {
        val items = list(c, key)
        if (!items.remove(app)) {
            if (items.size >= limit) return false
            items.add(app)
        }
        save(c, key, items)
        return true
    }

    fun iconStyle(c: Context): String = sp(c).getString("iconStyle", IconStyler.LINE) ?: IconStyler.LINE
    fun setIconStyle(c: Context, style: String) = sp(c).edit().putString("iconStyle", style).apply()

    /** First run: fill desktop, Start and taskbar with common apps. */
    fun ensureDefaults(c: Context, apps: List<AppEntry>) {
        if (sp(c).getBoolean("seeded", false) || apps.isEmpty()) return
        val byPackage = apps.associateBy { it.component.packageName }
        fun pick(pkgs: List<String>, max: Int) =
            pkgs.mapNotNull { byPackage[it]?.key }.distinct().take(max)

        val desktop = pick(FAVORITES, 12).ifEmpty { apps.take(12).map { it.key } }
        val start = (pick(FAVORITES, 16) + apps.map { it.key }).distinct().take(16)
        val taskbar = pick(TASKBAR_DEFAULTS, TASKBAR_LIMIT)

        save(c, DESKTOP, desktop)
        save(c, START, start)
        save(c, TASKBAR, taskbar)
        sp(c).edit().putBoolean("seeded", true).apply()
    }
}
