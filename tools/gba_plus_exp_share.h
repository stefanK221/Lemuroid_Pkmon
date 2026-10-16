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
/*
 * Dispatch through the game's own experience-award routine, once a verified
 * battle hook exists. An award callback must be provided by the ROM-specific
 * integration; never patch party EXP bytes directly.
 *
 * The caller owns transactional safety and must invoke this only at the
 * verified battle EXP event, not every emulator frame.
 */
/*
 * A battle hook must not replay a single KO award on every emulator frame.
 * Keep a small per-battle event identity; reset on battle end/ROM unload.
 */
typedef struct {
    uint64_t last_event;
    bool initialized;
} GbaPlusExpEventGuard;
static void gbaPlusExpGuardReset(GbaPlusExpEventGuard* guard) {
    if (guard) { guard->initialized = false; guard->last_event = 0; }
}
static bool gbaPlusExpGuardAccept(GbaPlusExpEventGuard* guard, uint64_t event_id) {
    if (!guard || !event_id) return false;
    if (guard->initialized && guard->last_event == event_id) return false;
    guard->last_event = event_id;
    guard->initialized = true;
    return true;
}
/*
 * Do not consume an event ID before the native award callbacks succeed.
 * A failed callback may indicate a partial award: caller must stop rather
 * than retry blindly. Mark event first in the battle hook's persistent
 * ledger before any mutations, and do not reissue on callback failure.
 */
typedef struct {
    GbaPlusExpEventGuard guard;
    bool failed;
} GbaPlusExpDispatchState;
static void gbaPlusExpDispatchReset(GbaPlusExpDispatchState* state) {
    if (state) { gbaPlusExpGuardReset(&state->guard); state->failed = false; }
}
typedef bool (*GbaPlusAwardExp)(void* context, size_t party_slot, uint32_t amount);
static bool gbaPlusDispatchFullExp(uint32_t award,
                                   const GbaPlusPartyMember* members,
                                   size_t count,
                                   bool enabled,
                                   GbaPlusAwardExp callback,
                                   void* context) {
    if (!callback) return false;
    uint32_t awards[6] = {0};
    if (!gbaPlusAllocateFullExp(award, members, count, enabled, awards)) return false;
    for (size_t i = 0; i < count; i++) {
        if (awards[i] && !callback(context, i, awards[i])) return false;
    }
    return true;
}
#endif
