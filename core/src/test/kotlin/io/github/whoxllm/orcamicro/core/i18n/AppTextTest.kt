package io.github.whoxllm.orcamicro.core.i18n

import io.github.whoxllm.orcamicro.core.settings.OrcaOptions
import io.github.whoxllm.orcamicro.core.settings.Russian
import kotlinx.serialization.json.*
import kotlin.test.*

class AppTextTest {
    @Test fun `language switch uses original option labels and keeps user data verbatim`() {
        try {
            AppText.english=true
            assertEquals("Nozzle: 0.4 mm",tr("Сопло: {0} мм","0.4"))
            assertEquals("Filament: Мой PLA {0}",tr("Пластик: {0}","Мой PLA {0}"))
            val option=OrcaOptions.all.first{it.key=="layer_height"};assertEquals(option.label,option.displayLabel);assertEquals(option.description,option.displayDescription)
            assertEquals("Generating infill",Russian.text("Generating infill"))
            AppText.english=false
            assertEquals("Сопло: 0.4 мм",tr("Сопло: {0} мм","0.4"));assertEquals(option.labelRu,option.displayLabel)
        }finally{AppText.english=false}
    }
    @Test fun `every translation preserves dynamic arguments and English has no Cyrillic UI text`() {
        val root=javaClass.getResourceAsStream("/app-en.json")!!.bufferedReader().use{Json.parseToJsonElement(it.readText()).jsonObject}
        assertTrue(root.size>=680)
        val argument=Regex("\\{[0-9]+\\}")
        for((ru,value) in root){val en=value.jsonPrimitive.content;assertEquals(argument.findAll(ru).map{it.value}.sorted().toList(),argument.findAll(en).map{it.value}.sorted().toList(),ru);assertFalse(en.contains(Regex("[А-Яа-яЁё]")),en)}
    }
}
