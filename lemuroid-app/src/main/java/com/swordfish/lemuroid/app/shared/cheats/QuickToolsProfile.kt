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
        /**
         * Species -> Pokédex order as stored in this exact 32MiB ROM.
         * Verified offline against ROM SHA-256 and observed pairs: 6→6, 277→252,
         * 404→382. This is NOT a direct storage species ID.
         * Whether the game's UI displays regional or national numbering still needs
         * a phone-side check before claiming 100% in-game dex correspondence.
         */
        fun dexToSpecies(): Map<Int, Int> = RandomAccessFile(file, "r").use { rom ->
            val result = LinkedHashMap<Int, Int>()
            rom.seek(0x31DC82L)
            for (species in 1..411) {
                val low = rom.readUnsignedByte()
                val high = rom.readUnsignedByte()
                val dex = low or (high shl 8)
                require(dex in 1..411 && result.put(dex, species) == null) {
                    "ROM图鉴编号映射重复或无效；已停止领取"
                }
            }
            require(result.size == 411 &&
                result[6] == 6 &&
                result[252] == 277 &&
                result[382] == 404
            ) { "ROM图鉴映射校验失败；已停止领取" }
            result
        }
        /**
         * Decode only provable Gen III English glyphs. Unrecognized ROM-hack
         * encodings return null rather than inventing a Pokémon name.
         */
        fun readableSpeciesName(id: Int): String? {
            val bytes = speciesName(id)
            val name = StringBuilder()
            for (raw in bytes) {
                val value = raw.toInt() and 255
                if (value == 255) break
                val character = when (value) {
                    in 0xBB..0xD4 -> ('A'.code + value - 0xBB).toChar()
                    in 0xD5..0xEE -> ('a'.code + value - 0xD5).toChar()
                    in 0xA1..0xAA -> ('0'.code + value - 0xA1).toChar()
                    0 -> ' '
                    0xAB -> '!'
                    0xAD -> '.'
                    0xB8 -> ','
                    else -> return null
                }
                name.append(character)
            }
            return name.toString().trim().takeIf { it.length in 2..10 && it.any(Char::isLetter) }
        }
        fun speciesName(id: Int): ByteArray = RandomAccessFile(file, "r").use { rom ->
            val table = pointer(rom, 0x144)
            require(table + id * 11 + 11 <= rom.length())
            rom.seek(table + id * 11)
            ByteArray(11).also { rom.readFully(it) }
        }
        /** ROM header 0x1BC points to 28-byte Gen III base stats; byte 19 is growth rate. */
        fun growthRate(species: Int): Int = RandomAccessFile(file, "r").use { rom ->
            require(species in 1..411)
            val table = pointer(rom, 0x1BC)
            val location = table + species * 28L + 19
            require(location < rom.length()) { "精灵成长类型表超出ROM范围" }
            rom.seek(location)
            rom.readUnsignedByte().also {
                require(it in 0..5) { "ROM经验成长类型无法识别，已停止操作" }
            }
        }
        fun baseStats(species: Int): List<Int> = RandomAccessFile(file, "r").use { rom ->
            require(species in 1..411)
            val table = pointer(rom, 0x1BC)
            val start = table + species * 28L
            require(start + 28 <= rom.length())
            rom.seek(start)
            List(6) { rom.readUnsignedByte() }
        }
        /** Gen III 28-byte base-stat row: gender ratio at 16; two ability IDs at 22/23. */
        fun speciesTraits(species: Int): Triple<Int, Int, Int> = RandomAccessFile(file, "r").use { rom ->
            require(species in 1..411)
            val table = pointer(rom, 0x1BC)
            val location = table + species * 28L
            require(location + 28 <= rom.length())
            rom.seek(location + 16)
            val gender = rom.readUnsignedByte()
            rom.seek(location + 22)
            Triple(gender, rom.readUnsignedByte(), rom.readUnsignedByte())
        }
        fun tacklePp(): Int = RandomAccessFile(file, "r").use { rom ->
            // Verified from SHA-256-matched 5.0EX+BW ROM, not the header at
            // 0x1CC (which contains an out-of-ROM value 0x09900000).
            val table = 0x31C898L
            require(table + 355 * 12 <= rom.length()) { "招式表越界" }
            rom.seek(table + 1 * 12)
            val pound = ByteArray(5).also { rom.readFully(it) }
            require(pound.contentEquals(byteArrayOf(0, 40, 0, 100, 35))) {
                "ROM招式表校验失败"
            }
            rom.seek(table + 33 * 12)
            val tackle = ByteArray(5).also { rom.readFully(it) }
            require(tackle.contentEquals(byteArrayOf(0, 35, 0, 95, 35))) {
                "撞击招式数据不匹配"
            }
            tackle[4].toInt() and 255
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
    // Internal IDs for this exact BW ROM; these are not National Pokédex numbers.
    val namedSpecies = linkedMapOf(
        1 to "妙蛙种子", 2 to "妙蛙草", 3 to "妙蛙花", 4 to "小火龙", 5 to "火恐龙", 6 to "喷火龙",
        7 to "杰尼龟", 8 to "卡咪龟", 9 to "水箭龟", 25 to "皮卡丘", 26 to "雷丘", 94 to "耿鬼",
        129 to "鲤鱼王", 130 to "暴鲤龙", 131 to "拉普拉斯", 132 to "百变怪", 133 to "伊布",
        134 to "水伊布", 135 to "雷伊布", 136 to "火伊布", 143 to "卡比兽", 144 to "急冻鸟",
        145 to "闪电鸟", 146 to "火焰鸟", 147 to "迷你龙", 148 to "哈克龙", 149 to "快龙",
        150 to "超梦", 151 to "梦幻", 196 to "太阳伊布", 197 to "月亮伊布", 243 to "雷公",
        244 to "炎帝", 245 to "水君", 249 to "洛奇亚", 250 to "凤王", 251 to "时拉比",
        253 to "藤叶蛇", 277 to "木守宫", 280 to "火稚鸡", 283 to "水跃鱼", 356 to "利欧路", 357 to "路卡利欧",
        370 to "圆陆鲨", 372 to "烈咬陆鲨", 392 to "拉鲁拉丝", 394 to "沙奈朵",
        400 to "巨金怪", 404 to "盖欧卡", 405 to "固拉多", 406 to "烈空坐",
        407 to "拉帝亚斯", 408 to "拉帝欧斯", 409 to "基拉祈", 410 to "代欧奇希斯",
    )
}
