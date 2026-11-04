package io.github.whoxllm.orcamicro.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.whoxllm.orcamicro.R

@Composable
fun DesktopHeader(state: UiState, viewModel: MainViewModel, actions: @Composable RowScope.() -> Unit = {}) {
    val tabs = listOf(Screen.PLATE to "Подготовка", Screen.PREVIEW to "Просмотр", Screen.DEVICE to "Принтер", Screen.ABOUT to "О программе")
    val selected = when (state.screen) { Screen.SETTINGS, Screen.PRESETS -> Screen.PLATE; else -> state.screen }
    val shortScreen = LocalConfiguration.current.screenHeightDp < 500
    Column(if (shortScreen) Modifier.statusBarsPadding() else Modifier) {
        if (!shortScreen) TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Image(painterResource(R.drawable.orca_logo), contentDescription = null, modifier = Modifier.size(28.dp))
                    Text("OrcaSlicer", style = MaterialTheme.typography.titleLarge)
                }
            },
            actions = actions,
        )
        TabRow(selectedTabIndex = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0), containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEach { (screen, title) ->
                Tab(selected = selected == screen, onClick = { viewModel.navigate(screen) }) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                        Text(title, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
