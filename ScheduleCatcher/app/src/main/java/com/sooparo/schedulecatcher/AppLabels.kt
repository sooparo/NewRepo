package com.sooparo.schedulecatcher

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

object AppLabels {
    fun label(context: Context, pkg: String): String {
        Prefs.DEFAULT_PACKAGES[pkg]?.let { return it }
        val pm = context.packageManager
        return runCatching {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getApplicationInfo(pkg, 0)
            }
            pm.getApplicationLabel(info).toString()
        }.getOrDefault(pkg)
    }
}
