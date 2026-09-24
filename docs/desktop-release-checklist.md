# Desktop release verification

## Build and first run

Use JDK 17 and Gradle 8.7 (the GUI screenshot workflow uses 8.10.2). Run:

```text
gradle test :game-gui:captureGuiScreenshots :game-gui:distZip
```

The distribution is `game-gui/build/distributions/game-gui-0.1.0-alpha.zip`.
Extract the entire archive and run `bin/game-gui.bat` on Windows or `bin/game-gui` on Linux/macOS with Java 17 installed. The archive includes dependencies, but not a Java runtime. This remains an alpha distribution, not a signed installer.

Choose your faction and Capital, place the Capital, and start a match. Select a hand card, then click a highlighted destination. Right-click cards to inspect them. F2 opens legal actions; arrows select and Enter executes. F3 opens the full action history. End Turn stays in the top HUD. Income describes the current board's recurring income, not guaranteed future income after opponent actions or extra triggered effects.

## Automated checks

The screenshot harness renders exact client areas at 1280×650, 1366×768, 1920×1080, and 1100×700. It captures opening, selected hand, expanded hand, and a synthetic crowded board, plus motion examples. Every normal-size capture asserts all 24 cells lie inside the viewport. The 1280×650 case accounts for taskbar and window borders on a 1280×720 screen. Expanded Hand intentionally prioritizes inspection; collapsing restores the board. These are client-area renders, not certification of every Windows scaling configuration. F4 toggles board-only view; Esc returns to the HUD and hand.

The crowded fixture deliberately populates stacks to stress layout; it is not a replay of a legal match. Motion previews demonstrate rendering, not end-to-end combat correctness. Rules tests cover engine behavior independently.

## Human release gate — record results before release

- On a clean Windows account, extract and launch the archive using only its documented prerequisites.
- At 100%, 125%, and 150% display scaling, verify top controls, resource labels, full-card inspection, and dialog buttons remain reachable. Test a physical 1366×768 monitor including the taskbar and window borders.
- Play a same-faction match and identify each player's cards without relying on faction art or color alone.
- Deploy, move, Blink, burrow, attack, activate an ability, cast a spell, and decline/accept a reaction using mouse and keyboard paths. Check ambiguous stack choices and opportunity warnings.
- Expand/collapse the hand with a card selected; confirm selection and legal targets survive. Open/close Actions and History, resize, and toggle fullscreen.
- Confirm End Turn cannot execute while an animation, bot turn, or reaction is unresolved. Confirm it becomes available afterward.
- Inspect crowded stacks and damaged units; verify stack count, owner, remaining defense/HP, and target labels. Check animations on a crowded board for stalls.
- Compare GP spending/income entries with the HUD. Verify reaction and destruction explanations remain in full history.
- Finish matches through ordinary victory and exhaustion; verify final board review, rematch, and new-match setup.
- Have at least three new testers finish a match without developer coaching. Record confusion, misclicks, match length, and completion failures.

Do not mark the desktop release ready until these checks have recorded outcomes. Balance-pass documents are historical experiments; the current rules specification and executable tests define implemented behavior.
