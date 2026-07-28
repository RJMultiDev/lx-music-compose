package cn.guoyujie666.music.compose

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import cn.guoyujie666.music.compose.ui.navigation.AppNavigation
import cn.guoyujie666.music.compose.ui.theme.LxMusicTheme
import cn.guoyujie666.music.compose.ui.theme.resolveBackgroundImageRes

@Composable
fun LxMusicApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val alert by viewModel.userApiAlert.collectAsState()
    val context = LocalContext.current

    val colorScheme by viewModel.themeManager.colorScheme.collectAsState()
    val isDark by viewModel.themeManager.isDark.collectAsState()
    val bgImageName by viewModel.themeManager.backgroundImage.collectAsState()
    val themeId by viewModel.themeManager.themeId.collectAsState()
    val primaryColor by viewModel.themeManager.primaryColor.collectAsState()
    val bgImageRes = resolveBackgroundImageRes(context, bgImageName)

    LxMusicTheme(
        colorScheme = colorScheme,
        isDark = isDark,
        backgroundImageRes = bgImageRes,
        themeId = themeId,
        primaryColor = primaryColor
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (uiState.isAppInitialized) {
                AppNavigation()
            } else {
                SplashScreen(onComplete = { viewModel.setAppInitialized() })
            }
        }

        alert?.let { a ->
            AlertDialog(
                onDismissRequest = { viewModel.dismissUpdateAlert() },
                title = { Text(if (a.isError) "音源初始化失败 - ${a.name}" else "音源更新 - ${a.name}") },
                text = { Text(text = a.log, style = MaterialTheme.typography.bodyMedium) },
                confirmButton = {
                    if (a.updateUrl != null) {
                        TextButton(onClick = {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.updateUrl))) } catch (_: Exception) {}
                            viewModel.dismissUpdateAlert()
                        }) { Text("更新") }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissUpdateAlert() }) { Text("关闭") }
                }
            )
        }
    }
}

@Composable
private fun SplashScreen(onComplete: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { onComplete() }
}
