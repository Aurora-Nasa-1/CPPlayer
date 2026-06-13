package cp.player.ui.component
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cp.player.R

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: @Composable () -> Unit,
    onBackPressed: (() -> Unit)? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    isLoading: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    bottomBar: @Composable () -> Unit = {},
    containerColor: androidx.compose.ui.graphics.Color = Color.Transparent,
    topBarContainerColor: androidx.compose.ui.graphics.Color = Color.Unspecified,
    content: @Composable (PaddingValues) -> Unit
) {
    val defaultScrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val actualScrollBehavior = scrollBehavior ?: defaultScrollBehavior

    // If topBarContainerColor is Unspecified, make it match the scaffold's containerColor
    // This ensures that the unscrolled TopAppBar blends seamlessly with the background
    // (Requirement: 默认情况下跟背景色一样)
    val actualTopBarContainerColor = if (topBarContainerColor == Color.Unspecified) {
        if (containerColor == Color.Transparent) MaterialTheme.colorScheme.surface.copy(alpha = 0f) else containerColor
    } else {
        topBarContainerColor
    }

    Scaffold(
        modifier = Modifier.nestedScroll(actualScrollBehavior.nestedScrollConnection),
        containerColor = containerColor,
        bottomBar = bottomBar,
        topBar = {
            MediumTopAppBar(
                title = title,
                navigationIcon = navigationIcon ?: {
                    if (onBackPressed != null) {
                        FilledIconButton(
                            onClick = onBackPressed,
                            modifier = Modifier.padding(start = 4.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = LocalContentColor.current
                            )
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.back))
                        }
                    }
                },
                actions = actions,
                scrollBehavior = actualScrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = actualTopBarContainerColor,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            content(PaddingValues(0.dp))
            if (isLoading) {
                ContainedLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    onBackPressed: (() -> Unit)? = null,
    navigationIcon: @Composable (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    isLoading: Boolean = false,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    bottomBar: @Composable () -> Unit = {},
    containerColor: androidx.compose.ui.graphics.Color = Color.Transparent,
    topBarContainerColor: androidx.compose.ui.graphics.Color = Color.Unspecified,
    content: @Composable (PaddingValues) -> Unit
) {
    AppScaffold(
        title = { Text(title) },
        onBackPressed = onBackPressed,
        navigationIcon = navigationIcon,
        actions = actions,
        isLoading = isLoading,
        scrollBehavior = scrollBehavior,
        bottomBar = bottomBar,
        containerColor = containerColor,
        topBarContainerColor = topBarContainerColor,
        content = content
    )
}
