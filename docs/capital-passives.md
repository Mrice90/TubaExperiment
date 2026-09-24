# Capital passive abilities

Every faction has three 20 HP Capital choices. Capitals remain outside the deck. Their passive abilities are automatic, deterministic, visible in the CLI, and recorded as `CAPITAL_PASSIVE_TRIGGERED` game events.

| Faction | Capital | Passive | Implemented effect |
|---|---|---|---|
| Zeus | Olympus Citadel | Olympian Muster | At the start of your turn, the first Blink Character gains +2 Attack for the turn. |
| Zeus | Keraunos Spire | Storm Tithe | The first Spell cast each turn refunds 1 GP. |
| Zeus | Cloud Throne | Cloudward | The first Character that Blinks each turn gains +2 Defense until its owner's next turn. |
| Poseidon | Atlantis Nexus | Tidal Renewal | At the start of your turn, heal 3 damage from your most damaged Land. |
| Poseidon | Trident Bastion | Trident Restoration | The first Land played each turn heals the Capital for 2. |
| Poseidon | Abyssal Court | Deep Reserves | The first Mole burrowed each turn refunds 1 GP. |
| Hades | House of Hades | Deathless Levy | At the start of your turn, return the most recently discarded Character to hand. |
| Hades | Styx Gate | Ferry Toll | The first enemy Character returned by your Spell each turn refunds 2 GP. |
| Hades | Tartarus Vault | Tartarus Endurance | The first friendly Permanent destroyed each turn heals another damaged friendly Permanent for 5. |
| Ares | Red Citadel | Bloodlust | The first attack each turn gains +1 Attack. |
| Ares | Iron War Camp | War Camp Drill | The first Character summoned each turn gains +1 Attack until its owner's next turn. |
| Ares | Spearpoint Keep | Relentless Advance | The first Character moved each turn recovers 1 movement. |
| Athena | Acropolis Command | Aegis Formation | At the start of your turn, the first Vanguard Character gains +1 Defense for the turn. |
| Athena | Aegis Archive | Archived Foresight | Draw one additional card every fourth personal turn. |
| Athena | Owlwatch Fortress | Owlward | The first Vanguard Character attacked each turn gains +1 Defense until its owner's next turn. |
| Hephaestus | The Great Forge | Forge Efficiency | The first Structure played each turn generates 1 GP. |
| Hephaestus | Volcanic Foundry | Salvage Fires | The first friendly Structure destroyed each turn heals the Capital for 2. |
| Hephaestus | Bronze Heart | Bronze Regeneration | At the start of your turn, heal the Capital for 1. |

These are prototype balance values. Once-per-turn passives reset on every turn change, including reaction windows on the opposing turn. Temporary bonuses reset at the affected card owner's next turn using the existing modifier lifecycle.
