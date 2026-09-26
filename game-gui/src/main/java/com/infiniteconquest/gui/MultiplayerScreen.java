package com.infiniteconquest.gui;

import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.DeckBuild;
import com.infiniteconquest.core.GameSnapshot;
import com.infiniteconquest.gui.net.LobbyService;
import com.infiniteconquest.gui.net.NetSession;
import com.infiniteconquest.net.EmbeddedServer;
import com.infiniteconquest.net.Protocol;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.function.Function;

/**
 * Online play: identity, deck pick, host/join, and the pre-match lobby.
 * The host runs an embedded server in-process; both sides then talk to it
 * over TCP (loopback for same-machine play).
 */
final class MultiplayerScreen extends SubScreen implements NetSession.Listener {
    private final GameShell shell;
    private final GameSettings settings;
    private final GameContext context;

    private JTextField nameField;
    private JToggleButton zeusButton;
    private JToggleButton poseidonButton;
    private JTextField addressField;
    private JTextField portField;
    private JButton hostButton;
    private JButton joinButton;
    // Internet play controls.
    private JButton hostOnlineButton;
    private JTextField tunnelLinkField;
    private JButton joinLinkButton;
    private DefaultListModel<LobbyService.LobbyEntry> lobbyBrowserModel;
    private JList<LobbyService.LobbyEntry> lobbyBrowserList;
    private JButton refreshBrowserButton;
    private JButton joinSelectedButton;
    private JTextField codeField;
    private JButton joinCodeButton;
    private JButton quickMatchButton;
    private JButton cancelQueueButton;
    private JLabel queueStatus;
    private JLabel tunnelShareLabel;
    // One inline error line per setup section; blank (invisible) until set.
    private JLabel identityError;
    private JLabel deckError;
    private JLabel internetError;
    private JLabel publicGamesError;
    private JLabel quickMatchError;
    private JLabel lanError;
    private JTextField tunnelShareField;
    private JButton copyLinkButton;
    private JPanel tunnelSharePanel;
    private JPanel lobbyPanel;
    private JLabel lobbyTitle;
    private DefaultListModel<String> playerListModel;
    private JLabel lobbyStatus;
    private JButton startButton;
    private JButton leaveButton;

    private NetSession session;
    private String faction = "ZEUS";
    private boolean inMatch;
    private boolean autoStart;
    private volatile Thread quickMatchThread;

    MultiplayerScreen(GameShell shell, GameSettings settings, GameContext context) {
        super(shell, "Multiplayer");
        this.shell = shell;
        this.settings = settings;
        this.context = context;
    }

    @Override
    protected JComponent buildContent() {
        JPanel card = SubScreen.card();
        card.setMaximumSize(new Dimension(640, 900));

        card.add(identitySection());
        card.add(Box.createVerticalStrut(14));
        card.add(deckSection());
        card.add(Box.createVerticalStrut(14));
        card.add(internetSection());
        card.add(Box.createVerticalStrut(14));

        if (lobbyService() != null) {
            card.add(publicGamesSection());
            card.add(Box.createVerticalStrut(14));
            card.add(quickMatchSection());
            card.add(Box.createVerticalStrut(14));
        } else {
            card.add(caption("Lobby browser, join codes, quick match, and ratings need a lobby server — "
                    + "set one under Settings \u2192 Lobby server. Direct tunnel links work without it."));
            card.add(Box.createVerticalStrut(14));
        }
        card.add(lanSection());
        card.add(Box.createVerticalStrut(14));

        lobbyPanel = buildLobbyPanel();
        lobbyPanel.setVisible(false);
        card.add(lobbyPanel);

        // The menu is taller than short windows, so it must scroll — but the
        // centered GridBagLayout wrapper drops zero-weight components to their
        // *minimum* size when the preferred size doesn't fit, and a
        // JScrollPane's stock minimum is a ~21x5 sliver. Keep the full
        // preferred width so the menu stays centered instead of vanishing;
        // the height still collapses, and contentConstraints() below makes the
        // pane fill the wrapper vertically so it scrolls (2026-09-25: the
        // screen showed only its title and back button).
        JScrollPane scroll = new JScrollPane(card) {
            @Override
            public Dimension getMinimumSize() {
                Dimension minimum = super.getMinimumSize();
                minimum.width = Math.max(minimum.width, getPreferredSize().width);
                return minimum;
            }
        };
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        return scroll;
    }

