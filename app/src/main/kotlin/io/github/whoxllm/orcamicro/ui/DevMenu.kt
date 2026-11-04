package io.github.whoxllm.orcamicro.ui

import io.github.whoxllm.orcamicro.core.i18n.tr
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import io.github.whoxllm.orcamicro.core.settings.*
import io.github.whoxllm.orcamicro.render.SceneRenderer

@Composable fun DevMenu(state: UiState, vm: MainViewModel, renderer: SceneRenderer, onDismiss: () -> Unit) {
    val o = state.renderOptions
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Dev menu · предпросмотр"), style = MaterialTheme.typography.titleLarge)
            Text(tr("Настройки влияют на изображение. Параметры нарезки задаются отдельно."), style = MaterialTheme.typography.bodySmall)
            Text(tr("Камера"), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((text, angles) in listOf(tr("Изометрия") to (-60f to 30f), tr("Сверху") to (-90f to 89f), tr("Спереди") to (-90f to 0f))) {
                    OutlinedButton(onClick = { renderer.camera.view(angles.first, angles.second); renderer.requestFrame?.invoke() }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text(text, style = MaterialTheme.typography.labelSmall) }
                }
            }
            TextButton(onClick = { state.bed?.let { renderer.camera.frame(it.centerX,it.centerY,0f,maxOf(it.width,it.depth)*0.6f) }; renderer.requestFrame?.invoke() }) { Text(tr("Вместить стол в экран")) }
            DevSwitch(tr("Свободная камера"), tr("Разрешить обзор снизу стола"), o.freeCamera) { vm.setRenderOptions(o.copy(freeCamera = it)) }
            HorizontalDivider()
            DevSwitch("SSAO", tr("Затенение по соседним значениям глубины · экспериментально"), o.ssao) { vm.setRenderOptions(o.copy(ssao = it)) }
            DevSwitch(tr("Проекционные тени"), tr("Тень модели и траекторий на плоскости стола"), o.shadows) { vm.setRenderOptions(o.copy(shadows = it)) }
            DevSwitch(tr("Сетка стола"), tr("Шаг 10 мм"), o.grid) { vm.setRenderOptions(o.copy(grid = it)) }
            DevSwitch(tr("Объём печати"), tr("Границы рабочей области принтера"), o.volume) { vm.setRenderOptions(o.copy(volume = it)) }
            DevSwitch(tr("Цвета типов линий"), tr("Выключите для цвета выбранного пластика"), o.featureColors) { vm.setRenderOptions(o.copy(featureColors = it)) }
            Text(tr("Поверхность стола"), style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((t, title) in listOf(PlateTexture.SMOOTH to tr("Гладкая"), PlateTexture.PEI to "PEI", PlateTexture.CARBON to tr("Карбон"), PlateTexture.NONE to tr("Нет"))) {
                    FilterChip(o.texture == t, { vm.setRenderOptions(o.copy(texture = t)) }, label = { Text(title, style = MaterialTheme.typography.labelSmall) })
                }
            }
            Text(tr("PEI и карбон — процедурные текстуры."), style = MaterialTheme.typography.bodySmall)
            Text(tr("Яркость: %.0f%%").format(o.brightness*100), style = MaterialTheme.typography.labelLarge)
            Slider(o.brightness, { vm.setRenderOptions(o.copy(brightness = it)) }, valueRange = 0.5f..1.5f)
            Text(tr("Толщина линий: %.1f px").format(o.lineWidth), style = MaterialTheme.typography.labelLarge)
            Slider(o.lineWidth, { vm.setRenderOptions(o.copy(lineWidth = it)) }, valueRange = 1f..3f)
            Text(tr("Разрешение сцены"), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for(v in listOf(0.5f,0.75f,1f)) FilterChip(o.resolution == v, { vm.setRenderOptions(o.copy(resolution = v)) }, label = { Text("${(v*100).toInt()}%") })
            }
            Text(tr("Для Mi A3 начните с 75% без SSAO. Кадры рисуются только при изменениях."), style = MaterialTheme.typography.bodySmall)
            Text(tr("Фон"), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for((rgb,title) in listOf(0x454545 to tr("Серый"),0x242424 to tr("Тёмный"),0xC3C5C7 to tr("Светлый"))) FilterChip(o.background == rgb, { vm.setRenderOptions(o.copy(background = rgb)) }, label = { Text(title) })
            }
            Text(renderer.renderStatus, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { vm.setRenderOptions(RenderOptions()) }, modifier = Modifier.fillMaxWidth()) { Text(tr("Сбросить графику")) }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) { Text(tr("Готово")) }
        }
    }
}
@Composable private fun DevSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) { Text(title); Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked, onChange, modifier = Modifier.semantics { contentDescription = title })
    }
}
