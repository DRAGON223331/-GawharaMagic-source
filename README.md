# GawharaMagic 2.0

GawharaMagic adds 12 elemental schools, 100 levels, secret trials every 5 levels, empty-hand moves, and optional RPG progression systems.

## Requirements

- Paper 1.21.4
- Java 21
- AuraSkills 2.2.8 (or a compatible build that exposes the AuraSkills API)

The AuraSkills API is a Maven `provided` dependency. It is not bundled into the plugin JAR; install AuraSkills on the server before GawharaMagic.

## Mana

All new GawharaMagic abilities and empty-hand elemental moves spend the player's actual AuraSkills mana. Mana costs and cooldowns can be changed in `plugins/GawharaMagic/config.yml`. AuraSkills Wisdom controls the maximum mana as usual.

## New skills

- `Shift + Right Click` with an empty main hand: the selected element's base move.
- `Shift + Left Click` in the air with an empty main hand: Elemental Spin.
- `Shift + F` with an empty main hand: Elemental Guard.
- `/gmagic ultimate`: ultimate aura (unlocks at level 60 by default).
- `/gmagic awaken`: Awakening (unlocks at level 100 by default).

Spin and Ultimate use particles, buffs and non-damaging crowd control; Guard reduces incoming damage while active.

## Additional systems (idea set 4)

- `/gmagic fusion <player>`: two nearby players with different elements combine their powers. Both players spend AuraSkills mana.
- `/gmagic forbidden`: short, high-cost self-enhancement with a recovery slowdown afterward.
- `/gmagic relics`: lists relics. Admins can issue one with `/gmagic relic give <player> <tide|focus|guardian>`. Right-click the relic to activate it.
- `/gmagic spirit [summon|dismiss]`: summon or dismiss a persistent visual Allay companion.
- `/gmagic bounty`: view a contract. Accept one with `/gmagic bounty accept <mine|cast|travel>` and claim it with `/gmagic bounty claim`.
- `/gmagic reputation`: view reputation and title. Reputation is earned from contracts, fusion and progression milestones.
- `/gmagic transform`: unlock and activate the element's rare transformation (level 80 + 150 reputation by default).

The bounty board uses non-combat server contracts (mining, casting skills, and travelling); it does not place bounties on players.

## Existing commands

- `/gmagic` - status and help
- `/gmagic powers` - list the 12 elements
- `/gmagic task` - secret trial status
- `/gmagic reset <player>` - admin reset
- `/gmagic level <player> <1-100>` - admin level adjustment
- `/grestore` - restore items saved by the existing death-recovery system

## Build

Run `mvn -B clean package`. The resulting JAR is `target/GawharaMagic.jar`.


## GawharaMagic 2.0 — AuraSkills mana systems

**Required server plugin:** AuraSkills 2.2.8 for Paper 1.21.4. GawharaMagic reads the real AuraSkills mana pool and spends it through the public API. Mana costs, cooldowns, unlock levels, relic reputation thresholds, and the spirit's mana pulse are configurable in `config.yml`.

### Combat abilities (no weapon required)
- `Shift + Left Click` in the air with an empty main hand: Elemental Spin.
- `Shift + F` with an empty main hand: Elemental Guard.
- `/gmagic ultimate`: team-oriented ultimate ability (default unlock level 60).
- `/gmagic awaken`: Awakening form (default unlock level 100).
- The element's regular empty-hand move remains `Shift + Right Click` in the air.
- `/gmagic mana` displays the player's current and maximum AuraSkills mana.

### Additional systems
- `/gmagic fusion <player>` sends a 30-second invitation; `/gmagic fusion accept` confirms it and only then both players pay AuraSkills mana. `/gmagic fusion decline` rejects it.
- `/gmagic forbidden` — high-level temporary power with an after-effect.
- `/gmagic relics` and `/gmagic relic claim <tide|focus|guardian>` — claim reusable relics after reaching the configured reputation threshold. Admins can use `/gmagic relic give <player> <id>`.
- `/gmagic spirit [summon|dismiss]` — summon an elemental Allay companion; while active it periodically restores a small amount of AuraSkills mana.
- `/gmagic bounty` — non-PvP contracts for mining, casting abilities, or travelling; claim completed contracts for reputation and mana.
- `/gmagic reputation` — view elemental reputation and title.
- `/gmagic transform` — unlock and activate an element-specific rare transformation after meeting its level and reputation requirements.

Player progression, reputation, contracts, relic claims, and companion ownership are stored in `systems.yml`. A `/gmagic reset <player>` also removes this data, relic items, companion, and active power states.
