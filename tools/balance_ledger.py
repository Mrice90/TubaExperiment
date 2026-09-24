"""Build the complete balance ledger from runtime exports (standard-library Python only).
Run from repository root after :game-cli:balancePass. No game data is modified.
"""
from pathlib import Path
import csv
import json

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/balance"
FACTIONS = {"ZEUS", "POSEIDON", "HADES", "ARES", "ATHENA", "HEPHAESTUS"}
def read(path):
    return json.loads((OUT / path).read_text(encoding="utf-8"))
def normalized(card):
    card = dict(card)
    for key in ("keywords", "archetypes"):
        card[key] = sorted(card[key])
    return card
before_raw, after_raw = read("before/catalog.json"), read("after/catalog.json")
before = {c["id"]: normalized(c) for c in before_raw["cards"]}
after = {c["id"]: normalized(c) for c in after_raw["cards"]}
assert before.keys() == after.keys(), "Stable card IDs changed"
assert len(after) == 426
assert sum(c["faction"] in FACTIONS for c in after.values()) == 402
reasons = read("decisions.json")
capital_reasons = {
    "zeus_capital_olympus_citadel": "Reduce recurring free Attack from 3 to 2; preserve Blink identity without overwhelming early bodies.",
    "zeus_capital_cloud_throne": "Reduce free Blink Defense from 4 to 2, leaving a counterplay window for ordinary attacks and Strike thresholds.",
    "hades_capital_house_of_hades": "Keep character recursion; remove the additional 3-HP repair that let one passive both rebuild and stall.",
    "hades_capital_styx_gate": "Replace generic spell rebate plus unbounded return rebates with 2 GP for the first enemy return each global turn; specialize in bounce.",
}
reports = {label: read(label + "/matches.json") for label in ("before", "after")}
plays = {label: {c["cardId"]: c["plays"] for c in r["cards"]} for label, r in reports.items()}
def display(value):
    if isinstance(value, (list, dict)):
        return json.dumps(value, sort_keys=True, ensure_ascii=False)
    return str(value)
def role(c):
    if c["type"] == "CAPITAL":
        return "Retain the 20-HP Capital and its conditional faction role: " + after_raw["capitalPassives"][c["id"]]
    if c["type"] == "CHARACTER":
        return (f"Retain {c['cost']} GP: A{c['attack']}/D{c['defense']}, movement {c['movement']}, range {c['range']}; "
                + ("keyword specialization " + ", ".join(c["keywords"]) if c["keywords"] else "stats-only body") + ".")
    if c["type"] == "SPELL":
        return f"Retain {c['cost']}-GP single-use effect: {display(c['effects'])}; no extra price for rarity."
    return (f"Retain turn-{max(1,c['cost'])} development: {c['hitPoints']} HP, {c['gpGeneration']} base income, "
            f"{c['developmentGoldCost']} gold; " + ("paid terrain utility." if c["keywords"] else
            "utility offset by activation payment/lower income or a free infrastructure baseline."))
rows = []
for id, c in sorted(after.items(), key=lambda pair: (pair[1]["faction"], pair[1]["type"], pair[1]["cost"], pair[1]["name"])):
    old = before[id]
    diff = {k: {"before": old[k], "after": c[k]} for k in old if old[k] != c[k]}
    if id in capital_reasons:
        diff["capitalPassive"] = {"before": before_raw["capitalPassives"][id], "after": after_raw["capitalPassives"][id]}
    mechanical = any(k != "archetypes" for k in diff)
    why = list(reasons.get(id, []))
    if "_tutor_" in id:
        why.append("Standardize typed draw at 2 GP. First two tiers stay free; later tiers pay 1/2/2 gold for durable draw engines, with faction utility on the final two tiers. Add Muster Ground or Recruitment grouping.")
    if id in capital_reasons:
        why.append(capital_reasons[id])
    legacy = c["faction"] not in FACTIONS
    if legacy:
        assert not diff, id + " legacy fixture was changed"
        why = ["Compatibility audit: legacy DEMO/UNASSIGNED card, excluded from legal faction decks; retained unchanged."]
    elif not why:
        why = [role(c)]
    row = {"id": id, "name": c["name"], "faction": c["faction"], "type": c["type"],
           "scope": "legacy" if legacy else "faction", "decision": "mechanical change" if mechanical else "archetype only" if diff else "retained",
           "changes": diff, "reason": " ".join(why), "current": c,
           "playsBefore": plays["before"].get(id, 0), "playsAfter": plays["after"].get(id, 0)}
    rows.append(row)
(OUT / "card-ledger.json").write_text(json.dumps(rows, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
with (OUT / "card-ledger.csv").open("w", encoding="utf-8", newline="") as f:
    fields = ["id", "name", "faction", "type", "scope", "decision", "changes", "reason", "playsBefore", "playsAfter"]
    writer = csv.DictWriter(f, fieldnames=fields); writer.writeheader()
    for row in rows:
        writer.writerow({k: display(row[k]) for k in fields})
lines = ["# Complete 0.4 card balance ledger", "", "All 402 faction cards/Capitals and 24 legacy entries are accounted for. Turns and gold are separate. Archetypes are descriptive only. See [method and results](README.md).", "",
         "Every row includes a decision and actual before/after play counts. Capital selections are covered in the match report rather than CARD_PLAYED counts.", ""]
for faction in sorted({r["faction"] for r in rows}):
    lines += ["## " + faction.title(), "", "| Card | Decision | Changes | Reason | Plays before / after |", "| --- | --- | --- | --- | ---: |"]
    for r in rows:
        if r["faction"] != faction: continue
        change = "; ".join(k + ": " + display(v["before"]) + " → " + display(v["after"]) for k, v in r["changes"].items()) or "Retained"
        lines += ["| " + " | ".join([r["name"] + " (`" + r["id"] + "`)", r["decision"], change, r["reason"], f"{r['playsBefore']} / {r['playsAfter']}"]).replace("\n", " ") + " |"]
    lines.append("")
(OUT / "card-ledger.md").write_text("\n".join(lines), encoding="utf-8")
summary = {"reviewedFactionCardsAndCapitals": 402, "legacyRetained": 24,
           "mechanicalChanges": sum(r["decision"] == "mechanical change" for r in rows),
           "archetypeOnlyChanges": sum(r["decision"] == "archetype only" for r in rows),
           "factionRetained": sum(r["decision"] == "retained" and r["scope"] == "faction" for r in rows),
           "playedFactionDeckCardsBefore": sum(v > 0 for v in plays["before"].values()),
           "playedFactionDeckCardsAfter": sum(v > 0 for v in plays["after"].values())}
(OUT / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
print(json.dumps(summary, indent=2))
