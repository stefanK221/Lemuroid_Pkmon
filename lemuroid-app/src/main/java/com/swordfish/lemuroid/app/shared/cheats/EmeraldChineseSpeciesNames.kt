package com.swordfish.lemuroid.app.shared.cheats

/**
 * ROM-specific Chinese species name decoding.
 * This hack stores each Chinese glyph as a TWO-BYTE code in the 11-byte species entry.
 * Learn the glyph alphabet using the known first 251 species; decode additional species
 * from the actual matching ROM rather than assuming their national-dex IDs.
 */
object EmeraldChineseSpeciesNames {
    private val knownFirst251 = """妙蛙种子|妙蛙草|妙蛙花|小火龙|火恐龙|喷火龙|杰尼龟|卡咪龟|水箭龟|绿毛虫|铁甲蛹|巴大蝶|独角虫|铁壳蛹|大针蜂|波波|比比鸟|大比鸟|小拉达|拉达|烈雀|大嘴雀|阿柏蛇|阿柏怪|皮卡丘|雷丘|穿山鼠|穿山王|尼多兰|尼多娜|尼多后|尼多朗|尼多力诺|尼多王|皮皮|皮可西|六尾|九尾|胖丁|胖可丁|超音蝠|大嘴蝠|走路草|臭臭花|霸王花|派拉斯|派拉斯特|毛球|摩鲁蛾|地鼠|三地鼠|喵喵|猫老大|可达鸭|哥达鸭|猴怪|火爆猴|卡蒂狗|风速狗|蚊香蝌蚪|蚊香君|蚊香泳士|凯西|勇基拉|胡地|腕力|豪力|怪力|喇叭芽|口呆花|大食花|玛瑙水母|毒刺水母|小拳石|隆隆石|隆隆岩|小火马|烈焰马|呆呆兽|呆壳兽|小磁怪|三合一磁怪|大葱鸭|嘟嘟|嘟嘟利|小海狮|白海狮|臭泥|臭臭泥|大舌贝|刺甲贝|鬼斯|鬼斯通|耿鬼|大岩蛇|催眠貘|引梦貘人|大钳蟹|巨钳蟹|霹雳电球|顽皮雷弹|蛋蛋|椰蛋树|卡拉卡拉|嘎啦嘎啦|飞腿郎|快拳郎|大舌头|瓦斯弹|双弹瓦斯|独角犀牛|钻角犀兽|吉利蛋|蔓藤怪|袋兽|墨海马|海刺龙|角金鱼|金鱼王|海星星|宝石海星|魔墙人偶|飞天螳螂|迷唇姐|电击兽|鸭嘴火兽|凯罗斯|肯泰罗|鲤鱼王|暴鲤龙|拉普拉斯|百变怪|伊布|水伊布|雷伊布|火伊布|多边兽|菊石兽|多刺菊石兽|化石盔|镰刀盔|化石翼龙|卡比兽|急冻鸟|闪电鸟|火焰鸟|迷你龙|哈克龙|快龙|超梦|梦幻|菊草叶|月桂叶|大菊花|火球鼠|火岩鼠|火暴兽|小锯鳄|蓝鳄|大力鳄|尾立|大尾立|咕咕|猫头夜鹰|芭瓢虫|安瓢虫|线球|阿利多斯|叉字蝠|灯笼鱼|电灯怪|皮丘|皮宝宝|宝宝丁|波克比|波克基古|天然雀|天然鸟|咩利羊|绵绵|电龙|美丽花|玛力露|玛力露丽|树才怪|蚊香蛙皇|毽子草|毽子花|毽子绵|长尾怪手|向日种子|向日花怪|阳阳玛|乌波|沼王|太阳精灵|月精灵|黑暗鸦|河马王|梦妖|未知图腾|果然翁|麒麟奇|榛果球|佛烈托斯|土龙弟弟|天蝎|大钢蛇|布卢|布卢皇|千针鱼|巨钳螳螂|壶壶|赫拉克罗斯|狃拉|熊宝宝|圈圈熊|熔岩虫|熔岩蜗牛|小山猪|长毛猪|太阳珊瑚|铁炮鱼|章鱼桶|信使鸟|巨翅飞鱼|盔甲鸟|戴鲁比|黑鲁加|刺龙王|小小象|顿甲|3D龙II|惊角鹿|图图犬|巴尔郎|柯波朗|迷唇娃|电击怪|小鸭嘴龙|大奶罐|幸福蛋|雷公|炎帝|水君|由基拉|沙基拉|班吉拉|洛奇亚|凤王|雪拉比""".split('|')
    private val glyphNames = listOf<Pair<Int, String>>(
        253 to "藤叶蛇",
        356 to "利欧路",
        357 to "路卡利欧",
        370 to "圆陆鲨",
        372 to "烈咬陆鲨",
        392 to "拉鲁拉丝",
        394 to "沙奈朵",
        400 to "巨金怪",
        404 to "盖欧卡",
        405 to "固拉多",
        406 to "烈空坐",
        407 to "拉帝亚斯",
        408 to "拉帝欧斯",
        409 to "基拉祈",
        410 to "代欧奇希斯",
    )

    fun decoded(romName: (Int) -> ByteArray): Map<Int, String> {
        val codeToCharacter = mutableMapOf<Int, Char>()
        val conflicts = mutableSetOf<Int>()
        val seeds = (1..251).map { it to knownFirst251[it - 1] } + glyphNames
        for ((species, readableName) in seeds) {
            val bytes = romName(species)
            val codes = bytes.takeWhile { it.toInt() and 255 != 255 }
            if (codes.size != readableName.length * 2) continue
            readableName.forEachIndexed { index, character ->
                val glyph = ((codes[index * 2].toInt() and 255) shl 8) or
                    (codes[index * 2 + 1].toInt() and 255)
                val old = codeToCharacter.putIfAbsent(glyph, character)
                if (old != null && old != character) conflicts.add(glyph)
            }
        }
        conflicts.forEach { codeToCharacter.remove(it) }
        return buildMap {
            for (species in 1..411) {
                val bytes = romName(species)
                val length = bytes.indexOfFirst { it.toInt() and 255 == 255 }
                if (length <= 0 || length % 2 != 0) continue
                val chars = StringBuilder()
                for (index in 0 until length step 2) {
                    val glyph = ((bytes[index].toInt() and 255) shl 8) or
                        (bytes[index + 1].toInt() and 255)
                    val c = codeToCharacter[glyph] ?: break
                    chars.append(c)
                }
                if (chars.length * 2 == length) put(species, chars.toString())
            }
            // First 251 names are the reference only if ROM matches the learned encoding.
            for ((species, name) in seeds) {
                if (!containsKey(species)) continue
                if (get(species) != name) remove(species)
            }
        }
    }
}
