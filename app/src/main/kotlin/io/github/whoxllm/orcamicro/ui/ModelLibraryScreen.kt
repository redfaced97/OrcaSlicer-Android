package io.github.whoxllm.orcamicro.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.whoxllm.orcamicro.core.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.net.HttpURLConnection
import java.net.URI

@Composable
fun ModelLibraryScreen(state:UiState,vm:MainViewModel) {
    LaunchedEffect(Unit) { vm.cleanModelCache() }
    val snackbar=remember { SnackbarHostState() };MessageEffect(state.message,snackbar,vm::consumeMessage)
    val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::openModel) }
    var query by rememberSaveable { mutableStateOf("") };var tagText by rememberSaveable { mutableStateOf("") }
    var showCache by remember { mutableStateOf(false) };var editTags by remember { mutableStateOf<LibraryModel?>(null) };var newTags by remember { mutableStateOf("") }
    val cacheSize=android.text.format.Formatter.formatShortFileSize(LocalContext.current,state.modelCacheBytes)
    val tags=ModelTags.parse(tagText)
    val filtered=state.libraryModels.filter { ModelTags.matches(it,query.takeUnless { q->q.contains("://") }.orEmpty(),tags) }
    fun search(source:ModelSource=ModelSource.THINGIVERSE) {
        if(query.contains("://"))vm.openModelLink(query)
        else vm.browseModels(if(query.isBlank()&&tags.isEmpty())source.home else source.search(query,tags))
    }
    Scaffold(topBar={DesktopHeader(state,vm) {
        if(state.libraryBrowser!=null)IconButton(onClick=vm::closeModelBrowser){Icon(Icons.AutoMirrored.Filled.ArrowBack,"Вернуться к карточкам")}
        else {
            IconButton(onClick={showCache=true}){OrcaIcon(OrcaAction.Settings,"Настройки кэша моделей")}
            IconButton(onClick={pick.launch(arrayOf("*/*"))}){OrcaIcon(OrcaAction.Import,"Открыть скачанный файл")}
        }
    }},snackbarHost={SnackbarHost(snackbar)}) { padding->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if(state.libraryBusy) { LinearProgressIndicator(Modifier.fillMaxWidth());Text(state.libraryProgress.orEmpty(),Modifier.padding(12.dp)) }
            val url=state.libraryBrowser
            if(url!=null) ModelBrowser(url,vm,Modifier.weight(1f),!state.libraryBusy)
            else {
                LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    item { Text("Библиотека моделей",style=MaterialTheme.typography.titleLarge) }
                    item { OutlinedTextField(query,{query=it},label={Text("Поиск или ссылка на модель")},placeholder={Text("Подставка или https://…")},modifier=Modifier.fillMaxWidth(),singleLine=true,keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),keyboardActions=KeyboardActions(onSearch={search()})) }
                    if(query.contains("://"))item { Button(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick={vm.openModelLink(query)},modifier=Modifier.fillMaxWidth()){Text("Открыть ссылку") } }
                    item { OutlinedTextField(tagText,{tagText=it},label={Text("Теги через запятую")},placeholder={Text("phone, holder")},supportingText={Text("Фильтр карточек и поиск по тегам на площадках.")},singleLine=true,modifier=Modifier.fillMaxWidth()) }
                    val commonTags=state.libraryModels.flatMap { it.tags }.distinct().sorted().take(20)
                    if(commonTags.isNotEmpty())item { androidx.compose.foundation.lazy.LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        items(commonTags) { tag->FilterChip(selected=tag in tags,onClick={tagText=(if(tag in tags)tags-tag else tags+tag).joinToString(", ")},label={Text("#$tag")}) }
                    } }
                    items(ModelSource.entries.chunked(2)) { pair->
                        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) { pair.forEach { source->
                            OutlinedCard(onClick={search(source)},modifier=Modifier.weight(1f)) {
                                Column(Modifier.padding(14.dp)) { Text(source.title,style=MaterialTheme.typography.titleSmall);Text(if(query.isBlank()&&tags.isEmpty())"Открыть →" else "Найти →",style=MaterialTheme.typography.bodySmall) }
                            }
                        } }
                    }
                    item { Text("Скачайте STL / ZIP / 3MF на площадке или откройте файл с телефона.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    item { Text("Мои модели · ${filtered.size}",style=MaterialTheme.typography.titleMedium) }
                    item { Text("Кэш: $cacheSize · ${if(state.modelCacheDays==0)"без срока" else "${state.modelCacheDays} дн. после последнего открытия"}",style=MaterialTheme.typography.bodySmall) }
                    if(state.libraryModels.isEmpty())item { Text("Пока нет сохранённых моделей. Можно добавить карточку со страницы или открыть файл с телефона.",style=MaterialTheme.typography.bodyMedium) }
                    if(filtered.isEmpty()&&state.libraryModels.isNotEmpty())item { Text("Ничего не найдено. Измените запрос или теги.") }
                    items(filtered,key={it.id}) { model->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            ModelThumbnail(model.imageUrl)
                            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text(model.source?.title ?: "По ссылке",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                                Text(model.title,style=MaterialTheme.typography.titleMedium,maxLines=3)
                                Text(model.filename ?: "Файл ещё не скачан",style=MaterialTheme.typography.bodySmall)
                                if(model.tags.isNotEmpty())Text(model.tags.joinToString(" "){"#$it"},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick={vm.openLibraryModel(model.id)},enabled=!state.libraryBusy,modifier=Modifier.weight(1f)){Text(if(model.localFile==null)"Скачать" else "На стол")}
                                    Button(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick={vm.openLibraryModel(model.id,setup=true)},enabled=model.localFile!=null&&!state.libraryBusy,modifier=Modifier.weight(1f)){Text("К печати")}
                                }
                                Row {
                                    TextButton(onClick={vm.openModelLink(model.url)}){Text("Источник")}
                                    TextButton(onClick={editTags=model;newTags=model.tags.joinToString(", ")}){Text("Теги")}
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick={vm.deleteLibraryCard(model.id)},enabled=!state.libraryBusy){Text("Удалить")}
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.navigationBarsPadding()) }
                }
            }
        }
    }
    editTags?.let { model->AlertDialog(onDismissRequest={editTags=null},title={Text("Теги модели")},text={OutlinedTextField(newTags,{newTags=it},label={Text("Через запятую")},modifier=Modifier.fillMaxWidth())},confirmButton={TextButton(onClick={vm.setModelTags(model.id,newTags);editTags=null}){Text("Сохранить")}},dismissButton={TextButton(onClick={editTags=null}){Text("Отмена")}}) }
    if(showCache)AlertDialog(onDismissRequest={showCache=false},title={Text("Кэш моделей")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())) {
        Text("Удалять файлы после последнего открытия. Карточки и модель на столе сохраняются. Очистка выполняется при запуске и ежедневно в фоне.",style=MaterialTheme.typography.bodySmall)
        ModelCache.durations.forEach { days->TextButton(onClick={vm.setCacheDays(days)},modifier=Modifier.fillMaxWidth()) { Text((if(days==state.modelCacheDays)"✓ " else "")+(if(days==0)"Не удалять автоматически" else "$days дн.")) } }
        OutlinedButton(shape=MaterialTheme.shapes.small,contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp),onClick={vm.cleanModelCache(all=true)},enabled=!state.libraryBusy,modifier=Modifier.fillMaxWidth()){Text("Очистить кэш · $cacheSize")}
    }},confirmButton={TextButton(onClick={showCache=false}){Text("Готово")}})
}

