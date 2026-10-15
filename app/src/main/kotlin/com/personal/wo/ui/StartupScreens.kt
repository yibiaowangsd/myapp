package com.personal.wo.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable fun StartupLoadingScreen() {
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(16.dp,Alignment.CenterVertically),horizontalAlignment=Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text("正在打开我的记录")
        }
    }
}

@Composable fun StartupFailureScreen(report:String,retry:()->Unit,copy:()->Unit) {
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(28.dp))
            Text("暂时无法打开记录",style=MaterialTheme.typography.headlineMedium)
            Text("现有数据已保留。可以重试，或复制下方诊断以便排查。请保留应用数据。")
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Button(onClick=retry) { Text("重试") }
                OutlinedButton(onClick=copy) { Text("复制诊断") }
            }
            Text("诊断仅包含版本、设备和错误位置，不包含生活记录或密钥。",style=MaterialTheme.typography.bodyMedium)
            SelectionContainer { Text(report,style=MaterialTheme.typography.bodySmall) }
        }
    }
}
