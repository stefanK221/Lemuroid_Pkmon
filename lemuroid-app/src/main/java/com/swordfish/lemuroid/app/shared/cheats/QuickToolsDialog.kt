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
    var ivText by remember { mutableStateOf("31,31,31,31,31,31") }
    var evText by remember { mutableStateOf("0,0,0,0,0,0") }
    var dexMap by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var natureMenuOpen by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(fileName) {
        val result = withContext(Dispatchers.IO) { runCatching { QuickToolsProfile.verify(context, fileName) } }
        result.onSuccess { verified ->
            runCatching { verified.dexToSpecies() }
                .onSuccess { dexMap = it; profile = verified; message = "ROM图鉴映射验证通过；请核对游戏中的图鉴显示。" }
                .onFailure { profile = null; message = it.message ?: "ROM图鉴映射失败" }
        }
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
                Text("获取指定精灵：加入电脑空盒位，不替换已有精灵；默认撞击，不自动补图鉴记录。先在游戏内保存。")
                Text("按ROM图鉴序号检索（不是存档内部物种ID）。如游戏显示的编号不符，请停止并反馈。")
                OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("搜索名称或ROM图鉴编号") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                val speciesToDex = dexMap.entries.associate { it.value to it.key }
                val availableDexNumbers = dexMap.entries.filter { (_, species) -> species in 1..251 || species in 277..411 }.map { it.key }.sorted()
                if (availableDexNumbers.isNotEmpty()) {
                    Text("可领取的ROM图鉴编号范围：${availableDexNumbers.first()}～${availableDexNumbers.last()}（部分编号不支持，请以查询结果为准）")
                }
                val number = search.trim().toIntOrNull()
                val isNumericSearch = search.trim().isNotEmpty() && search.trim().all { it.isDigit() }
                val resolvedSpecies = number?.let { dexMap[it] }?.takeIf { it in 1..251 || it in 277..411 }
                val nameAvailable = resolvedSpecies?.let { QuickToolsProfile.namedSpecies[it] } != null
                if (number != null) {
                    val mappedSpecies = resolvedSpecies
                    val mappedName = mappedSpecies?.let { QuickToolsProfile.namedSpecies[it] }
                    Text(if (mappedSpecies == null) "编号 #$number 当前不支持领取" else "编号 #$number 对应：${mappedName ?: "名称暂未收录，请先核对游戏图鉴"}")
                }
                val candidates = if (number != null) {
                    listOfNotNull(dexMap[number]?.takeIf { it in 1..251 || it in 277..411 }?.let { number to it })
                } else {
                    QuickToolsProfile.namedSpecies.entries.filter { (_, name) ->
                        search.isBlank() || name.contains(search, ignoreCase = true)
                    }.filter { (id, _) -> id in 1..251 || id in 277..411 }
                        .take(8).mapNotNull { (id, _) -> speciesToDex[id]?.let { it to id } }
                }
                if (candidates.isEmpty()) Text("没有可安全领取的对应精灵，请检查编号或名称；部分精灵尚未开放领取。")
                candidates.filter { (_, species) -> QuickToolsProfile.namedSpecies.containsKey(species) }.forEach { (dex, species) ->
                    TextButton(onClick = { selected = species }) {
                        val label = QuickToolsProfile.namedSpecies[species] ?: "精灵"
                        Text((if (selected == species) "✓ " else "") + "$label · 图鉴#$dex")
                    }
                }
                Text("已选：${QuickToolsProfile.namedSpecies[selected] ?: "精灵"} · 图鉴#${speciesToDex[selected] ?: "?"}")
                OutlinedTextField(
                    value = levelText,
                    onValueChange = { levelText = it.filter { char -> char.isDigit() }.take(3) },
                    label = { Text("领取精灵等级（1～100）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = (levelText.toIntOrNull() ?: 0) !in 1..100,
                )
                Text("六项顺序：HP、攻击、防御、速度、特攻、特防；用逗号隔开")
                fun parseStats(value: String, maximum: Int): List<Int>? {
                    val parts = value.split(',', '，').map { it.trim().toIntOrNull() }
                    if (parts.size != 6 || parts.any { it == null || it !in 0..maximum }) return null
                    return parts.filterNotNull()
                }
                val customIvs = parseStats(ivText, 31)
                val customEvs = parseStats(evText, 255)
                val statsValid = customIvs != null && customEvs != null && customEvs.sum() <= 510
                OutlinedTextField(value = ivText, onValueChange = { ivText = it.take(64) }, label = { Text("领取时IV（6项0～31）") }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = customIvs == null)
                OutlinedTextField(value = evText, onValueChange = { evText = it.take(64) }, label = { Text("领取时EV（各0～255，总和≤510）") }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = customEvs == null || customEvs.sum() > 510)
                if (!statsValid) Text("请检查IV/EV：均需6项，且EV总和不超过510")
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
                Button(enabled = profile != null && dexMap.isNotEmpty() && statsValid && (levelText.toIntOrNull() ?: 0) in 1..100 && (!isNumericSearch || (nameAvailable && selected == resolvedSpecies)), onClick = {
                    val id = selected
                    val selectedNature = nature
                    val selectedShiny = shiny
                    val selectedLevel = levelText.toIntOrNull()
                    require(id in 1..251 || id in 277..411) { "此精灵尚未开放领取" }
                    require(!isNumericSearch || (nameAvailable && selected == resolvedSpecies)) { "请先确认编号对应的精灵名称并点选" }
                    val selectedIvs = requireNotNull(customIvs).toList()
                    val selectedEvs = requireNotNull(customEvs).toList()
                    confirmation = "将新增${QuickToolsProfile.namedSpecies[id] ?: "精灵"}（图鉴#${speciesToDex[id]}，Lv.${selectedLevel ?: "无效"}，IV:$selectedIvs，EV:$selectedEvs，性格：${selectedNature?.let { natureNames[it] } ?: "随机"}，${if (selectedShiny) "闪光" else "普通"}）到第一个空盒位。会先备份并重启游戏，未保存的进度会丢失。已在游戏内保存后再执行。"
                    action = {
                        val verified = requireNotNull(profile)
                        require((selectedLevel ?: 0) in 1..100) { "请输入1～100的有效等级" }
                        val experience = PokemonExperience.forLevel(requireNotNull(selectedLevel), verified.growthRate(id))
                        val raw = onReadSave()
                        val pid = EmeraldQuickSavePatcher.personalityFor(raw, selectedNature, selectedShiny)
                        val gift = EmeraldQuickSavePatcher.addToFirstEmptyBox(raw, id, verified.speciesName(id), verified.tacklePp(), pid, experience, selectedIvs, selectedEvs)
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
