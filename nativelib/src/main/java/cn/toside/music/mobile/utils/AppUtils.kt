package cn.toside.music.mobile.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.Window
import android.view.WindowManager

/**
 * Kotlin wrapper replacing the RN UtilsModule.
 * All utility methods that were previously exposed via @ReactMethod.
 */
object AppUtils {

    fun exitApp(activity: Activity?) {
        if (activity == null) {
            android.os.Process.killProcess(android.os.Process.myPid())
        } else {
            activity.finishAndRemoveTask()
            System.exit(0)
        }
    }

    fun getSupportedAbis(): List<String> = Build.SUPPORTED_ABIS.toList()

    fun keepScreenAwake(activity: Activity?) {
        activity?.runOnUiThread {
            activity.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun unkeepScreenAwake(activity: Activity?) {
        activity?.runOnUiThread {
            activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) {
            model.replaceFirstChar { it.uppercase() }
        } else {
            "${manufacturer.replaceFirstChar { it.uppercase() }} $model"
        }
    }

    fun shareText(activity: Activity, shareTitle: String, title: String, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        activity.startActivity(Intent.createChooser(intent, shareTitle))
    }

    fun isNotificationsEnabled(context: Context): Boolean =
        NotificationPermissionUtil.isNotificationsEnabled(context)

    fun openNotificationPermissionActivity(context: Context): Boolean =
        NotificationPermissionUtil.openNotificationPermissionActivity(context)

    fun isIgnoringBatteryOptimization(context: Context, packageName: String): Boolean =
        BatteryOptimizationUtil.isIgnoringBatteryOptimization(context, packageName)

    fun requestIgnoreBatteryOptimization(context: Context, packageName: String): Boolean =
        BatteryOptimizationUtil.requestIgnoreBatteryOptimization(context, packageName)
}
