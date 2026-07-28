package cn.guoyujie666.music.compose.ui.animation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEvent.Companion.EDGE_LEFT
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import cn.guoyujie666.music.compose.ui.util.rememberDeviceCornerRadius
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AOSPCrossActivityAnimation : PredictiveBackAnimationHandler {
    private var exitingPageKey: String? = null
    private val exitAnimatable = Animatable(0f)
    private var inPredictiveBackAnimation = false

    override suspend fun onBackPressed(
        transitionState: NavigationEventTransitionState?,
        currentPageKey: NavKey?,
    ) {
        val isInterruptingEnter = transitionState is InProgress && !inPredictiveBackAnimation
        if (!isInterruptingEnter) {
            exitingPageKey = currentPageKey.toString()
            exitAnimatable.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 150, easing = LinearEasing)
            )
        }
    }

    override fun onPagePop(contentPageKey: Any, animationScope: CoroutineScope) {
        if (exitingPageKey == contentPageKey) {
            exitingPageKey = null
            animationScope.launch { exitAnimatable.snapTo(0f) }
        }
    }

    @Composable
    override fun Modifier.predictiveBackAnimationDecorator(
        transitionState: NavigationEventTransitionState?,
        contentPageKey: Any,
        currentPageKey: NavKey?,
    ): Modifier = composed {
        val windowInfo = LocalWindowInfo.current
        val navContent = LocalNavAnimatedContentScope.current
        val transition = navContent.transition
        val containerHeightPx = windowInfo.containerSize.height
        val pageKey = contentPageKey.toString()
        val deviceCornerRadius = rememberDeviceCornerRadius()
        val enteringStartOffsetPx = with(LocalDensity.current) { 96.dp.toPx() }

        val linearProgress = exitAnimatable.value
        val emphasizedProgress = CubicBezierEasing(0.2f, 0f, 0f, 1f).transform(linearProgress)

        val progressInProgress = (transitionState as? InProgress)
        val edge = progressInProgress?.latestEvent?.swipeEdge ?: 0
        val touchY = progressInProgress?.latestEvent?.touchY
        val gestureProgress = progressInProgress?.latestEvent?.progress ?: 0f

        val animatedScale by transition.animateFloat(
            transitionSpec = { tween(300) }, label = "PredictiveScale"
        ) { state ->
            when (state) {
                EnterExitState.PostExit -> 0.85f
                else -> 1f
            }
        }

        if (pageKey == currentPageKey.toString()) {
            inPredictiveBackAnimation = animatedScale != 1f
        }

        val directionMultiplier = if (edge == EDGE_LEFT) 1f else -1f

        val isExitingPage = exitingPageKey != null && exitingPageKey == pageKey
        val isCurrentNavTarget = exitingPageKey == null && pageKey == currentPageKey.toString()

        val maxScale = 0.85f
        val dragScale = 1f - (1f - maxScale) * gestureProgress

        val currentPivotY = if (touchY != null && containerHeightPx > 0) {
            (touchY / containerHeightPx).coerceIn(0.1f, 0.9f)
        } else 0.5f
        val currentPivotX = if (edge == EDGE_LEFT) 0.8f else 0.2f

        val isGestureActive = transitionState is InProgress && inPredictiveBackAnimation
        val isExitAnimationRunning = exitingPageKey != null
        val needsClip = isGestureActive || isExitAnimationRunning

        this
            .graphicsLayer {
                if (transitionState is InProgress && !inPredictiveBackAnimation && exitingPageKey == null) {
                    return@graphicsLayer
                }

                if (transitionState is InProgress)
                    transformOrigin = TransformOrigin(currentPivotX, currentPivotY)

                when {
                    isExitingPage -> {
                        // Animate scale back to 1.0 and slide off-screen
                        val computedScale = 1f - (1f - dragScale) * (1f - emphasizedProgress)
                        val computedTx = enteringStartOffsetPx * directionMultiplier * emphasizedProgress
                        scaleX = computedScale; scaleY = computedScale
                        translationX = computedTx; alpha = 1f
                    }
                    isCurrentNavTarget -> {
                        scaleX = dragScale; scaleY = dragScale
                        translationX = 0f; alpha = 1f
                    }
                    else -> {
                        val initTx = -enteringStartOffsetPx * directionMultiplier
                        if (exitingPageKey != null) {
                            scaleX = dragScale + (1f - dragScale) * emphasizedProgress
                            scaleY = dragScale + (1f - dragScale) * emphasizedProgress
                            translationX = initTx * (1f - emphasizedProgress)
                            alpha = 1f
                        } else if (transitionState is InProgress) {
                            scaleX = 1f; scaleY = 1f
                            translationX = 0f; alpha = 1f
                        }
                    }
                }
            }
            .clip(if (needsClip) RoundedCornerShape(deviceCornerRadius) else RoundedCornerShape(0.dp))
    }

    override fun AnimatedContentTransitionScope<Scene<NavKey>>.onPredictivePopTransitionSpec(
        swipeEdge: Int
    ): ContentTransform = ContentTransform(
        targetContentEnter = EnterTransition.None,
        initialContentExit = ExitTransition.None,
        sizeTransform = null
    )

    override fun AnimatedContentTransitionScope<Scene<NavKey>>.onPopTransitionSpec(): ContentTransform =
        ContentTransform(
            targetContentEnter = EnterTransition.None,
            initialContentExit = slideOutHorizontally { it },
            sizeTransform = null
        )

    override fun AnimatedContentTransitionScope<Scene<NavKey>>.onTransitionSpec(): ContentTransform =
        ContentTransform(
            targetContentEnter = slideInHorizontally { it },
            initialContentExit = slideOutHorizontally { -it },
            sizeTransform = null
        )
}
