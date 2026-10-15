# GBA Plus testing status

> **历史文件，请勿当作当前发布状态（2026-10-16 提醒）**：下文反映早期开发阶段，其中有关“未接入金手指/未编译 APK”的描述已过时。当前唯一持续维护的状态为 [需求路线图](docs/GBA_PLUS_ROADMAP.md)，并参见 [每次更新记录](docs/GBA_PLUS_CHANGELOG.md) 与 [常见故障](docs/GBA_PLUS_KNOWN_ISSUES.md)。\n\n
The 6V save transformation has been verified **offline** against the user-provided 128-KiB save (save counter 134), with all six modified party records and the active slot section checksum passing validation.

This verification proves file structure and internal checksums; it **does not** prove that the edited save loads correctly in the modified game on an Android emulator.

No game ROM or personal save is committed to this repository.

The Android app is NOT complete. The Compose cheat manager currently stores code and toggle state, but does not execute cheats. Game-specific behavior, nature editing and APK compilation remain unverified.

To make progress without asking for user-side testing:
1. Integrate the code editor with a LibretroDroid native cheat bridge, then wire toggles to the running core.
2. Compile the APK in Actions and inspect compiler / runtime logs.
3. Implement safe .sav import/export with backups, then port the tested 6V transformation.
4. Run emulator tests and only after that request optional actual-device validation.
