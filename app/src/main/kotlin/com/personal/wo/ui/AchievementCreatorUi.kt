package com.personal.wo.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.personal.wo.core.*
import java.util.UUID

/** A full-height editor with independently scrolling fields and a fixed save bar. */
@Composable internal fun AchievementCreator(onDismiss:()->Unit,onSave:(AchievementGoal)->Unit) {
    var name by rememberSaveable{mutableStateOf("")}
    var description by rememberSaveable{mutableStateOf("")}
    var target by rememberSaveable{mutableStateOf("")}
    var metricName by rememberSaveable{mutableStateOf(AchievementMetric.DAYS.name)}
    var expanded by remember{mutableStateOf(false)}
    val metric=AchievementMetric.valueOf(metricName)
    val candidate=remember(name,description,target,metric) {
        runCatching {
            AchievementGoal("custom-draft",name,description.ifBlank{"这是为自己设定的一份日常约定。"},metric,target.toLongOrNull()?:0,1,true).checked()
        }.getOrNull()
    }
    Surface(Modifier.fillMaxSize().testTag("achievement-editor"),shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxSize().padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("给自己一个小目标",style=MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(name,{name=it.take(28)},label={Text("成就名称")},singleLine=true,
                    modifier=Modifier.fillMaxWidth().testTag("custom-achievement-name"))
                OutlinedTextField(description,{description=it.take(180)},label={Text("写给自己的话（可选）")},minLines=2,modifier=Modifier.fillMaxWidth())
                Box {
                    OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text("统计依据 · ${metric.label}")}
                    DropdownMenu(expanded,{expanded=false}) {
                        AchievementMetric.entries.forEach { value->DropdownMenuItem(text={Text(value.label)},onClick={metricName=value.name;expanded=false}) }
                    }
                }
                OutlinedTextField(target,{target=it.filter(Char::isDigit).take(10)},label={Text("目标（${metric.unit}）")},singleLine=true,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth().testTag("custom-achievement-target"))
                Text(metric.note,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text("使用当前保留记录累计，不从创建日清零。图标随所选系列生成。",style=MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End,verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick=onDismiss){Text("取消")}
                Spacer(Modifier.width(10.dp))
                Button(onClick={candidate?.let{onSave(it.copy(id="custom-${UUID.randomUUID()}"))}},enabled=candidate!=null,
                    modifier=Modifier.testTag("custom-achievement-save")){Text("创建")}
            }
        }
    }
}
