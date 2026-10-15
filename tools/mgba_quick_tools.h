/* GBA Plus quick tools for the fingerprint-gated Emerald profile. */
#ifndef GBA_PLUS_QUICK_TOOLS_H
#define GBA_PLUS_QUICK_TOOLS_H
#include <mgba/core/core.h>
#include <string.h>

static unsigned gbaPlusQuickFlags;
static void gbaPlusQuickReset(void) { gbaPlusQuickFlags = 0; }

static bool gbaPlusQuickCommand(const char* code) {
	static const char prefix[] = "GBA_PLUS_QUICK_V1:";
	if (strncmp(code, prefix, sizeof(prefix) - 1)) return false;
	const char* value = code + sizeof(prefix) - 1;
	if (value[0] >= '0' && value[0] <= '3' && !value[1]) {
		gbaPlusQuickFlags = (unsigned) (value[0] - '0');
	}
	return true;
}

static bool gbaPlusEwramRange(uint32_t address, uint32_t size) {
	return !(address & 3) && address >= 0x02000000 &&
	       address <= 0x02040000 - size;
}

static void gbaPlusQuickFrame(struct mCore* core) {
	if (!gbaPlusQuickFlags || !core || core->platform(core) != mPLATFORM_GBA) return;
	/* Full SHA-256 gating is also performed by the app before sending commands. */
	static const char title[] = "POKEMON EMERBPEE";
	for (unsigned i = 0; i < sizeof(title) - 1; ++i) {
		if (core->busRead8(core, 0x080000A0 + i) != (uint8_t) title[i]) return;
	}
	uint32_t block1 = core->busRead32(core, 0x03005D8C);
	uint32_t block2 = core->busRead32(core, 0x03005D90);
	if (!gbaPlusEwramRange(block1, 0x3D88) || !gbaPlusEwramRange(block2, 0xF2C)) return;
	if (core->busRead8(core, 0x020244E9) < 1 || core->busRead8(core, 0x020244E9) > 6) return;
	uint32_t key = core->busRead32(core, block2 + 0xAC);
	uint32_t money = core->busRead32(core, block1 + 0x490) ^ key;
	if (money > 999999) return;
	int master = -1;
	int empty = -1;
	/* Refuse an unfamiliar ball-pocket layout before performing any writes. */
	for (int i = 0; i < 16; ++i) {
		uint32_t slot = block1 + 0x650 + i * 4;
		unsigned item = core->busRead16(core, slot);
		unsigned quantity = core->busRead16(core, slot + 2) ^ (key & 0xFFFF);
		if (item > 12 || (item && (quantity < 1 || quantity > 99))) return;
		if (item == 1 && master < 0) master = i;
		if (!item && empty < 0) empty = i;
	}
	if (gbaPlusQuickFlags & 1) core->busWrite32(core, block1 + 0x490, 999999 ^ key);
	if (gbaPlusQuickFlags & 2) {
		int index = master >= 0 ? master : empty;
		if (index >= 0) {
			uint32_t slot = block1 + 0x650 + index * 4;
			core->busWrite16(core, slot, 1);
			core->busWrite16(core, slot + 2, 99 ^ (key & 0xFFFF));
		}
	}
}
#endif
