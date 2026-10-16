#include <mgba/flags.h>
#include <mgba/core/core.h>
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "mgba_quick_tools.h"
static unsigned char ewram[0x40000], iwram[0x8000];
static unsigned writes;
static enum mPlatform platform(const struct mCore* c) { (void)c; return mPLATFORM_GBA; }
static uint32_t read8(struct mCore* c,uint32_t a) {
	(void)c;
	if (a>=0x02000000 && a<0x02040000) return ewram[a-0x02000000];
	if (a>=0x03000000 && a<0x03008000) return iwram[a-0x03000000];
	if (a>=0x080000A0 && a<0x080000B0) return (uint8_t)"POKEMON EMERBPEE"[a-0x080000A0];
	assert(false); return 0;
}
static uint32_t read16(struct mCore* c,uint32_t a) { return read8(c,a)|(read8(c,a+1)<<8); }
static uint32_t read32(struct mCore* c,uint32_t a) { return read16(c,a)|(read16(c,a+2)<<16); }
static void write8(struct mCore* c,uint32_t a,uint8_t n) {
	(void)c; ++writes;
	if (a>=0x02000000 && a<0x02040000) ewram[a-0x02000000]=n;
	else if (a>=0x03000000 && a<0x03008000) iwram[a-0x03000000]=n;
	else assert(false);
}
static void write16(struct mCore* c,uint32_t a,uint16_t n) { write8(c,a,n); write8(c,a+1,n>>8); }
static void write32(struct mCore* c,uint32_t a,uint32_t n) { write16(c,a,n); write16(c,a+2,n>>16); }
int main(void) {
	struct mCore c={0}; c.platform=platform;
	c.busRead8=read8; c.busRead16=read16; c.busRead32=read32;
	c.busWrite8=write8; c.busWrite16=write16; c.busWrite32=write32;
	const uint32_t b1=0x02001000,b2=0x02010000,key=0xDEADBEEF;
	write32(&c,0x03005D8C,b1); write32(&c,0x03005D90,b2); write8(&c,0x020244E9,2);
	write32(&c,b2+0xAC,key); write32(&c,b1+0x490,123^key);
	write16(&c,b1+0x650,4); write16(&c,b1+0x652,10^(key&65535));
	assert(gbaPlusQuickCommand("GBA_PLUS_QUICK_V1:3")); gbaPlusQuickFrame(&c);
	assert((read32(&c,b1+0x490)^key)==999999);
	assert(read16(&c,b1+0x650)==4 && (read16(&c,b1+0x652)^(key&65535))==10);
	assert(read16(&c,b1+0x654)==1 && (read16(&c,b1+0x656)^(key&65535))==99);
	write16(&c,b1+0x656,98^(key&65535)); gbaPlusQuickFrame(&c);
	assert((read16(&c,b1+0x656)^(key&65535))==99);
	assert(gbaPlusQuickCommand("GBA_PLUS_QUICK_V1:4")); assert(gbaPlusQuickFlags==4);
	writes=0; gbaPlusQuickFrame(&c); assert(writes==0); /* EXP toggle must never patch unverified RAM. */
    assert(gbaPlusQuickCommand("GBA_PLUS_QUICK_V1:7")); assert(gbaPlusQuickFlags==7);
	gbaPlusQuickReset(); write32(&c,b1+0x490,100^key); writes=0; gbaPlusQuickFrame(&c);
	assert(writes==0 && (read32(&c,b1+0x490)^key)==100);
	for(unsigned i=0;i<16;++i) { write16(&c,b1+0x650+i*4,4); write16(&c,b1+0x652+i*4,1^(key&65535)); }
	gbaPlusQuickCommand("GBA_PLUS_QUICK_V1:2"); writes=0; gbaPlusQuickFrame(&c); assert(writes==0);
	gbaPlusQuickCommand("GBA_PLUS_QUICK_V1:3"); write16(&c,b1+0x650,999); writes=0; gbaPlusQuickFrame(&c); assert(writes==0);
	write16(&c,b1+0x650,4); write32(&c,0x03005D8C,0x0203FF00); writes=0; gbaPlusQuickFrame(&c); assert(writes==0);
	puts("PASS: encrypted money and balls refill, disable, occupied/full pockets, invalid layouts and pointer bounds");
	return 0;
}
