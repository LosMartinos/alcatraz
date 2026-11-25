package com.alcatraz.client;

import com.alcatraz.client.Messages.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

final class LobbyWindow extends JFrame {
    private static final Logger LOG = Logger.getLogger(LobbyWindow.class.getName());
    private final JTextField gameId = new JTextField("demo", 18);
    private final JTextField name = new JTextField(18);
    private final DefaultListModel<String> players = new DefaultListModel<>();
    private final JLabel status = new JLabel("Enter a game ID and your name.");
    private final JButton register = new JButton("Register");
    private final JButton cancel = new JButton("Cancel");
    private final JButton start = new JButton("Start");
    private final JButton showGame = new JButton("Show game");
    private final ClientHttp http;
    private final RegistrationClient registration;
    private final CallbackServer callbacks;
    private final String callbackUrl;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Timer polling;
    private GameSession session;
    private String token;
    private String pending;
    private boolean busy;
    private boolean closing;

    /**
     * Creates the lobby and starts its callback listener.
     * @param http HTTP transport
     * @param registration registration server communication
     * @param host advertised callback host
     * @param port callback listener port
     */
    LobbyWindow(ClientHttp http, RegistrationClient registration, String host, int port) throws IOException {
        super("Alcatraz lobby");
        this.http = http;
        this.registration = registration;
        callbacks = new CallbackServer(port, () -> session);
        callbackUrl = "http://" + (host.contains(":") ? "[" + host + "]" : host) + ":" + callbacks.port();
        var fields = new JPanel(new GridLayout(2, 2, 8, 8));
        fields.add(new JLabel("Game ID"));
        fields.add(gameId);
        fields.add(new JLabel("Name"));
        fields.add(name);
        var buttons = new JPanel();
        buttons.add(register);
        buttons.add(cancel);
        buttons.add(start);
        buttons.add(showGame);
        var bottom = new JPanel(new BorderLayout(8, 8));
        bottom.add(buttons, BorderLayout.NORTH);
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(new JLabel("Callback: " + callbackUrl), BorderLayout.SOUTH);
        var content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        content.add(fields, BorderLayout.NORTH);
        content.add(new JScrollPane(new JList<>(players)), BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);
        setContentPane(content);
        setSize(570, 360);
        setLocationByPlatform(true);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                closeLobby();
            }
        });
        register.addActionListener(event -> register());
        cancel.addActionListener(event -> { pending = "cancel"; tick(); });
        start.addActionListener(event -> { pending = "start"; tick(); });
        showGame.addActionListener(event -> session.showWindow());
        polling = new Timer(2000, event -> tick());
        polling.start();
        refreshButtons();
    }

    /** Locks the registration identity and begins registration. */
    private void register() {
        String selectedGame = gameId.getText().trim();
        String selectedName = name.getText().trim();
        if (selectedGame.isEmpty() || selectedName.isEmpty() || selectedName.length() > 20) {
            status.setText("Enter a game ID and a name of 1 to 20 characters.");
            return;
        }
        session = new GameSession(selectedGame, selectedName, callbackUrl, http, status::setText, this::refreshButtons);
        pending = "register";
        tick();
    }

    /** Runs one pending operation or status poll in the background. */
    private void tick() {
        if (busy || closing || session == null) return;
        if (pending == null && session.initialized() && token != null) return;
        busy = true;
        refreshButtons();
        GameSession current = session;
        String action = pending;
        String auth = token;
        worker.submit(() -> {
            Object result = null;
            Exception failure = null;
            try {
                if ("register".equals(action)) {
                    result = registration.register(current.gameId, current.name, current.callbackUrl);
                } else if ("cancel".equals(action)) {
                    registration.cancel(current.gameId, current.name, auth);
                } else if ("start".equals(action)) {
                    registration.start(current.gameId, auth);
                } else {
                    result = registration.status(current.gameId);
                }
            } catch (Exception e) {
                failure = e;
            }
            Object response = result;
            Exception error = failure;
            SwingUtilities.invokeLater(() -> complete(current, action, response, error));
        });
    }

    /**
     * Updates the lobby after an HTTP operation.
     * @param current session that made the request
     * @param action registration operation, or null for polling
     * @param result response value
     * @param error request failure, or null
     */
    private void complete(GameSession current, String action, Object result, Exception error) {
        if (closing || current != session) return;
        busy = false;
        if (error != null) {
            LOG.log(Level.WARNING, "Lobby request failed", error);
            status.setText(error.getMessage());
            if (!(error instanceof IOException)) {
                pending = null;
                if ("register".equals(action) && !session.initialized()) reset();
                else if ("register".equals(action)) pending = "register";
            }
        } else {
            pending = null;
            if (result instanceof RegistrationClient.Registration response) {
                token = response.token();
                showPlayers(response.players());
                if (!session.initialized()) status.setText("Registered. Waiting for the game to start.");
            } else if ("cancel".equals(action)) {
                reset();
                status.setText("Registration cancelled.");
            } else if (result instanceof GameStatusResponse response) {
                showPlayers(response.players());
                try {
                    session.observe(response);
                } catch (RuntimeException e) {
                    LOG.log(Level.WARNING, "Invalid game status", e);
                    status.setText(e.getMessage());
                }
            }
        }
        refreshButtons();
    }

    /**
     * Displays the latest player list.
     * @param roster registered players
     */
    private void showPlayers(List<PlayerInfo> roster) {
        players.clear();
        roster.forEach(player -> players.addElement(player.name()));
    }

    /** Enables controls for the current lobby state. */
    private void refreshButtons() {
        boolean playing = session != null && session.initialized();
        boolean waiting = session != null && token != null && !playing && pending == null && !busy;
        gameId.setEnabled(session == null);
        name.setEnabled(session == null);
        register.setEnabled(session == null && !busy);
        cancel.setEnabled(waiting);
        start.setEnabled(waiting && players.size() >= 2);
        showGame.setEnabled(playing);
    }

    /** Clears a cancelled or rejected registration. */
    private void reset() {
        if (session != null) session.close();
        session = null;
        token = null;
        pending = null;
        players.clear();
    }

    /** Closes the client after confirming an active registration or game. */
    private void closeLobby() {
        if (session != null && JOptionPane.showConfirmDialog(this,
                "Exit this client? Cancel a waiting registration first. An active game cannot resume after exit.",
                "Exit Alcatraz", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        closing = true;
        polling.stop();
        if (session != null) session.close();
        callbacks.close();
        worker.shutdownNow();
        http.close();
        dispose();
    }
}
