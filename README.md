# GawharaMagic 2.0.2

GawharaMagic adds 12 elemental schools, 100 levels, secret trials every 5 levels, five distinct weapon tiers per element, empty-hand moves, and optional RPG progression systems.

## Requirements

- Paper 1.21.4
- Java 21
- AuraSkills 2.2.8 (or a compatible build that exposes the AuraSkills API)

The AuraSkills API is a Maven `provided` dependency. It is not bundled into the plugin JAR; install AuraSkills on the server before GawharaMagic.

## Mana

All new GawharaMagic abilities, weapon skills, and empty-hand elemental moves spend the player's actual AuraSkills mana. Mana costs and cooldowns can be changed in `plugins/GawharaMagic/config.yml`. AuraSkills Wisdom controls the maximum mana as usual.

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
- The element's regular empty-hand move works with `Shift + Right Click` in the air or on a normal non-interactable block. Use `/gwm m` as a direct fallback.
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


## 2.0.1 — Quick Commands and Unarmed Skill Fix

- Added `/gwm` as a short root command alias (alongside `/magic`).
- Added short aliases: `/gwm m`, `/gwm s`, `/gwm g`, `/gwm u`, `/gwm a`, `/gwm f`, `/gwm mn`, `/gwm fu`, `/gwm tr`, `/gwm b`, and `/gwm rep`.
- Added `/gwm m` as a direct trigger for the selected element's unarmed ability.
- Kept the sneak + right-click gesture (air or a non-interactable block) for the unarmed ability.
- Separated the unarmed ability cooldown from weapon ability cooldowns.
- Mana failures now show the current and required AuraSkills mana instead of failing silently.
- The unarmed skill now reports if the power was not selected or the advanced mana system did not start.


### Quick command reference (2.0.3)

- `/gwm m` — selected element move (no weapon).
- `/gwm s` — Elemental Spin.
- `/gwm g` — Elemental Guard.
- `/gwm u` — Ultimate.
- `/gwm a` — Awakening.
- `/gwm f` — Forbidden Power.
- `/gwm mn` — show AuraSkills mana.
- `/gwm fu <player>` — request Fusion.
- `/gwm tr` — rare transformation.
- `/gwm b` — bounties.
- `/gwm rep` — reputation.

The unarmed move costs 8 AuraSkills mana by default (configurable under `mana.base-move`). If mana is too low, the plugin now displays the exact required and current amount.


## 2.0.2 — Distinct Elemental Weapon Skills

- Every one of the 12 elements now receives all five weapon tiers; the unarmed move remains available as an additional option.
- Weapon tiers have different active styles: targeted slash, heavy area impact, ranged elemental arrow, wide cyclone, and tier-5 elemental burst.
- Lightning weapons have five separate techniques: Spark Bolt, Thunder Chain, Lightning Arrow, Lightning Storm, and Thunder Judgment.
- Elemental weapon hits now have distinct passive effects for all 12 elements, including chained lightning, freeze, poison, shadow wither, sound knockback, and crystal slow.
- Weapon abilities spend AuraSkills mana (default 18, tier-scaled) and use longer cooldowns for higher tiers.
- Weapon lore now describes its tier-specific technique. Normal chest/door/button interactions remain usable.

### Elemental weapon tiers (2.0.2)

Every element now grants its five weapons as the player unlocks weapon tiers. The five active weapon techniques are intentionally different: (1) focused elemental slash, (2) heavy area impact, (3) ranged elemental arrow, (4) wide cyclone, and (5) tier-five elemental burst. Each element applies its own hit effect; examples include fire burn, water slow, earth weakness/absorption, wind knockback, shadow wither, ice freeze, nature poison/healing, sound knockback/nausea, crystal slow, and lightning chain strikes.

Lightning has five dedicated weapon skills: Spark Bolt (tier 1), Thunder Chain (tier 2), Lightning Arrow (tier 3), Lightning Storm (tier 4), and Thunder Judgment (tier 5).

Weapon skills cost AuraSkills mana (`mana.weapon`, default 18) and have tier-scaled cooldowns (`cooldowns.weapon-base`, default 8 seconds, plus `cooldowns.weapon-per-tier`, default 3 seconds per tier). The unarmed move is still available for every element. Existing owners of any element can reclaim unlocked weapons with `/gmagic weapons`.


## 2.0.3 — 12 Distinct Weapon Schools

- Every elemental weapon now has its own display name, skill name, and description at each of five tiers.
- Active weapon skills now use a dedicated per-element skill path rather than the same generic tier attack.
- The five lightning weapons retain five separate techniques: Spark Bolt, Thunder Chain, Lightning Arrow, Lightning Storm, and Thunder Judgment.
- Fire, water, earth, wind, shadow, ice, metal, nature, light, sound, and crystal each have five mechanically distinct tier abilities.
- All weapon skills continue to consume the real AuraSkills mana pool and use tier-scaled cooldowns.


## 2.0.3 — 12 Distinct Weapon Schools

- Every elemental weapon now has its own display name, skill name, and description at each of five tiers.
- Active weapon skills now use a dedicated per-element skill path rather than the same generic tier attack.
- The five lightning weapons retain five separate techniques: Spark Bolt, Thunder Chain, Lightning Arrow, Lightning Storm, and Thunder Judgment.
- Fire, water, earth, wind, shadow, ice, metal, nature, light, sound, and crystal each have five mechanically distinct tier abilities.
- All weapon skills continue to consume the real AuraSkills mana pool and use tier-scaled cooldowns.


## 2.0.3 — 12 Distinct Weapon Schools

- Every elemental weapon now has its own display name, skill name, and description at each of five tiers.
- Active weapon skills now use a dedicated per-element skill path rather than the same generic tier attack.
- The five lightning weapons retain five separate techniques: Spark Bolt, Thunder Chain, Lightning Arrow, Lightning Storm, and Thunder Judgment.
- Fire, water, earth, wind, shadow, ice, metal, nature, light, sound, and crystal each have five mechanically distinct tier abilities.
- All weapon skills continue to consume the real AuraSkills mana pool and use tier-scaled cooldowns.


## 2.0.3 — Per-element weapon identities

Each of the 12 schools now has five distinct weapon names, five distinct skill names, and five tier descriptions. The weapon ability dispatcher is element-specific: fire uses flame arcs and inferno bursts; water uses pressure lances and tidal waves; earth uses stone spikes and quakes; wind uses gusts and tornado movement; lightning uses five separate lightning techniques; shadow uses eclipse and phantom-step skills; ice uses freeze waves and blizzards; metal uses magnetic pulls and steel guards; nature uses roots and healing blooms; light uses radiant flashes; sound uses shockwaves and echo pulses; crystal uses shards and prism cages. All active weapon abilities continue to spend AuraSkills mana and use tier-scaled cooldowns.
