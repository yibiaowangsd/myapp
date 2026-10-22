package com.personal.wo.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun DeepSeekPage(demo:Boolean,vm:DeepSeekViewModel=viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    DeepSeekSettings(state,demo,vm::saveAndReview,vm::review,vm::pause,vm::saveNotes,vm::forget)
}

@Composable fun DeepSeekSettings(
    s:DeepSeekUiState,demo:Boolean,save:(String,String,Boolean)->Unit,review:()->Unit,
    pause:()->Unit,saveNotes:(String)->Unit,forget:()->Unit
) {
    val context=LocalContext.current
    var key by remember { mutableStateOf("") }
    var model by rememberSaveable(s.model) { mutableStateOf(s.model) }
    var automatic by rememberSaveable(s.autoReview) { mutableStateOf(s.autoReview) }
    var notes by rememberSaveable(s.memory.notes) { mutableStateOf(s.memory.notes) }
    var confirmForget by remember { mutableStateOf(false) }
    val enabled=s.loaded&&!s.busy&&!demo
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("deepseek-page"),verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(bottom=28.dp)) {
        item {
            Text("DeepSeek 与长期记忆",style=MaterialTheme.typography.titleLarge)
            Muted("结合已收集的生活记录生成回顾。偏好和历史观察保存在本机，下次分析继续使用；开启数据互通后，也会同步给 ChatGPT 读取。")
        }
        if(demo)item { Text("请退出虚构演示后配置。演示数据不会发送给 DeepSeek。",color=MaterialTheme.colorScheme.error) }
        item { Paper {
            OutlinedTextField(key,{key=it},Modifier.fillMaxWidth().testTag("deepseek-key"),enabled=enabled,singleLine=true,
                label={Text(if(s.hasKey)"DeepSeek API Key（留空保留）" else "DeepSeek API Key")},visualTransformation=PasswordVisualTransformation())
            Text("模型",style=MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("deepseek-flash","deepseek-v4-pro").forEach { value ->
                    FilterChip(selected=model==value,onClick={model=value},enabled=enabled,label={Text(value)})
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text("自动每日回顾");Muted("每天至多尝试一次，分析昨天及此前的记录。") }
                Switch(automatic,{automatic=it},enabled=enabled,modifier=Modifier.testTag("deepseek-automatic"))
            }
            Muted("保存后会将近期结构化记录和记忆发送到 DeepSeek，按你的 API 账户计费。密钥仅在本机加密保存。")
            Button(onClick={save(key,model,automatic);key=""},enabled=enabled&&(s.hasKey||key.isNotBlank()),modifier=Modifier.testTag("deepseek-save")){Text("保存并生成回顾")}
            TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://platform.deepseek.com/api_keys")))}){Text("获取 DeepSeek API Key")}
        } }
        item { Paper {
            Text(if(s.enabled)"DeepSeek 已开启" else "DeepSeek 未开启",style=MaterialTheme.typography.titleMedium)
            if(s.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            s.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            s.memory.review?.let { r ->
                Text(r.text,modifier=Modifier.testTag("deepseek-review"))
                val time=Instant.ofEpochMilli(r.createdAtMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                Muted("AI 生成 · $time · 记录截至 ${r.throughDate} · ${r.model}")
            } ?: Muted("生成后的回顾会保留在这里。网络失败或重启 App 不会清空记忆。")
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick=review,enabled=enabled&&s.enabled){Text("更新回顾")}
                TextButton(onClick=pause,enabled=enabled&&s.enabled){Text("关闭 DeepSeek")}
            }
            Muted("关闭后停止调用，保留已有记忆。后台回顾受网络和系统省电策略影响；记录暂停时不会自动分析。")
        } }
        item { Paper {
            Text("希望记住的偏好与目标",style=MaterialTheme.typography.titleMedium)
            OutlinedTextField(notes,{if(it.length<=4000)notes=it},Modifier.fillMaxWidth().testTag("memory-notes"),enabled=enabled,
                label={Text("例如：每周想读书三次；回顾时关注睡眠和步行")},minLines=3,maxLines=8,supportingText={Text("${notes.length}/4000")})
            OutlinedButton(onClick={saveNotes(notes)},enabled=enabled&&notes.trim()!=s.memory.notes,modifier=Modifier.testTag("memory-save")){Text("保存记忆")}
            Muted("这是你亲自确认的信息。修改后会清除旧 AI 推断，再按新偏好分析。")
        } }
        item { Paper {
            Text("历史观察 · ${s.memory.observations.size} 条",style=MaterialTheme.typography.titleMedium)
            Muted("AI 根据记录推断，可能不准确。保留最近200条，每次分析带入最近64条和最多30个有记录的日期；完整日记录仍可通过数据互通查询。")
            s.memory.observations.takeLast(20).asReversed().forEach { observation ->
                Text(observation.text,style=MaterialTheme.typography.bodyMedium)
                Muted(observation.dates.joinToString("、"))
                HorizontalDivider()
            }
            TextButton(onClick={confirmForget=true},enabled=enabled,modifier=Modifier.testTag("memory-forget")){Text("清空记忆")}
            Muted("删除生活记录时会清除 AI 推断；你填写的偏好可在这里修改或清空。共享记忆与 DeepSeek、ChatGPT 各自账号的内置记忆相互独立。")
        } }
    }
    if(confirmForget)AlertDialog(onDismissRequest={confirmForget=false},title={Text("清空长期记忆？")},
        text={Text("清除偏好、历史观察和回顾，保留生活原始记录。后续分析可能从记录重新形成观察；已开启的数据互通会同步清空结果，GitHub 历史可能保留旧版本。")},
        confirmButton={TextButton(onClick={confirmForget=false;forget()}){Text("清空")}},dismissButton={TextButton(onClick={confirmForget=false}){Text("取消")}})
}
