# Alcatraz

Two identical Spring Boot processes replicate their in-memory registration state
through JGroups TCP/TCPPING. The oldest member of the current view is primary.
All four game endpoints, including GET, return 503 on backups and while a node
is initializing. The server never receives game moves.

The `server` and `client` Maven modules run as separate processes. The client uses
a Swing lobby and the supplied Alcatraz game window. Its behavior is recorded in
[docs/client-phase.md](docs/client-phase.md).

## Build and supplied files

Use JDK 21 and Maven, from the repository root:

```powershell
mvn package
```

This builds both modules and runs the server-flow and client networking/ordering
tests. Use `mvn -pl server package` to build just the server without the course
library, or `mvn -pl client package` to build just the client.

Obtain the distribution from the course and place it locally under:

```text
vendor/alcatraz-java/
  alcatraz-lib.jar
  alcatraz-doc.zip
  alcatraz-local-demo.jar
  alcatraz-local-demo-src.zip
  alcatraz-local-demo.bat
```

**The course forbids publishing these files.** This directory is ignored by Git,
must not be force-added, and is not packaged into the server. The server builds
without it. The former resources/static/alcatraz-java location is also ignored.
The executable client JAR includes the library for local use: do not publish the
client JAR either. All Maven `target` directories are ignored. Maven may warn
about the local system dependency; this keeps the course library out of public
repositories and avoids a separate installation step.

## Run the client

After starting the registration server(s), open one terminal per player:

```powershell
java -jar client/target/client-1.jar --callback-port=9001
java -jar client/target/client-1.jar --callback-port=9002
```

Enter the same game ID and different names, then Register. The lobby polls the
player list. Cancel removes a waiting registration; Start is enabled with at
least two players. Each process opens its own Alcatraz window. The window title
and lobby show whose turn is awaited. Closing the game window hides it; Show game
reopens it. Cancel before exiting a waiting lobby. Exiting an active client ends
that player's process; crash/restart recovery is outside scope.

For separate LAN machines, supply the registration server addresses and the
client machine's reachable LAN address:

```powershell
java -jar client/target/client-1.jar "--servers=http://192.168.1.10:8080,http://192.168.1.11:8080" --callback-host=192.168.1.20 --callback-port=9001
```

| Client option | Default |
| --- | --- |
| `--servers` | `http://127.0.0.1:8080,http://127.0.0.1:8081` |
| `--callback-host` | Local address used to reach the first server |
| `--callback-port` | An automatically selected free port |
| `--registration-timeout-ms` | `20000` |

Use an explicit callback port for the LAN demo and allow it through the firewall.
Override the host when automatic detection selects the wrong interface. With
loopback server addresses the detected callback host is also loopback. Keep the
registration timeout above the configured server cluster timeout. Registration,
cancellation and start retry temporary connection failures and 503 responses;
the selected name and game stay locked while an outcome is uncertain.

Moves travel directly between clients. Each peer has its own queue; network
failures and 5xx responses retry every two seconds with a three-second HTTP
timeout. A 4xx or unexpected response is logged and dropped for that recipient.
Early and out-of-order moves wait for initialization or missing predecessors.
For a cable-pull demo, temporarily disconnect one client, make available moves,
then reconnect it and verify the queued moves arrive and play resumes.

## Local and LAN deployment

Run these in separate terminals, from the repository root. Start terminal 1
first and wait for its cluster view before starting terminal 2.

```powershell
# Terminal 1: initially primary
java -jar server/target/server-1.jar --server.port=8080 --alcatraz.cluster.port=7800

# Terminal 2: initially backup
java -jar server/target/server-1.jar --server.port=8081 --alcatraz.cluster.port=7801
```

The HTTP listener starts before cluster synchronization completes; game endpoints
return 503 until the primary is ready. Defaults use 127.0.0.1 with static peers
127.0.0.1[7800],127.0.0.1[7801].

For LAN deployment, bind each server to its LAN IP and use the same static peer
list on both. Example for machine 1:

```powershell
java -jar server/target/server-1.jar --server.port=8080 --alcatraz.cluster.bind-address=192.168.1.10 --alcatraz.cluster.port=7800 "--alcatraz.cluster.peers=192.168.1.10[7800],192.168.1.11[7800]"
```

On machine 2 change bind-address to 192.168.1.11. Replace these example addresses
with the actual LAN IPs. Allow the configured HTTP and JGroups TCP ports through
the firewalls. Client callback URLs must be reachable by servers and peers.

Configuration is in server/src/main/resources/application.properties. Override
values using Spring Boot command-line arguments or environment variables.

| Setting | Default |
| --- | --- |
| server.port | 8080 |
| alcatraz.cluster.name | alcatraz-registration-cluster |
| alcatraz.cluster.bind-address | 127.0.0.1 |
| alcatraz.cluster.port | 7800 |
| alcatraz.cluster.peers | 127.0.0.1[7800],127.0.0.1[7801] |
| alcatraz.cluster.failure-timeout-ms | 6000 |
| alcatraz.cluster.timeout-ms (RPC and state transfer) | 12000 |
| alcatraz.notification.http-timeout-ms (connect and response) | 3000 |
| alcatraz.notification.retry-ms | 2000 |
| alcatraz.notification.window-ms | 60000 |

Keep the cluster timeout more than 3500 ms above the failure timeout. This allows
two fixed 1-second heartbeat intervals and 1500 ms for suspicion verification.
Actual failover time also depends on scheduling and network conditions.

## REST contract

| Request | Success |
| --- | --- |
| POST /games/{gameId}/players | 200; body `{"players":[...]}`; response header `Authorization: Bearer <token>` |
| DELETE /games/{gameId}/players/{playerName} | 200; no body |
| POST /games/{gameId}/start | 200; no body |
| GET /games/{gameId} | 200; `{"status":"WAITING","players":[...]}` or STARTED |

