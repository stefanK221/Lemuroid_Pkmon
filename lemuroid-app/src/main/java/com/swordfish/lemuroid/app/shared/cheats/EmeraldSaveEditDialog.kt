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
    var pendingImport by remember { mutableStateOf<ByteArray?>(null) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    var selectedBackup by remember { mutableStateOf<java.io.File?>(null) }
    var backupMenuExpanded by remember { mutableStateOf(false) }
    var inspection by remember { mutableStateOf<EmeraldSaveInspector.Inspection?>(null) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var showSaveConfirm by remember { mutableStateOf(false) }
    var pendingPatch by remember { mutableStateOf<ByteArray?>(null) }
    var patchSummary by remember { mutableStateOf("") }
    var individualMode by remember { mutableStateOf(false) }
    var individualEdits by remember { mutableStateOf<Map<Int, Pair<List<Int>, List<Int>?>>>(emptyMap()) }
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
            }.onSuccess { message = "SAV已成功导出到所选位置。" }
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
                individualEdits = emptyMap()
                message = "已验证存档，队伍共有${parsed.partyCount}只。默认只选中第一只，其他成员需手动勾选。"
            }.onFailure {
                saveBytes = null
                inspection = null
                individualEdits = emptyMap()
                selected = emptySet()
                liveSource = false
                message = "格式或校验不通过：${it.message}"
            }
        }
    }
    val importSave = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("无法读取所选文件")
                val parsed = EmeraldSaveInspector.inspect(raw)
                pendingImport = raw
                message = "导入存档已验证（序号${parsed.sequence}，队伍${parsed.partyCount}只）。请确认后替换当前游戏存档。"
                showImportConfirm = true
            }.onFailure {
                pendingImport = null
                message = "导入失败：${it.message}"
            }
        }
    }
    fun applyImported(raw: ByteArray) {
        EmeraldSaveInspector.inspect(raw)
        val current = onReadLiveSave()
        EmeraldSaveInspector.inspect(current)
        val dir = java.io.File(context.filesDir, "gba_plus_backups").apply { mkdirs() }
        java.io.File.createTempFile("before_import_", ".sav", dir).writeBytes(current)
        onApplyLiveSave(raw)
        saveBytes = null
        inspection = null
        individualEdits = emptyMap()
        liveSource = false
        message = "存档已应用，游戏将重启。原存档已自动备份。"
    }
    if (showImportConfirm) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false; pendingImport = null },
            title = { Text("确认导入SAV？") },
            text = { Text("将覆盖当前游戏存档并重启。当前存档会先自动备份，请确保已在游戏内保存。") },
            confirmButton = {
                TextButton(onClick = {
                    showImportConfirm = false
                    runCatching { applyImported(requireNotNull(pendingImport)) }
                        .onFailure { message = "应用导入存档失败：${it.message}" }
                    pendingImport = null
                }) { Text("备份并导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false; pendingImport = null }) { Text("取消") }
            },
        )
    }
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            title = { Text("确认恢复备份？") },
            text = { Text("将替换当前存档并重启。恢复前仍会自动备份当前存档。") },
            confirmButton = {
                TextButton(onClick = {
                    showRestoreConfirm = false
                    runCatching {
                        val raw = requireNotNull(selectedBackup).readBytes()
                        applyImported(raw)
                    }.onFailure { message = "恢复失败：${it.message}" }
                    selectedBackup = null
                }) { Text("确认恢复") }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirm = false; selectedBackup = null }) { Text("取消") }
            },
        )
    }
    if (showSaveConfirm) {
        AlertDialog(
            onDismissRequest = { showSaveConfirm = false; pendingPatch = null },
            title = { Text("确认修改队伍宝可梦？") },
            text = { Text(patchSummary + "\n将先备份当前SAV，然后保存修改并重启游戏。未保存的游戏进度可能丢失。") },
            confirmButton = {
                TextButton(onClick = {
                    showSaveConfirm = false
                    runCatching {
                        val original = requireNotNull(saveBytes)
                        val patched = requireNotNull(pendingPatch)
                        val dir = java.io.File(context.filesDir, "gba_plus_backups").apply { mkdirs() }
                        val backup = java.io.File.createTempFile("before_6v_", ".sav", dir)
                        backup.writeBytes(original)
                        onApplyLiveSave(patched)
                        liveSource = false
                        saveBytes = null
                        inspection = null
                        individualEdits = emptyMap()
                        message = "已更新存档并重启游戏。原存档备份：${backup.name}"
                    }.onFailure { message = "保存修改失败：${it.message}" }
                    pendingPatch = null
                }) { Text("确认保存") }
            },
            dismissButton = {
                TextButton(onClick = { showSaveConfirm = false; pendingPatch = null }) { Text("取消") }
            },
        )
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
                        individualEdits = emptyMap()
                        liveSource = true
                        message = "已读取当前存档。默认只选中第一只；请先在游戏中保存，修改会重启游戏。"
                    }.onFailure {
                        saveBytes = null
                        inspection = null
                        individualEdits = emptyMap()
                        selected = emptySet()
                        liveSource = false
                        message = "读取当前游戏存档失败：${it.message}"
                    }
                }) { Text("读取当前游戏存档（无需导入）") }
                Text("存档管理")
                Button(onClick = {
                    runCatching {
                        val raw = onReadLiveSave()
                        EmeraldSaveInspector.inspect(raw)
                        pendingExport = raw.copyOf()
                    }.onSuccess { export.launch("pokemon_current_backup.sav") }
                        .onFailure { message = "原样导出失败：${it.message}" }
                }) { Text("原样导出当前SAV（不修改）") }
                Button(onClick = { importSave.launch(arrayOf("application/octet-stream", "*/*")) }) {
                    Text("导入SAV并应用到游戏")
                }
                val backups = remember(message) {
                    java.io.File(context.filesDir, "gba_plus_backups")
                        .listFiles { file -> file.isFile && file.extension == "sav" }
                        ?.sortedByDescending { it.lastModified() }
                        ?.take(20)
                        ?: emptyList()
                }
                Box {
                    Button(enabled = backups.isNotEmpty(), onClick = { backupMenuExpanded = true }) {
                        Text("恢复自动备份（${backups.size}） ▾")
                    }
                    DropdownMenu(expanded = backupMenuExpanded, onDismissRequest = { backupMenuExpanded = false }) {
                        backups.forEach { file ->
                            DropdownMenuItem(
                                text = { Text(file.name) },
                                onClick = {
                                    backupMenuExpanded = false
                                    selectedBackup = file
                                    showRestoreConfirm = true
                                },
                            )
                        }
                    }
                }
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
                    TextButton(onClick = {
                        individualMode = !individualMode
                        individualEdits = emptyMap()
                        selected = if (info.partyCount > 0) setOf(1) else emptySet()
                        if (individualMode && info.partyCount > 0) {
                            customIvs = info.partyIvs[0].map { it.toString() }
                            customEvs = info.partyEvs[0].map { it.toString() }
                            useCustomIvs = true
                            evMode = 4
                        }
                    }) {
                        Text(if (individualMode) "✓ 逐只独立设置（点击切回批量）" else "逐只独立设置IV/EV")
                    }
                    if (individualMode) {
                        Text("下方每次只能选择一只；选择后自动填入当前IV、EV。调整后点击「记录这只的设置」，最后统一保存。")
                        Button(enabled = selected.size == 1, onClick = {
                            runCatching {
                                val position = selected.single()
                                individualEdits = individualEdits + (position to (ivValues() to evValues()))
                                message = "已记录队伍第${position}只的设置；可以选择下一只。"
                            }.onFailure { message = "设置无效：${it.message}" }
                        }) { Text("记录这只的设置") }
                        Text("已记录：${individualEdits.keys.sorted().joinToString(", ").ifBlank { "无" }}")
                        TextButton(onClick = { individualEdits = emptyMap() }) { Text("清空已记录设置") }
                    }
                    Text("存档序号：${info.sequence}")
                    (1..info.partyCount).forEach { position ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Checkbox(
                                checked = position in selected,
                                onCheckedChange = { enabled ->
                                    if (individualMode) {
                                        selected = if (enabled) setOf(position) else emptySet()
                                        if (enabled) {
                                            val existing = individualEdits[position]
                                            val ivs = existing?.first ?: info.partyIvs[position - 1]
                                            val evs = existing?.second ?: info.partyEvs[position - 1]
                                            customIvs = ivs.map { it.toString() }
                                            customEvs = evs.map { it.toString() }
                                            useCustomIvs = true
                                            evMode = 4
                                        }
                                    } else {
                                        selected = if (enabled) selected + position else selected - position
                                    }
                                },
                            )
                            Column {
                                Text("队伍第${position}只 · ${info.partyNames[position - 1].ifBlank { "宝可梦" }} · Lv.${info.partyLevels[position - 1]}")
                                Text("IV：${info.partyIvs[position - 1].joinToString("/")}")
                                Text("EV：${info.partyEvs[position - 1].joinToString("/")}")
                            }
                        }
                    }
                    if (liveSource) {
                        Button(enabled = if (individualMode) individualEdits.isNotEmpty() else selected.isNotEmpty(), onClick = {
                            runCatching {
                                val original = requireNotNull(saveBytes)
                                val evs = if (individualMode) null else evValues()
                                val patched = if (individualMode) {
                                    EmeraldSixVPatcher.applyIndividual(original, individualEdits)
                                } else {
                                    EmeraldSixVPatcher.apply(original, selected, evs, ivValues())
                                }
                                pendingPatch = patched
                                patchSummary = if (individualMode) {
                                    individualEdits.toSortedMap().entries.joinToString("\n") { (position, spread) ->
                                        "第${position}只：IV ${spread.first.joinToString("/")}，EV ${spread.second?.joinToString("/") ?: "保持原样"}"
                                    }
                                } else {
                                    "修改队伍位置：${selected.sorted().joinToString(", ")}\nIV：${ivValues().joinToString("/")}\nEV：${evs?.joinToString("/") ?: "保持原样"}"
                                }
                                showSaveConfirm = true
                            }.onFailure { message = "直接修改失败：${it.message}" }
                        }) { Text("保存修改并重启游戏") }
                        Text("请先在游戏内正常保存；此操作将重置游戏，未保存的进度可能丢失。")
                    }
                    Button(
                        enabled = if (individualMode) individualEdits.isNotEmpty() else selected.isNotEmpty(),
                        onClick = {
                            runCatching {
                                val source = requireNotNull(saveBytes)
                                pendingExport = if (individualMode) {
                                    EmeraldSixVPatcher.applyIndividual(source, individualEdits)
                                } else {
                                    EmeraldSixVPatcher.apply(source, selected, evValues(), ivValues())
                                }
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
