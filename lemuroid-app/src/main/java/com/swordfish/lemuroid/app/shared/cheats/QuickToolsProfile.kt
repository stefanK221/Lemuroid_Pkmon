package com.swordfish.lemuroid.app.shared.cheats

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

object QuickToolsProfile {
    private const val HASH = "389363bb2c87b8f197f23d67c11e459cc9f46befec75dc57a9e6fdac898eccc2"
    data class Verified(val file: File) {
        private fun pointer(rom: RandomAccessFile, offset: Long): Long {
            rom.seek(offset)
            var value = 0L
            repeat(4) { value = value or (rom.readUnsignedByte().toLong() shl (it * 8)) }
            val p = value - 0x08000000
            require(p in 0 until rom.length()) { "ROM 表指针无效" }
            return p
        }
        fun speciesName(id: Int): ByteArray = RandomAccessFile(file, "r").use { rom ->
            val table = pointer(rom, 0x144)
            require(table + id * 11 + 11 <= rom.length())
            rom.seek(table + id * 11)
            ByteArray(11).also { rom.readFully(it) }
        }
        fun tacklePp(): Int = RandomAccessFile(file, "r").use { rom ->
            val table = pointer(rom, 0x1CC)
            rom.seek(table + 33 * 12 + 4)
            rom.readUnsignedByte().also { require(it in 1..64) { "招式表不匹配" } }
        }
    }
    fun verify(context: Context, fileName: String): Verified {
        require(File(fileName).name == fileName) { "游戏文件名无效" }
        val file = File(context.cacheDir, "storage-framework-games/gba/$fileName")
        require(file.isFile) { "请先加载游戏，当前无法校验 ROM" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(65536)
            while (true) { val count = stream.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        require(hash == HASH) { "此 ROM 尚未适配，快捷修改已禁用" }
        return Verified(file)
    }
    // Never assume ROM-hack species IDs equal National Pokédex numbers.
    // This map contains only species whose internal IDs are known for the verified ROM.
    // Unmapped National Dex entries must be rejected rather than gifting a wrong species.
    val nationalToInternal: Map<Int, Int> = buildMap {
        for (number in 1..251) put(number, number)
        put(252, 277) // Treecko
        put(255, 280) // Torchic
        put(258, 283) // Mudkip
        put(448, 357) // Lucario
        put(447, 356) // Riolu
        put(443, 370) // Gible
        put(445, 372) // Garchomp
        put(280, 392) // Ralts
        put(282, 394) // Gardevoir
        put(376, 400) // Metagross
        put(382, 404) // Kyogre
        put(383, 405) // Groudon
        put(384, 406) // Rayquaza
        put(380, 407) // Latias
        put(381, 408) // Latios
        put(385, 409) // Jirachi
        put(386, 410) // Deoxys
    }
    val internalToNational: Map<Int, Int> = nationalToInternal.entries.associate { (dex, internal) -> internal to dex }

    // Internal IDs for this exact BW ROM; these are not National Pokédex numbers.
    val namedSpecies = linkedMapOf(
        1 to "妙蛙种子", 2 to "妙蛙草", 3 to "妙蛙花", 4 to "小火龙", 5 to "火恐龙", 6 to "喷火龙",
        7 to "杰尼龟", 8 to "卡咪龟", 9 to "水箭龟", 25 to "皮卡丘", 26 to "雷丘", 94 to "耿鬼",
        129 to "鲤鱼王", 130 to "暴鲤龙", 131 to "拉普拉斯", 132 to "百变怪", 133 to "伊布",
        134 to "水伊布", 135 to "雷伊布", 136 to "火伊布", 143 to "卡比兽", 144 to "急冻鸟",
        145 to "闪电鸟", 146 to "火焰鸟", 147 to "迷你龙", 148 to "哈克龙", 149 to "快龙",
        150 to "超梦", 151 to "梦幻", 196 to "太阳伊布", 197 to "月亮伊布", 243 to "雷公",
        244 to "炎帝", 245 to "水君", 249 to "洛奇亚", 250 to "凤王", 251 to "时拉比",
        277 to "木守宫", 280 to "火稚鸡", 283 to "水跃鱼", 356 to "利欧路", 357 to "路卡利欧",
        370 to "圆陆鲨", 372 to "烈咬陆鲨", 392 to "拉鲁拉丝", 394 to "沙奈朵",
        400 to "巨金怪", 404 to "盖欧卡", 405 to "固拉多", 406 to "烈空坐",
        407 to "拉帝亚斯", 408 to "拉帝欧斯", 409 to "基拉祈", 410 to "代欧奇希斯",
    )
}
