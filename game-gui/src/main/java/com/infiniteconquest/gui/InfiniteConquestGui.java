package com.infiniteconquest.gui;

import com.infiniteconquest.cli.*;
import com.infiniteconquest.core.*;
import com.infiniteconquest.gui.net.NetClient;
import com.infiniteconquest.gui.net.NetSession;

import static com.infiniteconquest.gui.UiTheme.*;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.*;

public final class InfiniteConquestGui extends JFrame implements NetClient.Listener {
    private InitiativeCoinPanel.Skin coinSkin = InitiativeCoinPanel.Skin.OLYMPIAN_GOLD;
    private final JLabel turnLabel = new JLabel();
    private final JLabel humanLabel = new JLabel();
    private final JLabel botLabel = new JLabel();
    private final JLabel messageLabel = new JLabel("Select a card or unit, then choose a legal action.");
    private final HexBoardPanel boardPanel = new HexBoardPanel();
    private final JPanel boardStage = new JPanel(new BorderLayout());
    private JScrollPane boardScroll;
    private final JPanel handPanel = new JPanel();
    private final DefaultListModel<ActionOption> actionModel = new DefaultListModel<>();
    private final JList<ActionOption> actionList = new JList<>(actionModel);
    private final DefaultListModel<String> historyModel = new DefaultListModel<>();
    private final JList<String> historyList = new JList<>(historyModel);
    private final JTabbedPane actionTabs = new JTabbedPane();
    private final Map<BoardPosition, JButton> boardButtons = new HashMap<>();
    private final Map<BoardPosition, EffectBadge> effectBadges = new HashMap<>();
    private final Set<BoardPosition> maskedBoardCells = new HashSet<>();
    private final CombatOverlay combatOverlay = new CombatOverlay();
    private final PresentationQueue presentationQueue;
    private final List<JButton> handButtons = new ArrayList<>();
    private final GameSettings gameSettings;
    private int lastBannerTurn = -1;
    private int lastBannerPlayer = -1;

    private GameState state;
    private CommandProcessor commands;
    private final ActionHints hints = new ActionHints();
    private BotPlayer bot = new BotPlayer();
    private DemoMatchFactory matchFactory;
    private FactionDecks factionDecks;
    private CapitalPassiveRules passiveRules;
    private final Map<String, DeckBuild> savedDecks = new HashMap<>();
    private DeckBuildStore buildStore;
    private final Runnable onQuitToTitle;
    private final Path deckDirectory = Path.of(System.getProperty("user.home"), ".infinite-conquest", "decks");
    private final InteractionState interaction = new InteractionState();
    private boolean botRunning;
    private String humanFaction = "ZEUS";
    private String humanAlly;
    private String botFaction = "POSEIDON";
    private CardDefinition humanCapital;
    private CardDefinition botCapital;
    private DragSource dragSource;
    private int historyNumber;
    private long lastSystemEvent = -1;
    private boolean playerOneBot;
    private boolean winnerSoundPlayed;
    // --- Online battle state (null/absent in local play) ---
    private NetSession netSession;
    private int netPlayer = -1;
    private java.util.function.Function<String, CardDefinition> netDefinitions;
    private PresentationSnapshot.Frame netLastFrame;
    private boolean netStarted;
    private boolean netGameOver;
    private Integer netWinner;
    /** Rating line for the game-over dialog, e.g. "Rating 1016 (+16)". Null = no rating to show. */
    private String netRatingLine;
    /** True once the rating report has resolved (or was skipped); gates the game-over dialog. */
    private boolean netRatingReady;
    private String opponentName = "Opponent";
    private String netPendingCommand;
    /** A mulligan prompt arrived and the local decision has not been sent yet. */
    private boolean netMulliganOpen;
    /** The local mulligan decision was sent; waiting on the opponent. */
    private boolean netMulliganWaiting;
    private boolean netReactionWaiting; // opponent's reaction window is open
    private javax.swing.Timer pulseTimer;
    private float pulsePhase;
    /** Generation counter for reaction windows; stale dialogs must not answer. */
    private int netReactionGen;
    /** The currently open network reaction dialog, if any (for timeout dismissal). */
    private VisualReactionDialog activeReactionDialog;
    private boolean victoryDialogShown;
    private boolean fullScreen;
    private boolean boardFullScreen;
    private boolean handExpanded;
    private final boolean captureMode;
    private boolean handPinned;
    private boolean hoverSuppressed;
    private javax.swing.Timer handHoverTimer;
    private long handExitTime;
    private JLayeredPane battlefieldLayers;
    private Path setupCaptureDirectory;
    private JPanel screenRoot;
    private JComponent headerArea;
    private JComponent actionArea;
    private JComponent handArea;
    private JButton muteButton;
    private JButton handExpandButton;
    private JButton endTurnButton;
    private final JLabel recentAction = new JLabel("Recent actions appear here — open History for the complete record.");
    private JDialog actionDialog;
    private MatchChoice lastMatchChoice;

    public InfiniteConquestGui() {
        this(false);
    }

    InfiniteConquestGui(boolean screenshotMode) {
        super("Infinite Conquest — Hex & Allies " + GameVersion.VERSION);
        captureMode = screenshotMode;
        onQuitToTitle = null;
        gameSettings = GameSettings.load();
        matchFactory = new DemoMatchFactory();
        factionDecks = new FactionDecks(matchFactory.pool());
        passiveRules = new CapitalPassiveRules();
        buildStore = new DeckBuildStore(matchFactory.pool(), matchFactory.capitals());
        presentationQueue = new PresentationQueue(this::playPresentation);
        initialize(screenshotMode, false);
    }

    /** Seat this client plays from: 0 in local play, the server-assigned seat online. */
    private int localPlayer() { return netSession != null ? netPlayer : 0; }
    private int remotePlayer() { return 1 - localPlayer(); }
    private boolean netMode() { return netSession != null; }

    /**
     * Shell-launched battle: reuses the loading screen's shared context so Play is
     * instant, and returns to the title menu when the player quits.
     */
    InfiniteConquestGui(GameContext context, Runnable onQuitToTitle) {
        super("Infinite Conquest — Hex & Allies " + GameVersion.VERSION);
        captureMode = false;
        this.onQuitToTitle = onQuitToTitle;
        gameSettings = context.settings;
        matchFactory = context.matchFactory;
        factionDecks = context.factionDecks;
        passiveRules = context.passiveRules;
        buildStore = context.buildStore;
        savedDecks.putAll(context.savedDecks);
        presentationQueue = new PresentationQueue(this::playPresentation);
        initialize(false, true);
    }

    /**
     * Online battle: the match is owned by the server; this frame renders only
     * server-confirmed snapshots. The initial snapshot replays through the
     * session as soon as the battle listener registers.
     */
    InfiniteConquestGui(GameContext context, Runnable onQuitToTitle, NetSession session,
                        java.util.function.Function<String, CardDefinition> definitions) {
        super("Infinite Conquest — Online Battle " + GameVersion.VERSION);
        captureMode = false;
        this.netSession = session;
        this.netPlayer = session.localPlayer();
        this.netDefinitions = definitions;
        this.onQuitToTitle = () -> {
            try { session.close(); } catch (RuntimeException ignored) {}
            onQuitToTitle.run();
        };
        gameSettings = context.settings;
        matchFactory = context.matchFactory;
        factionDecks = context.factionDecks;
        passiveRules = context.passiveRules;
        buildStore = context.buildStore;
        savedDecks.putAll(context.savedDecks);
        presentationQueue = new PresentationQueue(this::playPresentation);
        initialize(false, true);
        beginNetMatch();
    }

