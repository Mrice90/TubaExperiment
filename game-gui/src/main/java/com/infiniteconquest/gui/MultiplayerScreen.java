package com.infiniteconquest.gui;

import com.infiniteconquest.core.CardDefinition;
import com.infiniteconquest.core.DeckBuild;
import com.infiniteconquest.core.GameSnapshot;
import com.infiniteconquest.gui.net.NetSession;
import com.infiniteconquest.net.EmbeddedServer;
import com.infiniteconquest.net.Protocol;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
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
    private JPanel lobbyPanel;
    private JLabel lobbyTitle;
    private DefaultListModel<String> playerListModel;
    private JLabel lobbyStatus;
    private JButton startButton;
    private JButton leaveButton;

    private NetSession session;
    private String faction = "ZEUS";
    private boolean inMatch;

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

        card.add(sectionLabel("Host a game"));
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

        return card;
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
        hostButton.setEnabled(!connecting);
        joinButton.setEnabled(!connecting);
        nameField.setEnabled(!connecting);
        zeusButton.setEnabled(!connecting);
        poseidonButton.setEnabled(!connecting);
        addressField.setEnabled(!connecting);
        portField.setEnabled(!connecting);
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

    // --- Lobby ---

    private void enterLobby(NetSession newSession, boolean isHost) {
        this.session = newSession;
        this.inMatch = false;
        lobbyPanel.setVisible(true);
        startButton.setVisible(isHost);
        if (isHost) {
            lobbyTitle.setText("Lobby — hosting on port " + newSession.hostPort());
            lobbyStatus.setText("Waiting for an opponent to join…");
        } else {
            lobbyTitle.setText("Lobby");
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
            startButton.setEnabled(ready);
            if (ready) lobbyStatus.setText("Opponent joined — start when ready.");
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
        closeSession();
        lobbyPanel.setVisible(false);
        setConnecting(false);
        revalidate();
        repaint();
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
