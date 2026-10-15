# GBA Plus implementation status

> **历史文件，请勿当作当前发布状态（2026-10-16 提醒）**：下文反映早期开发阶段，其中有关“未接入金手指/未编译 APK”的描述已过时。当前唯一持续维护的状态为 [需求路线图](docs/GBA_PLUS_ROADMAP.md)，并参见 [每次更新记录](docs/GBA_PLUS_CHANGELOG.md) 与 [常见故障](docs/GBA_PLUS_KNOWN_ISSUES.md)。\n\n
This fork aims to keep Lemuroid's UI and mGBA-compatible GBA playback while adding a cheat manager.

## Implemented in this branch
- A manual GitHub Actions workflow to attempt building an installable unsigned-for-release **debug** APK. This workflow has not yet been verified on GitHub Actions.
- A Kotlin cheat definition and basic hex syntax validator. It does **not** apply any cheats.

## Not implemented yet
- UI to add/edit/toggle cheats.
- Native cheat execution. Lemuroid uses LibretroDroid's GLRetroView; the actual interface for retro_cheat_set must be verified and exposed through the native bridge before a switch can affect gameplay.
- A verified set of cheat codes specifically for Pokemon Dark Rising / 漆黑的魅影 5.0EX+BW.
- Permanent Pokémon nature, IV, EV, moves or ability editing.

## Important
Do not overwrite game saves when experimenting with cheat codes. Back up the in-game save file (.sav) before testing.
ROM files should remain on the user's device and must not be added to the GitHub repository.
Open source modifications must preserve upstream license obligations.
