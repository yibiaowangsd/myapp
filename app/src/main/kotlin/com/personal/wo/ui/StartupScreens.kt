package com.personal.wo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable private fun StartupWordmark() {
    Box(Modifier.size(88.dp).background(WoMint,RoundedCornerShape(28.dp)),contentAlignment=Alignment.Center) {
        Text("我",color=Ink,fontSize=48.sp,fontWeight=FontWeight.Medium)
    }
}

@Composable fun StartupLoadingScreen() {
    Surface(Modifier.fillMaxSize(),color=Ink,contentColor=WoPaper) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Canvas(Modifier.fillMaxWidth().height(190.dp).align(Alignment.Center)) {
                val y=size.height*.68f
                drawLine(WoMint.copy(alpha=.13f),Offset(0f,y),Offset(size.width,y),1.dp.toPx())
                listOf(.09f,.23f,.77f,.91f).forEachIndexed { index,x ->
                    drawCircle(WoMint.copy(alpha=if(index==1||index==2).5f else .2f),3.dp.toPx(),Offset(size.width*x,y))
                }
            }
            Column(Modifier.align(Alignment.Center).padding(bottom=32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
                StartupWordmark()
                Text("日子有迹，回忆有光。",style=MaterialTheme.typography.titleLarge)
                CircularProgressIndicator(Modifier.size(20.dp),color=WoMint,strokeWidth=2.dp)
            }
            Text("正在打开你的时间轴",Modifier.align(Alignment.BottomCenter).padding(bottom=36.dp),color=WoPaper.copy(alpha=.6f),style=MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable fun StartupFailureScreen(report:String,retry:()->Unit,copy:()->Unit) {
    var details by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(),color=WoPaper,contentColor=Ink) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(28.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Spacer(Modifier.height(40.dp))
            StartupWordmark()
            Spacer(Modifier.height(16.dp))
            Text("稍等一下，\n回忆还在这里。",style=MaterialTheme.typography.headlineLarge)
            Text("暂时无法打开记录，现有数据已保留。请重试一次。",color=Ink.copy(alpha=.72f),style=MaterialTheme.typography.bodyLarge)
            Button(onClick=retry,colors=ButtonDefaults.buttonColors(containerColor=Ink,contentColor=WoPaper),contentPadding=PaddingValues(horizontal=28.dp,vertical=14.dp)) { Text("重新打开") }
            TextButton(onClick={details=!details},colors=ButtonDefaults.textButtonColors(contentColor=Ink)) { Text(if(details)"收起详情" else "查看问题详情") }
            if(details) {
                Surface(shape=RoundedCornerShape(24.dp),color=Color.White) {
                    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("请保留应用数据。以下信息可以帮助排查，不包含生活记录或密钥。",style=MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick=copy) { Text("复制诊断") }
                        SelectionContainer { Text(report,style=MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
