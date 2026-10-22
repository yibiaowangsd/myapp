package com.personal.wo.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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

@Composable fun SharedDataPage(demo:Boolean,vm:SharedDataViewModel=viewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refresh() }
    SharedDataSettings(s,demo,vm::saveAndSync,vm::sync,vm::pause)
}

@Composable fun SharedDataSettings(s:SharedDataUiState,demo:Boolean,save:(String,String)->Unit,sync:()->Unit,pause:()->Unit) {
    val context=LocalContext.current
    var repository by rememberSaveable(s.repository) { mutableStateOf(s.repository) }
    var token by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    val enabled=s.loaded&&!s.busy&&!demo
    LazyColumn(Modifier.fillMaxSize().imePadding().testTag("shared-data-page"),verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(bottom=28.dp)) {
        item { Text("让 ChatGPT 读取你的生活记录",style=MaterialTheme.typography.titleLarge)
            Muted("记录同步到你自己的私有 GitHub 仓库。继续在 ChatGPT 里交流，通过已连接的 GitHub 读取当前和过去的数据。") }
        if(demo)item { Text("虚构演示中的数据不会上传。请先退出演示。",color=MaterialTheme.colorScheme.error) }
        item { Paper {
            OutlinedTextField(repository,{repository=it.trim();copied=false},Modifier.fillMaxWidth().testTag("shared-repository"),enabled=enabled,
                label={Text("私有仓库")},placeholder={Text("你的用户名/wo-life-data")},singleLine=true)
            OutlinedTextField(token,{token=it},Modifier.fillMaxWidth().testTag("shared-token"),enabled=enabled,
                label={Text(if(s.hasToken)"GitHub 令牌（留空保留）" else "GitHub 访问令牌")},singleLine=true,visualTransformation=PasswordVisualTransformation())
            Muted("令牌只需此数据仓库的 Contents 读写权限，在本机加密保存。这里不需要 OpenAI API 密钥。")
            Button(onClick={save(repository,token);token=""},enabled=enabled&&repository.contains('/')&&(s.hasToken||token.isNotBlank()),modifier=Modifier.testTag("shared-save")){Text("保存并同步")}
        } }
        item { Paper {
            Text(if(s.enabled)"自动同步已开启" else "自动同步未开启",style=MaterialTheme.typography.titleMedium)
            Text(s.status.message,style=MaterialTheme.typography.bodyMedium)
            if(s.status.lastSuccessMs>0)Muted("上次成功：${Instant.ofEpochMilli(s.status.lastSuccessMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))} · ${s.status.days} 天")
            Muted("记录变化后安排同步，并约每30分钟补同步；网络、省电和系统后台策略可能延后。历史较多时分批完成。")
            if(s.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            s.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick=sync,enabled=enabled&&s.enabled){Text("立即同步")}
                TextButton(onClick=pause,enabled=enabled&&s.enabled){Text("暂停同步")}
            }
        } }
        item { Paper {
            Text("在 ChatGPT 中连接一次",style=MaterialTheme.typography.titleMedium)
            Muted("确保 ChatGPT 的 GitHub 连接已获准访问这个私有仓库，再将下面的说明发到你的对话中。以后可以直接询问当天记录或历史变化。")
            OutlinedButton(onClick={
                val text="我的生活记录与共享记忆由 App「我」持续同步到私有仓库 $repository。请通过 GitHub 读取 wo/index.json、其中 memory 指向的 wo/memory.json，再按日期读取日文件。memory.notes 是我填写的偏好；observations 和 review 是 DeepSeek 的历史推断，不是确认事实。回答时结合当前可用的聊天记忆；以文件中的日期、来源和缺失说明为准。需要新数据时重新读取，不把旧快照当作实时数据。"
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("生活数据连接",text));copied=true
            },enabled=s.enabled&&repository==s.repository,modifier=Modifier.testTag("shared-copy")){Text(if(copied)"已复制连接说明" else "复制连接说明")}
            Muted("同步提供可持续查询的历史来源，不会自动把每条记录写入 ChatGPT 账号记忆。")
        } }
        item { Paper {
            Text("同步范围",style=MaterialTheme.typography.titleMedium)
            Muted("每日摘要、步数等指标、事件时间与标题、来源和缺失情况，以及已保存的偏好和 DeepSeek 分析记忆；不上传 API 密钥、相册原图、精确坐标与通知正文。关闭同步保留手机记录和已上传文件。")
            Muted("本地删除的日期在后续同步中更新或移出当前索引。GitHub 提交历史仍可能保留旧版本。")
            TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/new")))}){Text("创建仓库（请选择 Private）")}
            TextButton(onClick={context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/settings/personal-access-tokens/new")))}){Text("创建仅限数据仓库的访问令牌")}
        } }
    }
}