    private void initialize(boolean screenshotMode, boolean decksReady) {
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent event) {
                int answer = JOptionPane.showConfirmDialog(InfiniteConquestGui.this,
                        "Quit Infinite Conquest?", "Exit",
                        JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
                if (answer == JOptionPane.YES_OPTION) {
                    dispose();
                    System.exit(0);
                }
            }
        });
        setMinimumSize(new Dimension(1100, 640));
        setSize(1500, 980);
        if (!screenshotMode) setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLocationRelativeTo(null);
        installTheme();
        setJMenuBar(buildMenuBar());
        setContentPane(buildScreen());
        setGlassPane(combatOverlay);
        combatOverlay.setVisible(true);
        if (!screenshotMode && !decksReady) loadSavedDecks();
        if (screenshotMode) startMatch(defaultChoice(), 424242L, false);
        else if (netSession == null) newMatch();
        startPulseTimer();
    }

    /**
     * Gentle pulsing highlight on legal drop hexes while a card or unit is
     * selected. Runs at ~8fps and only repaints while a selection is active;
     * REDUCED mode keeps the static intent outline instead.
     */
    private void startPulseTimer() {
        pulseTimer = new javax.swing.Timer(120, event -> {
            if (Fx.reduced(gameSettings) || !interaction.hasSelection()) return;
            pulsePhase += .55f;
            boardPanel.putClientProperty("pulsePhase", pulsePhase);
            boardPanel.repaint();
        });
        pulseTimer.start();
    }

    /** Online battle setup: perspective, labels, and the initial server snapshot. */
    private void beginNetMatch() {
        boardPanel.setLocalPlayer(netPlayer);
        for (com.infiniteconquest.net.Protocol.LobbyPlayer player : netSession.lobbyPlayers()) {
            if (!player.uuid().equals(gameSettings.playerUuid)) opponentName = player.name();
        }
        netSession.addBattleListener(this);
    }

    /** First server snapshot: build the client-side state and paint the board. */
    private void applyNetSnapshot(GameSnapshot snapshot) {
        state = GameState.fromSnapshot(snapshot, netDefinitions);
        netLastFrame = PresentationSnapshot.capture(state);
        netMulliganWaiting = false; // a live snapshot means both mulligans are in
        netReactionWaiting = false; // and any reaction window is resolved
        if (!netStarted) {
            netStarted = true;
            initNetMatchMeta();
            historyModel.clear();
            historyNumber = 0;
            addHistory("Match", "Online battle — " + title(humanFaction) + " vs " + title(botFaction));
            message("Connected. " + (state.activePlayer() == localPlayer()
                    ? "Your move." : "Waiting for " + opponentName + "."));
        }
        refresh();
    }

    /** Derives labels/capitals/factions from the local player's snapshot view. */
    private void initNetMatchMeta() {
        CardDefinition localCapital = null;
        CardDefinition remoteCapital = null;
        // Capitals are the battlefield cards owned by each player with the capital type.
        for (java.util.UUID id : state.board().positions().stream()
                .map(position -> state.board().topAt(position)).filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get).toList()) {
            CardInstance card = state.card(id).orElse(null);
            if (card == null || card.definition().type() != CardType.CAPITAL) continue;
            if (card.owner() == localPlayer()) localCapital = card.definition();
            else remoteCapital = card.definition();
        }
        if (localCapital != null) {
            humanCapital = localCapital;
            humanFaction = localCapital.faction();
        }
        if (remoteCapital != null) {
            botCapital = remoteCapital;
            botFaction = remoteCapital.faction();
        }
        DeckBuild localDeck = savedDecks.get(humanFaction);
        humanAlly = localDeck == null ? null : localDeck.allyFaction();
        boardPanel.setBackgroundCard(humanCapital);
        lastBannerTurn = -1;
        lastBannerPlayer = -1;
    }

    // --- NetClient.Listener: server-confirmed events, already on the EDT ---

    @Override public void onLobby(java.util.List<com.infiniteconquest.net.Protocol.LobbyPlayer> players,
                                  String hostUuid) {
        // Lobby UI lives in the shell; the battle only cares about snapshots.
    }

    @Override public void onSnapshot(long seq, String matchId, GameSnapshot snapshot) {
        applyNetSnapshot(snapshot);
    }

    @Override public void onStateUpdate(long seq, String command, String result, int actor,
                                       GameSnapshot snapshot) {
        if (netGameOver) return;
        netPendingCommand = null;
        netReactionWaiting = false; // any open reaction window resolved with this update
        GameState after = GameState.fromSnapshot(snapshot, netDefinitions);
        PresentationSnapshot.Frame before = netLastFrame;
        netLastFrame = PresentationSnapshot.capture(after);
        state = after;
        if (before != null) showResolution(PresentationSnapshot.between(command, before, after));
        addHistory(actor == localPlayer() ? "You" : opponentName, describe(command));
        message(result);
        if (after.phase() == Phase.GAME_OVER) interaction.markGameOver();
        else interaction.finishResolution();
        refresh();
    }

    @Override public void onGameOver(Integer winner, String matchId, GameSnapshot snapshot) {
        netGameOver = true;
        netWinner = winner;
        netPendingCommand = null;
        netReactionWaiting = false;
        state = GameState.fromSnapshot(snapshot, netDefinitions);
        netLastFrame = PresentationSnapshot.capture(state);
        interaction.markGameOver();
        requestRatingReport(winner);
        refresh();
    }

    @Override public void onError(String message) {
        message("Server: " + message);
        if (netPendingCommand != null) {
            // The command was rejected without a state update; unlock input.
            netPendingCommand = null;
            interaction.finishResolution();
            refresh();
        }
    }

    @Override public void onDisconnected(String reason) {
        if (netGameOver) return;
        netGameOver = true;
        netReactionWaiting = false;
        JOptionPane.showMessageDialog(this,
                "Lost connection to the host.\n" + (reason == null ? "" : reason),
                "Disconnected", JOptionPane.WARNING_MESSAGE);
        onQuitToTitle.run();
        dispose();
    }

    /**
     * The server asks for a real mulligan decision. Builds the opening-hand
     * view from the redacted snapshot and shows the shared mulligan dialog.
     */
    @Override public void onMulliganPrompt(long seq, String matchId, GameSnapshot snapshot) {
        if (netGameOver || !netMode()) return;
        state = GameState.fromSnapshot(snapshot, netDefinitions);
        netLastFrame = PresentationSnapshot.capture(state);
        netMulliganOpen = true;
        initNetMatchMeta();
        refresh();
        message("Mulligan — choose up to 3 cards to discard and redraw.");
        SwingUtilities.invokeLater(this::showNetMulliganDialog);
    }

    private void showNetMulliganDialog() {
        if (!netMulliganOpen || netSession == null || netGameOver) return;
        List<MulliganChoice> choices = state.player(localPlayer()).hand().stream()
                .map(id -> new MulliganChoice(id, state.card(id).orElseThrow().definition()))
                .toList();
        Set<UUID> discarded = new VisualMulliganDialog(choices).choose();
        if (!netMulliganOpen || netSession == null || netGameOver) return; // resolved meanwhile
        netMulliganOpen = false;
        netMulliganWaiting = true;
        netSession.sendMulligan(new ArrayList<>(discarded));
        addHistory("You", "Mulligan — discarded and redrew " + discarded.size());
        message("Mulligan submitted — waiting for " + opponentName + "…");
        refresh();
    }

    @Override public void onMulliganUpdate(boolean decided0, boolean decided1) {
        if (netGameOver || !netMode()) return;
        boolean opponentDecided = localPlayer() == 0 ? decided1 : decided0;
        if (opponentDecided && netMulliganWaiting) {
            message(opponentName + " decided — the match starts when both players are ready.");
            refresh();
        }
    }

    /**
     * A reaction window opened for the local player. Shows the reaction
     * dialog with the server's exact legal commands; sends the chosen command
     * verbatim, or a pass when the dialog closes unanswered.
     */
    @Override public void onReactionPrompt(long seq, String matchId, int reactingPlayer,
                                           List<String> commands, int expiresInSeconds) {
        if (netGameOver || !netMode() || reactingPlayer != localPlayer()) return;
        message("Reaction window — answer before the timer runs out!");
        int generation = ++netReactionGen;
        activeReactionDialog = new VisualReactionDialog(reactingPlayer, commands, expiresInSeconds);
        String chosen = activeReactionDialog.choose();
        activeReactionDialog = null;
        if (generation != netReactionGen || netSession == null || netGameOver) return; // timed out/disconnected
        netSession.sendReaction(chosen);
        addHistory("You", chosen == null || chosen.isBlank() ? "Reaction — pass" : "Reaction — " + describe(chosen));
        refresh();
    }

    @Override public void onReactionTimeout(int player) {
        if (netGameOver || !netMode()) return;
        netReactionWaiting = false; // the window resolved; input unlocks below
        netReactionGen++; // any open dialog must not answer a dead window
        VisualReactionDialog dialog = activeReactionDialog;
        activeReactionDialog = null;
        if (dialog != null && player == localPlayer()) dialog.dispose();
        message(player == localPlayer() ? "Your reaction window expired — auto-passed."
                : opponentName + "'s reaction window expired — auto-passed.");
        refresh();
    }

    /**
     * The opponent is reacting: show the waiting indicator and lock
     * spell/attack input until the window resolves. This envelope carries no
     * card data — it is only delivered to the non-reacting player.
     */
    @Override public void onReactionWaiting(int reactingPlayer, int secondsLeft) {
        if (netGameOver || !netMode() || reactingPlayer == localPlayer()) return;
        netReactionWaiting = true;
        message("Opponent is reacting… (" + secondsLeft + "s)");
        refresh();
    }

    /** Net game-over dialog, shown once the final presentation finishes. */
    private void showNetGameOver() {
        if (netWinner == null || !netRatingReady) return;
        boolean won = netWinner == localPlayer();
        String title = won ? "Victory!" : "Defeat";
        String ratingLine = netRatingLine == null ? ""
                : "<br><br><font color='#f0bf49'>" + html(netRatingLine) + "</font>";
        int answer = JOptionPane.showConfirmDialog(this,
                "<html><div style='text-align:center'>"
                        + (won ? "You have destroyed the enemy capital!" : opponentName + " has destroyed your capital.")
                        + ratingLine
                        + "<br><br>Return to the title menu?</div></html>",
                title, JOptionPane.YES_NO_OPTION,
                won ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        netWinner = null; // show once
        if (answer == JOptionPane.YES_OPTION) {
            onQuitToTitle.run();
            dispose();
        }
    }

    /**
     * Reports this client's view of the match result to the lobby/rating
     * service. Both clients report independently; the service only applies
     * the Elo change when both reports agree (see net-server/SECURITY.md).
     * The game-over dialog waits for the report to resolve so it can show
     * the rating change.
     */
    private void requestRatingReport(Integer winner) {
        netRatingReady = false;
        netRatingLine = null;
        String workerUrl = gameSettings.effectiveLobbyWorkerUrl();
        String matchId = netSession != null ? netSession.matchId() : null;
        if (workerUrl == null || workerUrl.isBlank() || matchId == null || winner == null) {
            netRatingReady = true;
            return;
        }
        String winnerUuid = seatUuid(winner);
        String loserUuid = seatUuid(winner == 0 ? 1 : 0);
        if (winnerUuid == null || loserUuid == null) {
            netRatingReady = true;
            return;
        }
        int ratingBefore = gameSettings.playerRating;
        new javax.swing.SwingWorker<com.infiniteconquest.gui.net.LobbyService.ReportResult, Void>() {
            @Override
            protected com.infiniteconquest.gui.net.LobbyService.ReportResult doInBackground() {
                try {
                    com.infiniteconquest.gui.net.LobbyService service =
                            new com.infiniteconquest.gui.net.LobbyService(workerUrl);
                    return service.report(matchId, gameSettings.playerUuid, winnerUuid, loserUuid);
                } catch (Exception e) {
                    return null;
                }
            }

            @Override
            protected void done() {
                try {
                    com.infiniteconquest.gui.net.LobbyService.ReportResult result = get();
                    if (result != null && result.applied()) {
                        applyRatingResult(result.rating(), ratingBefore);
                    } else if (result != null && result.reason() != null
                            && result.reason().toLowerCase(java.util.Locale.ROOT).contains("waiting")) {
                        // We reported first: the change lands when the opponent's
                        // report arrives. Poll the rating until it moves.
                        pollRatingUntilApplied(workerUrl, ratingBefore);
                    } else if (result != null && result.reason() != null) {
                        netRatingLine = "Rating unchanged (" + result.reason() + ")";
                        netRatingReady = true;
                        refresh();
                    } else {
                        netRatingReady = true;
                        refresh();
                    }
                } catch (Exception ignored) {
                    netRatingReady = true;
                    refresh();
                }
            }
        }.execute();
    }

    /**
     * After reporting first, the Elo change is applied when the opponent's
     * report lands. Poll our rating until it moves (or a timeout), so both
     * players eventually see the delta instead of only the second reporter.
     */
    private void pollRatingUntilApplied(String workerUrl, int ratingBefore) {
        new javax.swing.SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                com.infiniteconquest.gui.net.LobbyService service =
                        new com.infiniteconquest.gui.net.LobbyService(workerUrl);
                long deadline = System.currentTimeMillis() + 90_000;
                while (System.currentTimeMillis() < deadline) {
                    try {
                        Thread.sleep(5_000);
                        com.infiniteconquest.gui.net.LobbyService.Rating rating =
                                service.ratingOf(gameSettings.playerUuid);
                        if (rating.rating() != ratingBefore) return rating.rating();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    } catch (Exception ignored) {
                        // Transient: keep polling until the deadline.
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    Integer now = get();
                    if (now != null) {
                        applyRatingResult(now, ratingBefore);
                    } else {
                        netRatingLine = "Rating will update once both results are in";
                        netRatingReady = true;
                        refresh();
                    }
                } catch (Exception ignored) {
                    netRatingReady = true;
                    refresh();
                }
            }
        }.execute();
    }

    /** Shows the rating delta, persists the new rating, and releases the game-over dialog. */
    private void applyRatingResult(int ratingNow, int ratingBefore) {
        int delta = ratingNow - ratingBefore;
        String sign = delta >= 0 ? "+" : "";
        netRatingLine = "Rating " + ratingNow + " (" + sign + delta + ")";
        gameSettings.playerRating = ratingNow;
        gameSettings.save();
        netRatingReady = true;
        refresh();
    }

    /** UUID of the player in the given seat, from the lobby roster. */
    private String seatUuid(int seat) {
        if (netSession == null) return null;
        java.util.List<com.infiniteconquest.net.Protocol.LobbyPlayer> players = netSession.lobbyPlayers();
        return seat >= 0 && seat < players.size() ? players.get(seat).uuid() : null;
    }

    @Override public void dispose() { if(handHoverTimer!=null)handHoverTimer.stop();super.dispose(); }
    @Override public void setVisible(boolean visible) { super.setVisible(visible);if(handHoverTimer!=null){if(visible)handHoverTimer.start();else handHoverTimer.stop();} }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new InfiniteConquestGui().setVisible(true));
    }

    private JComponent buildScreen() {
        screenRoot = new JPanel(new BorderLayout(4, 4));
        screenRoot.setBackground(INK);
        screenRoot.setBorder(new EmptyBorder(4, 4, 4, 4));
        headerArea = buildHeader();
        actionArea = buildActions();
        handArea = buildHand();
        screenRoot.add(headerArea, BorderLayout.NORTH);
        JComponent battlefield = buildBoard();
        battlefieldLayers = new JLayeredPane() {
            @Override public void doLayout() {
                battlefield.setBounds(0,0,getWidth(),Math.max(1,getHeight()-58));
                int trayHeight = handExpanded ? Math.min(230,getHeight()-60) : 58;
                handArea.setBounds(12,Math.max(0,getHeight()-trayHeight),Math.max(1,getWidth()-24),trayHeight);
            }
        };
        battlefieldLayers.add(battlefield,JLayeredPane.DEFAULT_LAYER);
        battlefieldLayers.add(handArea,JLayeredPane.PALETTE_LAYER);
        screenRoot.add(battlefieldLayers, BorderLayout.CENTER);
        screenRoot.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "exitBoardView");
        screenRoot.getActionMap().put("exitBoardView", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                if (boardFullScreen) toggleBoardFullScreen();
                else { handPinned=false; hoverSuppressed=true; setHandExpanded(false); }
            }
        });
        screenRoot.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke("F10"), "captureScreenshot");
        screenRoot.getActionMap().put("captureScreenshot", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                saveDebugScreenshot();
            }
        });
        return screenRoot;
    }

    Path captureScreenshot(Path output) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Screenshots must be captured on the Swing event-dispatch thread");
        }
        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            JRootPane captureRoot = getRootPane();
            Dimension size = captureRoot.getSize();
            if (size.width <= 0 || size.height <= 0) size = getSize();
            BufferedImage image = new BufferedImage(Math.max(1, size.width), Math.max(1, size.height),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            captureRoot.printAll(graphics);
            graphics.dispose();
            ImageIO.write(image, "png", output.toFile());
            return output;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not capture GUI screenshot", exception);
        }
    }

    void beginAutomatedCapture() {
        bot = new BotPlayer(BotDifficulty.HERO); // demo capture: both seats play the classic challenge
        playerOneBot = true;
        runBotTurn();
    }
    PresentationSnapshot captureActivePresentation() { return presentationQueue.active(); }
    String captureStateFingerprint() {
        return state.turnNumber()+":"+state.activePlayer()+":"+state.events().size()+":"+PresentationSnapshot.capture(state).cards();
    }

    void prepareScreenshotScenario(String scenario) {
        interaction.clearSelection();
        if ("deployment-motion".equals(scenario) || "selected-hand".equals(scenario)) {
            CardInstance card = new CardInstance(UUID.randomUUID(), matchFactory.pool().require("zeus_olympian_cloudbank"), 0, Zone.HAND);
            state.register(card);
            state.player(0).addToHand(card.instanceId());
        }
        if("terrain-board".equals(scenario)) {
            BoardPosition high=new BoardPosition(2,0), gun=new BoardPosition(2,4), medic=new BoardPosition(0,1);
            fixtureCard("zeus_eagles_perch_array",high,0);fixtureCard("zeus_structure_stormglass_relay",high,0);fixtureCard("zeus_cyclone_marksman",high,0);
            fixtureCard("zeus_apex_worldstorm_spire",gun,1);fixtureCard("poseidon_tidal_pump_station",medic,0);
            boardPanel.inspect(matchFactory.pool().require("zeus_structure_stormglass_relay"));
        }
        if ("crowded-board".equals(scenario)) {
            List<CardDefinition> characters = matchFactory.pool().cardsForFaction("ZEUS").stream()
                    .filter(card -> card.type() == CardType.CHARACTER).toList();
            int index = 0;
            for (BoardPosition position : state.board().positions()) {
                int owner = position.isOnPlayerSide(0) ? 0 : 1;
                for (int layer = 0; layer < 2; layer++) {
                    CardInstance card = new CardInstance(UUID.nameUUIDFromBytes(("fixture-" + index).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                            characters.get(index++ % characters.size()), owner, Zone.BATTLEFIELD);
                    state.register(card);
                    state.board().push(position, card.instanceId());
                }
            }
            int badgeIndex = 0;
            for (BoardPosition position : state.board().positions()) {
                if (badgeIndex++ % 4 == 0) effectBadges.put(position, new EffectBadge("RETALIATION • DESTROYED", "#ff7373"));
            }
        }
        if ("selected-hand".equals(scenario)) {
            List<String> legal = legalCommands();
            for (int index = 0; index < state.player(0).hand().size(); index++) {
                final int candidate = index;
                if (legal.stream().anyMatch(command -> command.matches("(play|burrow|cast) " + candidate + "( .*)?"))) {
                    interaction.selectHand(candidate);
                    break;
                }
            }
        }
        if ("expanded-hand".equals(scenario) && !handExpanded) toggleHandExpansion();
        refresh();
        validate();
        if ("deployment-motion".equals(scenario)) {
            String command = legalCommands().stream()
                    .filter(value -> value.matches("play \\d+ \\d+ \\d+"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("No deployment available for fixture"));
            executeHuman(command);
        }
        if ("invalid-drop-motion".equals(scenario)) {
            interaction.selectHand(0);
            animateInvalidDrop(new DragSource(0, null), new BoardPosition(0, BoardPosition.HEIGHT - 1));
        }
        if ("board-movement".equals(scenario)) {
            previewBoardMovement();
        }
        if ("melee-lunge".equals(scenario)) {
            previewMeleeLunge();
        }
        if ("card-destruction".equals(scenario)) {
            previewCardDestruction();
        }
    }

    private void fixtureCard(String id,BoardPosition position,int owner) {
        CardInstance card=new CardInstance(UUID.nameUUIDFromBytes((id+position+owner).getBytes(java.nio.charset.StandardCharsets.UTF_8)),matchFactory.pool().require(id),owner,Zone.BATTLEFIELD);
        state.register(card);state.board().push(position,card.instanceId());
    }

    private void saveDebugScreenshot() {
        Path output = Path.of("build", "screenshots", "manual-" + System.currentTimeMillis() + ".png");
        try {
            captureScreenshot(output);
            message("Screenshot saved to " + output.toAbsolutePath());
        } catch (IllegalStateException exception) {
            message(exception.getMessage());
        }
    }

    private JComponent buildHeader() {
        JPanel header = panel(new BorderLayout(8, 0));
        header.setBorder(new CompoundBorder(new BevelBorder(BevelBorder.RAISED), new EmptyBorder(5, 8, 5, 8)));
        JLabel title = new JLabel("INFINITE CONQUEST");
        title.setForeground(GOLD);
        title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        turnLabel.setForeground(Color.WHITE);
        turnLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));

        styleMeter(humanLabel, new Color(87, 203, 234));
        styleMeter(botLabel, new Color(239, 106, 122));
        humanLabel.setBorder(new EmptyBorder(2,4,2,4));botLabel.setBorder(new EmptyBorder(2,4,2,4));

        JButton deckBuilder = button("Deck Builder", e -> openDeckEditor());
        JButton newMatch = button("New Match", e -> newMatch());
        muteButton = button("Mute", e -> toggleMute(), true);
        muteButton.setToolTipText("Mute sound effects");
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 2));
        left.setOpaque(false);
        left.add(title);
        left.add(turnLabel);
        header.add(left, BorderLayout.WEST);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);
        JButton menu = button("Game ▾", e -> {
            JPopupMenu popup=new JPopupMenu();
            for(JButton action : new JButton[]{deckBuilder,newMatch,muteButton}) {
                JMenuItem item=new JMenuItem(action.getText());item.addActionListener(event->action.doClick());popup.add(item);
            }
            popup.show((Component)e.getSource(),0,((Component)e.getSource()).getHeight());
        });
        controls.add(menu);
        controls.add(button("Background", e -> boardPanel.toggleBackground()));
        controls.add(button("Actions", e -> openActionPanel(0)));
        controls.add(button("History", e -> openActionPanel(1)));
        endTurnButton = button("End Turn", e -> executeHuman("end"));
        controls.add(endTurnButton);
        header.add(controls, BorderLayout.EAST);
        JPanel information = new JPanel(new BorderLayout(8, 3));
        information.setOpaque(false);
        JPanel players = new JPanel(new GridLayout(1, 2, 12, 0));
        players.setOpaque(false);
        players.add(humanLabel);
        players.add(botLabel);
        information.add(players, BorderLayout.CENTER);
        recentAction.setForeground(Color.WHITE);
        recentAction.setPreferredSize(new Dimension(200, 22));
        // The action history remains available through History; keep the board HUD compact.
        header.add(information, BorderLayout.SOUTH);
        return header;
    }

    void prepareCaptureSize(int width, int height, boolean requireFullBoard) {
        // Render a deterministic client area even when the host window manager clamps the frame.
        getRootPane().setSize(width, height);
        layoutTree(getRootPane());
        fitBoardToViewport(boardScroll.getViewport().getExtentSize());
        layoutTree(getRootPane());
        if (requireFullBoard) {
            Rectangle view = boardScroll.getViewport().getViewRect();
            for (JButton cell : boardButtons.values()) {
                Rectangle bounds = SwingUtilities.convertRectangle(cell.getParent(), cell.getBounds(), boardStage);
                if (!view.contains(bounds)) throw new IllegalStateException("Battlefield cell clipped: " + bounds);
                if(width==1280&&height==650&&(cell.getWidth()<100||cell.getHeight()<85))throw new IllegalStateException("Battlefield is too small at laptop size");
            }
            if (!endTurnButton.isShowing()) throw new IllegalStateException("End Turn is hidden");
        }
    }

    void verifyHandOverlay() {
        Map<BoardPosition,Rectangle> before=new HashMap<>();boardButtons.forEach((p,b)->before.put(p,b.getBounds()));
        setHandExpanded(true);layoutTree(getRootPane());
        boardButtons.forEach((p,b)->{if(!before.get(p).equals(b.getBounds()))throw new IllegalStateException("Hand reveal shifted the battlefield");});
        interaction.clearSelection();
        JButton card=handButtons.get(0);
        card.dispatchEvent(new MouseEvent(card,MouseEvent.MOUSE_PRESSED,System.currentTimeMillis(),0,card.getWidth()/2,card.getHeight()/2,1,false,MouseEvent.BUTTON1));
        card.dispatchEvent(new MouseEvent(card,MouseEvent.MOUSE_RELEASED,System.currentTimeMillis(),0,card.getWidth()/2,card.getHeight()/2,1,false,MouseEvent.BUTTON1));
        if(handExpanded||!Objects.equals(interaction.handIndex(),0))throw new IllegalStateException("Selecting a hand card must tuck the tray away and retain selection");
        clearSelection();
        layoutTree(getRootPane());
    }

    private static void layoutTree(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container nested) layoutTree(nested);
        }
    }

    private void openActionPanel(int tab) {
        if (actionDialog == null) {
            actionDialog = new JDialog(this, "Actions and history", false);
            actionDialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            actionDialog.add(actionArea);
            actionDialog.setSize(440, 480);
        }
        actionTabs.setSelectedIndex(tab);
        actionArea.setVisible(true);
        actionDialog.setLocationRelativeTo(this);
        actionDialog.setVisible(true);
        if (tab == 0) actionList.requestFocusInWindow();
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu game = new JMenu("Game");
        JMenuItem decks = new JMenuItem("Deck Builder...");
        decks.setAccelerator(KeyStroke.getKeyStroke("control D"));
        decks.addActionListener(event -> openDeckEditor());
        JMenuItem newGame = new JMenuItem("New Match...");
        newGame.setAccelerator(KeyStroke.getKeyStroke("control N"));
        newGame.addActionListener(event -> newMatch());
        JMenuItem fullscreen = new JMenuItem("Toggle Full Screen");
        fullscreen.setAccelerator(KeyStroke.getKeyStroke("F11"));
        fullscreen.addActionListener(event -> toggleFullScreen());
        JMenuItem actions = new JMenuItem("Legal Actions");
        actions.setAccelerator(KeyStroke.getKeyStroke("F2"));
        actions.addActionListener(event -> openActionPanel(0));
        JMenuItem history = new JMenuItem("Action History");
        history.setAccelerator(KeyStroke.getKeyStroke("F3"));
        history.addActionListener(event -> openActionPanel(1));
        JMenuItem boardView = new JMenuItem("Toggle Board View");
        boardView.setAccelerator(KeyStroke.getKeyStroke("F4"));
        boardView.addActionListener(event -> toggleBoardFullScreen());
        game.add(decks); game.addSeparator(); game.add(newGame); game.add(fullscreen);
        game.add(actions); game.add(history); game.add(boardView);
        if (onQuitToTitle != null) {
            JMenuItem quitToTitle = new JMenuItem("Quit to Title");
            quitToTitle.addActionListener(event -> {
                dispose();
                onQuitToTitle.run();
            });
            game.addSeparator(); game.add(quitToTitle);
        }
        bar.add(game);
        return bar;
    }

    private void toggleFullScreen() {
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (fullScreen) device.setFullScreenWindow(null);
        dispose();
        fullScreen = !fullScreen;
        setUndecorated(fullScreen);
        setVisible(true);
        if (fullScreen && device.isFullScreenSupported()) device.setFullScreenWindow(this);
        else {
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
    }

    private JComponent buildBoard() {
        JPanel surround = panel(new BorderLayout(0, 8));
        boardPanel.setOpaque(false);
        boardPanel.setBorder(new EmptyBorder(8, 8, 8, 8));
        for (int y = BoardPosition.HEIGHT - 1; y >= 0; y--) {
            for (int x = 0; x < BoardPosition.WIDTH; x++) {
                BoardPosition position = new BoardPosition(x, y);
                JButton cell = new BattlefieldCell();
                cell.setVerticalAlignment(SwingConstants.TOP);
                cell.setHorizontalAlignment(SwingConstants.LEFT);
                cell.setFocusPainted(false);
                cell.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                cell.setMargin(new Insets(2, 2, 2, 2));
                cell.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                cell.addMouseListener(dragListener(new DragSource(null, position)));
                cell.putClientProperty("position", position);
                boardButtons.put(position, cell);
                boardPanel.add(cell);
            }
        }
        boardStage.setOpaque(true);
        boardStage.setBackground(BOARD_STAGE);
        boardStage.add(boardPanel);
        boardScroll = new JScrollPane(boardStage,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        boardScroll.setBorder(new LineBorder(BOARD_STAGE_EDGE, 1));
        boardScroll.getViewport().setBackground(BOARD_STAGE);
        boardScroll.getVerticalScrollBar().setUnitIncrement(24);
        boardScroll.getHorizontalScrollBar().setUnitIncrement(24);
        boardScroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                fitBoardToViewport(boardScroll.getViewport().getExtentSize());
            }
        });
        surround.add(boardScroll, BorderLayout.CENTER);
        return surround;
    }

    private void toggleMute() {
        SoundEffects.setMuted(!SoundEffects.isMuted());
        muteButton.setText(SoundEffects.isMuted() ? "Unmute" : "Mute");
        muteButton.setToolTipText(SoundEffects.isMuted() ? "Sound effects are muted" : "Mute sound effects");
    }

    private void toggleBoardFullScreen() {
        boardFullScreen = !boardFullScreen;
        headerArea.setVisible(!boardFullScreen);
        actionArea.setVisible(!boardFullScreen);
        handArea.setVisible(!boardFullScreen);
        refreshBoard();
        screenRoot.revalidate();
        screenRoot.repaint();
        SwingUtilities.invokeLater(() -> fitBoardToViewport(boardScroll.getViewport().getExtentSize()));
    }

    private void fitBoardToViewport(Dimension available) {
        Dimension boardSize = new Dimension(Math.max(640, available.width - 4),
                Math.max(300, available.height - 4));
        boardPanel.setPreferredSize(boardSize);
        boardPanel.setMinimumSize(boardSize);
        boardPanel.setMaximumSize(boardSize);
        boardStage.setPreferredSize(boardSize);
        boardStage.revalidate();
        if (state != null) refreshBoard();
    }

    private JComponent buildActions() {
        JPanel side = panel(new BorderLayout(8, 8));
        side.setPreferredSize(new Dimension(336, 390));
        actionList.setBackground(PANEL_LIGHT);
        actionList.setForeground(Color.WHITE);
        actionList.setSelectionBackground(new Color(48, 112, 137));
        actionList.setFixedCellHeight(40);
        actionList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        actionList.setCellRenderer(new ActionOptionRenderer());
        actionList.setBorder(new EmptyBorder(5, 5, 5, 5));
        actionList.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "executeAction");
        actionList.getActionMap().put("executeAction", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { executeSelectedAction(); }
        });
        actionList.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) executeSelectedAction();
            }
        });
        historyList.setBackground(PANEL_LIGHT);
        historyList.setForeground(new Color(218, 226, 237));
        historyList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        historyList.setFixedCellHeight(28);
        historyList.setBorder(new EmptyBorder(5, 5, 5, 5));
        actionTabs.addTab("ACTIONS", new JScrollPane(actionList));
        actionTabs.addTab("ACTION LOG", new JScrollPane(historyList));
        actionTabs.setMinimumSize(new Dimension(330, 145));
        side.add(actionTabs, BorderLayout.CENTER);

        JButton execute = button("Execute Selected", e -> executeSelectedAction());
        JButton clear = button("Clear Selection", e -> clearSelection());
        JButton end = button("End Turn", e -> executeHuman("end"));
        end.setBackground(new Color(143, 66, 71));
        JPanel controls = new JPanel(new GridLayout(2, 2, 7, 7));
        controls.setOpaque(false);
        controls.add(execute);
        controls.add(clear);
        controls.add(end);
        controls.add(new JLabel());

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);
        messageLabel.setForeground(new Color(205, 215, 229));
        messageLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        messageLabel.setVerticalAlignment(SwingConstants.TOP);
        messageLabel.setPreferredSize(new Dimension(320, 34));
        bottom.add(messageLabel, BorderLayout.NORTH);
        bottom.add(controls, BorderLayout.SOUTH);
        side.add(bottom, BorderLayout.SOUTH);
        JScrollPane sideScroll = new JScrollPane(side,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        sideScroll.setPreferredSize(new Dimension(350, 100));
        sideScroll.setBorder(null);
        sideScroll.getViewport().setBackground(PANEL);
        sideScroll.getVerticalScrollBar().setUnitIncrement(20);
        return sideScroll;
    }

    private JComponent buildHand() {
        JPanel area = new JPanel(new BorderLayout(10,4));area.setOpaque(false);
        handExpandButton=button("Hand ▴",e->toggleHandExpansion());
        handExpandButton.setToolTipText("Click to pin your hand open. Escape tucks it away.");
        JPanel toggle=new JPanel(new BorderLayout());toggle.setOpaque(false);toggle.add(handExpandButton,BorderLayout.NORTH);area.add(toggle,BorderLayout.WEST);
        handPanel.setLayout(new BoxLayout(handPanel,BoxLayout.X_AXIS));handPanel.setOpaque(false);
        JScrollPane scroll=new JScrollPane(handPanel,ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setOpaque(false);scroll.getViewport().setOpaque(false);scroll.setBorder(null);scroll.getHorizontalScrollBar().setUnitIncrement(32);
        area.add(scroll);
        handHoverTimer=new javax.swing.Timer(100,e->{
            if(captureMode||state==null||handArea==null||!isShowing()||boardFullScreen||!canAcceptHumanInput()||dragSource!=null)return;
            PointerInfo pointer=MouseInfo.getPointerInfo();if(pointer==null)return;
            Point point=pointer.getLocation();SwingUtilities.convertPointFromScreen(point,handArea);
            boolean inside=handArea.contains(point);
            if(!inside){hoverSuppressed=false;if(handExitTime==0)handExitTime=System.nanoTime();if(!handPinned&&System.nanoTime()-handExitTime>350_000_000L)setHandExpanded(false);}
            else {handExitTime=0;if(!hoverSuppressed)setHandExpanded(true);}
        });
        handHoverTimer.start();return area;
    }

    private void toggleHandExpansion() { handPinned=!handPinned;hoverSuppressed=!handPinned;setHandExpanded(handPinned); }
    private void setHandExpanded(boolean expanded) {
        if(handExpanded==expanded)return;
        handExpanded=expanded;handExpandButton.setText(expanded?"Hand ▾":"Hand ▴");
        if(state!=null)refreshHand();
        if(battlefieldLayers!=null){battlefieldLayers.doLayout();battlefieldLayers.repaint();}
    }

    private void newMatch() {
        MatchChoice choice = chooseMatch();
        if (choice == null && state != null) return;
        if (choice == null) choice = defaultChoice();
        startMatch(choice);
    }

    private void replayMatch() {
        startMatch(lastMatchChoice == null ? defaultChoice() : lastMatchChoice);
    }

    private void startMatch(MatchChoice choice) {
        startMatch(choice, System.nanoTime(), true);
    }

    private void startMatch(MatchChoice choice, long seed, boolean interactiveOpening) {
        lastMatchChoice = choice;
        humanFaction = choice.humanFaction();
        humanAlly = buildForFaction(humanFaction).allyFaction();
        botFaction = choice.botFaction();
        humanCapital = choice.humanCapital();
        boardPanel.setBackgroundCard(humanCapital);
        botCapital = choice.botCapital();
        BoardPosition botCapitalPosition = randomCapitalPosition(seed, 1);
        state = matchFactory.create(seed,
                new DeckBuild("Your Deck", humanFaction, buildForFaction(humanFaction).allyFaction(), humanCapital, deckForFaction(humanFaction)),
                new DeckBuild("Bot", botFaction, null, botCapital, factionDecks.starter(botFaction)),
                choice.humanCapitalPosition(), botCapitalPosition, BoardGeometry.HEX);
        commands = new CommandProcessor(state);
        interaction.reset();
        botRunning = false;
        bot = new BotPlayer(choice.botDifficulty());
        winnerSoundPlayed = false;
        victoryDialogShown = false;
        playerOneBot = choice.playerOneBot();
        lastSystemEvent = state.events().stream().mapToLong(GameEvent::sequence).max().orElse(-1);
        historyModel.clear();
        historyNumber = 0;
        if (interactiveOpening) {
            showCoinFlip(state.startingPlayer());
            runOpeningMulligans();
        } else {
            completeBotMulligan(0);
            completeBotMulligan(1);
            advanceScreenshotToHumanTurn();
        }
        lastSystemEvent = state.events().stream().mapToLong(GameEvent::sequence).max().orElse(-1);
        addHistory("Match", title(humanFaction) + " vs " + title(botFaction)
                + " — Player " + (state.startingPlayer() + 1) + " won the coin flip");
        message("Player " + (state.startingPlayer() + 1)
                + " starts. Player 1 begins at 0 GP; Player 2 begins at 1 GP; Capitals generate 1 GP per turn.");
        refresh();
        if (interactiveOpening && isAutomatedPlayer(state.activePlayer())) SwingUtilities.invokeLater(this::runBotTurn);
    }

    private void advanceScreenshotToHumanTurn() {
        int guard = 0;
        while (state.activePlayer() != 0 && state.phase() != Phase.GAME_OVER && guard++ < 64) {
            bot.takeNextAction(state, commands, state.activePlayer());
        }
        if (state.activePlayer() != 0) {
            throw new IllegalStateException("Screenshot fixture could not reach the human turn");
        }
    }

    private void runOpeningMulligans() {
        if (playerOneBot) completeBotMulligan(0);
        else showHumanMulligan();
        completeBotMulligan(1);
    }

    private void showHumanMulligan() {
        List<MulliganChoice> choices = state.player(0).hand().stream()
                .map(id -> new MulliganChoice(id, state.card(id).orElseThrow().definition())).toList();
        Set<UUID> discarded = new VisualMulliganDialog(choices).choose();
        state.mulligan(0, discarded);
        addHistory("You", "Mulligan — discarded and redrew " + discarded.size());
    }

    private void completeBotMulligan(int playerId) {
        List<CardInstance> cards = state.player(playerId).hand().stream()
                .map(id -> state.card(id).orElseThrow())
                .sorted(Comparator.comparingInt(this::openingKeepScore).reversed())
                .toList();
        Set<UUID> discarded = cards.stream().skip(Math.max(0, cards.size() - 3)).map(CardInstance::instanceId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        state.mulligan(playerId, discarded);
        addHistory("Bot " + (playerId + 1), "Mulligan — discarded and redrew " + discarded.size());
    }

    private int openingKeepScore(CardInstance card) {
        CardDefinition def = card.definition();
        if ((def.type() == CardType.LAND || def.type() == CardType.STRUCTURE) && def.cost() <= 2) return 100 - def.cost();
        if (def.type() == CardType.CHARACTER && def.cost() <= 3) return 80 - def.cost();
        if (def.type() == CardType.SPELL && def.cost() <= 3) return 70 - def.cost();
        return 20 - def.cost();
    }

    private MatchChoice defaultChoice() {
        return new MatchChoice("ZEUS", matchFactory.capitals().forFaction("ZEUS").get(0),
                "POSEIDON", matchFactory.capitals().forFaction("POSEIDON").get(0), false,
                new BoardPosition(1, 0), BotDifficulty.HERO);
    }

    private DeckBuild buildForFaction(String faction) {
        DeckBuild saved = savedDecks.get(faction);
        return saved != null ? saved : new DeckBuild(faction + " Starter", faction, null,
                matchFactory.capitals().forFaction(faction).get(0), factionDecks.starter(faction));
    }

    private List<CardDefinition> deckForFaction(String faction) { return buildForFaction(faction).cards(); }

    private void loadSavedDecks() {
        StarterDeckMigration.archiveRetiredFactionDecks(deckDirectory);
        for (String faction : FactionDecks.FACTIONS) {
            Path file = deckDirectory.resolve(faction.toLowerCase(Locale.ROOT) + ".json");
            if (!Files.exists(file)) continue;
            try {
                DeckBuild build = buildStore.load(file);
                if (!build.primaryFaction().equals(faction)) throw new IllegalArgumentException("Deck primary faction does not match its saved slot");
                build=StarterDeckMigration.upgrade(file,build,matchFactory.pool(),buildStore);
                savedDecks.put(faction, build);
            } catch (RuntimeException exception) {
                JOptionPane.showMessageDialog(this, exception.getMessage() + "\nOriginal file is unchanged. Use the deck builder to create a new build.", "Deck Loading", JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    private void openDeckEditor() {
        openDeckEditor(humanFaction);
    }

    private void openDeckEditor(String faction) {
        DeckBuild edited = new DeckBuilderDialog(this, matchFactory.pool(), matchFactory.capitals(), buildForFaction(faction)).choose();
        if (edited == null) return;
        try {
            Path path = deckDirectory.resolve(edited.primaryFaction().toLowerCase(Locale.ROOT) + ".json");
            buildStore.save(path, edited);
            savedDecks.put(edited.primaryFaction(), edited);
            JOptionPane.showMessageDialog(this, "Deck saved. Start a new match with " + edited.primaryFaction() + " to play it.\n" + path, "Deck Saved", JOptionPane.INFORMATION_MESSAGE);
        } catch (IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Deck Not Saved", JOptionPane.ERROR_MESSAGE);
        }
    }

    private MatchChoice chooseMatch() {
        List<String> factions = List.of("ZEUS", "POSEIDON");
        JComboBox<String> humanFactionBox = new JComboBox<>(factions.toArray(String[]::new));
        JComboBox<String> botFactionBox = new JComboBox<>(factions.toArray(String[]::new));
        humanFactionBox.setSelectedItem(humanFaction);
        botFactionBox.setSelectedItem(botFaction);
        JComboBox<CapitalChoice> humanCapitalBox = new JComboBox<>();
        JComboBox<CapitalChoice> botCapitalBox = new JComboBox<>();
        JComboBox<String> playerOneControl = new JComboBox<>(new String[]{"Human", "Bot (watch match)"});
        JComboBox<BotDifficulty> difficultyBox = new JComboBox<>(BotDifficulty.values());
        difficultyBox.setSelectedItem(gameSettings.botDifficulty == null ? BotDifficulty.HERO : gameSettings.botDifficulty);
        JLabel difficultyDescription = setupDescription();
        Runnable updateDifficulty = () -> {
            BotDifficulty selected = (BotDifficulty) difficultyBox.getSelectedItem();
            difficultyDescription.setText("<html>" + html(selected == null ? "" : selected.description()) + "</html>");
        };
        difficultyBox.addActionListener(e -> updateDifficulty.run());
        updateDifficulty.run();
        JLabel humanStrategy = setupDescription();
        JLabel botStrategy = setupDescription();
        JLabel humanPassive = setupDescription();
        JLabel botPassive = setupDescription();
        CapitalPlacementPicker capitalPlacement = new CapitalPlacementPicker();

        String[] previousFaction={null};
        Runnable update = () -> {
            updateCapitalBox(humanCapitalBox, (String) humanFactionBox.getSelectedItem());
            updateCapitalBox(botCapitalBox, (String) botFactionBox.getSelectedItem());
            DeckBuild saved = buildForFaction((String) humanFactionBox.getSelectedItem());
            if(!Objects.equals(previousFaction[0],saved.primaryFaction()))for (int i=0;i<humanCapitalBox.getItemCount();i++) if (humanCapitalBox.getItemAt(i).card().id().equals(saved.capital().id())) humanCapitalBox.setSelectedIndex(i);
            previousFaction[0]=saved.primaryFaction();
            humanStrategy.setText("<html>" + html(saved.primaryFaction()) + " · Ally: " + html(Objects.toString(saved.allyFaction(), "None")) + "<br>" + saved.cards().size() + " cards · " + html(saved.name()) + "</html>");
            botStrategy.setText(strategyHtml((String) botFactionBox.getSelectedItem()));
            updatePassiveLabel(humanPassive, (CapitalChoice) humanCapitalBox.getSelectedItem());
            updatePassiveLabel(botPassive, (CapitalChoice) botCapitalBox.getSelectedItem());
        };
        humanFactionBox.addActionListener(e -> update.run());
        botFactionBox.addActionListener(e -> update.run());
        humanCapitalBox.addActionListener(e -> updatePassiveLabel(humanPassive,
                (CapitalChoice) humanCapitalBox.getSelectedItem()));
        botCapitalBox.addActionListener(e -> updatePassiveLabel(botPassive,
                (CapitalChoice) botCapitalBox.getSelectedItem()));
        update.run();

        JPanel setup = new JPanel(new GridBagLayout());
        setup.setBackground(PANEL);
        setup.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 8, 4, 8);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        for(JComboBox<?> box : new JComboBox<?>[]{humanFactionBox,botFactionBox,humanCapitalBox,botCapitalBox,playerOneControl,difficultyBox}){
            box.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,16));box.setPreferredSize(new Dimension(380,36));
        }
        JPanel humanIdentity=new JPanel(new BorderLayout(0,6));humanIdentity.setOpaque(false);humanIdentity.add(humanFactionBox,BorderLayout.NORTH);humanIdentity.add(humanStrategy);
        JPanel botIdentity=new JPanel(new BorderLayout(0,6));botIdentity.setOpaque(false);botIdentity.add(botFactionBox,BorderLayout.NORTH);botIdentity.add(botStrategy);
        humanStrategy.setPreferredSize(new Dimension(350,40));botStrategy.setPreferredSize(new Dimension(350,40));
        humanPassive.setPreferredSize(new Dimension(390,100));botPassive.setPreferredSize(new Dimension(390,100));
        addSetupRow(setup,c,0,"YOUR FACTION & DECK",humanIdentity,"OPPONENT",botIdentity);
        addSetupRow(setup,c,1,"YOUR CAPITAL",humanCapitalBox,"BOT CAPITAL",botCapitalBox);
        addSetupRow(setup,c,2,"CAPITAL PASSIVE",humanPassive,"CAPITAL PASSIVE",botPassive);
        JComboBox<InitiativeCoinPanel.Skin> coinChoice=new JComboBox<>(InitiativeCoinPanel.Skin.values());
        coinChoice.setSelectedItem(coinSkin);coinChoice.setPreferredSize(new Dimension(380,36));coinChoice.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,16));
        coinChoice.addActionListener(e->coinSkin=(InitiativeCoinPanel.Skin)coinChoice.getSelectedItem());
        addSetupRow(setup,c,3,"PLAY AS",playerOneControl,"INITIATIVE COIN",coinChoice);
        difficultyDescription.setPreferredSize(new Dimension(380,36));
        addSetupRow(setup,c,4,"BOT DIFFICULTY",difficultyBox,"",difficultyDescription);
        JButton editDecks = button("Deck Builder", e -> { openDeckEditor((String) humanFactionBox.getSelectedItem()); previousFaction[0]=null; update.run(); });
        JPanel pages=new JPanel(new CardLayout());pages.setBackground(PANEL);
        JScrollPane settings=new JScrollPane(setup);settings.setBorder(null);settings.getVerticalScrollBar().setUnitIncrement(24);
        pages.add(settings,"settings");
        JPanel placement=new JPanel(new BorderLayout(12,12));placement.setBackground(PANEL);
        JLabel instruction=new JLabel("<html><b>Choose a blue hex on the right.</b> Your opponent's Capital stays secret until the match starts.</html>");
        instruction.setForeground(Color.WHITE);instruction.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,16));
        placement.add(instruction,BorderLayout.NORTH);placement.add(capitalPlacement);
        pages.add(placement,"placement");
        JDialog dialog=new JDialog(this,"Prepare your conquest",true);dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JPanel content=new JPanel(new BorderLayout(16,16));content.setBackground(PANEL);content.setBorder(new EmptyBorder(20,24,20,24));
        JLabel heading=new JLabel("Prepare your conquest");heading.setFont(new Font(Font.SERIF,Font.BOLD,28));heading.setForeground(GOLD);content.add(heading,BorderLayout.NORTH);content.add(pages);
        JPanel footer=new JPanel(new FlowLayout(FlowLayout.RIGHT,12,0));footer.setOpaque(false);
        JButton cancel=button("Cancel",e->dialog.dispose()),previous=button("Back",e->{}),advance=button("Choose placement →",e->{});
        previous.setVisible(false);footer.add(editDecks);footer.add(cancel);footer.add(previous);footer.add(advance);content.add(footer,BorderLayout.SOUTH);
        final boolean[] placing={false},accepted={false};
        previous.addActionListener(e->{placing[0]=false;editDecks.setVisible(true);((CardLayout)pages.getLayout()).show(pages,"settings");heading.setText("Prepare your conquest");previous.setVisible(false);advance.setText("Choose placement →");});
        advance.addActionListener(e->{if(!placing[0]){placing[0]=true;editDecks.setVisible(false);((CardLayout)pages.getLayout()).show(pages,"placement");heading.setText("Place your Capital");previous.setVisible(true);advance.setText("Begin conquest");}else{accepted[0]=true;dialog.dispose();}});
        dialog.setContentPane(content);
        Rectangle usable=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        dialog.setSize(Math.min(1040,usable.width-40),Math.min(660,usable.height-60));dialog.setLocationRelativeTo(this);
        if(setupCaptureDirectory!=null){
            dialog.setModal(false);dialog.setVisible(true);dialog.validate();
            captureComponent(dialog.getRootPane(),setupCaptureDirectory.resolve("opening-menu.png"));
            advance.doClick();dialog.validate();
            capitalPlacement.verifyChoices();
            captureComponent(dialog.getRootPane(),setupCaptureDirectory.resolve("capital-placement.png"));dialog.dispose();return null;
        }
        dialog.setVisible(true);
        if(!accepted[0])return null;
        CapitalChoice selectedHuman = (CapitalChoice) humanCapitalBox.getSelectedItem();
        CapitalChoice selectedBot = (CapitalChoice) botCapitalBox.getSelectedItem();
        BotDifficulty selectedDifficulty = (BotDifficulty) difficultyBox.getSelectedItem();
        if (selectedDifficulty == null) selectedDifficulty = BotDifficulty.HERO;
        gameSettings.botDifficulty = selectedDifficulty;
        gameSettings.save();
        return new MatchChoice((String) humanFactionBox.getSelectedItem(), selectedHuman.card(),
                (String) botFactionBox.getSelectedItem(), selectedBot.card(),
                playerOneControl.getSelectedIndex() == 1, capitalPlacement.selected(), selectedDifficulty);
    }

    void prepareReactionReview(Path directory) {
        playerOneBot=false;
        for(UUID id:List.copyOf(state.player(0).hand())){state.player(0).removeFromHand(id);state.card(id).orElseThrow().moveTo(Zone.DISCARD);}
        for(BoardPosition pos:state.board().positions())while(!state.board().isEmpty(pos))state.card(state.board().pop(pos)).orElseThrow().moveTo(Zone.DISCARD);
        fixtureCard("zeus_stormgate_adept",new BoardPosition(1,0),0);
        fixtureCard("poseidon_keyword_maelstrom_bulwark",new BoardPosition(1,4),1);
        fixtureCard("zeus_cloudline_courier",new BoardPosition(2,4),1);
        fixtureCard("zeus_land_thunderstep_plateau",new BoardPosition(0,0),0);
        for(String id:List.of("zeus_chain_lightning","zeus_stormcharge","zeus_windstep_protocol")){
            CardInstance card=new CardInstance(UUID.randomUUID(),matchFactory.pool().require(id),0,Zone.HAND);state.register(card);state.player(0).addToHand(card.instanceId());
        }
        state.player(0).restoreGp(10);
        if(state.activePlayer()==0)new GameEngine().apply(state,new GameAction.EndTurn(0));
        refresh();
        for(String id:List.of("poseidon_reefline_defender","zeus_keyword_stormgate_sentinel","zeus_land_thunderstep_plateau","zeus_apex_worldstorm_spire","zeus_chain_lightning")){
            CardInstance card=new CardInstance(UUID.randomUUID(),matchFactory.pool().require(id),0,Zone.HAND);state.register(card);
            CardInspectionPanel panel=new CardInspectionPanel(state,card);panel.setSize(800,520);layoutTree(panel);captureComponent(panel,directory.resolve("full-card-"+id+".png"));
        }
        CardInstance capital=new CardInstance(UUID.randomUUID(),matchFactory.capitals().require("zeus_capital_cloud_throne"),0,Zone.BATTLEFIELD);capital.addDamage(3);state.register(capital);
        CardInspectionPanel panel=new CardInspectionPanel(state,capital);panel.setSize(800,520);layoutTree(panel);captureComponent(panel,directory.resolve("full-card-capital.png"));
        var dialog=new VisualReactionDialog(0,hints.spellActionsForPlayer(state,0));
        dialog.addNotify();dialog.getRootPane().setSize(dialog.getWidth()-16,dialog.getHeight()-40);layoutTree(dialog.getRootPane());captureComponent((JComponent)dialog.getContentPane(),directory.resolve("reaction-before-selection.png"));
        String before=captureStateFingerprint();int gold=state.player(0).currentGp();
        dialog.selectSpell(0);dialog.previewTarget(new BoardPosition(1,4));
        layoutTree(dialog.getRootPane());captureComponent((JComponent)dialog.getContentPane(),directory.resolve("reaction-threshold-preview.png"));
        if(!before.equals(captureStateFingerprint())||gold!=state.player(0).currentGp())throw new IllegalStateException("Preview mutated the match");
        dialog.chooseTarget(new BoardPosition(2,4));
        if(dialog.result==null || !commands.execute(dialog.result).startsWith("OK:"))throw new IllegalStateException("Reaction UI did not emit an executable command");
        if(!state.board().isEmpty(new BoardPosition(2,4)) || state.player(0).currentGp()!=gold-3 || state.activePlayer()!=1)throw new IllegalStateException("Reaction did not resolve on opponent turn");
        dialog.dispose();
    }
    void beginReactionReview(){runBotTurn();}
    JDialog visibleReactionReview(){for(Window w:getOwnedWindows())if(w instanceof VisualReactionDialog d && d.isVisible())return d;return null;}

    void captureOpeningScreens(Path directory) {
        setupCaptureDirectory=directory;try{chooseMatch();}finally{setupCaptureDirectory=null;}
    }
    private void captureComponent(JComponent component,Path path) {
        try{Files.createDirectories(path.toAbsolutePath().getParent());BufferedImage image=new BufferedImage(component.getWidth(),component.getHeight(),BufferedImage.TYPE_INT_ARGB);Graphics2D graphics=image.createGraphics();component.printAll(graphics);graphics.dispose();ImageIO.write(image,"png",path.toFile());}catch(java.io.IOException e){throw new IllegalStateException(e);}
    }

    private BoardPosition randomCapitalPosition(long seed, int player) {
        Random random = new Random(seed ^ 0xB07CA917L);
        return new BoardPosition(random.nextInt(BoardPosition.WIDTH), player == 0
                ? random.nextInt(BoardPosition.HEIGHT / 2)
                : BoardPosition.HEIGHT / 2 + random.nextInt(BoardPosition.HEIGHT / 2));
    }

    private void showCoinFlip(int winner) {
        InitiativeCoinPanel coin = new InitiativeCoinPanel(winner,coinSkin);
        coin.setPreferredSize(new Dimension(460, 410));
        JDialog dialog = new JDialog(this, "Determine First Player", true);
        dialog.add(coin);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        long started = System.nanoTime();
        javax.swing.Timer animation = new javax.swing.Timer(16, e -> {
            long elapsed = System.nanoTime() - started;
            coin.setProgress((double) elapsed / InitiativeCoinPanel.DURATION_NANOS);
            if (elapsed >= InitiativeCoinPanel.DURATION_NANOS + 950_000_000L) dialog.dispose();
        });
        animation.setCoalesce(true);
        animation.start();
        try { dialog.setVisible(true); }
        finally { animation.stop(); dialog.dispose(); }
    }

    private void addSetupRow(JPanel panel, GridBagConstraints c, int row,
                             String leftTitle, JComponent left, String rightTitle, JComponent right) {
        c.gridy = row * 2;
        c.gridx = 0;
        panel.add(section(leftTitle, new Color(87, 203, 234)), c);
        c.gridx = 1;
        panel.add(section(rightTitle, new Color(239, 106, 122)), c);
        c.gridy = row * 2 + 1;
        c.gridx = 0;
        panel.add(left, c);
        c.gridx = 1;
        panel.add(right, c);
    }

    private JLabel setupDescription() {
        JLabel label = new JLabel();
        label.setForeground(Color.WHITE);
        label.setPreferredSize(new Dimension(330, 58));
        label.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));
        return label;
    }

    private void updateCapitalBox(JComboBox<CapitalChoice> box, String faction) {
        Object previous = box.getSelectedItem();
        box.removeAllItems();
        for (CardDefinition capital : matchFactory.capitals().forFaction(faction)) {
            box.addItem(new CapitalChoice(capital));
        }
        if (previous instanceof CapitalChoice old) {
            for (int i = 0; i < box.getItemCount(); i++) {
                if (box.getItemAt(i).card().id().equals(old.card().id())) box.setSelectedIndex(i);
            }
        }
    }

    private void updatePassiveLabel(JLabel label, CapitalChoice choice) {
        label.setText(choice == null ? "" : "<html><div style='width:240px'>" + html(passiveRules.description(choice.card())) + "</div></html>");
        label.setIcon(choice==null?null:CardArtFactory.iconFor(choice.card(),72,60));label.setIconTextGap(12);
    }

    private String strategyHtml(String faction) {
        return "<html><b>" + title(faction) + "</b> — "
                + FactionDecks.PRIMARY_TYPES.get(faction) + " / " + FactionDecks.SECONDARY_TYPES.get(faction)
                + "<br>Keywords: " + FactionDecks.PRIMARY_KEYWORDS.get(faction)
                + " / " + FactionDecks.SECONDARY_KEYWORDS.get(faction) + "</html>";
    }

    private void selectHand(int index) {
        if (!canAcceptHumanInput()) return;
        interaction.toggleHand(index);
        handPinned=false;hoverSuppressed=true;setHandExpanded(false);
        boardPanel.inspect(state.card(state.player(localPlayer()).hand().get(index)).orElseThrow().definition());
        refresh();
    }

    private void selectCell(BoardPosition position) {
        if (!canAcceptHumanInput()) return;
        interaction.toggleBoard(position);
        boardPanel.inspect(state.board().topAt(position).flatMap(state::card).map(CardInstance::definition).orElse(null));
        refresh();
    }

    private void clearSelection() {
        if (!canAcceptHumanInput()) return;
        interaction.clearSelection();
        boardPanel.inspect(null);
        refresh();
    }

    private void executeSelectedAction() {
        ActionOption option = actionList.getSelectedValue();
        if (option == null) {
            message("Choose an action from the list. Double-click also executes it.");
            return;
        }
        if (option.command().isBlank()) {
            message(option.label() + ". Legal destinations glow on the battlefield.");
            return;
        }
        executeHuman(option.command());
    }

    private void executeHuman(String command) {
        if (!canAcceptHumanInput() || !confirmOpportunityRisk(command)) return;
        if (netMode()) {
            // Online: the server is authoritative. Send the command; the confirmed
            // snapshot arrives via onStateUpdate and drives the animation.
            handPinned = false;
            hoverSuppressed = true;
            setHandExpanded(false);
            interaction.beginResolution();
            netPendingCommand = command;
            netSession.client().sendCommand(command);
            refresh();
            return;
        }
        handPinned = false;
        hoverSuppressed = true;
        setHandExpanded(false);
        interaction.beginResolution();
        PresentationSnapshot.Frame before = PresentationSnapshot.capture(state);
        String result = commands.execute(command);
        showResolution(PresentationSnapshot.between(command, before, state));
        addHistory("You", describe(command));
        message(result);
        if (state.phase() == Phase.GAME_OVER) interaction.markGameOver();
        else if (isAutomatedPlayer(state.activePlayer())) runBotTurn();
        else interaction.finishResolution();
        refresh();
    }

    private boolean canAcceptHumanInput() {
        if (netMode() && netPendingCommand != null) return false; // awaiting server confirmation
        if (netMode() && (netMulliganOpen || netMulliganWaiting)) return false; // mulligan not resolved
        if (netMode() && netReactionWaiting) return false; // opponent is reacting
        return state != null && !playerOneBot && !botRunning && state.activePlayer() == localPlayer()
                && state.phase() != Phase.GAME_OVER && interaction.acceptsHumanInput();
    }

    private void runBotTurn() {
        if (botRunning) return;
        var match = state;
        BotPresentationPacer pacer = new BotPresentationPacer();
        boolean[] reactionOffered = {false};
        botRunning = true;
        interaction.beginBotTurn();
        refresh();
        javax.swing.Timer timer = new javax.swing.Timer(60, null);
        timer.addActionListener(e -> {
            if (state != match || !isDisplayable()) { timer.stop(); return; }
            if (!pacer.ready(System.nanoTime(), presentationQueue.isPlaying())) return;
            if (state.phase() == Phase.GAME_OVER || !isAutomatedPlayer(state.activePlayer())) {
                timer.stop();
                botRunning = false;
                if (state.phase() == Phase.GAME_OVER) interaction.markGameOver();
                else interaction.finishBotTurn();
                refresh();
                return;
            }
            int active = state.activePlayer();
            if (!reactionOffered[0]) {
                reactionOffered[0] = true;
                // A modal Swing dialog pumps timer events; explicitly suspend this timer
                // so targets and hand indices cannot change while the player reads.
                timer.stop();
                try { offerReaction(active); }
                finally { if(state==match && isDisplayable())timer.start(); }
                if (presentationQueue.isPlaying()) return;
            }
            if (state.phase() == Phase.GAME_OVER || state.activePlayer() != active) return;
            reactionOffered[0] = false;
            PresentationSnapshot.Frame before = PresentationSnapshot.capture(state);
            BotPlayer.Decision decision = bot.takeNextAction(state, commands, active);
            pacer.acted(System.nanoTime());
            showResolution(PresentationSnapshot.between(decision.command(), before, state));
            addHistory(active == 0 ? "Bot 1" : "Bot 2", describe(decision.command()));
            message((active == 0 ? "Bot 1: " : "Bot 2: ") + describe(decision.command()) + " — " + decision.result());
            refresh();
        });
        timer.start();
    }

    private boolean isAutomatedPlayer(int player) {
        if (netMode()) return false; // online: both seats are human
        return player == 1 || playerOneBot;
    }

    private void offerReaction(int active) {
        int reacting = 1 - active;
        if (isAutomatedPlayer(reacting)) {
            PresentationSnapshot.Frame before = PresentationSnapshot.capture(state);
            BotPlayer.Decision reaction = bot.react(state, commands, reacting);
            if (reaction != null) {
                showResolution(PresentationSnapshot.between(reaction.command(), before, state));
                addHistory(reacting == 0 ? "Bot 1" : "Bot 2", "Reaction — " + describe(reaction.command()));
            }
            return;
        }
        List<String> reactions = hints.spellActionsForPlayer(state, reacting);
        if (reactions.isEmpty()) return;
        interaction.beginReaction();
        try {
            String chosen = new VisualReactionDialog(reacting, reactions).choose();
            if (chosen != null && !chosen.isBlank()) {
                PresentationSnapshot.Frame before = PresentationSnapshot.capture(state);
                message(commands.execute(chosen));
                showResolution(PresentationSnapshot.between(chosen, before, state));
                addHistory("You", "Reaction — " + describe(chosen));
                refresh();
            }
        } finally {
            interaction.finishReaction();
        }
    }

    private void refresh() {
        syncSystemEvents();
        turnLabel.setText("Turn " + state.turnNumber() + " • " + phaseText());
        int currentTurn = state.turnNumber();
        int currentPlayer = state.activePlayer();
        if (currentTurn != lastBannerTurn || currentPlayer != lastBannerPlayer) {
            lastBannerTurn = currentTurn;
            lastBannerPlayer = currentPlayer;
            String banner = currentPlayer == localPlayer()
                    ? (playerOneBot && !netMode() ? "BOT 1'S TURN" : "YOUR TURN")
                    : "ENEMY TURN";
            combatOverlay.showBanner(banner, new Color(240, 191, 73));
            SoundEffects.play(currentPlayer == localPlayer() ? SoundEffects.Cue.YOUR_TURN : SoundEffects.Cue.ENEMY_TURN);
        }
        PlayerState human = state.player(localPlayer());
        PlayerState enemy = state.player(remotePlayer());
        String foeName = netMode() ? opponentName : "BOT 2";
        humanLabel.setText("<html><b>" + (playerOneBot && !netMode() ? "BOT 1" : "YOU") + " • " + humanFaction
                + (humanAlly == null ? "" : " + " + humanAlly)
                + " • " + html(humanCapital.name()) + "</b><br>GP held " + human.currentGp() + "  (income +" + state.gpIncomePerTurn(localPlayer()) + "/turn)"
                + " • Deck " + human.deck().size() + " • Discard " + human.discard().size() + "</html>");
        botLabel.setText("<html><b>" + foeName + " • " + botFaction + " • " + html(botCapital.name()) + "</b><br>GP held " + enemy.currentGp()
                + "  (+" + state.gpIncomePerTurn(remotePlayer()) + "/turn) • Hand " + enemy.hand().size()
                + " • Deck " + enemy.deck().size() + "</html>");
        humanLabel.setToolTipText(humanLabel.getText());botLabel.setToolTipText(botLabel.getText());
        humanLabel.setText("YOU · " + humanFaction + (humanAlly==null?"":" + "+humanAlly) + "    GP " + human.currentGp() + " (+" + state.gpIncomePerTurn(localPlayer()) + ")    Deck " + human.deck().size());
        botLabel.setText(foeName + " · " + botFaction + "    GP " + enemy.currentGp() + " (+" + state.gpIncomePerTurn(remotePlayer()) + ")    Hand " + enemy.hand().size() + " · Deck " + enemy.deck().size());
        humanLabel.setIcon(null);botLabel.setIcon(null);
        endTurnButton.setEnabled(canAcceptHumanInput() && state.phase() == Phase.PLAY);
        refreshBoard();
        refreshHand();
        refreshActions();
        if (state.phase() == Phase.GAME_OVER && !presentationQueue.isPlaying()) {
            if (netMode()) showNetGameOver();
            else showWinner();
        }
    }

    private void refreshBoard() {
        for (BoardPosition position : state.board().positions()) {
            JButton cell = boardButtons.get(position);
            Optional<UUID> topId = maskedBoardCells.contains(position)
                    ? Optional.empty() : state.board().topAt(position);
            Color base = position.isOnPlayerSide(localPlayer()) ? HUMAN_PLOT : BOT_PLOT;
            Intent intent = destinationIntent(position);
            boolean selected = Objects.equals(interaction.boardPosition(), position);
            Color surface = intent == null ? base : blend(base, intent.color, TARGET_TINT);
            cell.setBackground(surface);
            cell.setForeground(Color.WHITE);
            int tilePadding = 1;
            Color outline = selected ? SELECTED : intent != null ? intent.color : base.brighter();
            int outlineWidth = selected || intent != null ? 3 : IDLE_BORDER;
            cell.putClientProperty("outline", outline);
            cell.putClientProperty("outlineWidth", outlineWidth);
            cell.putClientProperty("card", null);
            cell.putClientProperty("height", TerrainRules.height(state,position));
            cell.putClientProperty("badge", intent == null ? "" : intent.label);
            cell.putClientProperty("pulse", intent != null);
            cell.setBorder(new CompoundBorder(new BevelBorder(BevelBorder.RAISED,
                    surface.brighter(), surface.brighter(), surface.darker(), surface.darker()), new CompoundBorder(
                    new LineBorder(outline, outlineWidth, true),
                    new EmptyBorder(tilePadding, tilePadding, tilePadding, tilePadding))));
            if (topId.isEmpty()) {
                cell.setIcon(null);
                cell.setHorizontalAlignment(SwingConstants.LEFT);
                cell.setVerticalAlignment(SwingConstants.TOP);
                cell.setText("<html><font color='#78899d'>" + position.x() + "," + position.y() + "</font>"
                        + (intent == null ? "" : "<br><b><font color='" + intent.hex + "'>◆ " + intent.label + " HERE</font></b>") + "</html>");
                cell.setToolTipText("Empty cell " + position.x() + "," + position.y() + " — drop a legal card or unit here");
                continue;
            }
            CardInstance card = state.card(topId.orElseThrow()).orElseThrow();
            CardDefinition def = card.definition();
            cell.putClientProperty("card", def);
            cell.putClientProperty("owner", card.owner());
            cell.putClientProperty("stack", state.board().stackAt(position).size());
            cell.putClientProperty("stats", def.type() == CardType.CHARACTER ? "A " + new GameEngine().effectiveAttack(state, card) + " / D " + card.defenseRemaining() : "HP " + Math.max(0, def.hitPoints()-card.damage()));
            Color occupiedSurface = blend(base, factionColor(def.faction()), .42f);
            cell.setBackground(intent == null ? occupiedSurface : blend(occupiedSurface, intent.color, TARGET_TINT));
            cell.setIcon(CardArtFactory.iconFor(def, 64, 32));
            cell.setHorizontalTextPosition(SwingConstants.RIGHT);
            cell.setVerticalTextPosition(SwingConstants.CENTER);
            cell.setIconTextGap(7);
            cell.setHorizontalAlignment(SwingConstants.LEFT);
            cell.setVerticalAlignment(SwingConstants.CENTER);
            int stack = state.board().stackAt(position).size();
            String stats = def.type() == CardType.CHARACTER
                    ? "A " + new GameEngine().effectiveAttack(state, card) + "  D " + card.defenseRemaining()
                    + "/" + card.effectiveDefense() + "  M " + def.movement() + "  R " + def.range()
                    + (card.combatDamage() > 0 ? "  <font color='#ff9b73'>MARK " + card.combatDamage() + "</font>" : "")
                    : "HP " + Math.max(0, def.hitPoints() - card.damage()) + "/" + def.hitPoints()
                    + (card.damage() > 0 ? "  <font color='#ff9b73'>DMG " + card.damage() + "</font>" : "");
            EffectBadge badge = effectBadges.get(position);
            if (badge != null && intent == null) cell.putClientProperty("badge", badge.text());
            cell.setText("<html><b>" + html(compactName(def.name(), 18)) + "</b>"
                    + " <font size='-2' color='#aebdd0'>P" + (card.owner() + 1) + (stack > 1 ? " • S" + stack : "") + "</font>"
                    + "<br><font size='-2'>" + stats + "</font>"
                    + (badge == null ? "" : " <b><font color='" + badge.color() + "'>" + html(badge.text()) + "</font></b>")
                    + (intent == null ? "" : " <b><font color='" + intent.hex + "'>" + intent.label + "</font></b>") + "</html>");
            cell.setToolTipText("<html><b>" + html(def.name()) + "</b><br>" + stats
                    + "<br>Height "+TerrainRules.height(state,position)+" · Stack "+stack
                    + (badge == null ? "" : "<br>" + html(badge.text())) + "<br>" + html(keywordLine(def))
                    + (developmentText(def).isBlank() ? "" : "<br>" + html(developmentText(def)))
                    + (abilityLine(def).isBlank() ? "" : "<br>" + html(abilityLine(def)))
                    + "<br>Click a highlighted cell or drag; right-click to inspect stack.</html>");
        }
    }

    private String compactType(CardType type) {
        return switch (type) {
            case CHARACTER -> "CHAR";
            case STRUCTURE -> "STRUCT";
            case CAPITAL -> "CAPITAL";
            default -> type.name();
        };
    }

    private static final class BattlefieldCell extends JButton {
        BattlefieldCell() { setContentAreaFilled(false);setOpaque(false); }
        private Polygon shape() {
            int w=getWidth()-1,h=getHeight()-1;
            return new Polygon(new int[]{w/2,w,w,w/2,0,0},new int[]{0,h/4,3*h/4,h,3*h/4,h/4},6);
        }
        @Override public boolean contains(int x,int y){return shape().contains(x,y);}
        @Override protected void paintBorder(Graphics g) { }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g=(Graphics2D)graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            Polygon hex=shape();g.clip(hex);
            g.setPaint(new GradientPaint(0,0,getBackground().brighter(),getWidth(),getHeight(),getBackground()));g.fill(hex);
            CardDefinition card=(CardDefinition)getClientProperty("card");
            int width=getWidth(),height=getHeight();
            if(card!=null){
                // High-resolution token art, downscaled bicubically so tokens stay
                // crisp on large hexes and high-DPI displays.
                Image image=CardArtFactory.boardTokenIcon(card).getImage();
                g.drawImage(image,0,0,width,height,null);
                int fontSize = Math.max(10, Math.min(14, height / 8));
                // Owner chip: dark band with high-contrast owner coloring.
                g.setColor(new Color(5, 12, 20, 205));g.fillRect(0, (int)(height*.19), width, fontSize + 7);
                int viewer=((Number)Objects.requireNonNullElse(getClientProperty("viewer"),0)).intValue();
                boolean own=((Integer)getClientProperty("owner"))==viewer;
                g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,fontSize));
                g.setColor(own?new Color(130,231,255):new Color(255,157,160));
                centered(g,"P"+(own?1:2)+" · H"+Objects.toString(getClientProperty("height"),"0"),(int)(height*.19)+fontSize + 2);
                // Bottom scrim: a gradient that keeps the upper art fully visible
                // and fades to a near-opaque band behind the name and stats.
                int scrimTop=(int)(height*.58);
                g.setPaint(new GradientPaint(0,scrimTop,new Color(4,10,18,0),0,height,new Color(4,10,18,228)));
                g.fillRect(0,scrimTop,width,height-scrimTop);
                // Name and stats with drop shadows for contrast over bright art.
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.min(16, Math.max(12, width/11))));
                shadowed(g,card.name(),(int)(height*.74));
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.min(14, Math.max(11, width/13))));
                shadowed(g,Objects.toString(getClientProperty("stats"),""),(int)(height*.88));
            }else{
                g.setColor(new Color(182,209,218,100));BoardPosition p=(BoardPosition)getClientProperty("position");
                g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,11));centered(g,p.x()+","+p.y(),height/2);
            }
            String badge=(String)getClientProperty("badge");
            if(badge!=null&&!badge.isEmpty()){
                // Status pill below the owner chip, clear of the name/stats band.
                int pillH=Math.max(16,(int)(height*.11));
                g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,Math.max(9,Math.min(11,height/10))));
                String text=HexText.status(badge);
                FontMetrics metrics=g.getFontMetrics();
                int pillW=metrics.stringWidth(text)+18;
                int pillX=(width-pillW)/2,pillY=(int)(height*.30);
                g.setColor(new Color(120,30,36,225));
                g.fillRoundRect(pillX,pillY,pillW,pillH,pillH/2,pillH/2);
                g.setColor(new Color(252,226,137));
                g.drawString(text,pillX+9,pillY+pillH-metrics.getDescent()-3);
            }
            g.setClip(null);g.setStroke(new BasicStroke(isFocusOwner()?3:((Number)getClientProperty("outlineWidth")).floatValue()));
            Color outline = isFocusOwner() ? Color.WHITE : (Color) getClientProperty("outline");
            if (Boolean.TRUE.equals(getClientProperty("pulse"))) {
                // Gentle breathing glow on legal drop hexes while selected.
                java.awt.Container parent = getParent();
                Object phase = parent instanceof JComponent
                        ? ((JComponent) parent).getClientProperty("pulsePhase") : null;
                float wave = phase instanceof Number
                        ? (float) Math.sin(((Number) phase).floatValue()) : 0f;
                float glow = .55f + .45f * wave;
                outline = new Color(outline.getRed(), outline.getGreen(), outline.getBlue(),
                        Math.round(255 * Math.max(.2f, glow)));
            }
            g.setColor(outline);g.draw(hex);g.dispose();
        }
        private void centered(Graphics2D g,String text,int y){
            FontMetrics metrics = g.getFontMetrics();
            String fitted = HexText.fit(text, metrics, HexText.lineWidth(getWidth(),getHeight(),y-metrics.getAscent(),y+metrics.getDescent()));
            g.drawString(fitted,(getWidth()-metrics.stringWidth(fitted))/2,y);
        }
        private void shadowed(Graphics2D g,String text,int y){
            FontMetrics metrics = g.getFontMetrics();
            String fitted = HexText.fit(text, metrics, HexText.lineWidth(getWidth(),getHeight(),y-metrics.getAscent(),y+metrics.getDescent()));
            int x=(getWidth()-metrics.stringWidth(fitted))/2;
            g.setColor(new Color(0,0,0,200));g.drawString(fitted,x+1,y+1);
            g.setColor(Color.WHITE);g.drawString(fitted,x,y);
        }
    }

    private JButton auxiliaryHex(BoardPosition position) {
        JButton cell = new BattlefieldCell();
        cell.putClientProperty("position", position);cell.putClientProperty("height", TerrainRules.height(state,position));cell.putClientProperty("outline", PANEL_LIGHT);cell.putClientProperty("outlineWidth",1);
        cell.putClientProperty("badge", "");cell.setBackground(position.isOnPlayerSide(0)?HUMAN_PLOT:BOT_PLOT);
        state.board().topAt(position).flatMap(state::card).ifPresent(card -> {
            CardDefinition def=card.definition();cell.putClientProperty("card",def);cell.putClientProperty("owner",card.owner());
            cell.putClientProperty("stack",state.board().stackAt(position).size());
            cell.putClientProperty("stats",def.type()==CardType.CHARACTER?"A "+new GameEngine().effectiveAttack(state,card)+" / D "+card.defenseRemaining():"HP "+Math.max(0,def.hitPoints()-card.damage()));
            cell.setToolTipText(def.name());
        });return cell;
    }

    private String compactName(String name, int maximum) {
        return name.length() <= maximum ? name : name.substring(0, maximum - 1) + "…";
    }

    private void refreshHand() {
        handPanel.removeAll();
        handButtons.clear();
        List<UUID> hand = state.player(localPlayer()).hand();
        for (int index = 0; index < hand.size(); index++) {
            CardInstance card = state.card(hand.get(index)).orElseThrow();
            CardDefinition def = card.definition();
            int cardWidth = handExpanded ? 185 : 156;
            int cardHeight = handExpanded ? 202 : 40;
            int artWidth = handExpanded ? 169 : 30;
            int artHeight = handExpanded ? 105 : 28;
            JButton tile = new HoverLiftButton(handExpanded ? handCardHtml(def) : "<html>"+html(compactName(def.name(),18))+"<br>"+html(playRequirement(def))+"</html>", CardArtFactory.iconFor(def, artWidth, artHeight), gameSettings);
            Dimension cardSize = new Dimension(cardWidth, cardHeight);
            tile.setPreferredSize(cardSize);
            tile.setMaximumSize(cardSize);
            tile.setMinimumSize(cardSize);
            tile.setVerticalAlignment(SwingConstants.TOP);
            tile.setHorizontalAlignment(SwingConstants.CENTER);
            tile.setHorizontalTextPosition(SwingConstants.CENTER);
            tile.setVerticalTextPosition(handExpanded ? SwingConstants.BOTTOM : SwingConstants.CENTER);
            tile.setHorizontalTextPosition(handExpanded ? SwingConstants.CENTER : SwingConstants.RIGHT);
            tile.setForeground(Color.WHITE);
            boolean selected = Objects.equals(interaction.handIndex(), index);
            Color handSurface = blend(PANEL_LIGHT, factionColor(def.faction()), .36f);
            tile.setBackground(selected ? blend(handSurface, SELECTED, SELECTED_TINT) : handSurface);
            tile.setFocusPainted(false);
            tile.setToolTipText("<html><b>" + html(def.name()) + "</b><br>"
                    + html(playRequirement(def)) + " • " + html(keywordLine(def))
                    + (developmentText(def).isBlank() ? "" : "<br>" + html(developmentText(def)))
                    + (abilityLine(def).isBlank() ? "" : "<br>" + html(abilityLine(def))) + "</html>");
            tile.setBorder(new CompoundBorder(
                    new LineBorder(selected ? SELECTED : factionColor(def.faction()),
                            selected ? 3 : IDLE_BORDER, true),
                    new EmptyBorder(3, 3, 3, 3)));
            final int selectedIndex = index;
            tile.addMouseListener(dragListener(new DragSource(selectedIndex, null)));
            tile.setEnabled(canAcceptHumanInput());
            handButtons.add(tile);
            handPanel.add(tile);
            handPanel.add(Box.createHorizontalStrut(8));
        }
        handPanel.revalidate();
        handPanel.repaint();
    }

    private void refreshActions() {
        actionModel.clear();
        if (!canAcceptHumanInput()) return;
        if (!interaction.hasSelection()) {
            actionModel.addElement(new ActionOption("Select a card or unit", ""));
            return;
        }
        List<String> legal = hints.forActivePlayer(state, new GameEngine());
        legal.stream().filter(this::matchesSelection)
                .map(command -> new ActionOption(contextualActionLabel(command), command))
                .forEach(actionModel::addElement);
        if (actionModel.isEmpty()) {
            actionModel.addElement(new ActionOption("No legal action for this selection", ""));
        }
    }

    private String contextualActionLabel(String command) {
        String[] p = command.split("\\s+");
        return switch (p[0]) {
            case "play" -> "PLACE ON TOP  →  (" + p[2] + ", " + p[3] + ")";
            case "burrow" -> "BURROW BELOW  →  (" + p[2] + ", " + p[3] + ")";
            case "move" -> "MOVE  →  (" + p[3] + ", " + p[4] + ")";
            case "blink" -> "BLINK  →  (" + p[3] + ", " + p[4] + ")";
            case "attack" -> "ATTACK  →  (" + p[3] + ", " + p[4] + ")";
            case "activate" -> "ACTIVATE THIS CARD";
            case "cast" -> "CAST  →  (" + p[2] + ", " + p[3] + ")";
            default -> describe(command);
        };
    }

    private boolean matchesSelection(String command) {
        if (interaction.handIndex() != null) {
            return command.matches("(play|burrow|cast) " + interaction.handIndex() + "( .*)?");
        }
        if (interaction.boardPosition() != null) {
            String xy = interaction.boardPosition().x() + " " + interaction.boardPosition().y();
            return command.matches("(move|blink|attack) " + xy + " .*")
                    || command.equals("activate " + xy)
                    || command.matches("cast \\d+ " + xy + "( .*)?");
        }
        return true;
    }

    private MouseAdapter dragListener(DragSource source) {
        return new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (SwingUtilities.isRightMouseButton(event)) {
                    if (source.position() != null) showStackContextMenu(event, source.position());
                    else if (source.handIndex() != null) showHandCardContextMenu(event, source.handIndex());
                    return;
                }
                if (!canAcceptHumanInput()) return;
                if (source.position() != null && interaction.hasSelection()
                        && !Objects.equals(interaction.boardPosition(), source.position())
                        && isLegalDestination(source.position())) {
                    DragSource selectedSource = new DragSource(interaction.handIndex(), interaction.boardPosition());
                    dragSource = null;
                    interaction.finishDrag();
                    executeDrop(selectedSource, source.position());
                    return;
                }
                dragSource = source;
                interaction.beginDrag(source.handIndex(), source.position());
                refreshBoard();
                refreshActions();
            }

            @Override public void mouseReleased(MouseEvent event) {
                if (SwingUtilities.isRightMouseButton(event) || dragSource == null) return;
                Point boardPoint = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), boardPanel);
                Point handPoint = SwingUtilities.convertPoint(event.getComponent(),event.getPoint(),handArea);
                boolean overHand = handArea.isVisible() && handArea.contains(handPoint);
                BoardPosition destination = boardButtons.entrySet().stream()
                        .filter(entry -> !overHand && entry.getValue().contains(boardPoint.x-entry.getValue().getX(), boardPoint.y-entry.getValue().getY()))
                        .map(Map.Entry::getKey).findFirst().orElse(null);
                DragSource original = dragSource;
                dragSource = null;
                interaction.finishDrag();
                if(original.handIndex()!=null){handPinned=false;hoverSuppressed=true;setHandExpanded(false);}
                if (destination == null || Objects.equals(original.position(), destination)) {
                    refresh();
                    return;
                }
                executeDrop(original, destination);
            }

        };
    }

    private void showHandCardContextMenu(MouseEvent event, int handIndex) {
        if (handIndex < 0 || handIndex >= state.player(localPlayer()).hand().size()) return;
        CardInstance card = state.card(state.player(localPlayer()).hand().get(handIndex)).orElseThrow();
        JPopupMenu menu = new JPopupMenu();
        JMenuItem view = new JMenuItem("View full card");
        view.addActionListener(action -> showFullCard(card));
        menu.add(view);
        menu.show(event.getComponent(), event.getX(), event.getY());
    }

    private void showStackContextMenu(MouseEvent event, BoardPosition position) {
        JPopupMenu menu = new JPopupMenu();
        JMenuItem inspect = new JMenuItem("Inspect stack");
        inspect.addActionListener(action -> showStackInspector(position));
        menu.add(inspect);

        state.board().topAt(position).flatMap(state::card).ifPresent(top -> {
            if (top.definition().abilities().stream().anyMatch(ability -> ability.trigger() == AbilityTrigger.ACTIVATED)) {
                JMenuItem activate = new JMenuItem(activationMenuText(top));
                activate.setEnabled(canActivate(position));
                activate.setToolTipText(activate.isEnabled()
                        ? "Pay the listed GP cost and use this ability"
                        : "This ability cannot be used now (check turn, owner, GP, and once-per-turn limit)");
                activate.addActionListener(action -> executeHuman(activationCommand(position)));
                menu.add(activate);
            }
        });
        menu.show(event.getComponent(), event.getX(), event.getY());
    }

    private String activationCommand(BoardPosition position) {
        return "activate " + position.x() + " " + position.y();
    }

    private boolean canActivate(BoardPosition position) {
        if (!canAcceptHumanInput() || state.phase() != Phase.PLAY) return false;
        return hints.forActivePlayer(state, new GameEngine()).contains(activationCommand(position));
    }

    private String activationMenuText(CardInstance card) {
        int cost = card.definition().abilities().stream()
                .filter(ability -> ability.trigger() == AbilityTrigger.ACTIVATED)
                .mapToInt(CardAbility::gpCost).sum();
        return "Activate " + card.definition().name() + " (" + cost + " GP)";
    }

    private void executeDrop(DragSource source, BoardPosition destination) {
        List<ActionOption> choices = legalCommands().stream()
                .filter(command -> startsAt(command, source))
                .filter(command -> endsAt(command, destination))
                .map(command -> new ActionOption(describe(command), command)).toList();
        if (choices.isEmpty()) {
            animateInvalidDrop(source, destination);
            message("That is not a legal destination. Highlighted outlines show where this card can go.");
            refresh();
            return;
        }
        ActionOption choice = choices.get(0);
        if (choices.size() > 1) {
            String stack = stackSummary(destination);
            Object selected = JOptionPane.showInputDialog(this,
                    "Choose the action and stack position.\nCurrent stack (top first): " + stack,
                    "Choose Action / Stack Order",
                    JOptionPane.QUESTION_MESSAGE, null, choices.toArray(), choice);
            if (!(selected instanceof ActionOption selectedOption)) return;
            choice = selectedOption;
        }
        executeHuman(choice.command());
    }

    private void previewCardDestruction() {
        PresentationSnapshot.CardVisual destroyed = PresentationSnapshot.capture(state).cards().values().stream()
                .filter(card -> card.owner() == 0 && card.zone() == Zone.BATTLEFIELD
                        && card.top() && card.position() != null)
                .findFirst().orElseThrow(() -> new IllegalStateException("No card available for destruction fixture"));
        CardDefinition definition = state.card(destroyed.id()).map(CardInstance::definition).orElseThrow();
        BoardPosition position = destroyed.position();
        interaction.lockPresentation();
        maskedBoardCells.add(position);
        refreshBoard();
        combatOverlay.beginSequence(() -> {
            maskedBoardCells.remove(position);
            interaction.finishPresentation();
            refresh();
        });
        try {
            combatOverlay.animateDestroyed(definition, destroyed.owner(), position);
            combatOverlay.shake(definition.type() == CardType.CAPITAL ? 1.0f : 0.35f);
        } finally {
            combatOverlay.finishSequence();
        }
    }

    private void previewMeleeLunge() {
        PresentationSnapshot.CardVisual attacker = PresentationSnapshot.capture(state).cards().values().stream()
                .filter(card -> card.owner() == 0 && card.zone() == Zone.BATTLEFIELD
                        && card.top() && card.position() != null)
                .findFirst().orElseThrow(() -> new IllegalStateException("No attacker available for fixture"));
        CardDefinition definition = state.card(attacker.id()).map(CardInstance::definition).orElseThrow();
        BoardPosition from = attacker.position();
        int targetY = from.y() < BoardPosition.HEIGHT - 1 ? from.y() + 1 : from.y() - 1;
        BoardPosition target = new BoardPosition(from.x(), targetY);
        interaction.lockPresentation();
        maskedBoardCells.add(from);
        refreshBoard();
        combatOverlay.beginSequence(() -> {
            maskedBoardCells.remove(from);
            interaction.finishPresentation();
            refresh();
        });
        try {
            combatOverlay.animateMeleeCard(definition, attacker.owner(), from, target);
        } finally {
            combatOverlay.finishSequence();
        }
    }

    private void previewBoardMovement() {
        PresentationSnapshot.CardVisual moving = PresentationSnapshot.capture(state).cards().values().stream()
                .filter(card -> card.owner() == 0 && card.zone() == Zone.BATTLEFIELD
                        && card.top() && card.position() != null)
                .findFirst().orElseThrow(() -> new IllegalStateException("No board card available for fixture"));
        CardDefinition definition = state.card(moving.id()).map(CardInstance::definition).orElseThrow();
        BoardPosition from = moving.position();
        int targetX = from.x() < BoardPosition.WIDTH - 1 ? from.x() + 1 : from.x() - 1;
        BoardPosition to = new BoardPosition(targetX, from.y());
        interaction.lockPresentation();
        Set<BoardPosition> masked = Set.of(from, to);
        maskedBoardCells.addAll(masked);
        refreshBoard();
        combatOverlay.beginSequence(() -> {
            maskedBoardCells.removeAll(masked);
            interaction.finishPresentation();
            refresh();
        });
        try {
            combatOverlay.animateBoardCard(definition, moving.owner(), from, to,
                    new Color(91, 209, 255), AnimationStyle.MOVE);
        } finally {
            combatOverlay.finishSequence();
        }
    }

    private void animateInvalidDrop(DragSource source, BoardPosition attempted) {
        CardDefinition definition;
        BoardPosition returnBoard = source.position();
        if (source.handIndex() != null) {
            if (source.handIndex() < 0 || source.handIndex() >= state.player(0).hand().size()) return;
            definition = state.card(state.player(0).hand().get(source.handIndex()))
                    .orElseThrow().definition();
        } else {
            definition = state.board().topAt(source.position()).flatMap(state::card)
                    .map(CardInstance::definition).orElse(null);
            if (definition == null) return;
        }
        interaction.lockPresentation();
        combatOverlay.beginSequence(() -> {
            interaction.finishPresentation();
            refresh();
        });
        try {
            combatOverlay.animateSnapBack(definition, attempted, returnBoard);
        } finally {
            combatOverlay.finishSequence();
        }
    }

    private List<String> legalCommands() {
        if (!canAcceptHumanInput()) return List.of();
        return hints.forActivePlayer(state, new GameEngine());
    }

    private boolean startsAt(String command, DragSource source) {
        String[] p = command.split("\\s+");
        if (source.handIndex() != null) {
            return (p[0].equals("play") || p[0].equals("burrow") || p[0].equals("cast"))
                    && Integer.parseInt(p[1]) == source.handIndex();
        }
        return p.length >= 3 && (p[0].equals("move") || p[0].equals("blink") || p[0].equals("attack") || p[0].equals("activate"))
                && Integer.parseInt(p[1]) == source.position().x() && Integer.parseInt(p[2]) == source.position().y();
    }

    private boolean endsAt(String command, BoardPosition destination) {
        String[] p = command.split("\\s+");
        int xIndex = (p[0].equals("move") || p[0].equals("blink") || p[0].equals("attack")) ? 3 : 2;
        return p.length > xIndex + 1 && Integer.parseInt(p[xIndex]) == destination.x()
                && Integer.parseInt(p[xIndex + 1]) == destination.y();
    }

    private boolean isLegalDestination(BoardPosition destination) {
        if (!interaction.hasSelection()) return false;
        DragSource source = new DragSource(interaction.handIndex(), interaction.boardPosition());
        return legalCommands().stream().anyMatch(command -> startsAt(command, source) && endsAt(command, destination));
    }

    private Intent destinationIntent(BoardPosition destination) {
        if (!interaction.hasSelection()) return null;
        DragSource source = new DragSource(interaction.handIndex(), interaction.boardPosition());
        Set<Intent> intents = new LinkedHashSet<>();
        legalCommands().stream().filter(command -> startsAt(command, source) && endsAt(command, destination))
                .map(Intent::fromCommand).forEach(intents::add);
        if (intents.isEmpty()) return null;
        return intents.size() == 1 ? intents.iterator().next() : Intent.CHOOSE;
    }

    private String stackSummary(BoardPosition position) {
        List<UUID> stack = state.board().stackAt(position);
        if (stack.isEmpty()) return "empty";
        List<String> names = new ArrayList<>();
        for (int i = stack.size() - 1; i >= 0; i--) {
            names.add(state.card(stack.get(i)).orElseThrow().definition().name());
        }
        return String.join(" > ", names);
    }

    private boolean confirmOpportunityRisk(String command) {
        String[] p = command.split("\\s+");
        if (!p[0].equals("move")) return true;
        BoardPosition from = position(p, 1);
        BoardPosition to = position(p, 3);
        Optional<UUID> moverId = state.board().topAt(from);
        if (moverId.isEmpty()) return true;
        List<GameEngine.OpportunityThreat> threats = new GameEngine()
                .opportunityThreats(state, moverId.get(), to);
        if (threats.isEmpty()) return true;
        StringBuilder warning = new StringBuilder("This route crosses enemy attack range:\n\n");
        for (GameEngine.OpportunityThreat threat : threats) {
            warning.append("• ").append(threat.attackerName()).append(" at (")
                    .append(threat.attackerPosition().x()).append(", ")
                    .append(threat.attackerPosition().y()).append(") — ATK ")
                    .append(threat.attack()).append(" vs DEF ").append(threat.moverDefense())
                    .append("; triggers at (").append(threat.triggerPosition().x()).append(", ")
                    .append(threat.triggerPosition().y()).append(")")
                    .append(threat.lethal() ? " — LETHAL" : " — survives").append('\n');
        }
        warning.append("\nEach listed enemy gets one free attack during this move. Continue?");
        return JOptionPane.showConfirmDialog(this, warning.toString(), "Opportunity Attack Warning",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private void showResolution(PresentationSnapshot resolution) {
        presentationQueue.enqueue(resolution);
    }

    private void playPresentation(PresentationSnapshot resolution, Runnable completion) {
        interaction.lockPresentation();
        endTurnButton.setEnabled(false);
        Set<BoardPosition> masked = maskedDestinations(resolution);
        maskedBoardCells.addAll(masked);
        refreshBoard();
        combatOverlay.beginSequence(() -> {
            maskedBoardCells.removeAll(masked);
            interaction.finishPresentation();
            completion.run();
            refresh();
        });
        try {
            renderPresentation(resolution);
        } finally {
            combatOverlay.finishSequence();
        }
    }

    private Set<BoardPosition> maskedDestinations(PresentationSnapshot resolution) {
        if (resolution.command().startsWith("play ")) {
            return resolution.changes().stream()
                    .filter(change -> change.change() == PresentationSnapshot.Change.ENTERED_BATTLEFIELD)
                    .map(PresentationSnapshot.CardChange::after)
                    .filter(Objects::nonNull)
                    .map(PresentationSnapshot.CardVisual::position)
                    .filter(Objects::nonNull)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
        if (resolution.command().startsWith("move ") || resolution.command().startsWith("blink ")) {
            String[] parts = resolution.command().split("\\s+");
            return Set.of(resolution.movementDestination());
        }
        if (resolution.command().startsWith("attack ")) {
            String[] parts = resolution.command().split("\\s+");
            BoardPosition from = position(parts, 1);
            BoardPosition target = position(parts, 3);
            return state.rules().geometry().distance(from,target) <= 1 ? Set.of(from) : Set.of();
        }
        return Set.of();
    }

    private void renderPresentation(PresentationSnapshot resolution) {
        String command = resolution.command();
        PresentationSnapshot.Frame before = resolution.before();
        String[] p = command.split("\\s+");
        switch (p[0]) {
            case "move", "blink" -> {
                BoardPosition from = position(p, 1);
                BoardPosition to = resolution.movementDestination();
                boolean blink = p[0].equals("blink");
                badge(to, blink ? "BLINK" : "MOVE", "#71d7ff");
                PresentationSnapshot.CardVisual moving = before.cards().values().stream()
                        .filter(value -> Objects.equals(value.position(), from) && value.top())
                        .findFirst().orElse(null);
                CardDefinition definition = moving == null ? null
                        : state.card(moving.id()).map(CardInstance::definition).orElse(null);
                if (definition != null) {
                    combatOverlay.animateBoardCard(definition, moving.owner(), from, to,
                            new Color(91, 209, 255), blink ? AnimationStyle.BLINK : AnimationStyle.MOVE);
                } else {
                    combatOverlay.animate(from, to, new Color(91, 209, 255), false,
                            blink ? AnimationStyle.BLINK : AnimationStyle.MOVE);
                }
                SoundEffects.play(SoundEffects.Cue.MOVE);
            }
            case "play", "burrow" -> {
                BoardPosition to = position(p, 2);
                boolean burrow = p[0].equals("burrow");
                badge(to, burrow ? "BURROWED BELOW TOP" : "PLACED ON TOP", "#78e29a");
                PresentationSnapshot.CardVisual deployed = resolution.changes().stream()
                        .filter(change -> change.change() == PresentationSnapshot.Change.ENTERED_BATTLEFIELD)
                        .map(PresentationSnapshot.CardChange::after)
                        .filter(Objects::nonNull)
                        .filter(card -> Objects.equals(card.position(), to))
                        .findFirst().orElse(null);
                CardDefinition definition = deployed == null ? null
                        : state.card(deployed.id()).map(CardInstance::definition).orElse(null);
                Color burst = burrow ? BURROW : DEPLOY;
                if (definition != null) {
                    // The flight is driven by the state update, so it plays for
                    // local and network games alike; the burst lands on arrival.
                    boolean opponentPlay = deployed.owner() != localPlayer();
                    combatOverlay.animatePlayFlight(definition, opponentPlay, to, burst);
                    combatOverlay.animate(null, to, burst, false, AnimationStyle.DEPLOY, Fx.DEPLOY_MS);
                } else {
                    combatOverlay.animate(null, to, burst, false, AnimationStyle.DEPLOY, Fx.DEPLOY_MS);
                }
                SoundEffects.play(SoundEffects.Cue.DEPLOY);
            }
            case "attack" -> {
                BoardPosition from = position(p, 1);
                BoardPosition target = position(p, 3);
                boolean ranged = state.rules().geometry().distance(from,target) > 1;
                TargetOutcome outcome = showTargetResult(target, resolution, ranged ? "RANGED" : "MELEE", ranged ? "#ffb45b" : "#ff7373");
                PresentationSnapshot.CardVisual originalAttacker = before.cards().values().stream()
                        .filter(value -> Objects.equals(value.position(), from) && value.top())
                        .findFirst().orElse(null);
                CardDefinition attackerDefinition = originalAttacker == null ? null
                        : state.card(originalAttacker.id()).map(CardInstance::definition).orElse(null);
                boolean cardLunge = !ranged && attackerDefinition != null;
                if (cardLunge) {
                    combatOverlay.animateMeleeCard(attackerDefinition, originalAttacker.owner(), from, target);
                } else {
                    combatOverlay.animate(from, target, ranged ? new Color(255, 180, 91) : ATTACK, false,
                            ranged ? AnimationStyle.RANGED : AnimationStyle.MELEE);
                }
                if (outcome.damage() > 0 && !outcome.destroyed()) {
                    damageFeedback(target, resolution.before(), outcome.damage(),
                            cardLunge ? Fx.MELEE_MS : Fx.GENERIC_MS);
                }
                if (originalAttacker != null) {
                    PresentationSnapshot.CardVisual surviving = resolution.after().card(originalAttacker.id());
                    if (surviving == null || surviving.zone() != Zone.BATTLEFIELD) {
                        badge(from, "RETALIATION • DESTROYED", "#ff7373");
                        combatOverlay.animate(target, from, ATTACK, false, AnimationStyle.MELEE);
                    }
                }
                SoundEffects.play(ranged ? SoundEffects.Cue.RANGED : SoundEffects.Cue.MELEE);
            }
            case "cast", "react" -> {
                int targetIndex = p[0].equals("react") ? 3 : 2;
                BoardPosition target = position(p, targetIndex);
                TargetOutcome outcome = showTargetResult(target, resolution, "SPELL", "#df92ff");
                combatOverlay.animate(null, target, new Color(223, 146, 255), false, AnimationStyle.SPELL);
                if (outcome.damage() > 0 && !outcome.destroyed()) {
                    damageFeedback(target, resolution.before(), outcome.damage(), Fx.GENERIC_MS);
                }
                SoundEffects.play(SoundEffects.Cue.SPELL);
            }
            case "activate" -> {
                BoardPosition at = null;
                try { at = position(p, 1); } catch (RuntimeException ignored) { }
                if (at != null) {
                    badge(at, "ACTIVATED", "#ffd75c");
                    combatOverlay.animate(at, at, new Color(255, 215, 92), false, AnimationStyle.SPELL);
                }
                SoundEffects.play(SoundEffects.Cue.SPELL);
            }
            default -> { }
        }
        for (GameEvent event : resolution.events()) {
            if (event.type() == GameEvent.Type.GP_GENERATED && event.playerId() == localPlayer())
                SoundEffects.play(SoundEffects.Cue.COIN_GAIN);
            if (event.type() == GameEvent.Type.GP_SPENT && event.playerId() == localPlayer())
                delayedCue(SoundEffects.Cue.COIN_SPEND, 140);
            if (event.type() == GameEvent.Type.OPPORTUNITY_ATTACK) {
                String[] detail = event.detail().split("\\s+");
                try {
                    UUID attackerId = UUID.fromString(detail[0]);
                    UUID moverId = UUID.fromString(detail[2]);
                    PresentationSnapshot.CardVisual attacker = before.card(attackerId);
                    PresentationSnapshot.CardVisual mover = before.card(moverId);
                    if (attacker != null && mover != null) {
                        String[] xy = detail[4].split(",");
                        BoardPosition trigger = new BoardPosition(Integer.parseInt(xy[0]), Integer.parseInt(xy[1]));
                        PresentationSnapshot.CardVisual currentMover = resolution.after().card(moverId);
                        boolean destroyed = currentMover == null || currentMover.zone() != Zone.BATTLEFIELD;
                        badge(trigger, "FREE ATTACK • " + (destroyed ? "DESTROYED" : "BLOCKED"), "#ff7373");
                        combatOverlay.animate(attacker.position(), trigger, ATTACK, false, AnimationStyle.MELEE);
                        SoundEffects.play(SoundEffects.Cue.MELEE);
                    }
                } catch (IllegalArgumentException ignored) { }
            }
            if(event.type()==GameEvent.Type.TERRAIN_TRIGGERED) {
                String[] parts=event.detail().split(" ");
                if(parts.length>=5)try {
                    String[] xy=parts[4].split(",");BoardPosition target=new BoardPosition(Integer.parseInt(xy[0]),Integer.parseInt(xy[1]));
                    var source=before.card(UUID.fromString(parts[0]));
                    boolean attack=parts[2].equals("TURRET");
                    if (!attack) badge(target,parts[2].replace('_',' '),"#78e29a");
                    combatOverlay.animate(source==null?null:source.position(),target,attack?ATTACK:DEPLOY,false,attack?AnimationStyle.RANGED:AnimationStyle.SPELL);
                    if (attack) {
                        try {
                            damageFeedback(target, before, Integer.parseInt(parts[3]), Fx.GENERIC_MS);
                        } catch (NumberFormatException ignored) { }
                    }
                } catch(IllegalArgumentException ignored) { }
            }
            if (event.type() != GameEvent.Type.EXHAUSTION_DAMAGE) continue;
            try {
                UUID id = UUID.fromString(event.detail().split("\\s+")[0]);
                PresentationSnapshot.CardVisual old = before.card(id);
                if (old == null) continue;
                PresentationSnapshot.CardVisual current = resolution.after().card(id);
                int damage = current == null ? old.hitPoints() - old.damage()
                        : Math.max(1, current.damage() - old.damage());
                String cause = "EXHAUSTION";
                badge(old.position(), cause, "#ffcf5c");
                combatOverlay.animate(null, old.position(), new Color(255, 207, 92), true, AnimationStyle.RULES);
                damageFeedback(old.position(), before, damage, Fx.GENERIC_MS);
                SoundEffects.play(SoundEffects.Cue.PENALTY);
            } catch (IllegalArgumentException ignored) { }
        }
        Set<UUID> deaths=new LinkedHashSet<>();
        resolution.changes().stream().filter(c->c.change()==PresentationSnapshot.Change.DESTROYED)
                .map(c->c.before().id()).forEach(deaths::add);
        resolution.events().stream().filter(e->e.type()==GameEvent.Type.CARD_DESTROYED)
                .map(e->UUID.fromString(e.detail())).forEach(deaths::add);
        for(UUID id:deaths) {
            CardInstance card=state.card(id).orElse(null);BoardPosition position=resolution.destructionPosition(id);
            if(card!=null && position!=null) {
                combatOverlay.animateDestroyed(card.definition(),card.owner(),position);
                combatOverlay.shake(card.definition().type() == CardType.CAPITAL ? 1.0f : 0.35f);
            }
        }
    }

    private record TargetOutcome(int damage, boolean destroyed) { }

    private TargetOutcome showTargetResult(BoardPosition target, PresentationSnapshot resolution,
                                  String cause, String color) {
        PresentationSnapshot.CardVisual old = resolution.before().topAt(target);
        if (old == null) { badge(target, cause, color); return new TargetOutcome(0, false); }
        PresentationSnapshot.CardVisual current = resolution.after().card(old.id());
        if (current == null || current.zone() != Zone.BATTLEFIELD) {
            String outcome = cause.equals("SPELL") && current != null && current.zone() == Zone.HAND
                    ? "RETURNED TO HAND" : "DESTROYED";
            badge(target, cause + " • " + outcome, color);
            if (outcome.equals("DESTROYED")) SoundEffects.play(SoundEffects.Cue.DESTROY);
            return new TargetOutcome(0, true);
        }
        int damage = current.damage() - old.damage();
        if (damage <= 0) {
            badge(target, cause + " • " + (cause.equals("SPELL") ? "RESOLVED" : "BLOCKED"), color);
        }
        // damage > 0: the caller queues a floating damage number after the strike
        // animation, which replaces the old static "N DMG" badge.
        return new TargetOutcome(damage, false);
    }

    /** Queues floating damage feedback after a strike animation, with an impact-timed cue. */
    private void damageFeedback(BoardPosition target, PresentationSnapshot.Frame before,
                                int damage, int impactDelayMs) {
        combatOverlay.animateDamage(target, damage);
        boolean capital = isCapitalAt(target, before);
        delayedCue(capital ? SoundEffects.Cue.CAPITAL_HIT : SoundEffects.Cue.DAMAGE, impactDelayMs);
        if (capital) combatOverlay.shake(0.8f);
    }

    private void delayedCue(SoundEffects.Cue cue, int delayMs) {
        if (Fx.reduced(gameSettings) || delayMs <= 0) {
            SoundEffects.play(cue);
            return;
        }
        javax.swing.Timer timer = new javax.swing.Timer(delayMs, e -> SoundEffects.play(cue));
        timer.setRepeats(false);
        timer.start();
    }

    private boolean isCapitalAt(BoardPosition target, PresentationSnapshot.Frame before) {
        try {
            PresentationSnapshot.CardVisual top = before.topAt(target);
            if (top == null) return false;
            return state.card(top.id())
                    .map(card -> card.definition().type() == CardType.CAPITAL)
                    .orElse(false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private BoardPosition position(String[] parts, int index) {
        return new BoardPosition(Integer.parseInt(parts[index]), Integer.parseInt(parts[index + 1]));
    }

    private void badge(BoardPosition position, String text, String color) {
        EffectBadge full = new EffectBadge(text, color);
        EffectBadge dim = new EffectBadge(text, dimColor(color));
        effectBadges.put(position, dim);
        refreshBoard();
        javax.swing.Timer fadeIn = new javax.swing.Timer(Fx.BADGE_FADE_MS, e -> {
            if (effectBadges.get(position) == dim) {
                effectBadges.put(position, full);
                refreshBoard();
            }
        });
        fadeIn.setRepeats(false);
        fadeIn.start();
        javax.swing.Timer fadeOut = new javax.swing.Timer(Fx.BADGE_MS - Fx.BADGE_FADE_MS, e -> {
            EffectBadge current = effectBadges.get(position);
            if (current == full || current == dim) {
                effectBadges.put(position, dim);
                refreshBoard();
            }
        });
        fadeOut.setRepeats(false);
        fadeOut.start();
        javax.swing.Timer clear = new javax.swing.Timer(Fx.BADGE_MS, e -> {
            EffectBadge current = effectBadges.get(position);
            if (current == full || current == dim) effectBadges.remove(position);
            refreshBoard();
        });
        clear.setRepeats(false);
        clear.start();
    }

    /** Dimmed variant of a "#rrggbb" badge color for the fade in/out envelope. */
    private static String dimColor(String color) {
        try {
            Color parsed = Color.decode(color);
            float factor = 0.45f;
            Color dimmed = new Color(
                    Math.round(parsed.getRed() * factor),
                    Math.round(parsed.getGreen() * factor),
                    Math.round(parsed.getBlue() * factor));
            return String.format("#%02x%02x%02x", dimmed.getRed(), dimmed.getGreen(), dimmed.getBlue());
        } catch (RuntimeException e) {
            return "#5a6068";
        }
    }

    private void syncSystemEvents() {
        if (state == null) return;
        for (GameEvent event : state.events()) {
            if (event.sequence() <= lastSystemEvent) continue;
            String actor = event.playerId() == 0 ? (playerOneBot ? "Bot 1" : "You")
                    : event.playerId() == 1 ? "Bot 2" : "Rules";
            String detail = switch (event.type()) {
                case EXHAUSTION_DAMAGE -> friendlyCardDetail(event.detail(), " takes 1 exhaustion damage (empty deck)");
                case GP_GENERATED -> "+ Income — " + event.detail();
                case GP_SPENT -> "− GP spent — " + event.detail();
                case TURN_STARTED -> "↻ Turn " + event.turnNumber();
                case CARD_DESTROYED -> friendlyCardDetail(event.detail(), " was destroyed");
                case CAPITAL_PASSIVE_TRIGGERED -> "Capital passive — " + event.detail().replace('_', ' ').toLowerCase(Locale.ROOT);
                case DEVELOPMENT_PASSIVE_TRIGGERED -> friendlyDevelopmentPassive(event.detail());
                case CARD_ABILITY_TRIGGERED -> friendlyCardAbility(event.detail());
                case TERRAIN_TRIGGERED -> friendlyTerrain(event.detail());
                case OPPORTUNITY_ATTACK -> friendlyOpportunityDetail(event.detail());
                case GAME_OVER -> "GAME OVER — " + event.detail();
                default -> null;
            };
            if (detail != null) {
                addHistory(actor, detail);
                if (event.type() == GameEvent.Type.EXHAUSTION_DAMAGE
                        || event.type() == GameEvent.Type.GAME_OVER) actionTabs.setSelectedIndex(1);
            }
            lastSystemEvent = event.sequence();
        }
    }

    private String friendlyTerrain(String detail) {
        String[] parts=detail.split(" ");
        try {
            String source=state.card(UUID.fromString(parts[0])).orElseThrow().definition().name();
            String target=state.card(UUID.fromString(parts[1])).orElseThrow().definition().name();
            return source+" — "+title(parts[2].replace('_',' '))+" "+parts[3]+" → "+target;
        } catch(RuntimeException error){return detail;}
    }

    private String friendlyCardDetail(String detail, String suffix) {
        String idText = detail.split("\\s+")[0];
        try {
            CardInstance card = state.card(UUID.fromString(idText)).orElse(null);
            if (card != null) {
                String amount = detail.contains(" takes ") ? detail.substring(detail.indexOf(" takes ")) : "";
                return card.definition().name() + suffix + (amount.isBlank() ? "" : " (" + amount.trim() + ")");
            }
        } catch (IllegalArgumentException ignored) { }
        return detail + suffix;
    }

    private String friendlyOpportunityDetail(String detail) {
        String[] parts = detail.split("\\s+");
        try {
            String attacker = state.card(UUID.fromString(parts[0])).map(card -> card.definition().name()).orElse("Enemy");
            String mover = state.card(UUID.fromString(parts[2])).map(card -> card.definition().name()).orElse("mover");
            return attacker + " made a free opportunity attack against " + mover;
        } catch (IllegalArgumentException exception) {
            return "Opportunity attack resolved";
        }
    }

    private String friendlyDevelopmentPassive(String detail) {
        String[] parts = detail.split("\\s+", 2);
        try {
            String name = state.card(UUID.fromString(parts[0]))
                    .map(card -> card.definition().name()).orElse("Development");
            return name + " passive — " + (parts.length > 1 ? parts[1] : "resolved");
        } catch (IllegalArgumentException exception) {
            return "Development passive resolved";
        }
    }

    private String friendlyCardAbility(String detail) {
        String[] parts = detail.split("\\s+", 2);
        try {
            String name = state.card(UUID.fromString(parts[0]))
                    .map(card -> card.definition().name()).orElse("Card");
            return name + " ability — " + (parts.length > 1
                    ? parts[1].replace('_', ' ').toLowerCase(Locale.ROOT) : "resolved");
        } catch (IllegalArgumentException exception) {
            return "Card ability resolved";
        }
    }

    private void showStackInspector(BoardPosition position) {
        List<UUID> stack = state.board().stackAt(position);
        if (stack.isEmpty()) {
            message("Cell (" + position.x() + ", " + position.y() + ") is empty.");
            return;
        }
        JPanel cards = new JPanel();
        cards.setLayout(new BoxLayout(cards, BoxLayout.Y_AXIS));
        for (int i = stack.size() - 1; i >= 0; i--) {
            CardInstance card = state.card(stack.get(i)).orElseThrow();
            CardDefinition def = card.definition();
            JLabel row = new JLabel("<html><b>" + (i == stack.size() - 1 ? "TOP" : "Layer " + (i + 1))
                    + " — " + html(def.name()) + "</b><br>" + title(def.type().name()) + " • "
                    + html(def.faction()) + " • " + html(keywordLine(def))
                    + (developmentText(def).isBlank() ? "" : "<br><font color='#67d890'>" + html(developmentText(def)) + "</font>")
                    + (abilityLine(def).isBlank() ? "" : "<br><font color='#9be7ff'>" + html(abilityLine(def)) + "</font>")
                    + "</html>",
                    CardArtFactory.iconFor(def, 140, 58), SwingConstants.LEFT);
            row.setForeground(Color.WHITE);
            row.setBorder(new EmptyBorder(7, 7, 7, 7));
            boolean topCard = i == stack.size() - 1;
            row.setToolTipText("Right-click to view this card" + (topCard ? " or use its activated ability" : ""));
            row.addMouseListener(new MouseAdapter() {
                private void showCardMenu(MouseEvent event) {
                    if (!event.isPopupTrigger()) return;
                    JPopupMenu menu = new JPopupMenu();
                    JMenuItem view = new JMenuItem("View full card");
                    view.addActionListener(action -> showFullCard(card));
                    menu.add(view);
                    if (topCard && def.abilities().stream()
                            .anyMatch(ability -> ability.trigger() == AbilityTrigger.ACTIVATED)) {
                        menu.addSeparator();
                        JMenuItem activate = new JMenuItem(activationMenuText(card));
                        activate.setEnabled(canActivate(position));
                        activate.setToolTipText(activate.isEnabled()
                                ? "Use this ability now"
                                : "Unavailable: it must be your turn and you need enough GP; each ability is once per turn");
                        activate.addActionListener(action -> {
                            Window inspector = SwingUtilities.getWindowAncestor(cards);
                            if (inspector != null) inspector.dispose();
                            SwingUtilities.invokeLater(() -> executeHuman(activationCommand(position)));
                        });
                        menu.add(activate);
                    }
                    menu.show(event.getComponent(), event.getX(), event.getY());
                }

                @Override public void mousePressed(MouseEvent event) { showCardMenu(event); }
                @Override public void mouseReleased(MouseEvent event) { showCardMenu(event); }
            });
            cards.add(row);
        }
        cards.setBackground(PANEL);
        JScrollPane scroll = new JScrollPane(cards);
        scroll.setPreferredSize(new Dimension(510, Math.min(420, 95 * stack.size())));
        JPanel inspector = new JPanel(new BorderLayout(0, 8));
        inspector.setBackground(PANEL);
        inspector.add(scroll, BorderLayout.CENTER);
        JLabel help = new JLabel("Top card acts first. Right-click any row for a full card view or available ability.");
        help.setForeground(new Color(155, 231, 255));
        help.setBorder(new EmptyBorder(2, 7, 2, 7));
        inspector.add(help, BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(this, inspector,
                "Stack at (" + position.x() + ", " + position.y() + ") — top first", JOptionPane.PLAIN_MESSAGE);
    }

    private void showFullCard(CardInstance card) {
        JDialog dialog=new JDialog(this,card.definition().name(),true);
        JPanel content=panel(new BorderLayout(8,8));
        content.add(new CardInspectionPanel(state,card),BorderLayout.CENTER);
        content.add(button("Close",e->dialog.dispose()),BorderLayout.SOUTH);
        dialog.setContentPane(content);dialog.setResizable(true);
        Rectangle usable=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        dialog.setSize(Math.min(860,usable.width-32),Math.min(620,usable.height-40));
        dialog.setLocationRelativeTo(this);dialog.setVisible(true);
    }

    private void addHistory(String actor, String action) {
        historyModel.addElement(String.format("%02d  %s: %s", ++historyNumber, actor, action));
        recentAction.setText(ActionSummary.label(action) + " · " + actor + ": " + action);
        recentAction.setToolTipText(actor + ": " + action);
        int last = historyModel.size() - 1;
        if (last >= 0) historyList.ensureIndexIsVisible(last);
    }

    static String describe(String command) {
        String[] p = command.split("\\s+");
        return switch (p[0]) {
            case "play" -> "Place hand #" + p[1] + " on TOP at (" + p[2] + ", " + p[3] + ")";
            case "burrow" -> "Burrow hand #" + p[1] + " beneath (" + p[2] + ", " + p[3] + ")";
            case "move" -> "Move (" + p[1] + ", " + p[2] + ") → (" + p[3] + ", " + p[4] + ")";
            case "blink" -> "Blink (" + p[1] + ", " + p[2] + ") → (" + p[3] + ", " + p[4] + ")";
            case "attack" -> (Math.max(Math.abs(Integer.parseInt(p[1]) - Integer.parseInt(p[3])),
                    Math.abs(Integer.parseInt(p[2]) - Integer.parseInt(p[4]))) > 1 ? "Ranged attack " : "Melee attack ")
                    + "(" + p[1] + ", " + p[2] + ") → (" + p[3] + ", " + p[4] + ")";
            case "activate" -> "Activate paid ability at (" + p[1] + ", " + p[2] + ")";
            case "cast" -> "Cast hand #" + p[1] + " on (" + p[2] + ", " + p[3] + ")"
                    + (p.length > 4 ? " → (" + p[4] + ", " + p[5] + ")" : "");
            case "react" -> "React with hand #" + p[2] + " on (" + p[3] + ", " + p[4] + ")";
            case "end" -> "End your turn";
            default -> command;
        };
    }

    private String phaseText() {
        if (state.phase() == Phase.GAME_OVER) return "GAME OVER";
        if (botRunning || state.activePlayer() == 1) return "BOT TURN";
        return "YOUR TURN";
    }

    private void showWinner() {
        interaction.markGameOver();
        boolean playerOneWon = state.winner().isPresent() && state.winner().getAsInt() == 0;
        String result = state.winner().isEmpty() ? "DRAW"
                : playerOneBot ? "PLAYER " + (state.winner().getAsInt() + 1) + " WINS"
                : playerOneWon ? "VICTORY" : "DEFEAT";
        turnLabel.setText("Turn " + state.turnNumber() + " • " + result);
        String reason = state.events().stream().filter(event -> event.type() == GameEvent.Type.GAME_OVER)
                .reduce((first, second) -> second).map(GameEvent::detail).orElse("Match ended");
        message("<b>" + result + "</b> — " + reason
                + ". A player loses immediately when they have no permanents. Start a new match to play again.");
        if (!winnerSoundPlayed) {
            winnerSoundPlayed = true;
            SoundEffects.play(playerOneWon ? SoundEffects.Cue.VICTORY : SoundEffects.Cue.DEFEAT);
        }
        if (!victoryDialogShown) {
            victoryDialogShown = true;
            SwingUtilities.invokeLater(() -> showResultDialog(result, reason, playerOneWon));
        }
    }

    private void showResultDialog(String result, String reason, boolean playerOneWon) {
        if (state == null || state.phase() != Phase.GAME_OVER) return;
        int winner = state.winner().orElse(-1);
        String winnerFaction = winner == 0 ? humanFaction : winner == 1 ? botFaction : "NEUTRAL";
        CardDefinition winnerCapital = winner == 0 ? humanCapital : winner == 1 ? botCapital : humanCapital;
        int playerPermanents = state.battlefieldCards(0).size();
        int enemyPermanents = state.battlefieldCards(1).size();

        JDialog dialog = new JDialog(this, result, true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        VictoryPanel backdrop = new VictoryPanel(winnerFaction, playerOneWon && !playerOneBot);
        backdrop.setLayout(new BorderLayout(18, 18));
        backdrop.setBorder(new EmptyBorder(34, 42, 30, 42));

        JLabel heading = new JLabel(result, SwingConstants.CENTER);
        heading.setFont(new Font(Font.SERIF, Font.BOLD, 48));
        heading.setForeground(playerOneWon && !playerOneBot ? new Color(255, 224, 120)
                : result.equals("DEFEAT") ? new Color(255, 126, 126) : Color.WHITE);
        heading.setBorder(new EmptyBorder(0, 0, 8, 0));
        backdrop.add(heading, BorderLayout.NORTH);

        JPanel summary = new JPanel(new BorderLayout(18, 12));
        summary.setOpaque(false);
        JLabel art = new JLabel(CardArtFactory.iconFor(winnerCapital, 300, 150));
        art.setHorizontalAlignment(SwingConstants.CENTER);
        summary.add(art, BorderLayout.NORTH);
        String outcome = winner < 0 ? "The conquest ended without a victor."
                : "<b>" + title(winnerFaction) + " controls the battlefield.</b>";
        JLabel details = new JLabel("<html><div style='text-align:center'>" + outcome
                + "<br><br>Match length: <b>" + state.turnNumber() + " turns</b>"
                + "<br>Your permanents: <b>" + playerPermanents + "</b> &nbsp; • &nbsp; Enemy permanents: <b>" + enemyPermanents + "</b>"
                + "<br><br><font color='#c9d5e4'>" + html(friendlyGameOverReason(reason)) + "</font></div></html>",
                SwingConstants.CENTER);
        details.setForeground(Color.WHITE);
        details.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 16));
        summary.add(details, BorderLayout.CENTER);
        backdrop.add(summary, BorderLayout.CENTER);

        JButton rematch = button("New Match — Same Settings", event -> {
            dialog.dispose(); SwingUtilities.invokeLater(this::replayMatch);
        });
        JButton settings = button("Change Settings", event -> {
            dialog.dispose(); SwingUtilities.invokeLater(this::newMatch);
        });
        JButton review = button("Review Battlefield", event -> dialog.dispose());
        rematch.setBackground(new Color(48, 112, 137));
        settings.setBackground(new Color(91, 78, 137));
        review.setBackground(new Color(63, 72, 86));
        JPanel choices = new JPanel(new GridLayout(1, 3, 10, 0));
        choices.setOpaque(false); choices.add(rematch); choices.add(settings); choices.add(review);
        backdrop.add(choices, BorderLayout.SOUTH);

        dialog.setContentPane(backdrop);
        dialog.setSize(760, 590);
        dialog.setMinimumSize(new Dimension(680, 520));
        dialog.setLocationRelativeTo(this);
        backdrop.start();
        dialog.setVisible(true);
        backdrop.stop();
    }

    private String friendlyGameOverReason(String reason) {
        if (reason.matches("Player [01] wins")) {
            int winner = Integer.parseInt(reason.substring(7, 8));
            return "Player " + (winner + 1) + " won because the opponent lost every permanent.";
        }
        return reason.replace("Player 1", "Player 2").replace("Player 0", "Player 1");
    }

    private void message(String text) {
        messageLabel.setText("<html>" + html(text).replace("&lt;b&gt;", "<b>").replace("&lt;/b&gt;", "</b>") + "</html>");
        recentAction.setText("› " + text);
        recentAction.setToolTipText(text);
    }

    private String handCardHtml(CardDefinition def) {
        if (handExpanded) return cardHtml(def);
        String stats = def.type() == CardType.CHARACTER
                ? "A " + def.attack() + "  D " + def.defense() + "  M " + def.movement() + "  R " + def.range()
                : def.isPermanent() ? "HP " + def.hitPoints() + "  •  +" + def.income() + " GP/TURN"
                : compactName(effectLine(def), 30);
        return "<html><font color='#f0bf49'><b>" + html(playRequirement(def)) + "</b></font>"
                + " &nbsp; " + compactType(def.type())
                + "<br><b>" + html(compactName(def.name(), 24)) + "</b>"
                + "<br><font color='#dce7f5'>" + html(stats) + "</font></html>";
    }

    private String cardHtml(CardDefinition def) {
        String stats = def.type() == CardType.CHARACTER
                ? "ATK " + def.attack() + "  DEF " + def.defense() + "  MOVE " + def.movement() + "  RANGE " + def.range()
                : def.isPermanent() ? "HP " + def.hitPoints() : effectLine(def);
        return "<html><font color='#f0bf49'><b>" + html(playRequirement(def)) + "</b></font> &nbsp; " + def.type()
                + "<br><b>" + html(def.name()) + "</b><br><br>" + stats
                + (developmentText(def).isBlank() ? "" : "<br><font color='#67d890'><b>" + html(developmentText(def)) + "</b></font>")
                + "<br><font color='#c9d5e4'>" + html(keywordLine(def)) + "</font>"
                + (abilityLine(def).isBlank() ? "" : "<br><font color='#9be7ff'>" + html(abilityLine(def)) + "</font>") + "</html>";
    }

    private String abilityLine(CardDefinition definition) {
        return definition.abilities().stream().map(ability -> {
            String timing = switch (ability.trigger()) {
                case ENTERS_PLAY -> "When this enters play";
                case DESTROYED -> "When this is destroyed";
                case PASSIVE -> "Start of your turn";
                case ACTIVATED -> "Activate (" + ability.gpCost() + " GP)";
            };
            String effect = switch (ability.effect()) {
                case DRAW_CARD -> "draw " + ability.amount();
                case DRAW_CHARACTER -> "draw a next Character from your deck";
                case DRAW_STRUCTURE -> "draw a next Structure from your deck";
                case GAIN_GP -> "gain " + ability.amount() + " GP";
                case HEAL_SELF -> "heal this " + ability.amount();
                case HEAL_CAPITAL -> "heal your Capital " + ability.amount();
                case BUFF_SELF_ATTACK -> "this gains +" + ability.amount() + " Attack this turn";
                case BUFF_SELF_DEFENSE -> "this gains +" + ability.amount() + " Defense this turn";
                case DAMAGE_ENEMY_CAPITAL -> "deal " + ability.amount() + " damage to the enemy Capital";
            };
            return timing + ": " + effect + ".";
        }).collect(java.util.stream.Collectors.joining(" "));
    }

    private String developmentText(CardDefinition definition) {
        if (definition.type() != CardType.LAND && definition.type() != CardType.STRUCTURE) return "";
        String passive = DevelopmentRules.passiveText(definition.developmentPassive());
        return "+" + definition.income() + " GP/TURN" + (passive.isBlank() ? "" : " • " + passive);
    }

    private String playRequirement(CardDefinition definition) {
        if (definition.type() == CardType.LAND || definition.type() == CardType.STRUCTURE) {
            return "TURN " + Math.max(1, definition.cost()) + " • " + (definition.developmentGoldCost()==0?"FREE":definition.developmentGoldCost()+" GOLD");
        }
        return definition.cost() + " GP";
    }

    private String keywordLine(CardDefinition def) {
        String identity=def.faction()+(def.archetypes().isEmpty()?"":" • "+String.join(" / ",def.archetypes()).replace('_',' '));
        if (def.keywords().isEmpty()) return identity;
        return identity + " • " + def.keywords().stream()
                .map(keyword -> title(keyword.name().replace('_', ' ')))
                .collect(java.util.stream.Collectors.joining(" • "));
    }

    private String effectLine(CardDefinition def) {
        if (def.effects().isEmpty()) return def.faction();
        SpellEffect effect = def.effects().get(0);
        return title(effect.type().name().replace('_', ' ')) + " " + effect.amount()
                + " — " + title(effect.target().name().replace('_', ' '));
    }

    private JPanel panel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(PANEL);
        panel.setBorder(new CompoundBorder(new BevelBorder(BevelBorder.RAISED,
                        new Color(67, 82, 107), new Color(52, 66, 89), new Color(8, 13, 22), new Color(12, 18, 29)),
                new EmptyBorder(10, 10, 10, 10)));
        return panel;
    }

    private JLabel section(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        return label;
    }

    private JButton button(String text, java.awt.event.ActionListener listener) {
        return button(text, listener, false);
    }

    /** @param silentClick true when the caller plays its own cue (e.g. mulligan keep/redraw). */
    private JButton button(String text, java.awt.event.ActionListener listener, boolean silentClick) {
        JButton button = new JButton(text);
        button.setBackground(new Color(48, 83, 108));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorder(new CompoundBorder(new BevelBorder(BevelBorder.RAISED), new EmptyBorder(6, 12, 6, 12)));
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
        if (!silentClick) button.addActionListener(e -> SoundEffects.play(SoundEffects.Cue.CLICK));
        button.addActionListener(listener);
        return button;
    }

    private void styleMeter(JLabel label, Color color) {
        label.setForeground(color);
        label.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        label.setBorder(new CompoundBorder(new LineBorder(color.darker(), 1, true), new EmptyBorder(3, 7, 3, 7)));
    }

    private void installTheme() {
        UIManager.put("Button.arc", 12);
        UIManager.put("ScrollBar.thumb", new Color(68, 85, 109));
        UIManager.put("ScrollBar.track", PANEL);
        UIManager.put("ToolTip.background", PANEL_LIGHT);
        UIManager.put("ToolTip.foreground", Color.WHITE);
        UIManager.put("MenuBar.background", PANEL);
        UIManager.put("Menu.background", PANEL);
        UIManager.put("Menu.foreground", Color.WHITE);
        UIManager.put("MenuItem.background", PANEL_LIGHT);
        UIManager.put("MenuItem.foreground", Color.WHITE);
        UIManager.put("TabbedPane.background", PANEL);
        UIManager.put("TabbedPane.foreground", Color.WHITE);
    }

    private Color factionColor(String faction) {
        return switch (faction.toUpperCase(Locale.ROOT)) {
            case "POSEIDON" -> new Color(32, 137, 171);
            case "ZEUS" -> new Color(178, 154, 63);
            default -> new Color(93, 110, 130);
        };
    }

    private Color blend(Color first, Color second, float amount) {
        float keep = 1f - amount;
        return new Color((int) (first.getRed() * keep + second.getRed() * amount),
                (int) (first.getGreen() * keep + second.getGreen() * amount),
                (int) (first.getBlue() * keep + second.getBlue() * amount));
    }

    private String html(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String title(String value) {
        return value.charAt(0) + value.substring(1).toLowerCase(Locale.ROOT);
    }

    private record ActionOption(String label, String command) {
        @Override public String toString() { return label; }
    }

    private final class ActionOptionRenderer extends JLabel implements ListCellRenderer<ActionOption> {
        ActionOptionRenderer() {
            setOpaque(true);
            setBorder(new EmptyBorder(5, 9, 5, 9));
        }

        @Override public Component getListCellRendererComponent(JList<? extends ActionOption> list,
                ActionOption option, int index, boolean selected, boolean focused) {
            String verb = option.command().isBlank() ? "" : option.command().split("\\s+")[0];
            Color accent = switch (verb) {
                case "move" -> MOVE;
                case "blink" -> SELECTED;
                case "attack" -> ATTACK;
                case "play" -> DEPLOY;
                case "burrow" -> BURROW;
                case "cast", "activate" -> CAST;
                default -> new Color(154, 169, 190);
            };
            setText("<html><b>" + html(option.label()) + "</b></html>");
            setForeground(selected ? Color.WHITE : accent);
            setBackground(selected ? blend(PANEL_LIGHT, accent, .42f) : PANEL_LIGHT);
            setBorder(new CompoundBorder(new MatteBorder(0, 4, 0, 0, accent),
                    new EmptyBorder(5, 8, 5, 8)));
            return this;
        }
    }

    private record CapitalChoice(CardDefinition card) {
        @Override public String toString() { return card.name() + " • " + card.hitPoints() + " HP"; }
    }

    private record MulliganChoice(UUID id, CardDefinition card) {
        @Override public String toString() {
            String requirement = card.type() == CardType.LAND || card.type() == CardType.STRUCTURE
                    ? "Turn " + Math.max(1, card.cost()) + ", +" + card.gpGeneration() + " GP/turn"
                    : card.cost() + " GP";
            return card.name() + " — " + card.type() + " — " + requirement;
        }
    }

    private JButton visualChoiceCard(CardDefinition card, int width, int height) {
        return visualChoiceCard(card, width, height, 78);
    }

    private JButton visualChoiceCard(CardDefinition card, int width, int height, int artHeight) {
        JButton result = new JButton(cardHtml(card), CardArtFactory.iconFor(card, width - 16, artHeight));
        Dimension size = new Dimension(width, height);
        result.setPreferredSize(size); result.setMinimumSize(size); result.setMaximumSize(size);
        result.setVerticalAlignment(SwingConstants.TOP);
        result.setHorizontalAlignment(SwingConstants.CENTER);
        result.setHorizontalTextPosition(SwingConstants.CENTER);
        result.setVerticalTextPosition(SwingConstants.BOTTOM);
        result.setForeground(Color.WHITE);
        result.setBackground(blend(PANEL_LIGHT, factionColor(card.faction()), .40f));
        result.setBorder(new CompoundBorder(new LineBorder(factionColor(card.faction()), 2, true),
                new EmptyBorder(6, 6, 6, 6)));
        result.setFocusPainted(false);
        result.setToolTipText(CardRulesText.details(card));
        return result;
    }

    private final class VisualReactionDialog extends JDialog {
        private final int reacting;
        private final List<String> commands;
        private final JPanel spellTray = new JPanel();
        private final Map<BoardPosition, JButton> targets = new LinkedHashMap<>();
        private final JLabel instruction = new JLabel("Choose a spell, then click or drag it to a glowing target.");
        private final JLabel countdown = new JLabel();
        private javax.swing.Timer countdownTimer;
        private Integer selectedHandIndex;
        private String result;

        /** Local games: no server timeout, so no countdown. */
        VisualReactionDialog(int reacting, List<String> commands) {
            this(reacting, commands, 0);
        }

        /**
         * Network games: shows a visible countdown of the server's auto-pass
         * timeout ({@code expiresInSeconds} &gt; 0) and closes when it elapses.
         */
        VisualReactionDialog(int reacting, List<String> commands, int expiresInSeconds) {
            super(InfiniteConquestGui.this, "Reaction Window", true);
            this.reacting = reacting;
            this.commands = commands;
            SoundEffects.play(SoundEffects.Cue.REACTION);
            spellTray.setLayout(new BoxLayout(spellTray, BoxLayout.Y_AXIS));
            spellTray.setBackground(PANEL);
            HexBoardPanel board = new HexBoardPanel();
            board.setLocalPlayer(reacting);board.setShowContext(false);
            board.setOpaque(false);
            for (int y = BoardPosition.HEIGHT - 1; y >= 0; y--) for (int x = 0; x < BoardPosition.WIDTH; x++) {
                BoardPosition position = new BoardPosition(x, y);
                JButton cell = auxiliaryHex(position);
                cell.setText(reactionCellText(position));
                cell.setPreferredSize(new Dimension(135, 82));
                cell.setForeground(Color.WHITE); cell.setBackground(position.isOnPlayerSide(0) ? HUMAN_PLOT : BOT_PLOT);
                state.board().topAt(position).flatMap(state::card)
                        .ifPresent(card -> cell.setIcon(CardArtFactory.iconFor(card.definition(), 72, 42)));
                cell.setHorizontalTextPosition(SwingConstants.CENTER);
                cell.setVerticalTextPosition(SwingConstants.BOTTOM);
                cell.addActionListener(e -> chooseTarget(position));
                cell.addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { previewTarget(position); }

                });
                cell.addFocusListener(new FocusAdapter(){@Override public void focusGained(FocusEvent e){previewTarget(position);}});
                targets.put(position, cell); board.add(cell);
            }
            commands.stream().map(this::handIndex).distinct().forEach(index -> {
                CardDefinition spell = state.card(state.player(reacting).hand().get(index)).orElseThrow().definition();
                JButton card = new JButton("<html><div style='width:210px'><b>"+html(spell.name())+"</b><br>"+spell.goldCost()+" GOLD<br><br>"+html(CardRulesText.spellSummary(spell))+"<br><br><b>Choose targets →</b></div></html>");
                card.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,14));card.setForeground(Color.WHITE);card.setBackground(PANEL_LIGHT);
                card.setHorizontalAlignment(SwingConstants.LEFT);card.setMargin(new Insets(10,10,10,10));
                Dimension size=new Dimension(300,240);card.setPreferredSize(size);card.setMinimumSize(size);card.setMaximumSize(size);
                card.setToolTipText(CardRulesText.details(spell));
                card.addActionListener(e -> selectSpell(index));
                card.addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent e) { selectSpell(index); }
                    @Override public void mouseReleased(MouseEvent e) {
                        Point point = SwingUtilities.convertPoint(card, e.getPoint(), board);
                        targets.entrySet().stream().filter(entry -> entry.getValue().contains(point.x-entry.getValue().getX(), point.y-entry.getValue().getY()))
                                .map(Map.Entry::getKey).findFirst().ifPresent(VisualReactionDialog.this::chooseTarget);
                    }
                });
                spellTray.add(card); spellTray.add(Box.createVerticalStrut(8));
            });
            JButton pass = button("Pass Reaction", e -> dispose());
            instruction.setForeground(Color.WHITE);
            instruction.setText("<html><b>Between opponent actions · You have "+state.player(reacting).currentGp()+" gold.</b><br>Read a spell, choose it, then choose a target. Passing spends nothing.</html>");
            JPanel header = new JPanel(new BorderLayout()); header.setOpaque(false);header.setPreferredSize(new Dimension(800,74));
            header.add(instruction, BorderLayout.CENTER); header.add(pass, BorderLayout.EAST);
            if (expiresInSeconds > 0) {
                countdown.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
                countdown.setForeground(new Color(240, 191, 73));
                countdown.setHorizontalAlignment(SwingConstants.CENTER);
                JPanel timerWrap = new JPanel(new BorderLayout());
                timerWrap.setOpaque(false);
                timerWrap.add(countdown, BorderLayout.CENTER);
                timerWrap.setPreferredSize(new Dimension(150, 74));
                header.add(timerWrap, BorderLayout.WEST);
                int[] remaining = {expiresInSeconds};
                Runnable tick = () -> {
                    if (remaining[0] <= 0) {
                        if (countdownTimer != null) countdownTimer.stop();
                        instruction.setText("<html><b>Time's up — the server auto-passes.</b></html>");
                        dispose();
                        return;
                    }
                    countdown.setText("⏱ " + remaining[0] + "s");
                    countdown.getAccessibleContext().setAccessibleDescription(
                            "Reaction window closes in " + remaining[0] + " seconds");
                    remaining[0]--;
                };
                tick.run();
                countdownTimer = new javax.swing.Timer(1000, e -> tick.run());
                countdownTimer.start();
            }
            JPanel content = panel(new BorderLayout(8, 8)); content.setBorder(new EmptyBorder(12, 12, 12, 12));
            content.add(header, BorderLayout.NORTH);
            content.add(board, BorderLayout.CENTER);
            JScrollPane spells = new JScrollPane(spellTray, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            spells.getViewport().setBackground(PANEL);
            spells.setBorder(new TitledBorder(new LineBorder(CAST, 2), "REACTION SPELLS", TitledBorder.LEFT,
                    TitledBorder.TOP, getFont(), CAST));
            spells.setPreferredSize(new Dimension(330, 400)); content.add(spells, BorderLayout.WEST);
            setContentPane(content); Rectangle usable=GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds(); setSize(Math.min(1100,usable.width-40),Math.min(790,usable.height-50)); setLocationRelativeTo(InfiniteConquestGui.this);
            setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE); refreshTargets();
        }

        String choose() { setVisible(true); return result; }

        @Override public void dispose() {
            if (countdownTimer != null) countdownTimer.stop();
            super.dispose();
        }

        private int handIndex(String command) { return Integer.parseInt(command.split("\\s+")[2]); }
        private BoardPosition target(String command) {
            String[] p = command.split("\\s+"); return new BoardPosition(Integer.parseInt(p[3]), Integer.parseInt(p[4]));
        }
        private void selectSpell(int index) {
            selectedHandIndex = index;
            CardDefinition spell = state.card(state.player(reacting).hand().get(index)).orElseThrow().definition();
            instruction.setText("<html><b>" + html(spell.name()) + " · "+spell.goldCost()+" gold.</b><br>"+html(CardRulesText.spellSummary(spell))+"<br>Hover or focus a gold target to preview the result; click to cast.</html>");
            refreshTargets();
        }
        private void refreshTargets() {
            targets.forEach((position, button) -> {
                boolean legal = selectedHandIndex != null && commands.stream()
                        .anyMatch(command -> handIndex(command) == selectedHandIndex && target(command).equals(position));
                button.setBorder(new CompoundBorder(new BevelBorder(BevelBorder.RAISED),
                        new LineBorder(legal ? CAST : PANEL_LIGHT, legal ? 4 : 1, true)));
                button.putClientProperty("outline", legal ? CAST : PANEL_LIGHT);
                button.putClientProperty("outlineWidth", legal ? 3 : 1);
                button.setEnabled(selectedHandIndex == null || legal);
                if(legal) state.board().topAt(position).flatMap(state::card).ifPresent(target->{
                    var spell=state.card(state.player(reacting).hand().get(selectedHandIndex)).orElseThrow().definition();
                    button.setToolTipText("<html><div style='width:240px'><b>"+html(target.definition().name())+"</b><br>"+html(ReactionPreview.outcome(spell,target))+"</div></html>");
                    button.getAccessibleContext().setAccessibleDescription(ReactionPreview.outcome(spell,target));
                });
                button.repaint();
            });
        }
        private void previewTarget(BoardPosition position) {
            if(selectedHandIndex==null)return;
            var spell=state.card(state.player(reacting).hand().get(selectedHandIndex)).orElseThrow().definition();
            state.board().topAt(position).flatMap(state::card).ifPresent(target->instruction.setText(
                    "<html><b>"+html(spell.name())+" · "+spell.goldCost()+" gold → "+html(target.definition().name())+"</b><br>"+
                    html(ReactionPreview.outcome(spell,target))+"<br>Click a gold target to cast, or choose another spell.</html>"));
        }
        private void chooseTarget(BoardPosition position) {
            if (selectedHandIndex == null) return;
            List<String> matches = commands.stream().filter(command -> handIndex(command) == selectedHandIndex
                    && target(command).equals(position)).toList();
            if (matches.isEmpty()) return;
            if (matches.size() == 1) result = matches.get(0);
            else result = chooseTeleportDestination(matches);
            if (result != null) dispose();
        }
        private String chooseTeleportDestination(List<String> matches) {
            HexBoardPanel grid = new HexBoardPanel();
            grid.setLocalPlayer(reacting);grid.setShowContext(false);
            grid.setBackground(PANEL);
            final String[] selected = {null};
            JDialog picker = new JDialog(this, "Choose teleport destination", true);
            for (int y = BoardPosition.HEIGHT - 1; y >= 0; y--) for (int x = 0; x < BoardPosition.WIDTH; x++) {
                BoardPosition position = new BoardPosition(x, y);
                String match = matches.stream().filter(command -> {
                    String[] p = command.split("\\s+");
                    return Integer.parseInt(p[5]) == position.x() && Integer.parseInt(p[6]) == position.y();
                }).findFirst().orElse(null);
                JButton cell = auxiliaryHex(position);
                cell.putClientProperty("outline", match == null ? PANEL_LIGHT : MOVE);
                cell.setEnabled(match != null); cell.setBackground(match == null ? PANEL_LIGHT : MOVE); cell.setForeground(Color.WHITE);
                cell.addActionListener(e -> { selected[0] = match; picker.dispose(); }); grid.add(cell);
            }
            picker.setContentPane(grid); picker.setSize(620, 520); picker.setLocationRelativeTo(this); picker.setVisible(true);
            return selected[0];
        }
        private String reactionCellText(BoardPosition position) {
            Optional<UUID> top = state.board().topAt(position);
            if (top.isEmpty()) return "<html>" + position.x() + "," + position.y() + "<br>EMPTY</html>";
            CardDefinition card = state.card(top.get()).orElseThrow().definition();
            return "<html>" + position.x() + "," + position.y() + " • " + card.type() + "<br><b>" + html(card.name()) + "</b></html>";
        }
    }

    /**
     * Shared mulligan dialog for local and online games. Shows the player's
     * redacted opening hand at readable size; selected cards move to the
     * discard tray and are replaced from the deck. KEEP HAND keeps everything,
     * REDRAW SELECTED discards exactly the selected cards.
     */
    private final class VisualMulliganDialog extends JDialog {
        private static final Color KEEP_GREEN = new Color(104, 211, 139);
        private static final Color REDRAW_RED = new Color(239, 106, 122);
        private static final Color REDRAW_AMBER = new Color(240, 191, 73);

        private final List<MulliganChoice> choices;
        private final Set<UUID> discarded = new LinkedHashSet<>();
        private final JPanel handTray = new JPanel();
        private final JPanel discardTray = new JPanel();
        private final JLabel handTitle = new JLabel();
        private final JLabel discardTitle = new JLabel();
        private final JLabel statusLine = new JLabel();
        private final JButton keepButton;
        private final JButton redrawButton;
        private UUID dragging;
        private Point pressPoint;

        VisualMulliganDialog(List<MulliganChoice> choices) {
            super(InfiniteConquestGui.this, "Mulligan — Choose Your Opening Hand", true);
            this.choices = choices;
            handTray.setLayout(new BoxLayout(handTray, BoxLayout.X_AXIS));
            discardTray.setLayout(new BoxLayout(discardTray, BoxLayout.X_AXIS));
            handTray.setBackground(new Color(24, 72, 58));
            discardTray.setBackground(new Color(78, 42, 50));

            JLabel title = new JLabel("MULLIGAN");
            title.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            title.setForeground(REDRAW_AMBER);
            JLabel directions = new JLabel("<html>Review your opening hand. <b>Select up to 3 cards</b> you don't want — "
                    + "click a card (or press <b>Space</b> on it), or drag it between trays. "
                    + "Each discarded card is <b>replaced with a fresh card from your deck</b>; "
                    + "cards you don't select stay in your hand.</html>");
            directions.setForeground(Color.WHITE);
            directions.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            JPanel header = new JPanel(new BorderLayout(0, 6));
            header.setOpaque(false);
            header.setBorder(new EmptyBorder(4, 4, 8, 4));
            header.add(title, BorderLayout.NORTH);
            header.add(directions, BorderLayout.CENTER);

            JPanel trays = new JPanel(new GridLayout(2, 1, 0, 10)); trays.setOpaque(false);
            trays.add(tray(handTitle, "OPENING HAND — KEEPING", handTray, KEEP_GREEN));
            trays.add(tray(discardTitle, "DISCARD & REDRAW", discardTray, REDRAW_RED));

            keepButton = button("KEEP HAND", e -> { SoundEffects.play(SoundEffects.Cue.KEEP); discarded.clear(); dispose(); }, true);
            keepButton.setMnemonic(KeyEvent.VK_K);
            keepButton.setToolTipText("Keep your entire opening hand (Alt+K, or Enter)");
            keepButton.getAccessibleContext().setAccessibleDescription(
                    "Keep your entire opening hand and start the game");
            redrawButton = button("REDRAW SELECTED", e -> { SoundEffects.play(SoundEffects.Cue.SHUFFLE); dispose(); }, true);
            redrawButton.setMnemonic(KeyEvent.VK_R);
            redrawButton.setToolTipText("Discard the selected cards and draw replacements (Alt+R)");
            redrawButton.getAccessibleContext().setAccessibleDescription(
                    "Discard the selected cards and draw one replacement for each");
            getRootPane().setDefaultButton(keepButton);
            getRootPane().registerKeyboardAction(e -> { SoundEffects.play(SoundEffects.Cue.KEEP); discarded.clear(); dispose(); },
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

            statusLine.setForeground(Color.WHITE);
            statusLine.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            JPanel footer = new JPanel(new BorderLayout(10, 0)); footer.setOpaque(false);
            footer.setBorder(new EmptyBorder(8, 4, 0, 4));
            footer.add(statusLine, BorderLayout.WEST);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
            buttons.setOpaque(false);
            buttons.add(redrawButton); buttons.add(keepButton);
            footer.add(buttons, BorderLayout.EAST);

            JPanel content = panel(new BorderLayout(8, 8));
            content.setBorder(new EmptyBorder(12, 14, 12, 14));
            content.add(header, BorderLayout.NORTH); content.add(trays, BorderLayout.CENTER); content.add(footer, BorderLayout.SOUTH);
            setContentPane(content); setSize(1300, 760); setLocationRelativeTo(InfiniteConquestGui.this);
            setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
            rebuild();
        }

        Set<UUID> choose() { setVisible(true); return Set.copyOf(discarded); }

        private JPanel tray(JLabel titleLabel, String name, JPanel cards, Color color) {
            titleLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
            titleLabel.setForeground(color);
            JPanel header = new JPanel(new BorderLayout()); header.setOpaque(false);
            header.add(titleLabel, BorderLayout.WEST);
            JPanel result = new JPanel(new BorderLayout(5, 5)); result.setOpaque(false);
            result.add(header, BorderLayout.NORTH);
            JScrollPane scroll = new JScrollPane(cards, ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            scroll.setBorder(new LineBorder(color, 2, true)); result.add(scroll, BorderLayout.CENTER);
            result.getAccessibleContext().setAccessibleName(name);
            return result;
        }

        private void rebuild() {
            handTray.removeAll(); discardTray.removeAll();
            for (MulliganChoice choice : choices) {
                boolean selected = discarded.contains(choice.id());
                JPanel destination = selected ? discardTray : handTray;
                JButton card = visualChoiceCard(choice.card(), 200, 240, 108);
                card.setFocusPainted(true); // keyboard users must see the focused card
                if (selected) {
                    card.setBorder(new CompoundBorder(new LineBorder(REDRAW_RED, 4, true), card.getBorder()));
                }
                String state = selected ? "selected for redraw" : "in your opening hand";
                card.getAccessibleContext().setAccessibleDescription(html(choice.card().name()) + ", "
                        + choice.card().goldCost() + " gold, currently " + state
                        + ". Press Space to " + (selected ? "keep it" : "select it for redraw") + ".");
                // Mouse: click toggles, drag moves between trays. Keyboard: Space/Enter
                // toggle via an explicit binding (no ActionListener, so a mouse click
                // can never double-toggle through button activation).
                card.addMouseListener(new MouseAdapter() {
                    @Override public void mousePressed(MouseEvent e) {
                        dragging = choice.id();
                        pressPoint = e.getPoint();
                    }
                    @Override public void mouseReleased(MouseEvent e) {
                        boolean fromDiscard = discarded.contains(choice.id());
                        Point handPoint = SwingUtilities.convertPoint(card, e.getPoint(), handTray);
                        Point discardPoint = SwingUtilities.convertPoint(card, e.getPoint(), discardTray);
                        if (!fromDiscard && discardTray.contains(discardPoint)) moveToDiscard(choice.id());
                        else if (fromDiscard && handTray.contains(handPoint)) discarded.remove(choice.id());
                        else if (pressPoint != null && pressPoint.distance(e.getPoint()) < 8) toggle(choice.id());
                        dragging = null; pressPoint = null; rebuild();
                    }
                });
                AbstractAction toggleSelection = new AbstractAction() {
                    @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                        toggle(choice.id()); rebuild();
                    }
                };
                card.getInputMap(JComponent.WHEN_FOCUSED)
                        .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "mulligan-toggle");
                card.getInputMap(JComponent.WHEN_FOCUSED)
                        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "mulligan-toggle");
                card.getActionMap().put("mulligan-toggle", toggleSelection);
                destination.add(card); destination.add(Box.createHorizontalStrut(8));
            }
            int keeping = choices.size() - discarded.size();
            handTitle.setText("🛡  OPENING HAND — KEEPING (" + keeping + ")");
            discardTitle.setText("↻  DISCARD & REDRAW (" + discarded.size() + "/3)");
            statusLine.setText("Discarding " + discarded.size() + " of up to 3  •  Keeping " + keeping);
            redrawButton.setText("REDRAW SELECTED" + (discarded.isEmpty() ? "" : " (" + discarded.size() + ")"));
            redrawButton.setEnabled(!discarded.isEmpty());
            handTray.revalidate(); discardTray.revalidate(); handTray.repaint(); discardTray.repaint();
        }

        private void toggle(UUID id) { if (!discarded.remove(id)) moveToDiscard(id); }
        private void moveToDiscard(UUID id) {
            if (discarded.size() >= 3 && !discarded.contains(id)) { Toolkit.getDefaultToolkit().beep(); return; }
            discarded.add(id);
        }
    }

    private record MatchChoice(String humanFaction, CardDefinition humanCapital,
                               String botFaction, CardDefinition botCapital, boolean playerOneBot,
                               BoardPosition humanCapitalPosition, BotDifficulty botDifficulty) { }

    private final class CapitalPlacementPicker extends JPanel {
        private BoardPosition selected = new BoardPosition(1, 0);
        private final Map<BoardPosition, JButton> cells = new LinkedHashMap<>();

        CapitalPlacementPicker() {
            super(new BorderLayout());
            setOpaque(false);
            HexBoardPanel field=new HexBoardPanel();field.setShowContext(false);field.setPreferredSize(new Dimension(760,380));add(field);
            for(int y=5;y>=0;y--)for(int x=0;x<4;x++){
                BoardPosition position=new BoardPosition(x,y);
                JButton cell=new BattlefieldCell();cell.putClientProperty("position",position);cell.putClientProperty("outlineWidth",1);cell.putClientProperty("outline",PANEL_LIGHT);cell.putClientProperty("badge","");
                cell.setBackground(position.isOnPlayerSide(0)?HUMAN_PLOT:BOT_PLOT);cell.setEnabled(position.isOnPlayerSide(0));
                cell.setToolTipText(position.isOnPlayerSide(0)?"Place Capital at "+x+","+y:"Opponent territory");
                cell.addActionListener(e->{selected=position;refreshSelection();});cells.put(position,cell);field.add(cell);
            }
            refreshSelection();
        }

        void verifyChoices() {
            BoardPosition original=selected;
            int count=0;
            for(var entry:cells.entrySet())if(entry.getKey().isOnPlayerSide(0)){
                JButton cell=entry.getValue();Rectangle bounds=cell.getBounds();
                if(bounds.width<44||bounds.height<44||!new Rectangle(cell.getParent().getSize()).contains(bounds))throw new IllegalStateException("Capital placement clipped: "+entry.getKey());
                cell.doClick();if(!selected.equals(entry.getKey()))throw new IllegalStateException("Capital selection failed");count++;
            }
            if(count!=12)throw new IllegalStateException("Expected twelve Capital choices");selected=original;refreshSelection();
        }
        BoardPosition selected() { return selected; }

        private void refreshSelection() {
            cells.forEach((position, cell) -> {
                boolean chosen = position.equals(selected);
                cell.setText(chosen ? "CAPITAL" : (position.x() + 1) + "," + (position.y() + 1));
                cell.putClientProperty("outline",chosen?DEPLOY:PANEL_LIGHT);
                cell.putClientProperty("outlineWidth",chosen?3:1);
                cell.putClientProperty("badge",chosen?"CAPITAL":"");
                cell.setBackground(chosen ? blend(HUMAN_PLOT, DEPLOY, .38f) : position.isOnPlayerSide(0)?HUMAN_PLOT:BOT_PLOT);
                cell.setForeground(Color.WHITE);
            });
        }
    }
    private record DragSource(Integer handIndex, BoardPosition position) { }
    private record EffectBadge(String text, String color) { }

    private final class VictoryPanel extends JPanel {
        private final Color faction;
        private final boolean victory;
        private final List<Point> sparks = new ArrayList<>();
        private javax.swing.Timer animation;
        private float phase;

        VictoryPanel(String factionName, boolean victory) {
            this.faction = factionColor(factionName);
            this.victory = victory;
            setOpaque(true);
            Random random = new Random((factionName + state.turnNumber()).hashCode());
            for (int i = 0; i < 46; i++) sparks.add(new Point(random.nextInt(760), random.nextInt(590)));
        }

        void start() {
            animation = new javax.swing.Timer(32, event -> { phase += .025f; repaint(); });
            animation.start();
        }

        void stop() { if (animation != null) animation.stop(); }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, blend(INK, faction, .48f), getWidth(), getHeight(), INK));
            g.fillRect(0, 0, getWidth(), getHeight());
            int halo = 280 + Math.round(22 * (float)Math.sin(phase * Math.PI * 2));
            Color glow = victory ? new Color(255, 207, 91, 42) : new Color(255, 88, 108, 36);
            g.setColor(glow); g.fillOval(getWidth()/2-halo/2, 45-halo/4, halo, halo);
            VisualEffects.draw(g, victory ? VisualEffects.Sprite.LIGHT : VisualEffects.Sprite.SMOKE,
                    getWidth()/2, 150, halo, victory ? new Color(255,220,124) : new Color(146,110,180),
                    .22f, phase / 3.0);
            for (int i = 0; i < sparks.size(); i++) {
                Point spark = sparks.get(i);
                int y = Math.floorMod(spark.y - Math.round(phase * (18 + i % 24)), Math.max(1, getHeight()));
                float pulse = .35f + .65f * Math.abs((float)Math.sin(phase * 4 + i));
                g.setComposite(AlphaComposite.SrcOver.derive(pulse));
                g.setColor(victory ? new Color(255, 220, 124) : new Color(170, 190, 220));
                int size = 2 + i % 4; g.fillOval(Math.floorMod(spark.x, Math.max(1,getWidth())), y, size, size);
            }
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(255,255,255,20));
            for (int i=0;i<4;i++) g.drawRoundRect(12+i*3,12+i*3,getWidth()-25-i*6,getHeight()-25-i*6,28,28);
            g.dispose();
        }
    }

    private enum Intent {
        MOVE("MOVE", UiTheme.MOVE, "#48b5e6"),
        ATTACK("ATTACK", UiTheme.ATTACK, "#f45c5c"),
        DEPLOY("PLACE ON TOP", UiTheme.DEPLOY, "#68d38b"),
        BURROW("BURROW BELOW TOP", UiTheme.BURROW, "#be79eb"),
        CAST("SPELL TARGET", UiTheme.CAST, "#f6c24e"),
        BLINK("BLINK", UiTheme.SELECTED, "#5bd1ff"),
        CHOOSE("CHOOSE ACTION", Color.WHITE, "#ffffff");

        private final String label;
        private final Color color;
        private final String hex;
        Intent(String label, Color color, String hex) { this.label = label; this.color = color; this.hex = hex; }
        static Intent fromCommand(String command) {
            return switch (command.substring(0, command.indexOf(' '))) {
                case "move" -> MOVE; case "attack" -> ATTACK; case "burrow" -> BURROW;
                case "cast" -> CAST; case "blink" -> BLINK; default -> DEPLOY;
            };
        }
    }

    private enum AnimationStyle { MOVE, BLINK, MELEE, RANGED, SPELL, DEPLOY, PLAY_FLIGHT,
        SNAP_BACK, DESTROY, RULES, DAMAGE }

    private final class CombatOverlay extends JComponent {
        private Animation animation;
        private final ArrayDeque<Animation> queued = new ArrayDeque<>();
        private javax.swing.Timer timer;
        private Runnable sequenceCompletion;
        private boolean sequenceOpen;
        private long shakeStartedAt = -1L;
        private float shakeIntensity;
        private javax.swing.Timer shakeTimer;
        private String bannerText;
        private Color bannerColor;
        private long bannerStartedAt;
        private javax.swing.Timer bannerTimer;

        @Override public boolean contains(int x, int y) { return false; }

        void beginSequence(Runnable completion) {
            if (sequenceOpen || sequenceCompletion != null || animation != null || !queued.isEmpty()) {
                throw new IllegalStateException("Presentation sequences must not overlap");
            }
            sequenceOpen = true;
            sequenceCompletion = Objects.requireNonNull(completion);
        }

        void finishSequence() {
            if (!sequenceOpen) throw new IllegalStateException("No presentation sequence is open");
            sequenceOpen = false;
            if (animation == null && queued.isEmpty()) completeSequence();
        }

        /**
         * Card-play flight: the card arcs from the hand tray (or the top of the
         * screen for the opponent's plays) to the target hex, while a summon
         * swirl spins up at the destination. The arrival burst (DEPLOY) should
         * be queued right after so the landing lands with impact.
         */
        void animatePlayFlight(CardDefinition card, boolean opponentSource, BoardPosition to, Color color) {
            if (!sequenceOpen) throw new IllegalStateException("Animations require an open presentation sequence");
            Image image = CardArtFactory.iconFor(card, 160, 140).getImage();
            Animation requested = new Animation(null, to, color, false, AnimationStyle.PLAY_FLIGHT, 0L,
                    image, opponentSource, Fx.durationNanos(Fx.PLAY_FLIGHT_MS, gameSettings), false, false, null);
            if (animation != null) queued.addLast(requested);
            else start(requested);
        }

        void animateSnapBack(CardDefinition card, BoardPosition attempted, BoardPosition returnBoard) {
            if (!sequenceOpen) throw new IllegalStateException("Animations require an open presentation sequence");
            Image image = CardArtFactory.iconFor(card, 160, 140).getImage();
            Animation requested = new Animation(attempted, returnBoard, ATTACK, false,
                    AnimationStyle.SNAP_BACK, 0L, image, false, Fx.durationNanos(Fx.SNAP_BACK_MS, gameSettings),
                    returnBoard == null, true, null);
            if (animation != null) queued.addLast(requested);
            else start(requested);
        }

        void animateDestroyed(CardDefinition card, int owner, BoardPosition position) {
            if (!sequenceOpen) throw new IllegalStateException("Animations require an open presentation sequence");
            Image image = CardArtFactory.iconFor(card, 160, 140).getImage();
            Animation requested = new Animation(position, position, ATTACK, false,
                    AnimationStyle.DESTROY, 0L, image, owner == 1,
                    Fx.durationNanos(Fx.DESTROY_MS, gameSettings), false, false, null);
            if (animation != null) queued.addLast(requested);
            else start(requested);
        }

        void animateMeleeCard(CardDefinition card, int owner, BoardPosition from, BoardPosition target) {
            if (!sequenceOpen) throw new IllegalStateException("Animations require an open presentation sequence");
            Image image = CardArtFactory.iconFor(card, 160, 140).getImage();
            Animation requested = new Animation(from, target, ATTACK, false, AnimationStyle.MELEE, 0L,
                    image, owner == 1, Fx.durationNanos(Fx.MELEE_MS, gameSettings), false, false, null);
            if (animation != null) queued.addLast(requested);
            else start(requested);
        }

        void animateBoardCard(CardDefinition card, int owner, BoardPosition from, BoardPosition to,
                              Color color, AnimationStyle style) {
            if (!sequenceOpen) throw new IllegalStateException("Animations require an open presentation sequence");
            Image image = CardArtFactory.iconFor(card, 160, 140).getImage();
            Animation requested = new Animation(from, to, color, false, style, 0L,
                    image, owner == 1, Fx.durationNanos(Fx.MOVE_MS, gameSettings), false, false, null);
            if (animation != null) queued.addLast(requested);
            else start(requested);
        }

        void animate(BoardPosition from, BoardPosition to, Color color, boolean fromRules) {
            animate(from, to, color, fromRules, fromRules ? AnimationStyle.RULES
                    : from == null ? AnimationStyle.SPELL : AnimationStyle.MOVE);
        }

        void animate(BoardPosition from, BoardPosition to, Color color, boolean fromRules, AnimationStyle style) {
            animate(from, to, color, fromRules, style, Fx.GENERIC_MS);
        }

        void animate(BoardPosition from, BoardPosition to, Color color, boolean fromRules,
                     AnimationStyle style, int durationMs) {
            if (!sequenceOpen) throw new IllegalStateException("Animations require an open presentation sequence");
            Animation requested = new Animation(from, to, color, fromRules, style, 0L,
                    null, false, Fx.durationNanos(durationMs, gameSettings), false, false, null);
            if (animation != null) {
                queued.addLast(requested);
                return;
            }
            start(requested);
        }

        /** Floating damage number with a tile hit-flash. Queued after the strike animation. */
        void animateDamage(BoardPosition target, int amount) {
            if (!sequenceOpen) throw new IllegalStateException("Animations require an open presentation sequence");
            Animation requested = new Animation(target, target, new Color(255, 110, 95), false,
                    AnimationStyle.DAMAGE, 0L, null, false,
                    Fx.durationNanos(Fx.DAMAGE_FLOAT_MS, gameSettings), false, false, "-" + amount);
            if (animation != null) queued.addLast(requested);
            else start(requested);
        }

        /** Screen shake; intensity ~1 for capital hits, lower for ordinary destruction. */
        void shake(float intensity) {
            if (Fx.reduced(gameSettings)) return;
            if (!SwingUtilities.isEventDispatchThread()) {
                SwingUtilities.invokeLater(() -> shake(intensity));
                return;
            }
            shakeIntensity = intensity;
            shakeStartedAt = System.nanoTime();
            if (shakeTimer == null) {
                shakeTimer = new javax.swing.Timer(16, event -> {
                    boolean done = System.nanoTime() - shakeStartedAt >= Fx.nanos(Fx.SHAKE_MS);
                    if (done) {
                        ((javax.swing.Timer) event.getSource()).stop();
                        shakeTimer = null;
                        shakeStartedAt = -1L;
                    }
                    repaint();
                });
                shakeTimer.setCoalesce(true);
                shakeTimer.start();
            }
            repaint();
        }

        /** Full-board turn banner ("YOUR TURN" etc.). Independent of the animation queue. */
        void showBanner(String text, Color color) {
            if (Fx.reduced(gameSettings)) return;
            if (!SwingUtilities.isEventDispatchThread()) {
                SwingUtilities.invokeLater(() -> showBanner(text, color));
                return;
            }
            bannerText = text;
            bannerColor = color;
            bannerStartedAt = System.nanoTime();
            if (bannerTimer == null) {
                bannerTimer = new javax.swing.Timer(16, event -> {
                    boolean done = bannerText == null
                            || System.nanoTime() - bannerStartedAt >= Fx.nanos(Fx.TURN_BANNER_MS);
                    if (done) {
                        ((javax.swing.Timer) event.getSource()).stop();
                        bannerTimer = null;
                        bannerText = null;
                    }
                    repaint();
                });
                bannerTimer.setCoalesce(true);
                bannerTimer.start();
            }
            repaint();
        }

        /** Floating damage number with a tile hit-flash. */
        private void drawDamage(Graphics2D g, JButton targetButton, Point target, float progress) {
            String text = animation.overlayText() == null ? "" : animation.overlayText();
            // Hit-flash: warm wash over the struck tile, easing out so it melts away.
            float flashWindow = Fx.HIT_FLASH_MS / (float) Fx.DAMAGE_FLOAT_MS;
            if (progress < flashWindow && targetButton != null) {
                float flashAlpha = 1f - Fx.easeOutCubic(progress / flashWindow);
                Rectangle tile = SwingUtilities.convertRectangle(
                        targetButton, targetButton.getBounds(), this);
                g.setComposite(AlphaComposite.SrcOver.derive(0.55f * flashAlpha));
                g.setColor(animation.color());
                g.fillRoundRect(tile.x + 4, tile.y + 4,
                        Math.max(8, tile.width - 8), Math.max(8, tile.height - 8), 18, 18);
            }
            // Rising number: pops in, drifts up, holds so it can be read, then
            // eases out on the fade instead of snapping away.
            float rise = Fx.easeOutCubic(Math.min(1f, progress * 1.15f));
            float pop = 1f + 0.35f * Math.max(0f, 1f - progress * 5f);
            float alpha = progress < 0.55f ? 1f
                    : 1f - Fx.easeInOutQuad(Math.min(1f, (progress - 0.55f) / 0.45f));
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, Math.round(30 * pop)));
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            int width = metrics.stringWidth(text);
            int x = target.x - width / 2;
            int y = Math.round(target.y - 46 * rise);
            g.setComposite(AlphaComposite.SrcOver.derive(0.75f * alpha));
            g.setColor(Color.BLACK);
            g.drawString(text, x + 2, y + 2);
            g.setComposite(AlphaComposite.SrcOver.derive(alpha));
            g.setColor(new Color(255, 122, 105));
            g.drawString(text, x, y);
        }

        /** "YOUR TURN" style banner sweeping across the board. */
        private void drawBanner(Graphics2D g, long now) {
            float total = Fx.nanos(Fx.TURN_BANNER_MS);
            float bp = Math.min(1f, (now - bannerStartedAt) / total);
            Font font = new Font(Font.SANS_SERIF, Font.BOLD, 46);
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            int textWidth = metrics.stringWidth(bannerText);
            float sweep = Fx.easeInOutQuad(Math.min(1f, bp / 0.45f));
            int x = Math.round(-textWidth + (getWidth() / 2f - textWidth / 2f + textWidth) * sweep);
            int y = getHeight() / 2;
            float alpha = bp < 0.70f ? 1f
                    : 1f - Fx.easeInOutQuad(Math.min(1f, (bp - 0.70f) / 0.30f));
            g.setComposite(AlphaComposite.SrcOver.derive(alpha));
            g.setColor(new Color(0, 0, 0, 170));
            g.drawString(bannerText, x + 3, y + 3);
            g.setColor(bannerColor);
            g.drawString(bannerText, x, y);
        }

        private void start(Animation requested) {
            animation = new Animation(requested.from(), requested.to(), requested.color(),
                    requested.fromRules(), requested.style(), System.nanoTime(),
                    requested.cardImage(), requested.opponentSource(), requested.durationNanos(),
                    requested.returnToHand(), requested.spring(), requested.overlayText());
            timer = new javax.swing.Timer(16, event -> {
                repaint();
                if (animation != null && animation.progress() >= 1f) {
                    if (queued.isEmpty()) {
                        ((javax.swing.Timer) event.getSource()).stop();
                        animation = null;
                        repaint();
                        completeSequence();
                    } else {
                        Animation next = queued.removeFirst();
                        animation = new Animation(next.from(), next.to(), next.color(),
                                next.fromRules(), next.style(), System.nanoTime(),
                                next.cardImage(), next.opponentSource(), next.durationNanos(),
                                next.returnToHand(), next.spring(), next.overlayText());
                    }
                }
            });
            timer.setCoalesce(true);timer.start();
            repaint();
        }

        private void completeSequence() {
            Runnable completed = sequenceCompletion;
            sequenceCompletion = null;
            // Restore masked tiles in this same frame, before the repaint can show a blank hex.
            if (completed != null) completed.run();
        }

        @Override protected void paintComponent(Graphics graphics) {
            long now = System.nanoTime();
            boolean shaking = shakeStartedAt >= 0
                    && now - shakeStartedAt < Fx.nanos(Fx.SHAKE_MS)
                    && !Fx.reduced(gameSettings);
            boolean banner = bannerText != null
                    && now - bannerStartedAt < Fx.nanos(Fx.TURN_BANNER_MS);
            if (animation == null && !shaking && !banner) return;
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            if (shaking) {
                float shakeProgress = (now - shakeStartedAt) / (float) Fx.nanos(Fx.SHAKE_MS);
                // Ease the decay so the board settles instead of snapping back.
                float decay = 1f - Fx.easeOutCubic(Math.min(1f, shakeProgress));
                int dx = Math.round((float) (Math.sin(shakeProgress * 38.0) * 6.0 * decay * shakeIntensity));
                int dy = Math.round((float) (Math.cos(shakeProgress * 31.0) * 6.0 * decay * shakeIntensity));
                g.translate(dx, dy);
            }
            if (banner) drawBanner(g, now);
            if (animation == null) { g.dispose(); return; }
            JButton targetButton = animation.returnToHand() ? null : boardButtons.get(animation.to());
            if (!animation.returnToHand() && (targetButton == null || !targetButton.isShowing())) {
                g.dispose();
                return;
            }
            Point target = animation.returnToHand()
                    ? SwingUtilities.convertPoint(handPanel, Math.max(20, handPanel.getWidth() / 2), 0, this)
                    : SwingUtilities.convertPoint(targetButton,
                            targetButton.getWidth() / 2, targetButton.getHeight() / 2, this);
            Point source;
            if (animation.fromRules()) {
                source = new Point(target.x, 8);
            } else if (animation.from() == null) {
                source = animation.opponentSource()
                        ? new Point(target.x, 8)
                        : SwingUtilities.convertPoint(handPanel,
                                Math.max(20, handPanel.getWidth() / 2), 0, this);
            } else {
                JButton sourceButton = boardButtons.get(animation.from());
                if (sourceButton == null || !sourceButton.isShowing()) { g.dispose(); return; }
                source = SwingUtilities.convertPoint(sourceButton,
                        sourceButton.getWidth() / 2, sourceButton.getHeight() / 2, this);
            }

            float progress = animation.progress();
            boolean cardLunge = animation.cardImage() != null && animation.style() == AnimationStyle.MELEE;
            boolean cardDestroy = animation.cardImage() != null && animation.style() == AnimationStyle.DESTROY;
            boolean playFlight = animation.cardImage() != null && animation.style() == AnimationStyle.PLAY_FLIGHT;
            float fade = cardDestroy ? 1f - progress : animation.cardImage()!=null ? 1f
                    : progress < .72f ? 1f : Math.max(0f, (1f - progress) / .28f);
            g.setComposite(AlphaComposite.SrcOver.derive(.88f * fade));
            g.setColor(animation.color());
            float travel = cardDestroy ? 0f
                    : cardLunge ? (float) Math.sin(Math.PI * progress) * .72f
                    : animation.spring() ? Fx.spring(progress)
                    : Fx.easeOutCubic(animation.cardImage() == null ? Math.min(1f, progress / .72f) : progress);
            int orbX = Math.round(source.x + (target.x - source.x) * travel);
            int orbY = Math.round(source.y + (target.y - source.y) * travel);
            if (animation.cardImage() != null) {
                // Play flights arc higher so the throw reads clearly across the board.
                int arc = playFlight ? Math.min(72, Math.abs(target.x - source.x) / 5 + 26)
                        : Math.min(22, Math.abs(target.x-source.x)/12);
                float inverse = 1f - travel;
                orbX = Math.round(inverse * inverse * source.x
                        + 2 * inverse * travel * ((source.x + target.x) / 2f)
                        + travel * travel * target.x);
                orbY = Math.round(inverse * inverse * source.y
                        + 2 * inverse * travel * (Math.min(source.y, target.y) - arc)
                        + travel * travel * target.y);
                float collapse = cardDestroy ? Fx.easeOutCubic(progress) : 0f;
                // Landing pop: a spring overshoot on the card scale through the
                // final LAND_POP_MS of the flight so the arrival has impact.
                float popScale = 1f;
                if (playFlight) {
                    float popT = (progress * Fx.PLAY_FLIGHT_MS - (Fx.PLAY_FLIGHT_MS - Fx.LAND_POP_MS))
                            / (float) Fx.LAND_POP_MS;
                    if (popT > 0f) popScale = 1f + .16f * (Fx.spring(Math.min(1f, popT)) - Math.min(1f, popT));
                }
                JButton footprint=targetButton!=null?targetButton:boardButtons.values().iterator().next();
                int tileWidth=Math.max(44,footprint.getWidth()),tileHeight=Math.max(38,footprint.getHeight());
                int width=Math.max(16,Math.round(tileWidth*(cardDestroy?1f-.68f*collapse:1f)*popScale));
                int height=Math.max(16,Math.round(tileHeight*(cardDestroy?1f-.68f*collapse:1f)*popScale));
                orbX=Math.max(width/2+6,Math.min(getWidth()-width/2-6,orbX));
                orbY=Math.max(height/2+6,Math.min(getHeight()-height/2-6,orbY));
                g.setComposite(AlphaComposite.SrcOver.derive(.30f * fade));
                g.setColor(Color.BLACK);
                g.fillRoundRect(orbX - width / 2 + 6, orbY - height / 2 + 8, width, height, 16, 16);
                g.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, fade)));
                int left=orbX-width/2,top=orbY-height/2;
                Polygon sprite=new Polygon(new int[]{left+width/2,left+width,left+width,left+width/2,left,left},new int[]{top,top+height/4,top+3*height/4,top+height,top+3*height/4,top+height/4},6);
                Shape oldClip=g.getClip();g.clip(sprite);
                g.drawImage(animation.cardImage(),left,top,width,height,null);g.setClip(oldClip);
                g.setColor(animation.color());g.setStroke(new BasicStroke(2f));g.draw(sprite);
                if (playFlight && Fx.particles(gameSettings)) {
                    // Summon swirl: spins up at the destination while the card
                    // is in flight, then the DEPLOY burst lands on top of it.
                    VisualEffects.draw(g, VisualEffects.Sprite.TWIRL, target.x, target.y,
                            Math.round(48 + 96 * progress), animation.color(),
                            .5f * Math.min(1f, progress * 1.5f), progress * 5.0);
                    // Faint motion trail behind the flying card.
                    VisualEffects.draw(g, VisualEffects.Sprite.TRACE, orbX, orbY,
                            Math.max(24, width / 2), animation.color(), .3f * (1f - progress),
                            Math.atan2(target.y - source.y, target.x - source.x));
                } else {
                    VisualEffects.draw(g, VisualEffects.Sprite.LIGHT, orbX, orbY,
                            Math.max(width, height), animation.color(), .36f, progress);
                }
                if (cardLunge && progress > .34f && progress < .68f) {
                    float strikeAlpha = 1f - Math.abs(progress - .51f) / .17f;
                    VisualEffects.draw(g, VisualEffects.Sprite.SLASH, target.x, target.y, 104,
                            Color.WHITE, Math.max(0f, strikeAlpha), Math.atan2(target.y - source.y, target.x - source.x));
                }
                if (cardDestroy && Fx.particles(gameSettings)) {
                    // Textured debris chunks instead of flat circles.
                    VisualEffects.Sprite[] debris = {VisualEffects.Sprite.DEBRIS_A,
                            VisualEffects.Sprite.DEBRIS_B, VisualEffects.Sprite.DEBRIS_C};
                    Color ember = new Color(255, 140, 90);
                    for (int index = 0; index < 12; index++) {
                        double angle = index * Math.PI / 6.0 + .35;
                        int distance = Math.round(18 + progress * 74);
                        int particleX = orbX + (int) Math.round(Math.cos(angle) * distance);
                        int particleY = orbY + (int) Math.round(Math.sin(angle) * distance);
                        int size = Math.max(8, Math.round(10 + 22 * (1f - progress)));
                        VisualEffects.draw(g, debris[index % debris.length], particleX, particleY,
                                size, ember, Math.max(0f, .9f * (1f - progress)), angle + progress * 3.0);
                    }
                    VisualEffects.draw(g, VisualEffects.Sprite.SPARK, orbX, orbY,
                            Math.round(72 + progress * 86), ATTACK, .72f * (1f - progress), progress * 2.4);
                }
                g.dispose();
                return;
            }
            if (animation.style() == AnimationStyle.DAMAGE) {
                drawDamage(g, targetButton, target, progress);
                g.dispose();
                return;
            }
            VisualEffects.Sprite traveling = switch (animation.style()) {
                case MOVE -> VisualEffects.Sprite.TRACE;
                case BLINK, SPELL -> VisualEffects.Sprite.MAGIC;
                case MELEE -> VisualEffects.Sprite.SLASH;
                case RANGED -> VisualEffects.Sprite.SPARK;
                case DEPLOY -> VisualEffects.Sprite.LIGHT;
                case PLAY_FLIGHT -> VisualEffects.Sprite.TRACE; // flights always carry a card; unreachable
                case SNAP_BACK -> VisualEffects.Sprite.TRACE;
                case DESTROY -> VisualEffects.Sprite.SPARK;
                case RULES -> VisualEffects.Sprite.FLAME;
                case DAMAGE -> VisualEffects.Sprite.SPARK;
            };
            VisualEffects.draw(g, traveling, orbX, orbY,
                    animation.style()==AnimationStyle.SPELL ? 72 : 48,
                    animation.color(), .72f*fade, Math.atan2(target.y-source.y,target.x-source.x));

            if (animation.style() == AnimationStyle.MOVE || animation.style() == AnimationStyle.BLINK) {
                int arc = Math.max(28, Math.abs(target.x-source.x)/5 + 18);
                QuadCurve2D path = new QuadCurve2D.Float(source.x, source.y,
                        (source.x+target.x)/2f, Math.min(source.y,target.y)-arc, target.x,target.y);
                g.setStroke(new BasicStroke(animation.style()==AnimationStyle.BLINK?7f:4f,
                        BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND)); g.draw(path);
                if(animation.style()==AnimationStyle.BLINK){g.setComposite(AlphaComposite.SrcOver.derive(.34f*fade));
                    g.setStroke(new BasicStroke(15f));g.draw(path);g.setComposite(AlphaComposite.SrcOver.derive(.88f*fade));}
            } else {
                g.setStroke(new BasicStroke(animation.style()==AnimationStyle.MELEE?8f:5f,
                        BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
                if(animation.style()==AnimationStyle.RULES){
                    Path2D bolt=new Path2D.Double();bolt.moveTo(source.x,source.y);Random r=new Random(animation.startedAt());
                    for(int i=1;i<6;i++)bolt.lineTo(source.x+(target.x-source.x)*i/6.0+r.nextInt(19)-9,source.y+(target.y-source.y)*i/6.0);bolt.lineTo(target.x,target.y);g.draw(bolt);
                }else g.drawLine(source.x,source.y,target.x,target.y);
            }

            double angle = Math.atan2(target.y - source.y, target.x - source.x);
            int arrow = 15;
            Path2D head = new Path2D.Double();
            head.moveTo(target.x, target.y);
            head.lineTo(target.x - arrow * Math.cos(angle - .48), target.y - arrow * Math.sin(angle - .48));
            head.lineTo(target.x - arrow * Math.cos(angle + .48), target.y - arrow * Math.sin(angle + .48));
            head.closePath();
            g.fill(head);

            g.setColor(Color.WHITE);
            if(animation.style()==AnimationStyle.RANGED){
                Path2D projectile=new Path2D.Double();projectile.moveTo(orbX+10,orbY);projectile.lineTo(orbX,orbY-5);projectile.lineTo(orbX-10,orbY);projectile.lineTo(orbX,orbY+5);projectile.closePath();g.fill(projectile);
            }else if(animation.style()==AnimationStyle.MELEE&&progress>.34f){
                int slash=24+Math.round(18*progress);g.setStroke(new BasicStroke(6f));g.drawLine(target.x-slash,target.y+slash,target.x+slash,target.y-slash);g.drawLine(target.x-slash/2,target.y-slash,target.x+slash/2,target.y+slash);
            }else{g.fillOval(orbX-7,orbY-7,14,14);}
            g.setColor(animation.color());
            g.setStroke(new BasicStroke(4f));
            int pulse = 22 + Math.round(26 * progress);
            g.drawOval(target.x - pulse / 2, target.y - pulse / 2, pulse, pulse);
            if(animation.style()==AnimationStyle.SPELL){
                for(int i=0;i<3;i++){int ring=pulse+i*18;g.drawOval(target.x-ring/2,target.y-ring/2,ring,ring);double a=progress*10+i*2.1;g.fillOval(target.x+(int)(Math.cos(a)*ring/2)-4,target.y+(int)(Math.sin(a)*ring/2)-4,8,8);}
                VisualEffects.draw(g,VisualEffects.Sprite.ORBIT,target.x,target.y,
                        90+Math.round(progress*44),animation.color(),.68f*fade,progress*2.5);
            }
            if(animation.style()==AnimationStyle.DEPLOY){
                int highlightWidth = Math.max(96, targetButton == null ? 96 : targetButton.getWidth() - 18);
                int highlightHeight = Math.max(42, targetButton == null ? 42 : targetButton.getHeight() - 18);
                g.setComposite(AlphaComposite.SrcOver.derive(.3f*fade));
                g.fillRoundRect(target.x-highlightWidth/2,target.y-highlightHeight/2,
                        highlightWidth,highlightHeight,24,24);
                VisualEffects.draw(g,VisualEffects.Sprite.LIGHT,target.x,target.y,
                        100+Math.round(progress*30),animation.color(),.72f*fade,0);
            }
            if(animation.style()==AnimationStyle.MELEE&&progress>.28f)
                VisualEffects.draw(g,VisualEffects.Sprite.SLASH,target.x,target.y,112,
                        Color.WHITE,.8f*fade,angle);
            VisualEffects.draw(g,VisualEffects.Sprite.SPARK,target.x,target.y,
                    62+Math.round(progress*70),animation.color(),.62f*fade,progress*1.8);
            for(int i=0;i<8;i++){double a=i*Math.PI/4+progress*2;int distance=Math.round(progress*48);int px=target.x+(int)(Math.cos(a)*distance),py=target.y+(int)(Math.sin(a)*distance);g.fillOval(px-3,py-3,6,6);}
            g.dispose();
        }

    }

    private record Animation(BoardPosition from, BoardPosition to, Color color,
                             boolean fromRules, AnimationStyle style, long startedAt,
                             Image cardImage, boolean opponentSource, long durationNanos,
                             boolean returnToHand, boolean spring, String overlayText) {
        float progress() {
            return Math.min(1f, (System.nanoTime() - startedAt) / (float) durationNanos);
        }
    }
}
