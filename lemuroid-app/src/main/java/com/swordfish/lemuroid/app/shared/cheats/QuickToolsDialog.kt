package com.swordfish.lemuroid.app.shared.cheats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun QuickToolsDialog(
    fileName: String,
    onDismiss: () -> Unit,
    onReadSave: () -> ByteArray,
    onApplySave: (ByteArray) -> Unit,
    onGetFlags: () -> Int,
    onSetFlags: (Int) -> Unit,
) {
    val context = LocalContext.current
    var profile by remember { mutableStateOf<QuickToolsProfile.Verified?>(null) }
    var flags by remember { mutableStateOf(onGetFlags()) }
    var message by remember { mutableStateOf("正在核对游戏版本…") }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(6) }
    var nature by remember { mutableStateOf<Int?>(null) }
    var shiny by remember { mutableStateOf(false) }
    var levelText by remember { mutableStateOf("1") }
    var natureMenuOpen by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(fileName) {
        val result = withContext(Dispatchers.IO) { runCatching { QuickToolsProfile.verify(context, fileName) } }
        result.onSuccess { profile = it; message = "已适配：漆黑的魅影 5.0EX+BW（精确版本校验通过）" }
            .onFailure { message = it.message ?: "无法识别此游戏" }
    }
    fun backup(raw: ByteArray, prefix: String) {
        EmeraldSaveInspector.inspect(raw)
        val dir = File(context.filesDir, "gba_plus_backups")
        check(dir.isDirectory || dir.mkdirs()) { "无法创建备份目录" }
        File.createTempFile(prefix, ".sav", dir).writeBytes(raw)
    }
    fun requestToggle(bit: Int, title: String) {
        if (flags and bit != 0) {
            runCatching { onSetFlags(flags and bit.inv()); flags = onGetFlags(); message = "已停止$title；已获得的金钱或物品保留。" }
                .onFailure { message = "操作失败：${it.message}" }
        } else {
            confirmation = "开启$title？开启期间持续补充；关闭后停止补充。会先备份最近一次游戏内 SAV。精灵球口袋满时不会覆盖其他物品。"
            action = {
                val raw = onReadSave()
                val resources = EmeraldQuickSavePatcher.resources(raw)
                if (bit == 2) require(resources.hasBallSpace) { "精灵球口袋已满，请先腾出空位" }
                backup(raw, "before_quick_")
                onSetFlags(flags or bit)
                flags = onGetFlags()
                message = "${title}已开启，回到游戏后生效。关闭只停止补充，不扣回已获得内容。"
            }
        }
    }
    if (confirmation != null) {
        AlertDialog(
            onDismissRequest = { confirmation = null; action = null },
            title = { Text("确认快捷操作") },
            text = { Text(confirmation!!) },
            confirmButton = { TextButton(onClick = {
                val pending = action
                action = null; confirmation = null
                runCatching { requireNotNull(pending).invoke() }.onFailure { message = "未完成操作：${it.message}" }
            }) { Text("备份并执行") } },
            dismissButton = { TextButton(onClick = { confirmation = null; action = null }) { Text("取消") } },
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("常用功能") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message)
                Text("持续功能仅本次游戏会话有效。重开游戏需重新开启；修改会保留已获得的金钱与物品。")
                Button(enabled = profile != null, onClick = { requestToggle(1, "无限金钱") }) {
                    Text(if (flags and 1 != 0) "关闭无限金钱（已开启）" else "开启无限金钱（保持999999）")
                }
                Button(enabled = profile != null, onClick = { requestToggle(2, "无限大师球") }) {
                    Text(if (flags and 2 != 0) "关闭无限大师球（已开启）" else "开启无限大师球（保持99个）")
                }
                if (flags != 0) TextButton(onClick = {
                    runCatching { onSetFlags(0); flags = 0; message = "已停止金钱与大师球的持续补充。" }
                        .onFailure { message = "关闭失败：${it.message}" }
                }) { Text("关闭两个持续功能") }
                Text("获取指定精灵：加入电脑空盒位，不替换队伍。Lv.1、6V、招式撞击；不自动补图鉴记录。先在游戏内保存，执行后重启游戏。")
                OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("搜索名称或内部编号") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                val number = search.toIntOrNull()
                val known = QuickToolsProfile.namedSpecies.filter { (id, name) -> search.isBlank() || name.contains(search) || id.toString() == search }
                val candidates = if (number != null && (number in 1..251 || number in 277..411)) {
                    listOf(number to (QuickToolsProfile.namedSpecies[number] ?: "精灵（内部编号 $number）"))
                } else known.entries.take(8).map { it.key to it.value }
                if (candidates.isEmpty()) Text("暂无此名称。可输入本版本内部编号：1–251、277–411（不是全国图鉴编号）。")
                candidates.forEach { (id, name) ->
                    TextButton(onClick = { selected = id }) { Text((if (selected == id) "✓ " else "") + "$name · $id") }
                }
                Text("已选：${QuickToolsProfile.namedSpecies[selected] ?: "内部编号 $selected"}")
                OutlinedTextField(
                    value = levelText,
                    onValueChange = { levelText = it.filter { char -> char.isDigit() }.take(3) },
                    label = { Text("领取精灵等级（1～100）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = levelText.toIntOrNull() !in 1..100,
                )
                Text("赠送精灵的性格与闪光（不修改已有队伍）")
                val natureNames = listOf(
                    "勤奋", "怕寂寞", "勇敢", "固执", "顽皮",
                    "大胆", "坦率", "悠闲", "淘气", "乐天",
                    "胆小", "急躁", "认真", "爽朗", "天真",
                    "内敛", "慢吞吞", "冷静", "害羞", "马虎",
                    "温和", "温顺", "自大", "慎重", "浮躁",
                )
                Box {
                    Button(onClick = { natureMenuOpen = true }) {
                        Text("性格：${nature?.let { natureNames[it] } ?: "随机"} ▾")
                    }
                    DropdownMenu(expanded = natureMenuOpen, onDismissRequest = { natureMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("随机性格") }, onClick = {
                            nature = null
                            natureMenuOpen = false
                        })
                        natureNames.forEachIndexed { index, label ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                nature = index
                                natureMenuOpen = false
                            })
                        }
                    }
                }
                TextButton(onClick = { shiny = !shiny }) {
                    Text(if (shiny) "✓ 闪光精灵（点击改为普通）" else "普通精灵（点击设为闪光）")
                }
                Button(enabled = profile != null && levelText.toIntOrNull() in 1..100, onClick = {
                    val id = selected
                    val selectedNature = nature
                    val selectedShiny = shiny
                    val selectedLevel = levelText.toIntOrNull()
                    confirmation = "将新增${QuickToolsProfile.namedSpecies[id] ?: "内部编号 $id"}（Lv.${selectedLevel ?: "无效"}，性格：${selectedNature?.let { natureNames[it] } ?: "随机"}，${if (selectedShiny) "闪光" else "普通"}）到第一个空盒位。会先备份并重启游戏，未保存的进度会丢失。已在游戏内保存后再执行。"
                    action = {
                        val verified = requireNotNull(profile)
                        require(selectedLevel in 1..100) { "请输入1～100的有效等级" }
                        val experience = PokemonExperience.forLevel(selectedLevel, verified.growthRate(id))
                        val raw = onReadSave()
                        val pid = EmeraldQuickSavePatcher.personalityFor(raw, selectedNature, selectedShiny)
                        val gift = EmeraldQuickSavePatcher.addToFirstEmptyBox(raw, id, verified.speciesName(id), verified.tacklePp(), pid, experience)
                        backup(raw, "before_gift_")
                        onApplySave(gift.bytes)
                        message = "已加入电脑第${gift.box}盒、第${gift.position}格。进入游戏后到电脑领取；原存档已备份。"
                    }
                }) { Text("备份并加入电脑空位") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("返回游戏") } },
    )
}
