package com.swordfish.lemuroid.app.shared.cheats

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
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
fun EmeraldSaveEditDialog(onDismiss: () -> Unit, onReadLiveSave: () -> ByteArray, onApplyLiveSave: (ByteArray) -> Unit) {
    val context = LocalContext.current
    var saveBytes by remember { mutableStateOf<ByteArray?>(null) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    var inspection by remember { mutableStateOf<EmeraldSaveInspector.Inspection?>(null) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var evMode by remember { mutableStateOf(0) }
    var liveSource by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("选择128KB的游戏内存档 .sav；先备份原件。") }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val patched = requireNotNull(pendingExport) { "请重新选择导出" }
                context.contentResolver.openOutputStream(uri)?.use { it.write(patched) }
                    ?: error("无法写入文件")
            }.onSuccess { message = "新的6V存档已导出；原件没有修改。" }
                .onFailure { message = "导出失败：${it.message}" }
        }
        pendingExport = null
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("无法读取文件")
                val parsed = EmeraldSaveInspector.inspect(raw)
                liveSource = false
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
        title = { Text("宝可梦6V / 努力值工具（实验功能）") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(message)
                Button(onClick = {
                    runCatching {
                        val raw = onReadLiveSave()
                        val parsed = EmeraldSaveInspector.inspect(raw)
                        saveBytes = raw
                        inspection = parsed
                        selected = (1..parsed.partyCount).toSet()
                        liveSource = true
                        message = "已读取当前游戏存档。请先在游戏中正常保存；修改将重置游戏。"
                    }.onFailure { message = "读取当前游戏存档失败：${it.message}" }
                }) { Text("读取当前游戏存档（无需导入）") }
                Button(onClick = { load.launch(arrayOf("application/octet-stream", "*/*")) }) {
                    Text("选择 .sav 文件")
                }
                inspection?.let { info ->
                    Text("努力值方案（仅修改选中的队伍成员）")
                    listOf("保持原样", "攻击252 / 速度252 / HP6", "特攻252 / 速度252 / HP6", "全部清零").forEachIndexed { index, label ->
                        TextButton(onClick = { evMode = index }) {
                            Text((if (evMode == index) "✓ " else "") + label)
                        }
                    }
                    Text("存档序号：${info.sequence}")
                    (1..info.partyCount).forEach { position ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Checkbox(
                                checked = position in selected,
                                onCheckedChange = { enabled ->
                                    selected = if (enabled) selected + position else selected - position
                                },
                            )
                            Text("队伍第${position}只 IV：${info.partyIvs[position - 1].joinToString("/")}; EV：${info.partyEvs[position - 1].joinToString("/")}")
                        }
                    }
                    if (liveSource) {
                        Button(enabled = selected.isNotEmpty(), onClick = {
                            runCatching {
                                val original = requireNotNull(saveBytes)
                                val evs = when (evMode) {
                                    1 -> listOf(6, 252, 0, 252, 0, 0)
                                    2 -> listOf(6, 0, 0, 252, 252, 0)
                                    3 -> listOf(0, 0, 0, 0, 0, 0)
                                    else -> null
                                }
                                val patched = EmeraldSixVPatcher.apply(original, selected, evs)
                                val dir = java.io.File(context.filesDir, "gba_plus_backups").apply { mkdirs() }
                                val backup = java.io.File.createTempFile("before_6v_", ".sav", dir)
                                backup.writeBytes(original)
                                onApplyLiveSave(patched)
                                liveSource = false
                                saveBytes = null
                                inspection = null
                                message = "已直接更新游戏存档并重置游戏。原存档备份：${backup.name}"
                            }.onFailure { message = "直接修改失败：${it.message}" }
                        }) { Text("直接应用6V到当前游戏（会重置）") }
                        Text("请先在游戏内正常保存；此操作将重置游戏，未保存的进度可能丢失。")
                    }
                    Button(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            runCatching {
                                val source = requireNotNull(saveBytes)
                                val evs = when (evMode) {
                                    1 -> listOf(6, 252, 0, 252, 0, 0)
                                    2 -> listOf(6, 0, 0, 252, 252, 0)
                                    3 -> listOf(0, 0, 0, 0, 0, 0)
                                    else -> null
                                }
                                pendingExport = EmeraldSixVPatcher.apply(source, selected, evs)
                            }.onSuccess { export.launch("pokemon_6v_test.sav") }
                                .onFailure { message = "修改验证失败，未创建文件：${it.message}" }
                        },
                    ) { Text("导出新6V存档") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
