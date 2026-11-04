package io.github.whoxllm.orcamicro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.whoxllm.orcamicro.core.profiles.*
import io.github.whoxllm.orcamicro.render.PlateRenderer
import io.github.whoxllm.orcamicro.render.SceneView
import kotlin.math.max

const val FILAMENT_REMINDER = "(Удостоверьтесь что данный филамент загружен в принтер)"

data class PickerItem(val id:String,val title:String,val section:String="")

@Composable
fun ChoiceField(label:String,value:String,items:List<PickerItem>,enabled:Boolean=true,onSelect:(String)->Unit) {
    var show by remember { mutableStateOf(false) };var query by remember { mutableStateOf("") }
    OutlinedCard(onClick={ show=true },enabled=enabled,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) { Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary);Text(value.ifBlank { "Выбрать…" },style=MaterialTheme.typography.bodyMedium) }
    }
    if(show) AlertDialog(onDismissRequest={show=false},title={Text(label)},text={
        Column {
            OutlinedTextField(query,{query=it},label={Text("Поиск")},singleLine=true,modifier=Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max=380.dp)) {
                items(items.filter { it.title.contains(query,true) },key={it.id}) { item->
                    Column(Modifier.fillMaxWidth().clickable { onSelect(item.id);show=false;query="" }.padding(vertical=12.dp)) {
                        Text(item.title);if(item.section.isNotBlank())Text(item.section,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    };HorizontalDivider()
                }
            }
        }
    },confirmButton={TextButton(onClick={show=false}){Text("Закрыть")}})
}

@Composable
fun PrintSetupScreen(state:UiState,viewModel:MainViewModel) {
    val selection=state.selection ?: return;val catalog=state.catalog ?: return
    val stock=catalog.printer(selection.vendor,selection.printer)
    val ownMachine=state.userPresets.filter { it.type==PresetType.MACHINE }
    val ownProcess=state.userPresets.filter { it.type==PresetType.PROCESS }
    val ownFilament=state.userPresets.filter { it.type==PresetType.FILAMENT }
    val snackbar=remember { SnackbarHostState() };MessageEffect(state.message,snackbar,viewModel::consumeMessage)
    val validFields=remember { mutableStateMapOf<String,Boolean>() }
    Scaffold(topBar={DesktopHeader(state,viewModel)},snackbarHost={SnackbarHost(snackbar)},bottomBar={
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick={viewModel.navigate(Screen.PLATE)},modifier=Modifier.weight(1f)){Text("На стол")}
            Button(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick=viewModel::slice,enabled=state.model?.fits==true&&validFields.values.all{it},modifier=Modifier.weight(1f)){Text("Нарезать")}
        }
    }) { padding->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Подготовка к печати",style=MaterialTheme.typography.titleLarge)
            Text(state.model?.name ?: "Сначала откройте модель",style=MaterialTheme.typography.bodyMedium)
            val renderer=remember { PlateRenderer() };val framed=remember { arrayOfNulls<io.github.whoxllm.orcamicro.core.settings.BedShape>(1) }
            SceneView(renderer,Modifier.fillMaxWidth().height(190.dp)) { r->
                val bed=state.bed
                if(bed!=null&&framed[0]!=bed){r.camera.frame(bed.centerX,bed.centerY,0f,max(bed.width,bed.depth)*.6f);framed[0]=bed}
                r.setBed(bed,state.printableHeight);r.setModel(state.model?.placed,state.model?.fits?:true);r.modelColor=state.modelRgb();r.offsetX=state.model?.offsetX?:0f;r.offsetY=state.model?.offsetY?:0f
            }
            if(state.model?.fits==false)Text("Модель не помещается на выбранном принтере. Измените положение или масштаб на столе.",color=MaterialTheme.colorScheme.error)
            if(state.printers.isNotEmpty())ChoiceField("Принтер в сети",state.printers.firstOrNull { it.id==state.activePrinterId }?.name.orEmpty(),state.printers.map { PickerItem(it.id,it.name,it.connection.apiUrl) },onSelect=viewModel::selectSavedPrinter)
            else OutlinedButton(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick={viewModel.navigate(Screen.DEVICE)}){Text("Добавить подключение к принтеру")}
            ChoiceField("Профиль принтера",state.inputs?.machine?.string("name").orEmpty(),
                ownMachine.map { PickerItem(it.id,it.name,"Мои профили") }+PickerItem("stock",selection.printer,"Штатный профиль")) { id->
                if(id=="stock")viewModel.selectPrinter(selection.vendor,selection.printer)else ownMachine.firstOrNull{it.id==id}?.let(viewModel::selectUserPreset)
            }
            TextButton(onClick={viewModel.openPreset(PresetType.MACHINE)}){Text("Все принтеры из библиотеки")}
            ChoiceField("Пластик",state.inputs?.filament?.string("name").orEmpty(),ownFilament.map { PickerItem(it.id,it.name,"Мои профили") }+
                stock?.let { catalog.filamentsFor(selection.vendor,it).map { ref->PickerItem("stock:${ref.vendor}:${ref.name}",ref.name,"Штатные · ${ref.vendor}") } }.orEmpty()) { id->
                ownFilament.firstOrNull { it.id==id }?.let(viewModel::selectUserPreset) ?: stock?.let { p->catalog.filamentsFor(selection.vendor,p).firstOrNull { "stock:${it.vendor}:${it.name}"==id }?.let(viewModel::selectFilament) }
            }
            Text(FILAMENT_REMINDER,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
            ChoiceField("Процесс печати",state.inputs?.process?.string("name").orEmpty(),ownProcess.map { PickerItem(it.id,it.name,"Мои профили") }+
                stock?.let { catalog.processesFor(selection.vendor,it).map { name->PickerItem("stock:$name",name,"Штатные") } }.orEmpty()) { id->
                ownProcess.firstOrNull { it.id==id }?.let(viewModel::selectUserPreset) ?: viewModel.selectProcess(id.removePrefix("stock:"))
            }
            QuickParameter("Высота слоя, мм","layer_height",state,viewModel,.01f..(state.inputs?.machine?.first("nozzle_diameter")?.toFloatOrNull() ?: .4f),onValid={validFields["layer"]=it})
            QuickParameter("Заполнение, %","sparse_infill_density",state,viewModel,0f..100f,suffix="%",onValid={validFields["infill"]=it})
            QuickParameter("Количество стенок","wall_loops",state,viewModel,1f..50f,integer=true,onValid={validFields["walls"]=it})
            OutlinedButton(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick={viewModel.openSettings()}){Text("Все параметры печати")}
        }
    }
}

