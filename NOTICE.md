# Notice

Spartan Compat by Oculifelis. The 0.2.0 parity work adapts behavior and code from the MIT add-ons below to Minecraft 1.21.1 and the modern Spartan API.

## Weapon textures

The weapon textures come from the earlier 1.20.1 Spartan add-ons, used under the MIT License:

- Spartan Twilight Forest by KreloX (ironwood, steeleaf, knightmetal, fiery)
- Spartan Cataclysm by Cephelo (ignitium, cursium, witherite, ancient metal, black steel)
- Spartan Aquaculture by denispionicul (neptunium)
- Spartan Caverns and Chasms by denispionicul (necromium)

Those textures are modified versions of artwork from Spartan Weaponry by ObliviousSpartan, used under the Apache
License 2.0. This mod changes them: the fiery animation files drop their connected-textures block, and nothing else
is altered from the add-ons' copies.

The other materials (thaumium, void metal, starsteel, eclipse, cloggrum, froststeel, utherium, forgotten metal,
resonarium, warden, zanite, gravitite, allthemodium, vibranium, unobtainium, osmium, refined obsidian, refined
glowstone, lapis, soul stained steel, hallowed gold, deorum, draco arcanus) had no earlier add-on. Their textures are
Spartan Weaponry's own iron weapon sprites, recoloured by this mod: each metal pixel's brightness is mapped onto that
material's colours, and for most materials the wooden grip and copper bindings are moved onto colours that suit the
material's mod (its wood, its trim metal). Only colours were read from those mods; no file of theirs is copied.
Outlines and shading are Spartan's.

The licence texts are in `licenses/`. CREDITS.md lists which add-on each material's sprites come from and how each licence was checked.

## Other thanks

To the authors above, whose material lists and trait ideas this mod follows, to ObliviousSpartan for Spartan
Weaponry, and to the authors of its unofficial NeoForge port, which this mod builds on.

Twilight Forest, Aquaculture 2, L_Ender's Cataclysm, Caverns and Chasms, Thaumaturge, The Undergarden, Deeper and
Darker, The Aether, Allthemodium, Mekanism Tools, Malum and Forbidden Arcanus belong to their authors. This mod refers
to their items by id only and copies none of their files.

## Parity code in 0.2.0

- Spartan Twilight Forest, Copyright (c) 2022 KreloX: enchantment packages, armor coverage, Fiery callbacks and recipe choices. Source commit 78ace7c63b90cf6f13f8e124beda5e8ae6ce6d89.
- Spartan Cataclysm, Copyright (c) 2025 Cephelo: chance, stacking, duration, lifesteal and pulse behavior. Source commit b46756438e24937fc7e5d60e2647847526e8cc8e.
- Spartan Aquaculture, Copyright (c) 2026 denispionicul: Neptune's Might immersion and weapon categories. Source commit 8013cfa2c51d99893fa82ce7305de9d20304f15a.
- Spartan Caverns and Chasms, Copyright (c) 2026 denispionicul: universal Slowness II and smithing path. Source commit 89673373c8ee2ef8f4195ef9fbd4d7a9daf8a66b.

Full MIT notices are preserved in licenses/. Changes include NeoForge callbacks, startup JSON settings, registry-based enchantment lookup, a ranged projectile bridge and a standalone stun fallback. Cataclysm's own stun is used when installed. Its sound and particle assets are not copied; the traits play them by id when Cataclysm is installed. The Accursed Rage, Pulse Charge and Pulse Cooldown effect icons come from Spartan Cataclysm (MIT).

Create, Ars Nouveau, Occultism and Applied Energistics 2 belong to their authors. New modules reference their item IDs; only palette colors are sampled. Their six recolours derive from Spartan Weaponry's Apache-2.0 iron sprites.
