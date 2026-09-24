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

        nameField = styledField(settings.playerName, 24);
        nameField.setToolTipText("Shown to other players. Max 24 characters.");
        nameField.addActionListener(e -> saveName());
        card.add(SubScreen.row("Display name", nameField));
        card.add(caption("Public — other players will see this name. Your player ID stays private."));
        card.add(Box.createVerticalStrut(14));

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
        card.add(SubScreen.row("Your deck", factionRow));
        card.add(caption("Your saved deck for the chosen faction. The host will see your deck list."));
        card.add(Box.createVerticalStrut(14));

        card.add(sectionLabel("Internet play"));
        card.add(caption("Play over the internet through a free Cloudflare tunnel. "
                + "Neither player learns the other's IP address."));
        hostOnlineButton = new ShellUi.MenuButton("Host Online Game");
        hostOnlineButton.addActionListener(e -> hostOnlineGame());
        card.add(buttons(hostOnlineButton));
        card.add(caption("Hosting starts a tunnel on your machine — keep the game open while playing."));
        card.add(Box.createVerticalStrut(10));

        tunnelLinkField = styledField("", 24);
        tunnelLinkField.setToolTipText("Paste a wss:// tunnel link from the host.");
        joinLinkButton = new ShellUi.MenuButton("Join by Link");
        joinLinkButton.addActionListener(e -> joinByLink());
        JPanel linkRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        linkRow.setOpaque(false);
        linkRow.add(tunnelLinkField);
        linkRow.add(joinLinkButton);
        card.add(SubScreen.row("Tunnel link", linkRow));
        card.add(caption("The host will see your deck list. No account needed."));
        card.add(Box.createVerticalStrut(10));

        if (lobbyService() != null) {
            buildLobbyBrowser(card);
            card.add(Box.createVerticalStrut(10));
            buildQuickMatch(card);
        } else {
            card.add(caption("Lobby browser, join codes, quick match, and ratings need a lobby server — "
                    + "set one under Settings \u2192 Lobby server. Direct tunnel links work without it."));
        }
        card.add(Box.createVerticalStrut(14));

        card.add(sectionLabel("Local / LAN play"));
        hostButton = new ShellUi.MenuButton("Host Game");
        hostButton.addActionListener(e -> hostGame());
        card.add(buttons(hostButton));
        card.add(Box.createVerticalStrut(14));

        card.add(sectionLabel("Join a game"));
        addressField = styledField("127.0.0.1", 16);
        portField = styledField(Integer.toString(EmbeddedServer.DEFAULT_PORT), 8);
        JPanel joinRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        joinRow.setOpaque(false);
        joinRow.add(new JLabel("Host:"));
        styleLabel((JLabel) joinRow.getComponent(0));
        joinRow.add(addressField);
        joinRow.add(new JLabel("Port:"));
        styleLabel((JLabel) joinRow.getComponent(2));
        joinRow.add(portField);
        card.add(SubScreen.row("Connect to", joinRow));
        card.add(caption("The host will see your deck list. No account needed — play stays between the two machines."));
        joinButton = new ShellUi.MenuButton("Join Game");
        joinButton.addActionListener(e -> joinGame());
        card.add(buttons(joinButton));
        card.add(Box.createVerticalStrut(14));

        lobbyPanel = buildLobbyPanel();
        lobbyPanel.setVisible(false);
        card.add(lobbyPanel);

        JScrollPane scroll = new JScrollPane(card);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        return scroll;
    }

    /** Public lobby browser: refresh, join selected, join by code. */
    private void buildLobbyBrowser(JPanel card) {
        card.add(sectionLabel("Public games"));
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
        scroll.setMaximumSize(new Dimension(560, 76));
        scroll.setAlignmentX(CENTER_ALIGNMENT);
        card.add(scroll);
        card.add(Box.createVerticalStrut(6));

        refreshBrowserButton = new ShellUi.MenuButton("Refresh");
        refreshBrowserButton.addActionListener(e -> refreshLobbyBrowser());
        joinSelectedButton = new ShellUi.MenuButton("Join Selected");
        joinSelectedButton.addActionListener(e -> joinSelectedLobby());
        card.add(buttons(refreshBrowserButton, joinSelectedButton));
        card.add(Box.createVerticalStrut(6));

        codeField = styledField("", 10);
        codeField.setToolTipText("6-character lobby code from the host.");
        joinCodeButton = new ShellUi.MenuButton("Join by Code");
        joinCodeButton.addActionListener(e -> joinByCode());
        JPanel codeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        codeRow.setOpaque(false);
        codeRow.add(codeField);
        codeRow.add(joinCodeButton);
        card.add(SubScreen.row("Lobby code", codeRow));
    }

    /** Quick match: enqueue, poll, and either host or join the pairing. */
    private void buildQuickMatch(JPanel card) {
        card.add(sectionLabel("Quick match"));
        quickMatchButton = new ShellUi.MenuButton("Find Match");
        quickMatchButton.addActionListener(e -> findMatch());
        cancelQueueButton = new ShellUi.MenuButton("Cancel");
        cancelQueueButton.addActionListener(e -> cancelQuickMatch());
        cancelQueueButton.setVisible(false);
        card.add(buttons(quickMatchButton, cancelQueueButton));
        queueStatus = new JLabel(" ");
        queueStatus.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, 14));
        queueStatus.setForeground(new Color(170, 178, 190));
        queueStatus.setAlignmentX(CENTER_ALIGNMENT);
        card.add(Box.createVerticalStrut(4));
        card.add(queueStatus);
    }

    /** Lobby service, or null when no lobby server URL is configured. */
    private LobbyService lobbyService() {
        String url = settings.lobbyWorkerUrl;
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

        tunnelSharePanel = new JPanel();
        tunnelSharePanel.setOpaque(false);
        tunnelSharePanel.setLayout(new BoxLayout(tunnelSharePanel, BoxLayout.Y_AXIS));
        tunnelSharePanel.setAlignmentX(CENTER_ALIGNMENT);
        tunnelShareLabel = new JLabel(" ");
        tunnelShareLabel.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        tunnelShareLabel.setForeground(new Color(240, 191, 73));
        tunnelShareLabel.setAlignmentX(CENTER_ALIGNMENT);
        tunnelSharePanel.add(Box.createVerticalStrut(6));
        tunnelSharePanel.add(tunnelShareLabel);
        JPanel shareRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        shareRow.setOpaque(false);
        shareRow.setAlignmentX(CENTER_ALIGNMENT);
        tunnelShareField = styledField("", 26);
        tunnelShareField.setEditable(false);
        copyLinkButton = new ShellUi.MenuButton("Copy Link");
        copyLinkButton.addActionListener(e -> {
            copyToClipboard(tunnelShareField.getText());
            copyLinkButton.setText("Copied!");
            new javax.swing.Timer(1500, ev -> copyLinkButton.setText("Copy Link")).start();
        });
        shareRow.add(tunnelShareField);
        shareRow.add(copyLinkButton);
        tunnelSharePanel.add(Box.createVerticalStrut(4));
        tunnelSharePanel.add(shareRow);
        tunnelSharePanel.setVisible(false);
        panel.add(tunnelSharePanel);

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
        lobbyStatus.setText("Starting server…");
        new SwingWorker<NetSession, Void>() {
            @Override protected NetSession doInBackground() throws Exception {
                return NetSession.host(settings, chosenDeck(), context.matchFactory, MultiplayerScreen.this);
            }

            @Override protected void done() {
                try {
                    enterLobby(get(), true);
                } catch (Exception e) {
                    setConnecting(false);
                    showError("Could not host a game", e);
                }
            }
        }.execute();
    }

    private void joinGame() {
        saveName();
        String address = addressField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Port must be a number.", "Join Game",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (address.isEmpty()) address = "127.0.0.1";
        String finalAddress = address;
        setConnecting(true);
        lobbyStatus.setText("Connecting…");
        new SwingWorker<NetSession, Void>() {
            @Override protected NetSession doInBackground() throws Exception {
                return NetSession.join(settings, chosenDeck(), finalAddress, port, MultiplayerScreen.this);
            }

            @Override protected void done() {
                try {
                    enterLobby(get(), false);
                } catch (Exception e) {
                    setConnecting(false);
                    showError("Could not join " + finalAddress + ":" + port, e);
                }
            }
        }.execute();
    }

    private void showError(String what, Exception e) {
        Throwable cause = e.getCause() == null ? e : e.getCause();
        String detail = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        JOptionPane.showMessageDialog(this, what + ":\n" + detail, "Multiplayer",
                JOptionPane.ERROR_MESSAGE);
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
        lobbyStatus.setText("Starting tunnel — this can take up to a minute…");
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
                        lobbyStatus.setText("Public lobby open — share the code or the link.");
                    } else {
                        lobbyStatus.setText("Private game — share the link below.");
                    }
                } catch (Exception e) {
                    setConnecting(false);
                    lobbyStatus.setText(" ");
                    showError("Could not start the tunnel", e);
                }
            }
        }.execute();
    }

    private void joinByLink() {
        String link = tunnelLinkField.getText().trim();
        if (link.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Paste the host's tunnel link first.", "Join by Link",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!link.startsWith("wss://")) {
            if (link.startsWith("https://")) link = "wss://" + link.substring("https://".length());
            else {
                JOptionPane.showMessageDialog(this,
                        "That doesn't look like a tunnel link — it should start with wss://.",
                        "Join by Link", JOptionPane.WARNING_MESSAGE);
                return;
            }
        }
        joinTunnel(link);
    }

    /** Connects through a tunnel URL on a worker thread, then enters the lobby. */
    private void joinTunnel(String wssUrl) {
        saveName();
        setConnecting(true);
        lobbyStatus.setText("Connecting through the tunnel…");
        new SwingWorker<NetSession, Void>() {
            @Override protected NetSession doInBackground() throws Exception {
                return NetSession.joinViaTunnel(settings, chosenDeck(), wssUrl, MultiplayerScreen.this);
            }

            @Override protected void done() {
                try {
                    enterLobby(get(), false);
                } catch (Exception e) {
                    setConnecting(false);
                    lobbyStatus.setText(" ");
                    showError("Could not join through the tunnel", e);
                }
            }
        }.execute();
    }

    private void refreshLobbyBrowser() {
        LobbyService lobby = lobbyService();
        if (lobby == null) return;
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
                        JOptionPane.showMessageDialog(MultiplayerScreen.this,
                                "No public games right now — host one or try quick match.",
                                "Public games", JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    showError("Could not load the lobby list", e);
                }
            }
        }.execute();
    }

    private void joinSelectedLobby() {
        LobbyService.LobbyEntry entry = lobbyBrowserList.getSelectedValue();
        if (entry == null) {
            JOptionPane.showMessageDialog(this, "Select a game from the list first.", "Public games",
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        joinTunnel(entry.wssUrl());
    }

    private void joinByCode() {
        LobbyService lobby = lobbyService();
        if (lobby == null) return;
        String code = codeField.getText().trim().toUpperCase(java.util.Locale.ROOT);
        if (code.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter the host's 6-character lobby code.", "Join by Code",
                    JOptionPane.WARNING_MESSAGE);
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
                        JOptionPane.showMessageDialog(MultiplayerScreen.this,
                                "Unknown or expired lobby code.", "Join by Code",
                                JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    joinTunnel(entry.wssUrl());
                } catch (Exception e) {
                    showError("Could not look up the lobby code", e);
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
                        joinTunnel(poll.wssUrl());
                    });
                    return;
                }
                if ("host".equals(status)) {
                    String opponentUuid = poll.opponentUuid();
                    String opponentName = poll.opponentName();
                    SwingUtilities.invokeLater(() ->
                            queueStatus.setText("Match found — opening tunnel for " + opponentName + "…"));
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
                showError("Quick match failed", e);
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
            lobbyStatus.setText("Waiting for an opponent to join…");
        } else {
            lobbyTitle.setText("Lobby");
            tunnelSharePanel.setVisible(false);
            lobbyStatus.setText("Waiting for the host to start…");
        }
        refreshPlayers(newSession.lobbyPlayers());
        revalidate();
        repaint();
    }

    private void refreshPlayers(List<Protocol.LobbyPlayer> players) {
        playerListModel.clear();
        for (Protocol.LobbyPlayer player : players) {
            String marker = player.uuid().equals(settings.playerUuid) ? " (you)" : "";
            playerListModel.addElement(player.name() + marker);
        }
        if (session != null && session.isHost()) {
            boolean ready = players.size() == 2;
            startButton.setEnabled(ready && !autoStart);
            if (ready && autoStart) {
                // Quick match: the guest is here because we published the
                // pairing for them — start without another click.
                autoStart = false;
                lobbyStatus.setText("Opponent joined — starting match…");
                startMatch();
            } else if (ready) lobbyStatus.setText("Opponent joined — start when ready.");
            else lobbyStatus.setText("Waiting for an opponent to join…");
        }
    }

    private void startMatch() {
        startButton.setEnabled(false);
        lobbyStatus.setText("Starting match…");
        try {
            session.startMatch();
        } catch (RuntimeException e) {
            startButton.setEnabled(true);
            lobbyStatus.setText("Could not start: " + e.getMessage());
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

    @Override public void onError(String message) {
        if (inMatch) return;
        JOptionPane.showMessageDialog(this, message, "Multiplayer", JOptionPane.WARNING_MESSAGE);
    }

    @Override public void onDisconnected(String reason) {
        if (inMatch) return;
        closeSession();
        lobbyPanel.setVisible(false);
        setConnecting(false);
        JOptionPane.showMessageDialog(this,
                "Disconnected from host." + (reason == null ? "" : "\n" + reason),
                "Multiplayer", JOptionPane.WARNING_MESSAGE);
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
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    private static JLabel caption(String text) {
        JLabel label = new JLabel("<html><i>" + text + "</i></html>");
        label.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        label.setForeground(new Color(150, 158, 172));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }

    private static JPanel buttons(JButton... buttons) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.CENTER_ALIGNMENT);
        for (JButton button : buttons) panel.add(button);
        return panel;
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
