// SPDX-License-Identifier: GPL-3.0-only
// Ported from InstallerX Revived
package cn.guoyujie666.music.compose.ui.util

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun rememberDeviceCornerRadius(defaultRadius: Dp = 0.dp): Dp {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(context, view, density, defaultRadius) {
        val radiusPx = getDeviceCornerRadiusPx(context, view)
        if (radiusPx > 0) with(density) { radiusPx.toDp() } else defaultRadius
    }
}

private fun getDeviceCornerRadiusPx(context: Context, view: View): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val r = getRoundedCornerRadiusPx(view)
        if (r > 0) return r
    }
    return getCornerRadiusBottom(context)
}

private fun getRoundedCornerRadiusPx(view: View): Int {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0
    val insets = view.rootWindowInsets ?: return 0
    val corner = insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)
        ?: insets.getRoundedCorner(RoundedCorner.POSITION_TOP_RIGHT)
        ?: insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)
        ?: insets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)
    return corner?.radius ?: 0
}

@SuppressLint("DiscouragedApi")
private fun getCornerRadiusBottom(context: Context): Int {
    val res = context.resources
    var id = res.getIdentifier("rounded_corner_radius_bottom", "dimen", "android")
    if (id > 0) return res.getDimensionPixelSize(id)
    id = res.getIdentifier("rounded_corner_radius", "dimen", "android")
    return if (id > 0) res.getDimensionPixelSize(id) else 0
}
