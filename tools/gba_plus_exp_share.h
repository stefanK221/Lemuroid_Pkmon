/* Pure battle EXP allocation policy; integration into battle award pending. */
#ifndef GBA_PLUS_EXP_SHARE_H
#define GBA_PLUS_EXP_SHARE_H
#include <stdint.h>
#include <stdbool.h>
#include <stddef.h>

typedef struct {
    bool present;
    bool fainted;
    bool egg;
    uint8_t level;
} GbaPlusPartyMember;

/*
 * Full award to EACH eligible party member. This function never touches SRAM,
 * performs a level up, or changes game memory. The actual battle hook must
 * invoke the verified game's native gain-EXP routine for each recipient.
 */
static bool gbaPlusAllocateFullExp(uint32_t award,
                                 const GbaPlusPartyMember* members,
                                 size_t count,
                                 bool enabled,
                                 uint32_t* output) {
    if (!members || !output || count > 6) return false;
    for (size_t i = 0; i < count; i++) {
        if (members[i].level < 1 || members[i].level > 100) return false;
    }
    for (size_t i = 0; i < count; i++) {
        const GbaPlusPartyMember* member = &members[i];
        output[i] = (enabled && member->present && !member->fainted &&
                     !member->egg && member->level < 100) ? award : 0;
    }
    return true;
}
#endif
