package io.github.whoxllm.orcamicro.core.i18n

import kotlinx.serialization.json.*

/** Built-in UI and error messages only. User names, model metadata and raw diagnostics are never translated. */
object AppText {
    @Volatile var english:Boolean=false
    private val translations:Map<String,String> by lazy {
        javaClass.getResourceAsStream("/app-en.json")!!.bufferedReader().use { input->Json.parseToJsonElement(input.readText()).jsonObject.mapValues{it.value.jsonPrimitive.content} }
    }
    private val placeholder=Regex("\\{([0-9]+)\\}")
    fun text(source:String,vararg values:Any?):String {
        val template=if(english)translations[source] ?: source else source
        return placeholder.replace(template){match->val index=match.groupValues[1].toInt();if(index in values.indices)values[index]?.toString().orEmpty()else match.value}
    }
}
fun tr(source:String,vararg values:Any?):String=AppText.text(source,*values)
