package com.example.ui.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.components.BrandGradientButton
import com.example.ui.theme.LocalNeuColors
import com.example.ui.theme.brandGradientHorizontal
import com.example.ui.theme.neumorphicRaised
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val showAppIcon: Boolean = false,
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Rounded.EditNote,
        title = "Welcome to MyNotes+",
        subtitle = "A calm, private space for everything on your mind - notes, checklists and ideas.",
        showAppIcon = true,
    ),
    OnboardingPage(
        icon = Icons.Rounded.Lock,
        title = "Encrypted by design",
        subtitle = "Note content is encrypted on your device. Optional Google tools may download models and send SDK diagnostics.",
    ),
    OnboardingPage(
        icon = Icons.Rounded.Checklist,
        title = "Notes, checklists & more",
        subtitle = "Write freely or tick off interactive checklists. Pin, colour and archive to stay organised - all offline, instantly.",
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val insets = WindowInsets.systemBars.asPaddingValues()
    val isLast = pagerState.currentPage == pages.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding()),
    ) {
        // Fixed-height top bar: the Skip button is only shown on the first two pages, but the bar
        // keeps its height on the last page too, so hiding Skip never shifts the pager upwards.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 20.dp),
        ) {
            if (!isLast) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onFinish)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            OnboardingPageContent(pages[page])
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pages.size) { index ->
                val selected = index == pagerState.currentPage
                val width by animateDpAsState(if (selected) 24.dp else 8.dp, label = "dotWidth")
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(width)
                        .clip(CircleShape)
                        .then(
                            if (selected) Modifier.background(brandGradientHorizontal())
                            else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        ),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        BrandGradientButton(
            text = if (isLast) "Get started" else "Next",
            onClick = {
                if (isLast) onFinish()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    val neu = LocalNeuColors.current
    // The icon sits at a fixed share of the page height, and the title/subtitle each live in a
    // FIXED-height slot. That means every element has the exact same position on all three pages,
    // so swiping never makes the icon jump or the text reflow into place - only the copy differs.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Spacer(Modifier.fillMaxHeight(0.16f))
        Box(
            modifier = Modifier
                .size(120.dp)
                .neumorphicRaised(60.dp, neu, elevation = 14.dp)
                .clip(CircleShape)
                .then(
                    if (page.showAppIcon) Modifier
                    else Modifier.background(brandGradientHorizontal())
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (page.showAppIcon) {
                // Render the real adaptive launcher icon so the first screen matches
                // the icon the user tapped on their home screen.
                Image(
                    painter = painterResource(R.drawable.ic_launcher_background),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Spacer(Modifier.height(40.dp))
        // Reserve room for up to two title lines; bottom-align so the gap to the subtitle is
        // identical whether the title is one line or two.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(14.dp))
        // Reserve room for the longest subtitle (up to four lines); top-align so it always starts
        // at the same position no matter how long the copy is.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(112.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = page.subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 4,
            )
        }
    }
}
