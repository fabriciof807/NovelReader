package com.novelreader.ui.reader

import android.app.Activity
import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import com.novelreader.R
import com.novelreader.ui.theme.AppDimens
import com.novelreader.ui.theme.AppMotion
import com.novelreader.ui.theme.rememberReducedMotion

data class ReaderChromeState(
    val topBar: Boolean,
    val optionsBar: Boolean,
    val status: Boolean,
    val sleepPill: Boolean
)

fun readerChrome(
    optionsVisible: Boolean,
    searchActive: Boolean,
    batteryAlwaysOn: Boolean,
    sleepRunning: Boolean
): ReaderChromeState = ReaderChromeState(
    topBar = optionsVisible || searchActive,
    optionsBar = optionsVisible,
    status = optionsVisible || batteryAlwaysOn,
    sleepPill = sleepRunning
)

fun readerBackgroundIsDark(background: Color): Boolean = background.luminance() < 0.5f

internal fun readerStatusReserve(
    labelLineHeight: Dp,
    iconSize: Dp = 16.dp,
    verticalPadding: Dp = 4.dp
): Dp = maxOf(iconSize, labelLineHeight) + verticalPadding * 2

internal fun restoreBarAppearanceDark(capturedLightStatusBars: Boolean): Boolean =
    !capturedLightStatusBars

internal fun capturedBarAppearanceDark(view: View): Boolean? {
    val window = (view.context as? Activity)?.window ?: return null
    val lightStatusBars = WindowInsetsControllerCompat(window, view)
        .isAppearanceLightStatusBars
    return restoreBarAppearanceDark(lightStatusBars)
}

const val READER_OPTIONS_INITIALLY_VISIBLE = false

const val READER_SYSTEM_BARS_SETTLE_MILLIS = 400L

const val READER_SLEEP_PILL_TAG = "reader_sleep_pill"

@Composable
fun ReaderChromeOverlay(
    chrome: ReaderChromeState,
    battery: BatteryState,
    sleepRemainingSeconds: Int?,
    barColor: Color,
    topBar: @Composable () -> Unit,
    optionsBar: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val reducedMotion = rememberReducedMotion()
    val fadeSpec = tween<Float>(AppMotion.fadeMillis(reducedMotion))
    val slideSpec = readerBarSlideSpec(reducedMotion)
    val topOffset = if (reducedMotion) NoSlideOffset else TopBarSlideOffset
    val bottomOffset = if (reducedMotion) NoSlideOffset else OptionsBarSlideOffset

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = chrome.topBar,
            modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(fadeSpec) + slideInVertically(slideSpec, initialOffsetY = topOffset),
            exit = fadeOut(fadeSpec) + slideOutVertically(slideSpec, targetOffsetY = topOffset)
        ) {
            topBar()
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            AnimatedVisibility(
                visible = chrome.optionsBar,
                enter = fadeIn(fadeSpec) +
                    slideInVertically(slideSpec, initialOffsetY = bottomOffset),
                exit = fadeOut(fadeSpec) +
                    slideOutVertically(slideSpec, targetOffsetY = bottomOffset)
            ) {
                optionsBar()
            }

            when {
                chrome.status -> ReaderStatusBar(
                    battery = battery,
                    sleepRemainingSeconds = sleepRemainingSeconds,
                    containerColor = barColor,
                    modifier = Modifier.fillMaxWidth()
                )

                chrome.sleepPill && sleepRemainingSeconds != null -> ReaderSleepPill(
                    battery = battery,
                    sleepRemainingSeconds = sleepRemainingSeconds,
                    barColor = barColor,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(end = 12.dp, bottom = 8.dp)
                )
            }
        }
    }
}

private val TopBarSlideOffset: (Int) -> Int = { height -> -height / 4 }
private val OptionsBarSlideOffset: (Int) -> Int = { height -> height / 2 }
private val NoSlideOffset: (Int) -> Int = { 0 }

private fun readerBarSlideSpec(reduced: Boolean): FiniteAnimationSpec<IntOffset> =
    if (reduced) tween(AppMotion.slideMillis(reduced)) else AppMotion.settle(hasMomentum = false)

@Composable
private fun ReaderSleepPill(
    battery: BatteryState,
    sleepRemainingSeconds: Int,
    barColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(percent = 50),
        modifier = modifier.testTag(READER_SLEEP_PILL_TAG)
    ) {
        ReaderStatusBar(
            battery = battery,
            sleepRemainingSeconds = sleepRemainingSeconds,
            containerColor = barColor,
            showBattery = false
        )
    }
}

@Composable
fun ReaderOptionsBar(
    barColor: Color,
    prevEnabled: Boolean,
    nextEnabled: Boolean,
    bookmarkCount: Int,
    onPrev: () -> Unit,
    onBookmark: () -> Unit,
    onSettings: () -> Unit,
    onChapterList: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = barColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.TopBarHeight)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrev, enabled = prevEnabled) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = stringResource(R.string.previous)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onBookmark) {
                Box {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = stringResource(R.string.bookmarks),
                        tint = if (bookmarkCount > 0) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurface
                    )
                    if (bookmarkCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .align(Alignment.TopEnd)
                                .background(
                                    MaterialTheme.colorScheme.secondary,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$bookmarkCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondary
                            )
                        }
                    }
                }
            }

            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = stringResource(R.string.settings)
                )
            }

            IconButton(onClick = onChapterList) {
                Icon(
                    Icons.AutoMirrored.Filled.List,
                    contentDescription = stringResource(R.string.chapter_list)
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = onNext, enabled = nextEnabled) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = stringResource(R.string.next)
                )
            }
        }
    }
}
