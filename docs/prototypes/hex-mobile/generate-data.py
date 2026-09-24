"""Refresh prototype rules metadata from the game's sources, preserving art choices."""
import json
import re
from pathlib import Path

here = Path(__file__).resolve().parent
root = here.parents[2]
data_file = here / 'data.js'
data = json.loads(data_file.read_text(encoding='utf-8').split('=', 1)[1].strip().rstrip(';'))
catalog = {}
for source in (root / 'game-core/src/main/resources/cards').glob('*.json'):
    content = json.loads(source.read_text(encoding='utf-8-sig'))
    if isinstance(content, dict):
        for card in content.get('cards', []):
            catalog[card['id']] = card
rules = (root / 'game-core/src/main/java/com/infiniteconquest/core/CapitalPassiveRules.java').read_text()
passives = dict(re.findall(r'Map.entry\("([a-z_]+)", CapitalPassive\.([A-Z_]+)\)', rules))
descriptions = dict(re.findall(r'Map.entry\(CapitalPassive\.([A-Z_]+), "([^"]+)"\)', rules))
for card in data['cards'] + data['capitals']:
    card.update(catalog[card['id']])
    development = card['type'] in ('LAND', 'STRUCTURE')
    card.setdefault('gpGeneration', min(5, 1 + (max(1, card['cost']) - 1) // 2) if development else 0)
    if card['type'] == 'CAPITAL':
        card['passiveName'] = passives[card['id']].replace('_', ' ').title()
        card['passiveText'] = descriptions[passives[card['id']]]
        card['gpGeneration'] = 1
data_file.write_text('window.ICData = ' + json.dumps(data, separators=(',', ':')) + ';\n', encoding='utf-8')
