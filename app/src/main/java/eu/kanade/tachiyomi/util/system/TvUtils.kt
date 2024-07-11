package eu.kanade.tachiyomi.util.system

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build

fun isTvBox(context: Context): Boolean {
    val pm: PackageManager = context.packageManager

    // TV for sure
    if (
        pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        context.getSystemService(UiModeManager::class.java)
            .getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION
    ) {
        return true
    }

    // Missing Files app (DocumentsUI) means box (some boxes still have non functional app or stub)
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
    intent.addCategory(Intent.CATEGORY_OPENABLE)
    intent.setType("video/*")
    if (intent.resolveActivity(pm) == null) {
        return true
    }

    // Legacy storage no longer works on Android 11 (level 30)
    if (Build.VERSION.SDK_INT < 30) {
        // (Some boxes still report touchscreen feature)
        if (!pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
            return true
        }
        if (pm.hasSystemFeature("android.hardware.hdmi.cec")) {
            return true
        }
        if (Build.MANUFACTURER.equals("zidoo", ignoreCase = true)) {
            return true
        }
    }

    // Default: No TV - use SAF
    return false
}