    /** The menu scrolls, so it fills the wrapper vertically instead of being
     * centered at a preferred height the window may not have (see the
     * GridBagLayout note in {@link SubScreen#contentConstraints()}). */
    @Override
    protected GridBagConstraints contentConstraints() {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.fill = GridBagConstraints.VERTICAL;
        constraints.weighty = 1.0;
        return constraints;
    }

    /** Your Identity section: display name plus its privacy caption. */
    private JPanel identitySection() {
        JPanel section = newSection("Your Identity");
        nameField = styledField(settings.playerName, 24);
        nameField.setToolTipText("Shown to other players. Max 24 characters.");
        nameField.addActionListener(e -> saveName());
        section.add(fieldRow("Display name", nameField));
        section.add(Box.createVerticalStrut(6));
        section.add(sectionCaption("Public — other players will see this name. Your player ID stays private."));
        section.add(Box.createVerticalStrut(6));
        identityError = sectionErrorLabel();
        section.add(identityError);
        return section;
    }

    /** Your Deck section: faction toggle plus its caption. */
    private JPanel deckSection() {
        JPanel section = newSection("Your Deck");
        JPanel factionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        factionRow.setOpaque(false);
        zeusButton = factionToggle("Zeus");
        poseidonButton = factionToggle("Poseidon");
        ButtonGroup group = new ButtonGroup();
        group.add(zeusButton);
        group.add(poseidonButton);
        zeusButton.setSelected(true);
        factionRow.add(zeusButton);
        factionRow.add(poseidonButton);
        section.add(fieldRow("Your deck", factionRow));
        section.add(Box.createVerticalStrut(6));
        section.add(sectionCaption("Your saved deck for the chosen faction. The host will see your deck list."));
        section.add(Box.createVerticalStrut(6));
        deckError = sectionErrorLabel();
        section.add(deckError);
        return section;
    }

    /** Internet Play section: host online, join by tunnel link, link sharing. */
    private JPanel internetSection() {
        JPanel section = newSection("Internet Play");
        section.add(sectionCaption("Play over the internet through a free Cloudflare tunnel. "
                + "Neither player learns the other's IP address."));
        section.add(Box.createVerticalStrut(8));
        hostOnlineButton = new ShellUi.MenuButton("Host Online Game");
        hostOnlineButton.addActionListener(e -> hostOnlineGame());
        section.add(sectionButtons(hostOnlineButton));
        section.add(Box.createVerticalStrut(6));
        section.add(sectionCaption("Hosting starts a tunnel on your machine — keep the game open while playing."));
        section.add(Box.createVerticalStrut(8));

        tunnelLinkField = styledField("", 24);
        tunnelLinkField.setToolTipText("Paste a wss:// tunnel link from the host.");
        joinLinkButton = new ShellUi.MenuButton("Join by Link");
        joinLinkButton.addActionListener(e -> joinByLink());
        section.add(rowLabel("Tunnel link"));
        section.add(Box.createVerticalStrut(4));
        JPanel linkRow = new JPanel(new BorderLayout(10, 0));
        linkRow.setOpaque(false);
        linkRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        linkRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        linkRow.add(tunnelLinkField, BorderLayout.CENTER);
        linkRow.add(joinLinkButton, BorderLayout.EAST);
        section.add(linkRow);
        section.add(Box.createVerticalStrut(6));
        section.add(sectionCaption("The host will see your deck list. No account needed."));
        section.add(Box.createVerticalStrut(8));

        tunnelSharePanel = new JPanel();
        tunnelSharePanel.setOpaque(false);
        tunnelSharePanel.setLayout(new BoxLayout(tunnelSharePanel, BoxLayout.Y_AXIS));
        tunnelSharePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        tunnelShareLabel = new JLabel(" ");
        tunnelShareLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        tunnelShareLabel.setForeground(new Color(240, 191, 73));
        tunnelShareLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        tunnelSharePanel.add(tunnelShareLabel);
        JPanel shareRow = new JPanel(new BorderLayout(8, 0));
        shareRow.setOpaque(false);
        shareRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        shareRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        tunnelShareField = styledField("", 26);
        tunnelShareField.setEditable(false);
        copyLinkButton = new ShellUi.MenuButton("Copy Link");
        copyLinkButton.addActionListener(e -> {
            copyToClipboard(tunnelShareField.getText());
            copyLinkButton.setText("Copied!");
            new javax.swing.Timer(1500, ev -> copyLinkButton.setText("Copy Link")).start();
        });
        shareRow.add(tunnelShareField, BorderLayout.CENTER);
        shareRow.add(copyLinkButton, BorderLayout.EAST);
        tunnelSharePanel.add(Box.createVerticalStrut(4));
        tunnelSharePanel.add(shareRow);
        tunnelSharePanel.setVisible(false);
        section.add(tunnelSharePanel);

        section.add(Box.createVerticalStrut(6));
        internetError = sectionErrorLabel();
        section.add(internetError);
        return section;
    }

