package com.marei.launcher

import android.content.ComponentName
import android.graphics.Bitmap
import android.os.UserHandle

data class AppEntry(
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val icon: Bitmap,
) {
    val key: String get() = component.flattenToString()
}