@Composable private fun ModelThumbnail(url:String?) {
    var bitmap by remember(url) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(url) {
        bitmap=withContext(Dispatchers.IO) { runCatching {
            val uri=URI(url ?: return@withContext null)
            if(uri.scheme!="https"||uri.userInfo!=null)return@withContext null
            val http=(uri.toURL().openConnection() as HttpURLConnection).apply { connectTimeout=7000;readTimeout=7000;instanceFollowRedirects=false }
            try {
                if(http.responseCode !in 200..299 || http.contentLengthLong>3*1024*1024)return@withContext null
                val bytes=http.inputStream.use { it.readNBytes(3*1024*1024+1) }
                if(bytes.size>3*1024*1024)return@withContext null
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                val options=BitmapFactory.Options().apply { inSampleSize=maxOf(1,maxOf(bounds.outWidth,bounds.outHeight)/640) }
                BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
            } finally {http.disconnect()}
        }.getOrNull() }
    }
    bitmap?.let { Image(it.asImageBitmap(),null,Modifier.fillMaxWidth().height(170.dp),contentScale=ContentScale.Crop) }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable private fun ModelBrowser(initial:String,vm:MainViewModel,modifier:Modifier,enabled:Boolean) {
    val context=LocalContext.current
    var title by remember(initial) { mutableStateOf(ModelSource.of(initial)?.title.orEmpty()) }
    var page by remember(initial) { mutableStateOf(initial) };var image by remember(initial) { mutableStateOf<String?>(null) }
    var tags by remember(initial) { mutableStateOf(emptyList<String>()) }
    var loading by remember(initial) { mutableStateOf(true) };var back by remember(initial) { mutableStateOf(false) };var error by remember(initial) { mutableStateOf<String?>(null) }
    var browser:WebView? by remember(initial) { mutableStateOf(null) }
    fun download(url:String,filename:String,agent:String="")=vm.downloadModel(url,browser?.url?.takeIf { ModelSource.of(it)!=null } ?: page,browser?.title?.takeIf { it.isNotBlank() } ?: title,filename,image,agent,tags)
    val result=remember(initial) { runCatching {
        WebView(context).apply {
            settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false
            settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW;settings.useWideViewPort=true;settings.loadWithOverviewMode=true
            setDownloadListener { url,agent,disposition,mime,_->
                if(url.startsWith("https://"))download(url,URLUtil.guessFileName(url,disposition,mime),agent)
                else vm.showMessage("Сайт создал внутреннюю загрузку. Откройте его в браузере, скачайте файл и импортируйте через кнопку папки.")
            }
            webViewClient=object:WebViewClient() {
                override fun doUpdateVisitedHistory(view:WebView?,url:String?,isReload:Boolean) { if(url!=null&&ModelSource.of(url)!=null){page=url;title=view?.title.orEmpty();back=view?.canGoBack()==true} }
                override fun onPageStarted(view:WebView?,url:String?,icon:android.graphics.Bitmap?) { loading=true;error=null }
                override fun onPageFinished(view:WebView,url:String) {
                    loading=false;back=view.canGoBack()
                    if(ModelSource.of(url)!=null) {
                        page=url;title=view.title.orEmpty()
                        view.evaluateJavascript("(function(){var a=Array.from(document.querySelectorAll('a[href*=\"/tag/\"],a[href*=\"/tags/\"],a[href*=\"tag:\"]')).map(function(x){return x.textContent.trim().replace(/^#/, '');});return JSON.stringify({title:document.title,image:(document.querySelector('meta[property=\"og:image\"]')||{}).content||'',tags:a.slice(0,20)});})()") { encoded->
                            runCatching { val json=Json.parseToJsonElement(Json.parseToJsonElement(encoded).jsonPrimitive.content).jsonObject;title=json["title"]?.jsonPrimitive?.content ?: title;image=json["image"]?.jsonPrimitive?.content?.takeIf { it.startsWith("https://") };tags=ModelTags.parse((json["tags"] as? JsonArray)?.joinToString(","){it.jsonPrimitive.content}.orEmpty()) }
                        }
                    }
                }
                override fun onReceivedError(view:WebView?,request:WebResourceRequest?,problem:WebResourceError?) { if(request?.isForMainFrame==true){loading=false;error=problem?.description?.toString()} }
                override fun shouldOverrideUrlLoading(view:WebView?,request:WebResourceRequest?):Boolean {
                    val uri=request?.url ?: return true
                    if(uri.scheme in setOf("prusaslicer","orcaslicer","bambustudio")) {
                        runCatching {
                            when(val link=ModelLinks.parse(uri.toString())) {
                                is ModelLink.File -> download(link.url,link.name,view?.settings?.userAgentString.orEmpty())
                                is ModelLink.Page -> vm.browseModels(link.url)
                                null -> vm.showMessage("Выберите Download STL или Download 3MF")
                            }
                        }.onFailure { vm.showMessage(it.message ?: "Проверьте ссылку") }
                        return true
                    }
                    if(uri.scheme!="https")return true
                    return false
                }
            }
            loadUrl(initial)
        }
    } }
    val web=result.getOrNull()
    LaunchedEffect(web) {
        while(web!=null) {
            kotlinx.coroutines.delay(1500)
            val current=web.url
            if(current!=null&&ModelSource.of(current)!=null) {
                page=current;title=web.title.orEmpty();back=web.canGoBack()
                web.evaluateJavascript("(function(){return JSON.stringify({image:(document.querySelector('meta[property=\"og:image\"]')||{}).content||'',tags:Array.from(document.querySelectorAll('a[href*=\"/tag/\"],a[href*=\"/tags/\"],a[href*=\"tag:\"]')).map(function(x){return x.textContent.trim();}).slice(0,20)});})()") { encoded->
                    if(web.url==current)runCatching { val json=Json.parseToJsonElement(Json.parseToJsonElement(encoded).jsonPrimitive.content).jsonObject;image=json["image"]?.jsonPrimitive?.content?.takeIf{it.startsWith("https://")};tags=ModelTags.parse((json["tags"] as? JsonArray)?.joinToString(","){it.jsonPrimitive.content}.orEmpty()) }
                }
            }
        }
    }
    DisposableEffect(web) { browser=web;onDispose{web?.stopLoading();web?.destroy()} }
    BackHandler { if(back){browser?.goBack()}else vm.closeModelBrowser() }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            TextButton(onClick={vm.addLibraryCard(page,title,image,tags);vm.showMessage("Карточка сохранена")},enabled=enabled&&ModelSource.of(page)!=null,modifier=Modifier.weight(1f)){Text("В мои модели")}
            TextButton(onClick={runCatching { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(page))) }},modifier=Modifier.weight(1f)){Text("В браузере")}
        }
        if(loading)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(web==null)Text("Системный WebView недоступен. Откройте площадку в браузере и импортируйте скачанный файл.",Modifier.padding(12.dp))
        error?.let { Text("Не удалось открыть сайт: $it",Modifier.padding(12.dp),color=MaterialTheme.colorScheme.error) }
        if(web!=null)AndroidView(factory={web},modifier=Modifier.weight(1f).fillMaxWidth())
    }
}