    /** Public lobby browser: refresh, join selected, join by code. */
    private JPanel publicGamesSection() {
        JPanel section = newSection("Public Games");
        lobbyBrowserModel = new DefaultListModel<>();
        lobbyBrowserList = new JList<>(lobbyBrowserModel);
        lobbyBrowserList.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        lobbyBrowserList.setBackground(new Color(25, 35, 52));
        lobbyBrowserList.setForeground(new Color(232, 236, 244));
        lobbyBrowserList.setVisibleRowCount(3);
        lobbyBrowserList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                          boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof LobbyService.LobbyEntry entry) {
                    setText(entry.hostName() + "  (rating " + entry.hostRating()
                            + ")  \u2014  code " + entry.code());
                }
                return this;
            }
        });
        JScrollPane scroll = new JScrollPane(lobbyBrowserList);
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(scroll);
        section.add(Box.createVerticalStrut(6));

        refreshBrowserButton = new ShellUi.MenuButton("Refresh");
        refreshBrowserButton.addActionListener(e -> refreshLobbyBrowser());
        joinSelectedButton = new ShellUi.MenuButton("Join Selected");
        joinSelectedButton.addActionListener(e -> joinSelectedLobby());
        section.add(sectionButtons(refreshBrowserButton, joinSelectedButton));
        section.add(Box.createVerticalStrut(6));

        codeField = styledField("", 10);
        codeField.setToolTipText("6-character lobby code from the host.");
        joinCodeButton = new ShellUi.MenuButton("Join by Code");
        joinCodeButton.addActionListener(e -> joinByCode());
        section.add(rowLabel("Lobby code"));
        section.add(Box.createVerticalStrut(4));
        JPanel codeRow = new JPanel(new BorderLayout(10, 0));
        codeRow.setOpaque(false);
        codeRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        codeRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 56));
        codeRow.add(codeField, BorderLayout.CENTER);
        codeRow.add(joinCodeButton, BorderLayout.EAST);
        section.add(codeRow);
        section.add(Box.createVerticalStrut(6));
        publicGamesError = sectionErrorLabel();
        section.add(publicGamesError);
        return section;
    }

    /** Quick match: enqueue, poll, and either host or join the pairing. */
    private JPanel quickMatchSection() {
        JPanel section = newSection("Quick Match");
        quickMatchButton = new ShellUi.MenuButton("Find Match");
        quickMatchButton.addActionListener(e -> findMatch());
        cancelQueueButton = new ShellUi.MenuButton("Cancel");
        cancelQueueButton.addActionListener(e -> cancelQuickMatch());
        cancelQueueButton.setVisible(false);
        section.add(sectionButtons(quickMatchButton, cancelQueueButton));
        queueStatus = new JLabel(" ");
        queueStatus.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 14));
        queueStatus.setForeground(new Color(170, 178, 190));
        queueStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(Box.createVerticalStrut(4));
        section.add(queueStatus);
        section.add(Box.createVerticalStrut(6));
        quickMatchError = sectionErrorLabel();
        section.add(quickMatchError);
        return section;
    }

    /** Local / LAN Play section: host on the LAN, or join by address and port. */
    private JPanel lanSection() {
        JPanel section = newSection("Local / LAN Play");
        hostButton = new ShellUi.MenuButton("Host Game");
        hostButton.addActionListener(e -> hostGame());
        section.add(sectionButtons(hostButton));
        section.add(Box.createVerticalStrut(8));

        addressField = styledField("127.0.0.1", 16);
        portField = styledField(Integer.toString(EmbeddedServer.DEFAULT_PORT), 8);
        section.add(rowLabel("Connect to"));
        section.add(Box.createVerticalStrut(4));
        JPanel joinRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        joinRow.setOpaque(false);
        joinRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        joinRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        joinRow.add(new JLabel("Host:"));
        styleLabel((JLabel) joinRow.getComponent(0));
        joinRow.add(addressField);
        joinRow.add(new JLabel("Port:"));
        styleLabel((JLabel) joinRow.getComponent(2));
        joinRow.add(portField);
        section.add(joinRow);
        section.add(Box.createVerticalStrut(6));
        section.add(sectionCaption("The host will see your deck list. No account needed — play stays between the two machines."));
        section.add(Box.createVerticalStrut(6));
        joinButton = new ShellUi.MenuButton("Join Game");
        joinButton.addActionListener(e -> joinGame());
        section.add(sectionButtons(joinButton));
        section.add(Box.createVerticalStrut(6));
        lanError = sectionErrorLabel();
        section.add(lanError);
        return section;
    }

    /** Lobby service, or null when no lobby server URL is configured. */
    private LobbyService lobbyService() {
        String url = settings.effectiveLobbyWorkerUrl();
        if (url == null || url.isBlank()) return null;
        return new LobbyService(url.trim());
    }

    private JPanel buildLobbyPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createLineBorder(new Color(240, 191, 73), 1));
        panel.setAlignmentX(CENTER_ALIGNMENT);

        lobbyTitle = new JLabel("Lobby");
        lobbyTitle.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
        lobbyTitle.setForeground(new Color(240, 224, 178));
        lobbyTitle.setAlignmentX(CENTER_ALIGNMENT);
        panel.add(Box.createVerticalStrut(10));
        panel.add(lobbyTitle);

        playerListModel = new DefaultListModel<>();
        JList<String> players = new JList<>(playerListModel);
        players.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        players.setBackground(new Color(25, 35, 52));
        players.setForeground(new Color(232, 236, 244));
        players.setVisibleRowCount(2);
        JScrollPane scroll = new JScrollPane(players);
        scroll.setMaximumSize(new Dimension(420, 64));
        scroll.setAlignmentX(CENTER_ALIGNMENT);
        panel.add(Box.createVerticalStrut(8));
        panel.add(scroll);

        lobbyStatus = new JLabel(" ");
        lobbyStatus.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 14));
        lobbyStatus.setForeground(new Color(170, 178, 190));
        lobbyStatus.setAlignmentX(CENTER_ALIGNMENT);
        panel.add(Box.createVerticalStrut(8));
        panel.add(lobbyStatus);

        // The tunnel share panel now lives in the Internet Play setup section;
        // enterLobby() toggles its visibility there when hosting online.
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(CENTER_ALIGNMENT);
        startButton = new ShellUi.MenuButton("Start Match");
        startButton.addActionListener(e -> startMatch());
        leaveButton = new ShellUi.MenuButton("Leave");
        leaveButton.addActionListener(e -> leaveLobby());
        buttons.add(startButton);
        buttons.add(leaveButton);
        panel.add(Box.createVerticalStrut(6));
        panel.add(buttons);
        panel.add(Box.createVerticalStrut(10));
        return panel;
    }

    // --- Setup actions ---

    private void saveName() {
        settings.setPlayerName(nameField.getText());
        settings.save();
        nameField.setText(settings.playerName);
    }

    private JToggleButton factionToggle(String label) {
        JToggleButton button = new JToggleButton(label);
        button.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        button.setPreferredSize(new Dimension(150, 42));
        button.addActionListener(e -> faction = label.toUpperCase(java.util.Locale.ROOT));
        return button;
    }

    private DeckBuild chosenDeck() {
        return context.buildForFaction(faction);
    }

    private void setConnecting(boolean connecting) {
        boolean enabled = !connecting;
        hostButton.setEnabled(enabled);
        joinButton.setEnabled(enabled);
        hostOnlineButton.setEnabled(enabled);
        joinLinkButton.setEnabled(enabled);
        nameField.setEnabled(enabled);
        zeusButton.setEnabled(enabled);
        poseidonButton.setEnabled(enabled);
        addressField.setEnabled(enabled);
        portField.setEnabled(enabled);
        tunnelLinkField.setEnabled(enabled);
        if (refreshBrowserButton != null) refreshBrowserButton.setEnabled(enabled);
        if (joinSelectedButton != null) joinSelectedButton.setEnabled(enabled);
        if (joinCodeButton != null) joinCodeButton.setEnabled(enabled);
        if (quickMatchButton != null) quickMatchButton.setEnabled(enabled);
    }

    private void hostGame() {
        saveName();
        setConnecting(true);
        setLobbyStatus("Starting server…", false);
        new SwingWorker<NetSession, Void>() {
            @Override protected NetSession doInBackground() throws Exception {
                return NetSession.host(settings, chosenDeck(), context.matchFactory, MultiplayerScreen.this);
            }

            @Override protected void done() {
                try {
                    enterLobby(get(), true);
                } catch (Exception e) {
                    setConnecting(false);
                    showError(lanError, "Could not host a game", e);
                }
            }
        }.execute();
    }

    private void joinGame() {
        saveName();
        setSectionError(lanError, null); // a fresh attempt clears the old error
        String address = addressField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            setSectionError(lanError, "Port must be a number.");
            return;
        }
        if (address.isEmpty()) address = "127.0.0.1";
        String finalAddress = address;
        setConnecting(true);
        setLobbyStatus("Connecting…", false);
        new SwingWorker<NetSession, Void>() {
            @Override protected NetSession doInBackground() throws Exception {
                return NetSession.join(settings, chosenDeck(), finalAddress, port, MultiplayerScreen.this);
            }

            @Override protected void done() {
                try {
                    enterLobby(get(), false);
                } catch (Exception e) {
                    setConnecting(false);
                    showError(lanError, "Could not join " + finalAddress + ":" + port, e);
                }
            }
        }.execute();
    }

    private void showError(JLabel sectionError, String what, Exception e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        String detail = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        setSectionError(sectionError, what + ": " + detail);
    }

    // --- Internet play ---

    /**
     * Hosts over the internet: embedded server + WebSocket bridge + a free
     * cloudflared tunnel. Registers a public lobby when a lobby server is
     * configured; otherwise the game is private and guests join by link.
     */
    private void hostOnlineGame() {
        saveName();
        setConnecting(true);
        setLobbyStatus("Starting tunnel — this can take up to a minute…", false);
        LobbyService lobby = lobbyService();
        new SwingWorker<NetSession, Void>() {
            @Override protected NetSession doInBackground() throws Exception {
                return NetSession.hostWithTunnel(settings, chosenDeck(), context.matchFactory,
                        lobby, true, MultiplayerScreen.this);
            }

            @Override protected void done() {
                try {
                    NetSession hosted = get();
                    enterLobby(hosted, true);
                    if (hosted.lobbyCode() != null) {
                        setLobbyStatus("Public lobby open — share the code or the link.", false);
                    } else {
                        setLobbyStatus("Private game — share the link below.", false);
                    }
                } catch (Exception e) {
                    setConnecting(false);
                    setLobbyStatus(" ", false);
                    showError(internetError, "Could not start the tunnel", e);
                }
            }
        }.execute();
    }

    private void joinByLink() {
        setSectionError(internetError, null); // a fresh attempt clears the old error
        String link = tunnelLinkField.getText().trim();
        if (link.isEmpty()) {
            setSectionError(internetError, "Paste the host's tunnel link first.");
            return;
        }
        if (!link.startsWith("wss://")) {
            if (link.startsWith("https://")) link = "wss://" + link.substring("https://".length());
            else {
                setSectionError(internetError,
                        "That doesn't look like a tunnel link — it should start with wss://.");
                return;
            }
        }
        joinTunnel(link);
    }

    /** Connects through a tunnel URL on a worker thread, then enters the lobby. */
    private void joinTunnel(String wssUrl) {
        saveName();
        setConnecting(true);
        setLobbyStatus("Connecting through the tunnel…", false);
        new SwingWorker<NetSession, Void>() {
            @Override protected NetSession doInBackground() throws Exception {
                return NetSession.joinViaTunnel(settings, chosenDeck(), wssUrl, MultiplayerScreen.this);
            }

            @Override protected void done() {
                try {
                    enterLobby(get(), false);
                } catch (Exception e) {
                    setConnecting(false);
                    setLobbyStatus(" ", false);
                    showError(internetError, "Could not join through the tunnel", e);
                }
            }
        }.execute();
    }

    private void refreshLobbyBrowser() {
        LobbyService lobby = lobbyService();
        if (lobby == null) return;
        setSectionError(publicGamesError, null); // a fresh refresh clears the old error
        refreshBrowserButton.setEnabled(false);
        new SwingWorker<List<LobbyService.LobbyEntry>, Void>() {
            @Override protected List<LobbyService.LobbyEntry> doInBackground() throws Exception {
                return lobby.listLobbies();
            }

            @Override protected void done() {
                refreshBrowserButton.setEnabled(true);
                try {
                    lobbyBrowserModel.clear();
                    List<LobbyService.LobbyEntry> entries = get();
                    for (LobbyService.LobbyEntry entry : entries) lobbyBrowserModel.addElement(entry);
                    if (entries.isEmpty()) {
                        setSectionError(publicGamesError,
                                "No public games right now — host one or try quick match.");
                    }
                } catch (Exception e) {
                    showError(publicGamesError, "Could not load the lobby list", e);
                }
            }
        }.execute();
    }

    private void joinSelectedLobby() {
        setSectionError(publicGamesError, null); // a fresh attempt clears the old error
        LobbyService.LobbyEntry entry = lobbyBrowserList.getSelectedValue();
        if (entry == null) {
            setSectionError(publicGamesError, "Select a game from the list first.");
            return;
        }
        joinTunnel(entry.wssUrl());
    }

    private void joinByCode() {
        LobbyService lobby = lobbyService();
        if (lobby == null) return;
        setSectionError(publicGamesError, null); // a fresh attempt clears the old error
        String code = codeField.getText().trim().toUpperCase(java.util.Locale.ROOT);
        if (code.isEmpty()) {
            setSectionError(publicGamesError, "Enter the host's 6-character lobby code.");
            return;
        }
        joinCodeButton.setEnabled(false);
        new SwingWorker<LobbyService.LobbyEntry, Void>() {
            @Override protected LobbyService.LobbyEntry doInBackground() throws Exception {
                return lobby.getLobby(code);
            }

            @Override protected void done() {
                joinCodeButton.setEnabled(true);
                try {
                    LobbyService.LobbyEntry entry = get();
                    if (entry == null) {
                        setSectionError(publicGamesError, "Unknown or expired lobby code.");
                        return;
                    }
                    joinTunnel(entry.wssUrl());
                } catch (Exception e) {
                    showError(publicGamesError, "Could not look up the lobby code", e);
                }
            }
        }.execute();
    }

    /**
     * Quick match: enqueue, then poll. The earlier ticket's player hosts the
     * tunnel and publishes the pairing; the later one joins it. Hosting
     * auto-starts once the guest's lobby join lands.
     */
    private void findMatch() {
        LobbyService lobby = lobbyService();
        if (lobby == null) return;
        saveName();
        setConnecting(true);
        quickMatchButton.setVisible(false);
        cancelQueueButton.setVisible(true);
        queueStatus.setText("Searching for an opponent…");
        Thread thread = new Thread(() -> quickMatchLoop(lobby), "ic-quick-match");
        thread.setDaemon(true);
        quickMatchThread = thread;
        thread.start();
    }

    private void cancelQuickMatch() {
        Thread thread = quickMatchThread;
        quickMatchThread = null;
        if (thread != null) thread.interrupt();
        LobbyService lobby = lobbyService();
        if (lobby != null) {
            String uuid = settings.playerUuid;
            new Thread(() -> {
                try { lobby.leaveQueue(uuid); } catch (Exception ignored) {}
            }, "ic-queue-leave").start();
        }
        queueStatus.setText(" ");
        quickMatchButton.setVisible(true);
        cancelQueueButton.setVisible(false);
        setConnecting(false);
    }

    private void quickMatchLoop(LobbyService lobby) {
        String uuid = settings.playerUuid;
        try {
            lobby.enqueue(uuid, settings.playerName, settings.playerRating);
            long start = System.currentTimeMillis();
            while (quickMatchThread == Thread.currentThread()) {
                LobbyService.QueuePoll poll = lobby.pollQueue(uuid);
                String status = poll.status();
                if ("ready".equals(status)) {
                    SwingUtilities.invokeLater(() -> {
                        queueStatus.setText("Match found — joining " + poll.opponentName() + "…");
                        SoundEffects.play(SoundEffects.Cue.NOTIFY);
                        joinTunnel(poll.wssUrl());
                    });
                    return;
                }
                if ("host".equals(status)) {
                    String opponentUuid = poll.opponentUuid();
                    String opponentName = poll.opponentName();
                    SwingUtilities.invokeLater(() ->
                            queueStatus.setText("Match found — opening tunnel for " + opponentName + "…"));
                    SoundEffects.play(SoundEffects.Cue.NOTIFY);
                    NetSession hosted = NetSession.hostWithTunnel(settings, chosenDeck(),
                            context.matchFactory, null, false, MultiplayerScreen.this);
                    hosted.publishQuickMatchPairing(lobby, opponentUuid);
                    SwingUtilities.invokeLater(() -> {
                        queueStatus.setText(" ");
                        quickMatchButton.setVisible(true);
                        cancelQueueButton.setVisible(false);
                        autoStart = true;
                        enterLobby(hosted, true);
                    });
                    return;
                }
                long waited = (System.currentTimeMillis() - start) / 1000;
                long elapsed = waited;
                SwingUtilities.invokeLater(() ->
                        queueStatus.setText("Searching for an opponent… (" + elapsed + "s)"));
                if (waited > 120) {
                    SwingUtilities.invokeLater(() -> {
                        queueStatus.setText("No opponent found — try again later.");
                        cancelQuickMatch();
                    });
                    return;
                }
                Thread.sleep(3000);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> {
                queueStatus.setText(" ");
                cancelQuickMatch();
                showError(quickMatchError, "Quick match failed", e);
            });
        }
    }

    private void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    // --- Lobby ---

    private void enterLobby(NetSession newSession, boolean isHost) {
        this.session = newSession;
        this.inMatch = false;
        lobbyPanel.setVisible(true);
        startButton.setVisible(isHost);
        if (isHost) {
            if (newSession.tunnelUrl() != null) {
                String code = newSession.lobbyCode();
                lobbyTitle.setText(code == null ? "Lobby — online (private)"
                        : "Lobby — online, code " + code);
                tunnelSharePanel.setVisible(true);
                tunnelShareLabel.setText(code == null ? "Share this link with your opponent:"
                        : "Code " + code + " — or share the link:");
                tunnelShareField.setText(newSession.tunnelUrl());
                copyLinkButton.setText("Copy Link");
            } else {
                lobbyTitle.setText("Lobby — hosting on port " + newSession.hostPort());
                tunnelSharePanel.setVisible(false);
            }
            setLobbyStatus("Waiting for an opponent to join…", false);
        } else {
            lobbyTitle.setText("Lobby");
            tunnelSharePanel.setVisible(false);
            setLobbyStatus("Waiting for the host to start…", false);
        }
        refreshPlayers(newSession.lobbyPlayers());
        revalidate();
        repaint();
    }

    private void refreshPlayers(List<Protocol.LobbyPlayer> players) {
        int before = playerListModel.size();
        playerListModel.clear();
        for (Protocol.LobbyPlayer player : players) {
            String marker = player.uuid().equals(settings.playerUuid) ? " (you)" : "";
            playerListModel.addElement(player.name() + marker);
        }
        if (before < 2 && players.size() == 2)
            SoundEffects.play(SoundEffects.Cue.NOTIFY); // opponent just joined the lobby
        if (session != null && session.isHost()) {
            boolean ready = players.size() == 2;
            startButton.setEnabled(ready && !autoStart);
            if (ready && autoStart) {
                // Quick match: the guest is here because we published the
                // pairing for them — start without another click.
                autoStart = false;
                setLobbyStatus("Opponent joined — starting match…", false);
                startMatch();
            } else if (ready) setLobbyStatus("Opponent joined — start when ready.", false);
            else setLobbyStatus("Waiting for an opponent to join…", false);
        }
    }

    private void startMatch() {
        startButton.setEnabled(false);
        setLobbyStatus("Starting match…", false);
        try {
            session.startMatch();
        } catch (RuntimeException e) {
            startButton.setEnabled(true);
            setLobbyStatus("Could not start: " + e.getMessage(), false);
        }
    }

    private void leaveLobby() {
        cancelQuickMatchQuiet();
        autoStart = false;
        closeSession();
        lobbyPanel.setVisible(false);
        setConnecting(false);
        revalidate();
        repaint();
    }

    /** Stops the quick-match thread without touching the buttons twice. */
    private void cancelQuickMatchQuiet() {
        Thread thread = quickMatchThread;
        quickMatchThread = null;
        if (thread != null) {
            thread.interrupt();
            LobbyService lobby = lobbyService();
            if (lobby != null) {
                String uuid = settings.playerUuid;
                Thread leave = new Thread(() -> {
                    try { lobby.leaveQueue(uuid); } catch (Exception ignored) {}
                }, "ic-queue-leave");
                leave.setDaemon(true);
                leave.start();
            }
        }
        if (cancelQueueButton != null) cancelQueueButton.setVisible(false);
        if (quickMatchButton != null) quickMatchButton.setVisible(true);
        if (queueStatus != null) queueStatus.setText(" ");
    }

    private void closeSession() {
        if (session != null) {
            try { session.close(); } catch (RuntimeException ignored) {}
            session = null;
        }
    }

    // --- NetSession.Listener (on the EDT) ---

    @Override public void onLobby(List<Protocol.LobbyPlayer> players) {
        if (session != null) refreshPlayers(players);
    }

    @Override public void onMatchStarted(GameSnapshot snapshot) {
        inMatch = true;
        shell.openNetBattle(session);
        session = null; // the battle owns it now
    }

    @Override public void onMulliganPrompt(GameSnapshot snapshot) {
        // Online mulligans are real decisions: open the battle on the prompt so
        // the player decides on their opening hand; the live snapshot follows.
        inMatch = true;
        shell.openNetBattle(session);
        session = null; // the battle owns it now
    }

    @Override public void onError(String message) {
        if (inMatch) return;
        setLobbyStatus(message, true);
    }

    @Override public void onDisconnected(String reason) {
        if (inMatch) return;
        closeSession();
        lobbyPanel.setVisible(false);
        setConnecting(false);
        setLobbyStatus("Disconnected from host." + (reason == null ? "" : " " + reason), true);
        revalidate();
        repaint();
    }

    @Override public void onHide() {
        if (!inMatch) {
            cancelQuickMatchQuiet();
            autoStart = false;
            closeSession();
            inMatch = false;
        }
    }

    @Override public void onShow() {
        super.onShow();
        // Fresh visit without a live session: reset to the setup view.
        if (session == null) {
            inMatch = false;
            lobbyPanel.setVisible(false);
            setConnecting(false);
            revalidate();
            repaint();
        }
    }

    // --- Styling helpers ---

    private static JTextField styledField(String text, int columns) {
        JTextField field = new JTextField(text, columns);
        field.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        field.setBackground(new Color(25, 35, 52));
        field.setForeground(new Color(232, 236, 244));
        field.setCaretColor(new Color(240, 191, 73));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 85, 110)),
                new EmptyBorder(6, 8, 6, 8)));
        field.setMaximumSize(new Dimension(280, 38));
        return field;
    }

    private static void styleLabel(JLabel label) {
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        label.setForeground(new Color(220, 226, 236));
    }

    private static JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 17));
        label.setForeground(new Color(240, 191, 73));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /** Bordered sub-panel for one setup section: gold header, 12px padding, left-aligned content. */
    private static JPanel newSection(String title) {
        JPanel section = new JPanel();
        section.setOpaque(false);
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(70, 85, 110)),
                new EmptyBorder(12, 12, 12, 12)));
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        section.setAlignmentX(Component.CENTER_ALIGNMENT);
        section.add(sectionLabel(title));
        section.add(Box.createVerticalStrut(8));
        return section;
    }

    /** Compact labeled row for section interiors: narrower label than SubScreen.row. */
    private static JPanel fieldRow(String labelText, JComponent control) {
        JPanel row = new JPanel(new BorderLayout(12, 0));
        row.setOpaque(false);
        JLabel label = new JLabel(labelText);
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
        label.setForeground(new Color(220, 226, 236));
        label.setPreferredSize(new Dimension(120, 40));
        row.add(label, BorderLayout.WEST);
        row.add(control, BorderLayout.CENTER);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        return row;
    }

    /** Small label that sits above a full-width control row inside a section. */
    private static JLabel rowLabel(String text) {
        JLabel label = new JLabel(text);
        styleLabel(label);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /** Caption pinned to the section's left edge. */
    private static JLabel sectionCaption(String text) {
        JLabel label = caption(text);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /** Left-aligned button row for section interiors. */
    private static JPanel sectionButtons(JButton... buttons) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        for (JButton button : buttons) panel.add(button);
        return panel;
    }

    /** The red inline error line at the bottom of a section; blank and hidden until set. */
    private static JLabel sectionErrorLabel() {
        JLabel label = new JLabel(" ");
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        label.setForeground(new Color(255, 120, 120));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setVisible(false);
        return label;
    }

    /** Sets a section's inline error (null/blank clears it back to hidden). */
    private static void setSectionError(JLabel label, String message) {
        if (label == null) return;
        if (message == null || message.isBlank()) {
            label.setText(" ");
            label.setVisible(false);
            return;
        }
        String html = escapeHtml(message).replace("\n", "<br>");
        label.setText("<html><div style='width:560px;'>" + html + "</div></html>");
        label.setVisible(true);
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Pre-match lobby status line; errors render red, normal statuses gray. */
    private void setLobbyStatus(String text, boolean error) {
        lobbyStatus.setText(text);
        lobbyStatus.setForeground(error ? new Color(255, 120, 120) : new Color(170, 178, 190));
    }

    private static JLabel caption(String text) {
        JLabel label = new JLabel("<html><i>" + text + "</i></html>");
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        label.setForeground(new Color(150, 158, 172));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    /** Card-definition lookup for rebuilding client state from snapshots. */
    static Function<String, CardDefinition> definitionsFor(GameContext context) {
        return id -> {
            try { return context.pool.require(id); }
            catch (RuntimeException e) {
                try { return context.capitals.require(id); }
                catch (RuntimeException e2) { return null; }
            }
        };
    }
}
