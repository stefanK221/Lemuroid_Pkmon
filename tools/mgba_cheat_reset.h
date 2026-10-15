/* GBA Plus: undo tracked ROM patches and remove hooks before freeing cheats. */
#ifndef GBA_PLUS_CHEAT_RESET_H
#define GBA_PLUS_CHEAT_RESET_H

#include <mgba/core/cheats.h>

static void gbaPlusResetCheats(struct mCheatDevice* device) {
	while (mCheatSetsSize(&device->cheats)) {
		struct mCheatSet* set = *mCheatSetsGetPointer(&device->cheats, 0);
		/* The disabled refresh path restores each tracked ROM patch, including
		 * shared patches. Clearing the list first loses its original values. */
		set->enabled = false;
		mCheatRefresh(device, set);
		mCheatRemoveSet(device, set);
		mCheatSetDeinit(set);
	}
}

#endif

