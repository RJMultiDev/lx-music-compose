package cn.guoyujie666.music.compose.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.navigation3.runtime.NavKey

class Navigator(
    val backStack: MutableList<NavKey>
) {
    fun push(key: NavKey) {
        if (backStack.lastOrNull() == key) return
        backStack.add(key)
    }

    fun pop() {
        if (backStack.size <= 1) return
        backStack.removeLastOrNull()
    }

    fun goBack() = pop()

    fun current(): NavKey? = backStack.lastOrNull()

    fun backStackSize(): Int = backStack.size

    fun canGoBack(): Boolean = backStack.size > 1
}

val LocalNavigator = staticCompositionLocalOf<Navigator> {
    error("LocalNavigator not provided")
}
