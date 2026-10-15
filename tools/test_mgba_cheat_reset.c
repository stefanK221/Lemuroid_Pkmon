/* Exercise real mGBA patch tracking without a commercial ROM or a game save. */
#include <mgba/core/core.h>
#include <mgba/core/cheats.h>
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "mgba_cheat_reset.h"

static uint32_t memory[4];
static unsigned removals;
static uint32_t readMemory(struct mCore* core, uint32_t address, int segment) {
	(void) core; (void) segment;
	return memory[(address - 0x08000000) / 4];
}
static void write8(struct mCore* c, uint32_t a, int s, uint8_t v) {
	(void) c; (void) s; memory[(a - 0x08000000) / 4] = v;
}
static void write16(struct mCore* c, uint32_t a, int s, uint16_t v) {
	(void) c; (void) s; memory[(a - 0x08000000) / 4] = v;
}
static void write32(struct mCore* c, uint32_t a, int s, uint32_t v) {
	(void) c; (void) s; memory[(a - 0x08000000) / 4] = v;
}
static void removed(struct mCheatSet* set, struct mCheatDevice* device) {
	(void) set; (void) device; ++removals;
}
static struct mCheatSet* addPatch(struct mCheatDevice* device, int width, uint32_t value) {
	struct mCheatSet* set = calloc(1, sizeof(*set));
	assert(set);
	mCheatSetInit(set, "ROM patch regression");
	set->remove = removed;
	struct mCheatPatch* patch = mCheatPatchListAppend(&set->romPatches);
	*patch = (struct mCheatPatch) {
		.address = 0x08000000, .segment = -1, .value = value, .width = width,
	};
	mCheatAddSet(device, set);
	mCheatRefresh(device, set);
	assert(memory[0] == value);
	return set;
}
int main(int argc, char** argv) {
	bool legacy = argc > 1 && strcmp(argv[1], "--legacy") == 0;
	struct mCore core = {0};
	core.rawRead8 = readMemory; core.rawRead16 = readMemory; core.rawRead32 = readMemory;
	core.rawWrite8 = write8; core.rawWrite16 = write16; core.rawWrite32 = write32;
	struct mCheatDevice* device = calloc(1, sizeof(*device));
	assert(device);
	mCheatDeviceCreate(device);
	device->p = &core;
	for (int width = 1; width <= 4; width *= 2) {
		memory[0] = 0x42;
		unsigned before = removals;
		addPatch(device, width, 0x11);
		addPatch(device, width, 0x22); /* Two sets share one original value. */
		if (legacy) mCheatDeviceClear(device); else gbaPlusResetCheats(device);
		if (memory[0] != 0x42) {
			fprintf(stderr, "FAIL: reset left patched ROM value %x (expected 42)\n", memory[0]);
			return 1;
		}
		assert(removals == before + 2);
		assert(mCheatSetsSize(&device->cheats) == 0);
		gbaPlusResetCheats(device); /* Empty reset must be safe. */
		addPatch(device, width, 0x33); /* Re-enable must capture a fresh original. */
		gbaPlusResetCheats(device);
		assert(memory[0] == 0x42);
	}
	mCheatDeviceDestroy(device);
	puts("PASS: byte/halfword/word ROM restoration, shared patches, remove hooks, repeated enable/reset");
	return 0;
}

