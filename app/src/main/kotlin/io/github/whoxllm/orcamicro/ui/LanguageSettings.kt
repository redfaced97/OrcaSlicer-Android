package io.github.whoxllm.orcamicro.ui

import android.app.LocaleManager
import android.os.LocaleList
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.whoxllm.orcamicro.core.i18n.tr

@Composable fun LanguageSettingsCard() {
    val context=LocalContext.current
    val configuration=LocalConfiguration.current
    val manager=context.getSystemService(LocaleManager::class.java)
    val selected=manager.applicationLocales.toLanguageTags()
    val system=if(configuration.locales[0].language=="ru")"По языку системы" else "Follow system language"
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(tr("Язык интерфейса"),style=MaterialTheme.typography.titleMedium)
            ChoiceField(tr("Язык"),when(selected){"ru"->"Русский";"en"->"English";else->system},listOf(PickerItem("system",system),PickerItem("ru","Русский"),PickerItem("en","English"))) {
                manager.applicationLocales=if(it=="system")LocaleList.getEmptyLocaleList()else LocaleList.forLanguageTags(it)
            }
        }
    }
}
