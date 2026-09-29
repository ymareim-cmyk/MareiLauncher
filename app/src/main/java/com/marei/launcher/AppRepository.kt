package com.marei.launcher

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process

class AppRepository(private val context: Context, private val launcherApps: LauncherApps) {

    fun load(style: String, iconSizePx: Int): List<AppEntry> {
        val density = context.resources.displayMetrics.densityDpi
        return launcherApps.getActivityList(null, Process.myUserHandle())
            .filter { it.componentName.packageName != context.packageName }
            .map { info ->
                val raw = info.getIcon(density) ?: context.packageManager.defaultActivityIcon
                AppEntry(
                    label = info.label.toString(),
                    component = info.componentName,
                    user = info.user,
                    icon = IconStyler.render(raw, style, iconSizePx),
                )
            }
            .sortedBy { it.label.lowercase() }
    }
}
