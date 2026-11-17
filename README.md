# GawharaMagic 1.2

Paper plugin for Minecraft 1.21.4, built with Java 21 and Maven.

## Progression
- 100 player levels.
- A secret elemental trial blocks progress at levels 5, 10, 15, ... 100.
- Each selected element has its own riddle-style environmental trials.
- Players see only a clue and a hidden progress counter; the exact condition is not shown.
- Trial progress and completed milestones are stored in `plugins/GawharaMagic/data.yml`.
- Existing player points and selected elements are retained.

## Player commands
- `/gmagic` — view element, level, and progress.
- `/gmagic task` — view the current riddle and hidden progress.
- `/gmagic weapons` — restore the plugin's existing unlocked items.
- `/grestore` — restore items and experience saved after death.

## Admin commands
- `/gmagic level <player> <1-100>` — set a player's level. Setting a level exactly on a multiple of five leaves that milestone's trial pending.
- `/gmagic reset <player>` — reset a player's magic data, remove their old magic weapons and effects, invalidate older copies even if stored in chests, and reopen element selection.

## Build
Run `mvn -B clean package`. The output is `target/GawharaMagic.jar`.
GitHub Actions is configured in `.github/workflows/main.yml` to build and upload this JAR as an artifact.
