# Client phase

Add the client as the second Maven module in the later implementation phase.
The server contract is fixed in the root README. No client implementation is
included in the server phase.

## Lobby and startup

Use a small Swing lobby with game ID, name, polled player list and
Register/Cancel/Start buttons. Detect the callback host and support command-line
host/port overrides. The advertised address must be reachable by servers and peers.
Start the embedded REST listener before registering.

Keep a configured list of registration server URLs. Fail over on network errors,
timeouts and 503. Use an HTTP timeout longer than the server cluster timeout (12 s
by default). Read the token from the registration Authorization response header,
including retries; send it in the same request header for cancel/start.
RegisterResponse contains only players. Names are trimmed and case-sensitive.

Poll GET for status. Both STARTED status and /gameStarted may initialize the game.
For polling, find the local player using the registered name and normalized URL.
Initialize only once, including when polling races with duplicate callbacks.

## Library integration

Keep the supplied course distribution locally in vendor/alcatraz-java and never
publish it. Depend on alcatraz-lib.jar only from the client module.

Create one Alcatraz instance per process. Call init(playerCount, localPlayerId),
set every name using getPlayer(id).setName(name), add the listener, show the
window and call start(). Perform game/UI changes on the Swing event thread.
Use this instance's getPlayer()/getPrisoner() objects for received doMove() calls.
Pass all coordinates unchanged and support ROW, COL and BOAT.

Read-only bytecode inspection on 2026-10-29 confirmed that the supplied
Alcatraz.doMove() calls its internal move method with listener notification
disabled: it does **not** trigger MoveListener.moveDone(). Still broadcast only
callbacks for the local player's ID; re-check this behavior if the library changes.
Remote winning moves can trigger gameWon(), which is not an outgoing move.

A local GUI move is already applied: advance its sequence and queue delivery
without applying the same move again.

## REST and move ordering

Expose POST /gameStarted with:
GameStartedPayload(String gameId, int yourPlayerId, List<PlayerInfo> players).

Expose POST /move with:
MovePayload(String gameId, int sequenceNumber, int playerId, int prisonerId,
int rowOrCol, int row, int col).

Use a global sequence starting at 0. The expected player is sequenceNumber mod
playerCount. Buffer early and out-of-order moves by sequence and answer 200.
After initialization, drain consecutive moves on the Swing event thread.
If the roster is not known yet, defer player/turn validation until it is available.

Wrong game: 404. Player inconsistent with sequence mod playerCount: 409, log and
ignore. Same sequence with different content: first message wins, log the rest
and answer 200. Older sequences: duplicates, answer 200. Reject malformed payloads;
clients are otherwise trusted, with no anti-cheat requirement.

## Delivery and interruptions

Maintain outgoing delivery separately for each peer. Use background senders with
timeouts and retries so a disconnected peer blocks neither other peers nor the GUI.

For POST /move, **all 4xx responses are permanent**: drop that recipient's delivery
and log it. Retry **only network errors, timeouts and 5xx**. A 200 acknowledges
an applied, buffered or duplicate move. Do not treat 3xx as acknowledgement or
retry it indefinitely; log/drop the unexpected response. This move policy is
distinct from the server's start-callback retries.

Display whose move is awaited. Deliver queued moves after reconnection and resume
in sequence. Permanent client crash/shutdown recovery, anti-cheat, persistence
and server-side move forwarding remain outside scope.
