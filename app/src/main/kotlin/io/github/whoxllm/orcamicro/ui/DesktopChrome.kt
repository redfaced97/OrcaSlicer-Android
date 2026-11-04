package io.github.whoxllm.orcamicro.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.whoxllm.orcamicro.R

@Composable
fun DesktopHeader(state: UiState, viewModel: MainViewModel, actions: @Composable RowScope.() -> Unit = {}) {
    val tabs = listOf(Screen.PLATE to "Подготовка", Screen.PREVIEW to "Просмотр", Screen.LIBRARY to "Модели", Screen.DEVICE to "Принтер", Screen.ABOUT to "О программе")
    val config = LocalConfiguration.current
    val compactTabs = config.screenWidthDp < 400 || config.fontScale > 1.2f
    val selected = when (state.screen) { Screen.SETTINGS, Screen.PRESETS, Screen.PRINT_SETUP -> Screen.PLATE; else -> state.screen }
    Column(Modifier.statusBarsPadding()) {
        Surface {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.orca_logo), null, Modifier.size(26.dp))
                Text(if (state.screen == Screen.PRINT_SETUP) "Quick Print" else if (state.screen == Screen.PRESETS) "Профили" else tabs.firstOrNull { it.first == selected }?.second.orEmpty(), Modifier.weight(1f).padding(start = 8.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if(state.screen != Screen.DEVICE && state.screen != Screen.PRESETS) {
                    IconButton(onClick = { viewModel.openPreset(state.settingsType) }) { OrcaIcon(OrcaAction.Library, "Библиотека профилей") }
                    IconButton(onClick = { viewModel.openSettings() }) { OrcaIcon(OrcaAction.Settings, "Параметры печати") }
                }
                actions()
            }
        }
        TabRow(selectedTabIndex = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0), containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEach { (screen, title) ->
                Tab(selected = selected == screen, modifier = Modifier.semantics { contentDescription = title }, onClick = { viewModel.navigate(screen) }, selectedContentColor = MaterialTheme.colorScheme.primary, unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
                        Text(if(compactTabs) when(screen) { Screen.PLATE -> "Стол"; Screen.PREVIEW -> "Слои"; Screen.LIBRARY -> "Модели"; Screen.DEVICE -> "Принтер"; else -> "Инфо" } else title, style = MaterialTheme.typography.labelSmall, maxLines = 2, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
