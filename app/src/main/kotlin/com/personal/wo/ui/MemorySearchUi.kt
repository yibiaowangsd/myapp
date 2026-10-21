package com.personal.wo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.wo.MainActivity
import com.personal.wo.core.Episode
import com.personal.wo.core.Source
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun MemorySearchPage(s:WoUiState,vm:WoViewModel,onOpen:(Episode)->Unit) {
    val context=LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var all by rememberSaveable { mutableStateOf(true) }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var importMenu by rememberSaveable { mutableStateOf(false) }
    val photoReady=s.sources.any { it.source==Source.PHOTO&&it.authorization in listOf("GRANTED","PARTIAL") }
    LaunchedEffect(query,all,filter) { vm.search(query,all,filter) }
    LaunchedEffect(Unit) { if(s.demo)vm.demo(false) }
    val palette=MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize()) {
        Text("把日子，找回来。",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold,
            modifier=Modifier.padding(top=12.dp,bottom=8.dp))
        Text("照片、地点，还有你留下的每个片段。",color=palette.onSurfaceVariant,
            style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(bottom=20.dp))
        OutlinedTextField(value=query,onValueChange={query=it},modifier=Modifier.fillMaxWidth(),
            placeholder={Text("片段标题、地点或日期")},singleLine=true,shape=RoundedCornerShape(24.dp),
            leadingIcon={Icon(Icons.Outlined.Search,null)},
            trailingIcon={if(query.isNotBlank())IconButton(onClick={query=""}){Icon(Icons.Outlined.Close,"清除搜索")}})
        Row(Modifier.fillMaxWidth().padding(top=10.dp),horizontalArrangement=Arrangement.SpaceBetween,
            verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={all=!all}) {
                Text(if(all)"全部历史" else "最近90天")
                Icon(Icons.Outlined.SwapHoriz,null,Modifier.padding(start=4.dp).size(16.dp))
            }
            TextButton(onClick={if(photoReady)importMenu=true else (context as? MainActivity)?.requestPhotoJourney()},enabled=!s.photoImportBusy) {
                Icon(Icons.Outlined.AddPhotoAlternate,null,Modifier.size(18.dp))
                Text("导入照片",Modifier.padding(start=6.dp))
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to "全部","PHOTO" to "影像","FAVORITE" to "收藏","PLACE" to "足迹","APP" to "应用").forEach { (id,label) ->
                FilterChip(selected=filter==id,onClick={filter=id},label={Text(label)},shape=CircleShape)
            }
        }
        if(s.photoImportBusy) {
            Surface(color=palette.primaryContainer,shape=RoundedCornerShape(20.dp),modifier=Modifier.padding(vertical=10.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(s.photoImportProgress,Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
                        TextButton(onClick=vm::cancelPhotoImport){Text("停止")}
                    }
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp))
                }
            }
        }
        if(s.searching)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp).padding(top=1.dp))
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(top=12.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(query.isBlank())item(key="search-hint") {
                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth().padding(vertical=4.dp)) {
                    Text(if(filter=="FAVORITE")"珍藏的片段" else "最近的回忆",style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
                    Text("${s.results.size}${if(s.searchHasMore)"+" else ""} 项",color=palette.onSurfaceVariant,style=MaterialTheme.typography.labelMedium)
                }
            }
            if(s.results.isEmpty()&&!s.searching)item(key="empty-memory") {
                Surface(shape=RoundedCornerShape(28.dp),color=palette.surface,border=BorderStroke(1.dp,palette.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Icon(if(filter=="FAVORITE")Icons.Outlined.BookmarkBorder else Icons.Outlined.AutoAwesome,
                            null,tint=palette.primary,modifier=Modifier.size(34.dp))
                        Text(if(query.isNotBlank())"换个线索，再找找" else if(filter=="FAVORITE")"给回忆留一颗星" else "第一段回忆，从照片开始",
                            style=MaterialTheme.typography.titleLarge)
                        Text(when {
                            query.isNotBlank()->"可以搜索片段标题，或输入“昨天”“2026-10”。照片内容暂不自动识别。"
                            filter=="FAVORITE"->"打开一个片段，点收藏。以后想回看的日子，就在这里。"
                            else->"导入已有照片，按真实拍摄时间整理成生活时间轴。"
                        },style=MaterialTheme.typography.bodyMedium,color=palette.onSurfaceVariant)
                        if(query.isBlank()&&filter!="FAVORITE")Button(onClick={if(photoReady)importMenu=true else (context as? MainActivity)?.requestPhotoJourney()},enabled=!s.photoImportBusy) {
                            Text("从相册找回日子")
                        }
                    }
                }
            }
            itemsIndexed(s.results,key={_,episode->episode.id}) { index,episode ->
                val date=Instant.ofEpochMilli(episode.startMs).atZone(ZoneId.of(s.policy.archiveZone)).toLocalDate()
                val previous=s.results.getOrNull(index-1)?.let { Instant.ofEpochMilli(it.startMs).atZone(ZoneId.of(s.policy.archiveZone)).toLocalDate() }
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    if(date!=previous)Text(date.format(DateTimeFormatter.ofPattern("yyyy年 M月 d日")),
                        style=MaterialTheme.typography.labelLarge,color=palette.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,start=2.dp))
                    MemoryResultCard(episode,s.policy.archiveZone){onOpen(episode)}
                }
            }
            if(s.searchHasMore)item(key="more-memory") {
                OutlinedButton(onClick=vm::moreSearch,enabled=!s.searching,modifier=Modifier.fillMaxWidth()) { Text("继续往前翻") }
            }
        }
    }
    if(importMenu)AlertDialog(onDismissRequest={importMenu=false},title={Text("从相册找回日子")},text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("照片留在手机相册里，按拍摄时间整理。",color=palette.onSurfaceVariant)
            listOf(30 to "最近30天",365 to "过去一年",0 to "全部照片").forEach { (days,label) ->
                OutlinedButton(onClick={importMenu=false;vm.importPhotos(days)},modifier=Modifier.fillMaxWidth()) {
                    Text(label,Modifier.weight(1f));Icon(Icons.Outlined.ArrowForward,null,Modifier.size(18.dp))
                }
            }
        }
    },confirmButton={},dismissButton={TextButton(onClick={importMenu=false}){Text("稍后")}})
}

@Composable private fun MemoryResultCard(episode:Episode,zone:String,onClick:()->Unit) {
    val palette=MaterialTheme.colorScheme
    Surface(modifier=Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).clickable(onClick=onClick),
        shape=RoundedCornerShape(24.dp),color=palette.surface,border=BorderStroke(1.dp,palette.outlineVariant.copy(alpha=.6f))) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            val photo=episode.photoUris.firstOrNull()
            if(photo!=null)PhotoThumbnail(photo,Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)),crop=true)
            else Box(Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(laneTint(episode.lane).copy(alpha=.12f)),contentAlignment=Alignment.Center) {
                Icon(laneIcon(episode.lane),null,tint=laneTint(episode.lane))
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text(episode.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text("${clock(episode.startMs,zone)} · ${laneName(episode.lane)}",style=MaterialTheme.typography.labelMedium,color=palette.onSurfaceVariant)
                if(episode.detail.isNotBlank()&&photo==null)Text(episode.detail,style=MaterialTheme.typography.bodySmall,
                    color=palette.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            if(episode.isFavorite)Icon(Icons.Outlined.Bookmark,null,Modifier.size(18.dp),tint=palette.primary)
            Icon(Icons.Outlined.ChevronRight,null,Modifier.size(18.dp),tint=palette.onSurfaceVariant)
        }
    }
}
