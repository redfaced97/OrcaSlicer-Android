package io.github.whoxllm.orcamicro.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.whoxllm.orcamicro.core.network.*
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable fun PrinterControlPanel(state:UiState,vm:MainViewModel,modifier:Modifier=Modifier) {
    val snapshot=state.telemetry
    val enabled=snapshot?.ready==true&&!state.deviceBusy&&state.deviceError==null
    var step by remember { mutableFloatStateOf(1f) };var confirm by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(if(snapshot?.ready==true)"Klipper · подключён" else "Klipper · нет данных",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            Button(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick=vm::emergencyStop,enabled=state.connection.apiUrl.isNotBlank(),colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("STOP")}
        }
        state.deviceError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        if(state.deviceBusy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(snapshot==null)Text(if(state.connection.apiUrl.isBlank())"Добавьте принтер во вкладке «Подключения»." else "Ожидание телеметрии Moonraker. При недоступном Klipper элементы управления отключены.",style=MaterialTheme.typography.bodyMedium)
        snapshot?.let { s->
            if(s.message.isNotBlank()&&!s.ready)Text(s.message,style=MaterialTheme.typography.bodySmall)
            Text("Состояние: ${when(s.printState){"printing"->"Печать";"paused"->"Пауза";"complete"->"Завершено";"error"->"Ошибка";"cancelled"->"Отменено";else->"Ожидание"}}",style=MaterialTheme.typography.bodyMedium)
            if(s.filename.isNotBlank())Text(s.filename,style=MaterialTheme.typography.bodySmall)
            if(s.printing){LinearProgressIndicator(progress={s.progress},modifier=Modifier.fillMaxWidth());Text("${(s.progress*100).roundToInt()}%")}
            if(s.printing)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick={vm.printerCommand { it.pause() }},enabled=enabled&&s.printState=="printing",modifier=Modifier.weight(1f)){Text("Пауза")}
                OutlinedButton(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick={vm.printerCommand { it.resume() }},enabled=enabled&&s.printState=="paused",modifier=Modifier.weight(1f)){Text("Продолжить",maxLines=1,style=MaterialTheme.typography.labelMedium,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)}
                OutlinedButton(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick={confirm="cancel"},enabled=enabled&&s.printing,modifier=Modifier.weight(1f)){Text("Отмена")}
            }
            Text("Температуры",style=MaterialTheme.typography.titleMedium)
            TemperatureChart(state.temperatureHistory)
            state.temperatureHistoryError?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
            if(s.heaters.isEmpty())Text("В Klipper не найдены датчики температуры",style=MaterialTheme.typography.bodySmall)
            s.heaters.forEach { heater->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                        Text("${heater.label} · ${"%.1f".format(heater.temperature)} °C"+(heater.target?.let { " → ${it.roundToInt()} °C" } ?: ""),style=MaterialTheme.typography.titleSmall)
                        val currentTarget=heater.target
                        val maximum=heater.max
                        if(currentTarget!=null) {
                            var target by remember(state.activePrinterId,heater.name,currentTarget) { mutableStateOf(currentTarget.roundToInt().toString()) }
                            val value=target.replace(',','.').toFloatOrNull()
                            val valid=value!=null&&value.isFinite()&&(value==0f || (maximum!=null&&value in maxOf(0f,heater.min)..maximum))
                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(target,{target=it},label={Text("Цель, °C")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,isError=!valid,modifier=Modifier.weight(1f))
                                Button(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick={value?.let { temp->vm.printerCommand { it.setHeater(heater.name,temp) } }},enabled=enabled&&valid){Text("Задать")}
                            }
                            Text(heater.max?.let { "Предел Klipper: ${heater.min.roundToInt()}…${it.roundToInt()} °C; 0 — выключить" } ?: "Предел не получен из Klipper. Доступно выключение (0).",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            OutlinedButton(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick={vm.printerCommand { it.heatersOff() }},enabled=enabled,modifier=Modifier.fillMaxWidth()){Text("Выключить все нагреватели")}
            HorizontalDivider()
            Text("Перемещение каретки",style=MaterialTheme.typography.titleMedium)
            Text("X ${s.position[0].roundToInt()}  Y ${s.position[1].roundToInt()}  Z ${"%.1f".format(s.position[2])} мм · Home: ${s.homedAxes.ifBlank { "не выполнен" }}",style=MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf(.1f,1f,10f,50f).forEach { amount->FilterChip(selected=step==amount,onClick={step=amount},label={Text("$amount мм")}) } }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { listOf("X","Y","Z").forEach { axis->
                Column(Modifier.weight(1f)) {
                    Button(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick={vm.printerCommand { it.jog(axis,step) }},enabled=enabled&&!s.printing&&axis.lowercase() in s.homedAxes,modifier=Modifier.fillMaxWidth()){Text("$axis +")}
                    OutlinedButton(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick={vm.printerCommand { it.jog(axis,-step) }},enabled=enabled&&!s.printing&&axis.lowercase() in s.homedAxes,modifier=Modifier.fillMaxWidth()){Text("$axis −")}
                }
            } }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Button(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick={confirm="home"},enabled=enabled&&!s.printing,modifier=Modifier.weight(1f)){Text("Home XYZ")}
                OutlinedButton(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick={confirm="motors"},enabled=enabled&&!s.printing,modifier=Modifier.weight(1f)){Text("Откл. моторы")}
            }
            val currentFan=s.fan
            if(currentFan!=null) {
                var fan by remember(state.activePrinterId,currentFan) { mutableFloatStateOf(currentFan*100) }
                Text("Обдув детали: ${fan.roundToInt()}%",style=MaterialTheme.typography.titleSmall)
                Slider(fan,{fan=it},valueRange=0f..100f,enabled=enabled)
                OutlinedButton(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick={vm.printerCommand { it.setFan(fan.roundToInt()) }},enabled=enabled){Text("Применить обдув")}
            }
        }
    }
    confirm?.let { action->AlertDialog(onDismissRequest={confirm=null},title={Text(when(action){"home"->"Выполнить Home XYZ?";"motors"->"Отключить двигатели?";else->"Отменить печать?"})},text={Text(when(action){"home"->"Каретка и стол начнут движение. Убедитесь, что им ничего не мешает.";"motors"->"Оси потеряют удержание; для следующих перемещений потребуется Home.";else->"Текущая печать будет отменена."})},confirmButton={Button(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick={confirm=null;vm.printerCommand { when(action){"home"->it.home();"motors"->it.motorsOff();else->it.cancelPrint()} }}){Text("Выполнить")}},dismissButton={TextButton(onClick={confirm=null}){Text("Назад")}}) }
}

