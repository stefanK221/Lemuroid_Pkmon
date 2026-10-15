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
import androidx.compose.material3.Switch
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
 * Reusable cheat-entry editor. It intentionally does NOT suggest enabled entries are applied
 * to the running emulator: the LibretroDroid native bridge is still required.
 */
@Composable
fun CheatManagerDialog(
    gameId: String,
    store: CheatStore,
    onDismiss: () -> Unit,
    onOpenSaveEditor: () -> Unit,
) {
    var entries by remember(gameId) { mutableStateOf(store.read(gameId)) }
    var title by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(CheatFormat.CODE_BREAKER) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("金手指管理（尚未接入游戏核心）") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("目前仅支持记录作弊码，尚不能在游戏中生效。")
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
                ) { Text("保存代码（尚不能生效）") }
                entries.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title)
                            Text(item.format.name)
                        }
                        Switch(
                            checked = false,
                            enabled = false,
                            onCheckedChange = null,
                        )
                        TextButton(onClick = {
                            store.delete(gameId, item.id)
                            entries = store.read(gameId)
                        }) { Text("删除") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
