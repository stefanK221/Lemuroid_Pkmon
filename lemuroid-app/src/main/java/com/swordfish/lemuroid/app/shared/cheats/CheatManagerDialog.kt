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
    onClearAllCheats: () -> Unit,
    onExitGame: () -> Unit,
) {
    var entries by remember(gameId) { mutableStateOf(store.read(gameId)) }
    var title by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(CheatFormat.CODE_BREAKER) }
    var error by remember { mutableStateOf<String?>(null) }
    var needsRestart by remember(gameId) { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    var showAddForm by remember { mutableStateOf(false) }
    var potentiallyActive by remember(gameId) { mutableStateOf(setOf<String>()) }
    var lastDisabledIds by remember(gameId) { mutableStateOf(setOf<String>()) }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("退出游戏以彻底关闭金手指？") },
            text = { Text("请先回到正常位置并在游戏内存档。将先关闭所有已启用的代码，再退出游戏。重新打开游戏后才可能清除穿墙等残留内存效果；未保存的进度可能丢失。") },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        onClearAllCheats()
                        entries.filter { it.enabled }.forEach { item ->
                            store.setEnabled(gameId, item.id, false)
                        }
                        entries = store.read(gameId)
                        confirmExit = false
                        onExitGame()
                    }.onFailure {
                        entries = store.read(gameId)
                        confirmExit = false
                        error = "关闭金手指失败，未退出游戏：${it.message}"
                    }
                }) { Text("关闭金手指并退出") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("取消") } },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("金手指管理（测试版）") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("金手指可能改变游戏内存；即使停止执行，穿墙等效果仍可能持续。请先备份存档。")
                Text("已保存金手指（${entries.size}）")
                if (entries.any { it.enabled }) {
                    Button(onClick = {
                        runCatching {
                            onClearAllCheats()
                            entries.filter { it.enabled }.forEach { item ->
                                store.setEnabled(gameId, item.id, false)
                                potentiallyActive = potentiallyActive + item.id
                                lastDisabledIds = lastDisabledIds + item.id
                            }
                            entries = store.read(gameId)
                            needsRestart = true
                            error = null
                        }.onFailure {
                            entries = store.read(gameId)
                            error = "批量关闭失败，部分代码可能仍在运行：${it.message}"
                        }
                    }) { Text("关闭全部（可能需重启）") }
                }

                if (entries.isEmpty()) Text("暂无金手指，点击下方「新增代码」。")
                entries.forEachIndexed { index, item ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title)
                            Text(when {
                                item.enabled -> "已启用 · ${item.format.name}"
                                item.id in potentiallyActive -> "已请求关闭 · 可能需重启"
                                else -> item.format.name
                            })
                        }
                        Button(onClick = {
                            runCatching {
                                onApplyCheat(index, !item.enabled, item.code)
                                store.setEnabled(gameId, item.id, !item.enabled)
                                if (item.enabled) {
                                    needsRestart = true
                                    potentiallyActive = potentiallyActive + item.id
                                    lastDisabledIds = lastDisabledIds + item.id
                                } else {
                                    potentiallyActive = potentiallyActive - item.id
                                    lastDisabledIds = lastDisabledIds - item.id
                                }
                                entries = store.read(gameId)
                                error = null
                            }.onFailure { error = "操作失败：${it.message ?: it.javaClass.simpleName}" }
                        }) { Text(if (item.enabled) "关闭" else "启用") }
                        TextButton(enabled = entries.none { it.enabled } && potentiallyActive.isEmpty() && !needsRestart, onClick = {
                            runCatching {
                                store.delete(gameId, item.id)
                                entries = store.read(gameId)
                                error = null
                            }.onFailure { error = "删除失败：${it.message}" }
                        }) { Text("删除") }
                    }
                }
                Button(onClick = { showAddForm = !showAddForm }) {
                    Text(if (showAddForm) "收起新增代码" else "＋ 新增代码")
                }
                if (showAddForm) {
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
                            Text(if (format == option) "✓ " + (if (option == CheatFormat.CODE_BREAKER) "CodeBreaker" else "GameShark") else if (option == CheatFormat.CODE_BREAKER) "CodeBreaker" else "GameShark")
                        }
                    }
                }
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
                }
                Button(onClick = onOpenSaveEditor) { Text("打开6V存档编辑器") }
                if (error != null) Text(error!!)
                if (needsRestart || potentiallyActive.isNotEmpty() || lastDisabledIds.isNotEmpty()) {
                    Text("⚠️ 已请求关闭金手指，但穿墙等内存效果可能持续。请在游戏内正常存档后退出并重新进入。不要加载开启金手指时制作的即时存档。")
                    Button(onClick = { confirmExit = true }) { Text("退出游戏以彻底关闭（先存档）") }
                }
                if (error != null) Text("操作失败时请先退出本界面，避免重复提交不确定状态的代码。")


            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