@Composable private fun TemperatureChart(history:List<TemperatureSeries>) {
    val colors=listOf(MaterialTheme.colorScheme.primary,Color(0xFFEF8354),Color(0xFF778BEB),Color(0xFF2FA77A),Color(0xFFC878BA))
    var hidden by remember { mutableStateOf(emptySet<String>()) }
    val shown=history.filterNot { it.name in hidden }
    val maxValue=maxOf(50f,ceil((shown.flatMap { it.temperatures+it.targets }.filterNotNull().maxOrNull() ?: 50f)/25f)*25f)
    val grid=MaterialTheme.colorScheme.outlineVariant
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Row { Text("${maxValue.roundToInt()} °C",style=MaterialTheme.typography.labelSmall);Spacer(Modifier.weight(1f));Text("Цель — пунктир",style=MaterialTheme.typography.labelSmall) }
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                for(i in 0..4)drawLine(grid,Offset(0f,size.height*i/4),Offset(size.width,size.height*i/4),1f)
                history.forEachIndexed { index,series->if(series.name !in hidden) {
                    fun line(values:List<Float?>,dashed:Boolean) {
                        val path=Path();var connected=false
                        values.forEachIndexed { i,temp->
                            if(temp==null)connected=false else {
                                val x=size.width*(600-values.size+i)/599f
                                val y=size.height*(1-(temp/maxValue).coerceIn(0f,1f))
                                if(connected)path.lineTo(x,y)else path.moveTo(x,y);connected=true
                            }
                        }
                        drawPath(path,colors[index%colors.size],style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx(),pathEffect=if(dashed)PathEffect.dashPathEffect(floatArrayOf(10f,7f))else null))
                    }
                    line(series.temperatures,false);line(series.targets,true)
                } }
            }
            Row { Text("−10 мин",style=MaterialTheme.typography.labelSmall);Spacer(Modifier.weight(1f));Text("Сейчас · 0 °C",style=MaterialTheme.typography.labelSmall) }
            if(history.isEmpty())Text("Ожидание истории температур Moonraker",style=MaterialTheme.typography.bodySmall)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) { history.forEachIndexed { index,series->
                FilterChip(selected=series.name !in hidden,onClick={hidden=if(series.name in hidden)hidden-series.name else hidden+series.name},label={Text("● "+HeaterState(series.name,0f,null).label,color=colors[index%colors.size])})
            } }
        }
    }
}
