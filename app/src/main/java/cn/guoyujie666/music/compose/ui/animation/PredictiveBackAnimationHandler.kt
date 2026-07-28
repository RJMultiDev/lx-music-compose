// SPDX-License-Identifier: GPL-3.0-only
// Ported from InstallerX Revived
package cn.guoyujie666.music.compose.ui.animation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState
import kotlinx.coroutines.CoroutineScope

interface PredictiveBackAnimationHandler {
    /**
     * Callback invoked when the back event is committed (e.g., gesture completed or button clicked).
     *
     * **Implementation Requirements:**
     * - Must check [transitionState] to determine if a predictive back gesture is active.
     * - If a predictive back animation is in-progress, this method must play the exit animations
     *   to avoid the page disappearing without any transition.
     *
     * @param transitionState The state tracking the current predictive back gesture/animation.
     * @param currentPageKey The [NavKey] of the page currently being popped.
     */
    suspend fun onBackPressed(
        transitionState: NavigationEventTransitionState?,
        currentPageKey: NavKey?,
    )

    /**
     * Callback when a page is actually popped from the backstack.
     * The page will be removed from the view tree immediately after this callback.
     *
     * @param contentPageKey The [NavKey] of the page being popped.
     * @param animationScope A [CoroutineScope] for resetting animation state only.
     */
    fun onPagePop(
        contentPageKey: Any,
        animationScope: CoroutineScope
    ) {}

    /**
     * A UI decorator applied to every page during rendering.
     * Allows custom modifications to the page graphics layer (scale, translation, alpha, clip)
     * during predictive back gestures and exit animations.
     */
    @Composable
    fun Modifier.predictiveBackAnimationDecorator(
        transitionState: NavigationEventTransitionState?,
        contentPageKey: Any,
        currentPageKey: NavKey?,
    ): Modifier

    /** Transition specs for a predictive back (swipe) gesture. */
    fun AnimatedContentTransitionScope<Scene<NavKey>>.onPredictivePopTransitionSpec(
        @NavigationEvent.SwipeEdge swipeEdge: Int
    ): ContentTransform

    /** Transition specs for a standard pop navigation (e.g., toolbar back button). */
    fun AnimatedContentTransitionScope<Scene<NavKey>>.onPopTransitionSpec(): ContentTransform

    /** Default transition specs for forward navigation (push). */
    fun AnimatedContentTransitionScope<Scene<NavKey>>.onTransitionSpec(): ContentTransform
}
