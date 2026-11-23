package app.booktts.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Scaffold content insets: system bars plus the display cutout (landscape side notches). */
val contentInsets: WindowInsets
    @Composable get() = ScaffoldDefaults.contentWindowInsets.union(WindowInsets.displayCutout)

/** Top app bar insets: system bars plus the cutout, so actions never sit under a notch. */
@OptIn(ExperimentalMaterial3Api::class)
val appBarInsets: WindowInsets
    @Composable get() = TopAppBarDefaults.windowInsets.union(
        WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
    )

/** Scaffold padding minus the bottom edge — apply to the container, give the bottom to the list. */
@Composable
fun PaddingValues.withoutBottom(): PaddingValues {
    val ld = LocalLayoutDirection.current
    return PaddingValues(start = calculateStartPadding(ld), top = calculateTopPadding(), end = calculateEndPadding(ld))
}

/** Bottom-only padding so list content can scroll behind the transparent navigation bar. */
fun PaddingValues.bottomOnly(extra: Dp = 0.dp) = PaddingValues(bottom = calculateBottomPadding() + extra)
