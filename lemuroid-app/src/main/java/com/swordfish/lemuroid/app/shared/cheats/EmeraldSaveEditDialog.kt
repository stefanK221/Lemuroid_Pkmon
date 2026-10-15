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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
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
    var evMenuExpanded by remember { mutableStateOf(false) }
    var customIvs by remember { mutableStateOf(List(6) { "31" }) }
    var customEvs by remember { mutableStateOf(List(6) { "0" }) }
    var useCustomIvs by remember { mutableStateOf(false) }
    // Gen III EV order: HP, Attack, Defense, Speed, Sp. Attack, Sp. Defense.
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
                selected = if (parsed.partyCount > 0) setOf(1) else emptySet()
                message = "已验证存档，队伍共有${parsed.partyCount}只。默认只选中第一只，其他成员需手动勾选。"
            }.onFailure {
                saveBytes = null
                inspection = null
                selected = emptySet()
                liveSource = false
                message = "格式或校验不通过：${it.message}"
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("宝可梦属性编辑器") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(message)
                Button(onClick = {
                    runCatching {
                        val raw = onReadLiveSave()
                        val parsed = EmeraldSaveInspector.inspect(raw)
                        saveBytes = raw
                        inspection = parsed
                        selected = if (parsed.partyCount > 0) setOf(1) else emptySet()
                        liveSource = true
                        message = "已读取当前存档。默认只选中第一只；请先在游戏中保存，修改会重启游戏。"
                    }.onFailure {
                        saveBytes = null
                        inspection = null
                        selected = emptySet()
                        liveSource = false
                        message = "读取当前游戏存档失败：${it.message}"
                    }
                }) { Text("读取当前游戏存档（无需导入）") }
                Button(onClick = { load.launch(arrayOf("application/octet-stream", "*/*")) }) {
                    Text("选择 .sav 文件")
                }
                inspection?.let { info ->
                    val evOptions = listOf(
                        "保持原样",
                        "攻击252 / 速度252 / HP6",
                        "特攻252 / 速度252 / HP6",
                        "全部清零",
                        "自定义努力值",
                    )
                    Text("努力值方案（仅修改选中的成员）")
                    Box {
                        Button(onClick = { evMenuExpanded = true }) {
                            Text(evOptions[evMode] + " ▾")
                        }
                        DropdownMenu(
                            expanded = evMenuExpanded,
                            onDismissRequest = { evMenuExpanded = false },
                        ) {
                            evOptions.forEachIndexed { index, label ->
                                DropdownMenuItem(
                                    text = { Text((if (evMode == index) "✓ " else "") + label) },
                                    onClick = {
                                        evMode = index
                                        evMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                    TextButton(onClick = { useCustomIvs = !useCustomIvs }) {
                        Text(if (useCustomIvs) "✓ 自定义个体值" else "使用默认6V（点击自定义）")
                    }
                    val labels = listOf("HP", "攻击", "防御", "速度", "特攻", "特防")
                    if (useCustomIvs || evMode == 4) {
                        labels.forEachIndexed { index, label ->
                            Row {
                                Text(label, modifier = Modifier.width(52.dp))
                                if (useCustomIvs) {
                                    OutlinedTextField(
                                        value = customIvs[index],
                                        onValueChange = { value -> customIvs = customIvs.toMutableList().also { it[index] = value } },
                                        label = { Text("IV") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.width(92.dp),
                                    )
                                }
                                if (evMode == 4) {
                                    OutlinedTextField(
                                        value = customEvs[index],
                                        onValueChange = { value -> customEvs = customEvs.toMutableList().also { it[index] = value } },
                                        label = { Text("EV") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        modifier = Modifier.width(92.dp),
                                    )
                                }
                            }
                        }
                    }
                    fun ivValues(): List<Int> {
                        if (!useCustomIvs) return List(6) { 31 }
                        val values = customIvs.map { it.toIntOrNull() ?: error("IV必须为0～31的整数") }
                        require(values.all { it in 0..31 }) { "IV必须为0～31" }
                        return values
                    }
                    fun evValues(): List<Int>? {
                        val values = when (evMode) {
                            1 -> listOf(6, 252, 0, 252, 0, 0)
                            2 -> listOf(6, 0, 0, 252, 252, 0)
                            3 -> List(6) { 0 }
                            4 -> customEvs.map { it.toIntOrNull() ?: error("EV必须为整数") }
                            else -> return null
                        }
                        require(values.all { it in 0..252 } && values.sum() <= 510) { "EV单项不超过252，总和不超过510" }
                        return values
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
                            Column {
                                Text("队伍第${position}只 · Lv.${info.partyLevels[position - 1]}")
                                Text("IV：${info.partyIvs[position - 1].joinToString("/")}")
                                Text("EV：${info.partyEvs[position - 1].joinToString("/")}")
                            }
                        }
                    }
                    if (liveSource) {
                        Button(enabled = selected.isNotEmpty(), onClick = {
                            runCatching {
                                val original = requireNotNull(saveBytes)
                                val evs = evValues()
                                val patched = EmeraldSixVPatcher.apply(original, selected, evs, ivValues())
                                val dir = java.io.File(context.filesDir, "gba_plus_backups").apply { mkdirs() }
                                val backup = java.io.File.createTempFile("before_6v_", ".sav", dir)
                                backup.writeBytes(original)
                                onApplyLiveSave(patched)
                                liveSource = false
                                saveBytes = null
                                inspection = null
                                message = "已直接更新游戏存档并重置游戏。原存档备份：${backup.name}"
                            }.onFailure { message = "直接修改失败：${it.message}" }
                        }) { Text("保存修改并重启游戏") }
                        Text("请先在游戏内正常保存；此操作将重置游戏，未保存的进度可能丢失。")
                    }
                    Button(
                        enabled = selected.isNotEmpty(),
                        onClick = {
                            runCatching {
                                val source = requireNotNull(saveBytes)
                                val evs = evValues()
                                pendingExport = EmeraldSixVPatcher.apply(source, selected, evs, ivValues())
                            }.onSuccess { export.launch("pokemon_edited.sav") }
                                .onFailure { message = "修改验证失败，未创建文件：${it.message}" }
                        },
                    ) { Text("导出修改后的存档") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}
