#include "gba_plus_exp_share.h"
#include <assert.h>
#include <stdio.h>

typedef struct { unsigned count; unsigned mask; uint32_t total; } AwardLog;
static bool record_award(void* context, size_t index, uint32_t amount) {
    AwardLog* log = (AwardLog*)context;
    log->count++;
    log->mask |= 1u << index;
    log->total += amount;
    return true;
}
int main(void) {
    GbaPlusPartyMember party[6] = {
        { true, false, false, 50 },
        { true, false, false, 1 },
        { true, true, false, 60 },
        { true, false, true, 5 },
        { false, false, false, 3 },
        { true, false, false, 100 },
    };
    uint32_t awards[6] = { 9, 9, 9, 9, 9, 9 };
    GbaPlusExpEventGuard guard = {0};
    assert(gbaPlusExpGuardAccept(&guard, 42));
    assert(!gbaPlusExpGuardAccept(&guard, 42));
    assert(gbaPlusExpGuardAccept(&guard, 43));
    assert(!gbaPlusExpGuardAccept(&guard, 0));
    gbaPlusExpGuardReset(&guard);
    assert(gbaPlusExpGuardAccept(&guard, 42));
    AwardLog log = {0};
    assert(gbaPlusDispatchFullExp(1000, party, 6, true, record_award, &log));
    assert(log.count == 2 && log.mask == 3 && log.total == 2000);
    log = (AwardLog){0};
    assert(gbaPlusDispatchFullExp(1000, party, 6, false, record_award, &log));
    assert(log.count == 0);
    assert(!gbaPlusDispatchFullExp(1000, party, 6, true, NULL, &log));
    assert(gbaPlusAllocateFullExp(1000, party, 6, true, awards));
    assert(awards[0] == 1000 && awards[1] == 1000);
    for (int i = 2; i < 6; i++) assert(awards[i] == 0);
    assert(gbaPlusAllocateFullExp(1000, party, 6, false, awards));
    for (int i = 0; i < 6; i++) assert(awards[i] == 0);
    assert(!gbaPlusAllocateFullExp(1, party, 7, true, awards));
    party[0].level = 0;
    assert(!gbaPlusAllocateFullExp(1, party, 6, true, awards));
    puts("PASS native EXP policy: full awards, eligibility, toggle, bad data");
    return 0;
}
