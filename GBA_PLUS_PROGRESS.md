# GBA Plus implementation status

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
