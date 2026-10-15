package com.swordfish.lemuroid.app.shared.cheats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Per-game cheat editor. Applying cheats is delegated to the active GLRetroView.
 */
@Composable
fun CheatManagerDialog(
    gameId: String,
    store: CheatStore,
    onDismiss: () -> Unit,
    onOpenSaveEditor: () -> Unit,
    onApplyCheat: (Int, Boolean, String) -> Unit,
) {
    var entries by remember(gameId) { mutableStateOf(store.read(gameId)) }
    var title by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(CheatFormat.CODE_BREAKER) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("金手指管理（测试版）") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("金手指启用后可以尝试关闭；请先备份存档。某些代码的效果可能持续到重启或重新进入地图。")
                Button(onClick = onOpenSaveEditor) { Text("打开6V存档编辑器") }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名称") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("代码（允许多行十六进制）") },
                    minLines = 2,
                )
                Text("代码类型（需与来源一致）")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(CheatFormat.CODE_BREAKER, CheatFormat.GAME_SHARK).forEach { option ->
                        TextButton(onClick = { format = option }) {
                            Text(if (format == option) "✓ ${option.name}" else option.name)
                        }
                    }
                }
                if (error != null) Text(error!!)
                Text("启用时如发生错误，原因会显示在这里。")
                Button(
                    onClick = {
                        runCatching {
                            store.add(gameId, title, code, format)
                            entries = store.read(gameId)
                            title = ""
                            code = ""
                            error = null
                        }.onFailure { error = it.message ?: "无法保存代码" }
                    },
                ) { Text("保存代码") }
                entries.forEachIndexed { index, item ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title)
                            Text(item.format.name)
                        }
                        Button(
                            onClick = {
                                runCatching {
                                    onApplyCheat(index, !item.enabled, item.code)
                                    store.setEnabled(gameId, item.id, !item.enabled)
                                    entries = store.read(gameId)
                                    error = null
                                }.onFailure {
                                    error = "操作失败：${it.message ?: it.javaClass.simpleName}"
                                }
                            },
                        ) { Text(if (item.enabled) "关闭" else "启用") }
                        TextButton(onClick = {
                            runCatching {
                                if (item.enabled) onApplyCheat(index, false, item.code)
                                store.delete(gameId, item.id)
                                entries = store.read(gameId)
                                error = null
                            }.onFailure { error = "删除失败：${it.message}" }
                        }) { Text("删除") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