@Composable private fun QuickParameter(label:String,key:String,state:UiState,vm:MainViewModel,range:ClosedFloatingPointRange<Float>,suffix:String="",integer:Boolean=false,onValid:(Boolean)->Unit) {
    val stored=state.inputs?.process?.first(key).orEmpty().removeSuffix("%")
    var text by remember(stored) { mutableStateOf(stored) }
    val value=text.replace(',','.').toFloatOrNull()
    val valid=value!=null&&value.isFinite()&&value in range&&(!integer||value%1f==0f)
    SideEffect { onValid(valid) }
    OutlinedTextField(text,{text=it;if(it.replace(',','.').toFloatOrNull()?.let { n->n.isFinite()&&n in range&&(!integer||n%1f==0f) }==true)vm.setEdit(PresetType.PROCESS,key,it.replace(',','.')+suffix)},label={Text(label)},isError=!valid,supportingText={if(!valid)Text("Допустимо: ${range.start}–${range.endInclusive}")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth())
}

@Composable
fun PrintSendDialog(state:UiState,vm:MainViewModel) {
    var confirmed by remember(state.lastResult,state.activePrinterId) { mutableStateOf(false) }
    AlertDialog(onDismissRequest={vm.showPrintDialog(false)},title={Text("Отправка на печать")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(state.printers.firstOrNull{it.id==state.activePrinterId}?.name ?: "Подключение не настроено")
            Text("Принтер: ${state.inputs?.machine?.string("name").orEmpty()}")
            Text("Пластик: ${state.inputs?.filament?.string("name").orEmpty()}")
            Text("Процесс: ${state.inputs?.process?.string("name").orEmpty()}")
            Text(FILAMENT_REMINDER,style=MaterialTheme.typography.bodySmall)
            Row(verticalAlignment=Alignment.CenterVertically) { Checkbox(confirmed,{confirmed=it});Text("Филамент загружен, стол свободен",style=MaterialTheme.typography.bodyMedium) }
            state.uploadedFile?.let { Text("На принтере: $it",style=MaterialTheme.typography.labelMedium) }
            if(state.connection.apiUrl.isBlank())TextButton(onClick={vm.showPrintDialog(false);vm.navigate(Screen.DEVICE)}){Text("Настроить принтер")}
        }
    },confirmButton={
        if(state.uploadedFile!=null)Button(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick=vm::startUploadedPrint,enabled=confirmed&&!state.deviceBusy){Text("Начать печать")}
        else Button(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick=vm::uploadLastGcode,enabled=confirmed&&state.lastResult!=null&&state.connection.apiUrl.isNotBlank()&&state.uploadPercent==null){Text("Загрузить G-code")}
    },dismissButton={TextButton(onClick={vm.showPrintDialog(false)}){Text("Закрыть")}})
}
