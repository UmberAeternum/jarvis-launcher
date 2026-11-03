package com.umberaeternum.jarvis.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle

/** One launchable app in the drawer / radial ring. */
data class AppEntry(
    val label: String,
    val packageName: String,
    val activity: String,
    val icon: Drawable,
)

object AppCatalog {
    fun load(context: Context): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = context.packageManager.queryIntentActivities(intent, 0)
        return resolveInfos
            .map { info ->
                AppEntry(
                    label = info.loadLabel(context.packageManager).toString(),
                    packageName = info.activityInfo.packageName,
                    activity = info.activityInfo.name,
                    icon = info.loadIcon(context.packageManager),
                )
            }
            .sortedBy { it.label.lowercase() }
            .filter { it.packageName != context.packageName } // JARVIS itself is the home button
    }

    fun launch(context: Context, entry: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setClassName(entry.packageName, entry.activity)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent, Bundle())
    }
}