Registration accepts `{"name":"Alice","callbackUrl":"http://host:9001"}`.
Player entries contain id, name and callbackUrl. IDs are null while waiting and
assigned contiguously in registration order at start. Tokens never appear in
player lists or JSON response bodies.

Cancel/start require `Authorization: Bearer <token>` in the request header.
An identical registration retry returns the same token in the response header,
including after start. Match the trimmed, **case-sensitive** name and normalized
callback URL: Alice and alice are different players. Names contain 1–20 characters
after trimming. Game IDs have no application-level format restriction; use a single
URL path segment and URL-encode it when needed. Unknown games return “Game not found”
for status/start; registration creates them. Callback URLs are absolute
HTTP(S) base URLs; trailing slashes are stripped.

Invalid input returns 400, missing Bearer authentication 401, and a wrong token
for an existing player 403. Unknown games return 404 for status/start. Name
conflicts, a fifth player, and changes to a started roster return 409. Starting
with fewer than two players returns 400. A valid repeated start returns 200
without initiating another notification round.

Cancelling an absent player or unknown game returns 200 with a non-empty Bearer
token; the old token cannot be checked after the registration is removed.
Cancellation in a started game returns 409. Empty waiting games are removed;
started games stay in memory until shutdown.

## Manual curl demo

Use PowerShell and curl.exe (not the PowerShell curl alias). Piping JSON to stdin
avoids differences in Windows argument quoting. The callback ports may be
unavailable for this server-only demo: delivery retries in the background and
status remains queryable.

```powershell
$primary = 'http://localhost:8080'
$secondary = 'http://localhost:8081'

# Register Alice and Bob. Copy Alice's token from her Authorization response header.
'{"name":"Alice","callbackUrl":"http://localhost:9001"}' | curl.exe -sS -i --max-time 20 -H "Content-Type: application/json" --data-binary '@-' "$primary/games/demo/players"
'{"name":"Bob","callbackUrl":"http://localhost:9002"}' | curl.exe -sS -i --max-time 20 -H "Content-Type: application/json" --data-binary '@-' "$primary/games/demo/players"
$aliceToken = '<paste Alice token without Bearer prefix>'

# Register then cancel Charlie; copy his token from his response header.
'{"name":"Charlie","callbackUrl":"http://localhost:9003"}' | curl.exe -sS -i --max-time 20 -H "Content-Type: application/json" --data-binary '@-' "$primary/games/demo/players"
$charlieToken = '<paste Charlie token without Bearer prefix>'
curl.exe -sS -i --max-time 20 -X DELETE -H "Authorization: Bearer $charlieToken" "$primary/games/demo/players/Charlie"

# Primary: WAITING with Alice and Bob, IDs null. Backup: 503.
curl.exe -sS -i --max-time 20 "$primary/games/demo"
curl.exe -sS -i --max-time 20 "$secondary/games/demo"

# Start: 200, no body. Status: STARTED, IDs 0 and 1.
curl.exe -sS -i --max-time 20 -X POST -H "Authorization: Bearer $aliceToken" "$primary/games/demo/start"
curl.exe -sS -i --max-time 20 "$primary/games/demo"

# Force-stop only the process listening on the primary's HTTP port.
$primaryProcessId = (Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object -First 1).OwningProcess
Get-Process -Id $primaryProcessId
Stop-Process -Id $primaryProcessId -Force

# Retry on the second server. If it still returns 503, wait and repeat.
curl.exe -sS -i --max-time 20 "$secondary/games/demo"
curl.exe -sS -i --max-time 20 -X POST -H "Authorization: Bearer $aliceToken" "$secondary/games/demo/start"
'{"name":"Alice","callbackUrl":"http://localhost:9001/"}' | curl.exe -sS -i --max-time 20 -H "Content-Type: application/json" --data-binary '@-' "$secondary/games/demo/players"
```

After takeover the frozen roster remains, repeated start returns 200, and Alice's
registration retry returns her original token. Promotion initiates notifications;
the repeated start does not. Restart terminal 1 with its original command: it
joins as backup, transfers state and returns 503 for game requests.

For the presentation, use separate LAN machines. A temporary client cable
disconnection exercises the client's queued move delivery. A partition
between the two registration servers can create two primaries and is outside scope.

## Replication and recovery

Primary operations are serialized. Each mutation is applied locally with a
global monotonic version and acknowledged after synchronous GET_ALL replication.
A timeout returns 503 with an uncertain outcome. Retries first reconcile existing
state, rather than acknowledging a merely local change. Game deletion is versioned.

Joining/restarted replicas and replicas detecting a gap request a complete
JGroups getState/setState transfer, including tokens, rosters and the version.
A separate recovery worker avoids waiting for transfers inside RPC handlers.
A replica without a complete initial snapshot cannot serve requests.

On promotion, the surviving replica uses its last complete state. Changes it
never received were not acknowledged to the caller, which must retry. All state
is in memory: stopping both servers loses the registry.

Start callbacks run outside the request path. Failed recipients are retried every
two seconds for up to 60 seconds, with bounded HTTP timeouts. Promotion schedules
all started games again. Clients must ignore duplicate starts and poll GET as a fallback.

## Changes compared to the original design

For the final presentation, explicitly mention the following. The Word document
has intentionally not been edited.

- **Figure 2 and section 8:** registration returns the token in the Authorization
  response header instead of the body. Cancel/start send it in the request header.
- **GET only on the primary:** backups return 503 for status requests too.
- **Replication versions:** each change has a monotonic version; gaps trigger
  full state transfer and uncertain outcomes are reconciled before acknowledgement.
- **TCP/TCPPING:** configurable static hosts replace reliance on UDP multicast.
