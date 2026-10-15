package com.swordfish.lemuroid.app.shared.cheats

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * User-driven document import/export. A new file is created; the source file is never overwritten.
 * Do not import the edited save into an emulator until the game has been closed and a backup exists.
 */
@Composable
fun EmeraldSaveEditDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var saveBytes by remember { mutableStateOf<ByteArray?>(null) }
    var inspection by remember { mutableStateOf<EmeraldSaveInspector.Inspection?>(null) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var message by remember { mutableStateOf("选择128KB的游戏内存档 .sav；先备份原件。") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val source = requireNotNull(saveBytes)
                val patched = EmeraldSixVPatcher.apply(source, selected)
                context.contentResolver.openOutputStream(uri)?.use { it.write(patched) }
                    ?: error("无法写入文件")
            }.onSuccess { message = "新的6V存档已导出；原件没有修改。" }
                .onFailure { message = "导出失败：${it.message}" }
        }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("无法读取文件")
                val parsed = EmeraldSaveInspector.inspect(raw)
                saveBytes = raw
                inspection = parsed
                selected = (1..parsed.partyCount).toSet()
                message = "已验证存档，队伍共有${parsed.partyCount}只。可选需要修改的成员。"
            }.onFailure {
                saveBytes = null
                inspection = null
                selected = emptySet()
                message = "格式或校验不通过：${it.message}"
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("宝可梦6V存档工具（实验功能）") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(message)
                Button(onClick = { load.launch(arrayOf("application/octet-stream", "*/*")) }) {
                    Text("选择 .sav 文件")
                }
                inspection?.let { info ->
                    Text("存档序号：${info.sequence}")
                    (1..info.partyCount).forEach { position ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Checkbox(
                                checked = position in selected,
                                onCheckedChange = { enabled ->
                                    selected = if (enabled) selected + position else selected - position
                                },
                            )
                            Text("队伍第${position}只：${info.partyIvs[position - 1].joinToString("/")}")
                        }
                    }
                    Button(
                        enabled = selected.isNotEmpty(),
                        onClick = { export.launch("pokemon_6v_test.sav") },
                    ) { Text("导出新6V存档") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
