package io.github.whoxllm.orcamicro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.whoxllm.orcamicro.core.network.EmergencyStopConfirmation

@Composable
fun EmergencyStopDialog(state:UiState,vm:MainViewModel) {
    val challenge=state.stopChallenge ?: return
    var text by remember(challenge) { mutableStateOf("") }
    val samePrinter=state.connection==challenge.connection&&state.activePrinterId==challenge.printerId
    AlertDialog(onDismissRequest=vm::dismissEmergencyStop,title={Text("Аварийная остановка Klipper")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Принтер: ${challenge.printerName}")
            Text("Остановит движение и нагрев. Для восстановления потребуется FIRMWARE_RESTART.",style=MaterialTheme.typography.bodyMedium)
            Text("Для экстренной остановки принтера введите текст "+'"'+EmergencyStopConfirmation.TEXT+'"')
            OutlinedTextField(text,{text=it},label={Text("Подтверждение STOP")},singleLine=true,keyboardOptions=KeyboardOptions(capitalization=KeyboardCapitalization.None,autoCorrectEnabled=false),modifier=Modifier.fillMaxWidth())
            if(!samePrinter)Text("Принтер изменился. Откройте подтверждение заново.",color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={Button(onClick={vm.confirmEmergencyStop(text)},enabled=samePrinter&&EmergencyStopConfirmation.matches(text),shape=MaterialTheme.shapes.small,colors=ButtonDefaults.buttonColors(containerColor=MaterialTheme.colorScheme.error)){Text("Остановить принтер")}},dismissButton={TextButton(onClick=vm::dismissEmergencyStop){Text("Отмена")}})
}
