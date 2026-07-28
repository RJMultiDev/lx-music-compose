package cn.guoyujie666.music.compose.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneInfo
import androidx.navigation3.scene.SinglePaneSceneStrategy
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.NavDisplayTransitionEffects
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.NavigationEventState
import androidx.navigationevent.compose.rememberNavigationEventState
import cn.guoyujie666.music.compose.ui.animation.MiuixPredictiveBackAnimation
import cn.guoyujie666.music.compose.ui.animation.PredictiveBackAnimationHandler
import cn.guoyujie666.music.compose.ui.comment.CommentScreen
import cn.guoyujie666.music.compose.ui.home.HomeScreen
import cn.guoyujie666.music.compose.ui.home.tabs.SettingsCategoryDetail
import cn.guoyujie666.music.compose.ui.playdetail.PlayDetailScreen
import cn.guoyujie666.music.compose.ui.songlistdetail.SonglistDetailScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavigation() {
    val predictiveBackHandler: PredictiveBackAnimationHandler = remember { MiuixPredictiveBackAnimation() }

    val backStack = rememberNavBackStack(HomeRoute)
    val navigator = remember(backStack) { Navigator(backStack) }
    val navigationScope = rememberCoroutineScope()

    CompositionLocalProvider(LocalNavigator provides navigator) {
        var gestureState: NavigationEventState<SceneInfo<NavKey>>? = null

        val onBack: (() -> Unit) -> Unit = { callback ->
            navigationScope.launch {
                predictiveBackHandler.onBackPressed(
                    gestureState?.transitionState,
                    navigator.current()
                )
                callback()
                navigator.pop()
            }
        }

        val entries = rememberDecoratedNavEntries(
            backStack = backStack,
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
                NavEntryDecorator(
                    onPop = { key ->
                        predictiveBackHandler.onPagePop(key, navigationScope)
                    }
                ) { content ->
                    with(predictiveBackHandler) {
                        Box(
                            modifier = Modifier.predictiveBackAnimationDecorator(
                                gestureState?.transitionState,
                                content.contentKey,
                                navigator.current()
                            )
                        ) {
                            content.Content()
                        }
                    }
                }
            ),
            entryProvider = entryProvider {
                entry<HomeRoute> { HomeScreen(navigator = navigator) }

                entry<PlayDetailRoute> {
                    PlayDetailScreen(
                        onBack = { navigator.goBack() },
                        onShowComment = { navigator.push(CommentRoute) }
                    )
                }

                entry<SonglistDetailRoute> { key ->
                    SonglistDetailScreen(listId = key.listId, source = key.source, onBack = { navigator.goBack() })
                }

                entry<CommentRoute> {
                    CommentScreen(musicInfo = null, onBack = { navigator.goBack() })
                }

                entry<SettingsDetailRoute> { key ->
                    SettingsCategoryDetail(category = key.category, onBack = { navigator.goBack() })
                }
            }
        )

        val sceneState = rememberSceneState(
            entries = entries,
            sceneStrategies = listOf(SinglePaneSceneStrategy()),
            sceneDecoratorStrategies = emptyList(),
            sharedTransitionScope = null,
            onBack = { onBack {} }
        )
        val scene = sceneState.currentScene

        // Predictive back gesture state
        val currentInfo = SceneInfo(scene)
        val previousSceneInfos = sceneState.previousScenes.map { SceneInfo(it) }
        gestureState = rememberNavigationEventState(
            currentInfo = currentInfo,
            backInfo = previousSceneInfos
        )

        NavigationBackHandler(
            state = gestureState,
            isBackEnabled = scene.previousEntries.isNotEmpty(),
            onBackCompleted = { onBack {} },
            onBackCancelled = {}
        )

        NavDisplay(
            sceneState = sceneState,
            navigationEventState = gestureState,
            transitionEffects = NavDisplayTransitionEffects(
                blockInputDuringTransition = true
            ),
            predictivePopTransitionSpec = { swipeEdge ->
                with(predictiveBackHandler) { onPredictivePopTransitionSpec(swipeEdge) }
            },
            popTransitionSpec = {
                with(predictiveBackHandler) { onPopTransitionSpec() }
            },
            transitionSpec = {
                with(predictiveBackHandler) { onTransitionSpec() }
            }
        )
    }
}
