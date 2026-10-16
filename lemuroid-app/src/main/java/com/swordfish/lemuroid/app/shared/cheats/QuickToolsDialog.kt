package com.swordfish.lemuroid.app.shared.cheats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
    var showGiftDetails by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(6) }
    var nature by remember { mutableStateOf<Int?>(null) }
    var shiny by remember { mutableStateOf(false) }
    var levelText by remember { mutableStateOf("1") }
    var ivText by remember { mutableStateOf("31,31,31,31,31,31") }
    var evText by remember { mutableStateOf("0,0,0,0,0,0") }
    var dexMap by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var romNames by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    val displayNames = romNames + QuickToolsProfile.namedSpecies
    var natureMenuOpen by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var action by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(fileName) {
        val result = withContext(Dispatchers.IO) { runCatching { QuickToolsProfile.verify(context, fileName) } }
        result.onSuccess { verified ->
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val mapping = verified.dexToSpecies()
                    val names = EmeraldChineseSpeciesNames.decoded(verified::speciesName) +
                        mapping.values.distinct().mapNotNull { species ->
                            verified.readableSpeciesName(species)?.let { species to it }
                        }.toMap()
                    mapping to names
                }
            }
            loaded.onSuccess { (mapping, names) ->
                dexMap = mapping
                // BW edition dex entries cross-checked against published EX+BW index.
                // Keep these keyed by ROM dex number, not by internal species ID.
                val bwDexNames = mapOf(
                    387 to "比克提尼", 388 to "藤叶蛇", 389 to "青藤蛇", 390 to "君主蛇",
                    391 to "暖暖猪", 392 to "炒炒猪", 393 to "炎武王",
                    394 to "水水獭", 395 to "双刃丸", 396 to "大剑鬼",
                    397 to "白色酋雷姆", 398 to "帝牙卢卡", 399 to "帕路奇犽",
                    400 to "雷吉奇卡斯", 401 to "骑拉帝纳", 402 to "克雷色利亚",
                    403 to "达克莱伊", 404 to "雷希拉姆", 405 to "捷克罗姆",
                    406 to "黑色酋雷姆", 407 to "洁咪", 408 to "玛纳霏",
                    409 to "阿尔宙斯", 410 to "凯路迪欧", 411 to "盖诺赛克特",
                )
                romNames = names + bwDexNames.mapNotNull { (dex, name) ->
                    mapping[dex]?.let { species -> species to name }
                }.toMap()
                profile = verified
                message = "ROM图鉴映射已验证；已解析名称${(names + QuickToolsProfile.namedSpecies).size}种。请核对游戏图鉴。"
            }.onFailure { profile = null; message = it.message ?: "ROM图鉴映射失败" }
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
        title = { Text("GBA Plus · 快捷工具") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, color = MaterialTheme.colorScheme.primary)
                HorizontalDivider()
                Text("游戏资源", style = MaterialTheme.typography.titleMedium)
                Text("金钱与大师球仅在本次游戏会话内持续补充；重新启动后需再次开启。")
                Text("状态：金钱 ${if (flags and 1 != 0) "开启" else "关闭"} · 大师球 ${if (flags and 2 != 0) "开启" else "关闭"}", color = MaterialTheme.colorScheme.secondary)
                Button(enabled = profile != null, onClick = { requestToggle(1, "无限金钱") }) {
                    Text(if (flags and 1 != 0) "关闭无限金钱（已开启）" else "开启无限金钱（保持999999）")
                }
                Button(enabled = profile != null, onClick = { requestToggle(2, "无限大师球") }) {
                    Text(if (flags and 2 != 0) "关闭无限大师球（已开启）" else "开启无限大师球（保持99个）")
                }
                if (flags and 3 != 0) TextButton(onClick = {
                    runCatching { onSetFlags(0); flags = 0; message = "已停止金钱与大师球的持续补充。" }
                        .onFailure { message = "关闭失败：${it.message}" }
                }) { Text("一键关闭资源补充") }
                HorizontalDivider()
                Text("领取精灵到电脑", style = MaterialTheme.typography.titleMedium)
                Text("加入第一个空盒位，不覆盖已有精灵；默认撞击，不自动补图鉴。执行前请在游戏内保存。")
                val speciesToDex = dexMap.entries.associate { it.value to it.key }
                val availableDexNumbers = dexMap.entries
                    .filter { (_, species) -> species in 1..411 }
                    .map { it.key }.sorted()
                if (availableDexNumbers.isNotEmpty()) {
                    Text("可查询图鉴编号：${availableDexNumbers.first()}～${availableDexNumbers.last()}（无效物种不可领取）")
                }
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it.filter(Char::isDigit).take(3) },
                    label = { Text("输入ROM图鉴编号") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                val number = search.toIntOrNull()
                val resolvedSpecies = number?.let { dexMap[it] }?.takeIf { it in 1..251 || it in 277..411 }
                LaunchedEffect(resolvedSpecies) {
                    if (resolvedSpecies != null) selected = resolvedSpecies
                }
                if (search.isNotBlank()) {
                    Text(when {
                        resolvedSpecies == null -> "编号 #${search} 暂不支持领取"
                        else -> "图鉴 #${number} · ${displayNames[resolvedSpecies] ?: "ROM物种编号 #$resolvedSpecies（名称暂未识别）"}"
                    }, style = MaterialTheme.typography.titleSmall)
                } else {
                    Text("输入编号后显示对应精灵；不再展示示例列表。")
                }
                TextButton(onClick = { showGiftDetails = !showGiftDetails }) {
                    Text(if (showGiftDetails) "收起高级参数（等级、IV/EV、性格） ▲" else "展开领取参数（等级、IV/EV、性格） ▼")
                }
                if (showGiftDetails) {
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
                TextButton(onClick = { ivText = "31,31,31,31,31,31"; evText = "0,0,0,0,0,0" }) { Text("恢复默认：6V / 0努力值") }
                OutlinedTextField(value = ivText, onValueChange = { ivText = it.take(64) }, label = { Text("领取时IV（6项0～31）") }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = customIvs == null)
                OutlinedTextField(value = evText, onValueChange = { evText = it.take(64) }, label = { Text("领取时EV（各0～255，总和≤510）") }, modifier = Modifier.fillMaxWidth(), singleLine = true, isError = customEvs == null || customEvs.sum() > 510)
                if (!statsValid) Text("请检查IV/EV：均需6项，且EV总和不超过510")
                HorizontalDivider()
                Text("性格与外观", style = MaterialTheme.typography.titleMedium)
                Text("仅调整本次领取的精灵，不修改队伍中的现有精灵。")
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
                HorizontalDivider()
                Button(enabled = profile != null && resolvedSpecies != null && statsValid && (levelText.toIntOrNull() ?: 0) in 1..100, onClick = {
                    val id = requireNotNull(resolvedSpecies)
                    val selectedNature = nature
                    val selectedShiny = shiny
                    val selectedLevel = levelText.toIntOrNull()
                    require(id in 1..411) { "此精灵尚未开放领取" }
                    require(number != null && dexMap[number] == id) { "编号与物种不匹配" }
                    val selectedIvs = requireNotNull(customIvs).toList()
                    val selectedEvs = requireNotNull(customEvs).toList()
                    confirmation = "将新增${displayNames[id] ?: "ROM物种#$id"}（图鉴#${speciesToDex[id]}，Lv.${selectedLevel ?: "无效"}，IV:$selectedIvs，EV:$selectedEvs，性格：${selectedNature?.let { natureNames[it] } ?: "随机"}，${if (selectedShiny) "闪光" else "普通"}）到第一个空盒位。会先备份并重启游戏，未保存的进度会丢失。已在游戏内保存后再执行。"
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
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("返回游戏") } },
    )
}
