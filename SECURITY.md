# Security

Chums lets a watch and an iPhone send instructions to AI coding agents that run on your Mac with your user account.
Anyone who holds the watch token can do what the watch does. Read this before you run it.

## Reporting a vulnerability

Do not open a public issue for a security problem. Use GitHub's private vulnerability reporting on this repository
(Security › Report a vulnerability). Include the version (commit), what an attacker needs, and the steps to reproduce.
The report reaches the maintainers only.

## What runs where

| Part | Listens on | Who can reach it |
| --- | --- | --- |
| Hub, local side (`hub/hub.mjs`) | `127.0.0.1:7717` (`port` in `~/.chums/config.json`) | Processes on this Mac: the channel and hooks of each Claude Code session, the Simulators |
| Hub, watch side | `127.0.0.1:7718` (`publicPort`; `publicHost` can change the address) | Only `/v1/…` paths. Reached from the internet through Tailscale Funnel, or from a network when `publicHost` says so |
| Tailscale Funnel (`bin/chums-tunnel`) | `https://<machine>.<tailnet>.ts.net` | Anyone on the internet. Funnel forwards HTTPS requests to `127.0.0.1:7718` |
| Speech to text (`whisperkit-cli serve`, or `tools/whisper-server.py` on Linux) | `localhost:50061` | Processes on this Mac |
| The agents' voices (`tools/kokoro-server.py`) | `127.0.0.1:50062` | Processes on this Mac |
| Codex agents (`codex app-server`) | Unix sockets in `/tmp/chums-<uid>/`, a folder only your user can open (`0700`, its owner checked) | Your user |

By default the hub never listens on a non-loopback address, and the only way in from outside the Mac is the Funnel
URL. Setting `publicHost` (for the Android app on a LAN) opens the watch side, never the local side, to that network.

## Tokens

The hub creates two random tokens (18 bytes each, base64url) on its first start and stores them in
`~/.chums/config.json` with file mode `0600`:

- `token`: for the Claude Code side on this Mac (channel and hooks). It is accepted only on the local listener.
- `watchToken`: for the watch. It is accepted on both listeners. The public listener refuses the local `token`.

Every request carries one of them as `Authorization: Bearer <token>`. Tokens are compared in constant time. On the
public listener a wrong token waits one second before the `401`.

`tools/gen-config.sh` writes the watch token and the Funnel URL into `app/ChumsWatch/HubConfig.swift`, which is
compiled into the watch app, the iPhone app and the iPhone's widgets. That file is git-ignored. Do not commit it, and
do not share a built `.app` or `.ipa`: the token can be read out of the binary.

## What the watch token allows

With the watch token, a client on the internet can do what the watch and the iPhone app do:

- read the current state: session names, their status, the conversation, tool call summaries (file names, commands),
  pending approval requests and the command they would run;
- send text or audio that becomes an instruction to an agent, Claude Code or Codex (`/v1/say`, `/v1/utterance`);
- answer a pending permission request with allow or deny (`/v1/verdict`);
- fetch pictures the sessions showed and of what you sent (screenshots of your screen or an app, rendered pages and
  files), and the files themselves as they were, up to 25 MB each (`/v1/artifact/<id>/source` and `/view`: a document,
  a text file, a video an agent showed);
- read and change the agents' souls (`~/.chums/souls`): a change said by voice runs `claude -p` once on your account,
  and a typed one (`/v1/agents/<id>/soul/set`) is saved as written;
- switch an agent between Claude Code and Codex (`/v1/agents/<id>/runtime`, which stops one and starts the other),
  change its model (`/v1/agents/<id>/model`, which restarts it) and its voice (`/v1/agents/<id>/voice`);
- change an agent's permission mode (`/v1/agents/<id>/permission`, which restarts it), including the modes that don't
  ask you: "Decides on its own" (Claude Code's auto mode, which acts without asking and refuses only what its
  classifier finds risky) and "Never asks" (Codex's `never`, which runs commands without asking, inside its sandbox).
  It can't set Claude Code's `bypassPermissions` or change a Codex agent's sandbox;
- set which agent the iPhone's widgets show (`/v1/focus`);
- fetch the agents' spoken replies (`/v1/speech/<id>`);
- put files on the Mac (`/v1/upload`: into `~/.chums/uploads` only, 25 MB at most each, the last 40 kept, names made
  safe) and send an order that names them; an order can only name files in that folder. Claude Code agents may read
  that folder without asking. An uploaded HTML or SVG file is turned into a picture by WebKit on the Mac, which can
  load what the Mac can reach (your network, localhost). Other uploads are read on the Mac to make their picture, by
  PDFKit, Quick Look, ffmpeg or the hub's own Markdown, text and CSV reader (`hub/render.mjs`), each in a process of
  its own with a time limit: a file made to exploit one of them runs as your user;
- add lines to `~/.chums/watch-events.log` (`/v1/event`, the apps' diagnostics; the hub starts a new file past 10 MB).

That is remote control of your coding agents. What a session can then do is limited by its permission mode, which
the watch token can change, and by the rest of Claude Code's or Codex's settings on your Mac (permission rules, the
Codex sandbox); approvals that an agent asks for can be granted from the watch.

## Rotating a token

If the watch token may have leaked (a lost watch, a shared build, a log or screenshot that shows it):

1. Stop the hub.
2. Delete `watchToken` (and `token`, if needed) from `~/.chums/config.json`.
3. Start the hub. It writes new tokens.
4. Run `bin/chums-install` to build the new token into the watch app and the iPhone app.
5. Restart the Claude Code sessions so the channel and hooks read the new `token`: `bin/chums-agents install` for the
   background agents, and any `bin/chums-claude` session in a terminal. Codex agents restart with the hub.

The Android app keeps the URL and the watch token in its private preferences, which are excluded from backups. An
earlier version of `app/ChumsAndroid` had a watch token written into `HubClient.kt`; it is still in the git history.
If you ran a hub with that token, rotate it as above.

To take the watch API off the internet at once: `tailscale --socket ~/.chums/tailscale/tailscaled.sock funnel reset`.

## Things to know

- `publicHost` other than `127.0.0.1` puts the watch API on that network over plain HTTP: anyone who can see the
  traffic can read the watch token. Prefer the machine's Tailscale address, which is encrypted. The hub's own port
  (`7717`) always listens on `127.0.0.1` only.
- A Hermes agent in "Decides on its own" runs shell commands and Home Assistant services the model asks for, with no
  approval. In "Asks first" (the default) each command, and each lock, cover or alarm call, needs Allow.
- Funnel hostnames are public. Tailscale issues a TLS certificate for the name, so it appears in certificate
  transparency logs. Assume people can find the URL; the token is what protects it.
- `bin/chums-claude` starts Claude Code with `--dangerously-load-development-channels server:chums` (channels are a
  research preview), allows `chums_reply` without asking (`--allowedTools`), and adds permission rules for that
  session only (its `--settings` file, `channel/hooks.json`): it may read `~/.chums/uploads` (the photos and files you
  send from the iPhone) without asking; its Edit, Write and MultiEdit tools may not write in `~/.chums/souls` (a shell
  command still could; the souls are yours to change, from the watch or the iPhone); and it may not open Claude Code's
  question dialog (`AskUserQuestion`), which only its terminal would show: it asks on the watch and the iPhone with
  `chums_reply` instead. It does not change your other permission settings.
- The background agents (`bin/chums-agents`) run Claude Code sessions with access to the folders listed in
  `~/.chums/agents.json`. Every default agent is given your home folder. Edit `agents.json` to narrow it.
- Every default agent's guide (`"guide"` in `agents.json`) tells it it can run JavaScript in Safari with `osascript`
  to read or click inside pages you're logged in to, once you allow Develop › Allow JavaScript from Apple Events. So
  whoever has the watch token can, through an agent, act in your logged-in Safari. Leave that setting off, or remove
  the sentence from the agents' guides, if you don't want that.
- `chums_show` can take screenshots. macOS asks for Screen Recording permission for the app the session runs in.
- `chums_show` can show any file the session can read: a text file is shown as text, and the file is kept (up to
  25 MB) in `~/.chums/artifacts` for the iPhone to open.
- An HTML or SVG file a session showed or you sent is served to the iPhone with `Content-Security-Policy: sandbox
  allow-scripts`: its scripts run, in an origin of their own, not the hub's. The iPhone app shows it in a web view
  of its own that keeps nothing between pages: its scripts run, but it can't go to another page, send a form or open
  a window, and a link you tap opens in Safari.
- Speech is transcribed on the Mac by WhisperKit, and the replies are spoken by Kokoro on the Mac. Audio is not sent
  to a third party by Chums. Instructions you speak go to the agent (Claude Code or Codex), which sends them to its
  provider like any other prompt. With `"tts": { "engine": "openai" }` in `~/.chums/config.json`, the text of every
  reply goes to OpenAI to be spoken.
- A Codex agent's MCP server is given the path of `~/.chums/config.json`, not the token, because `codex app-server`'s
  arguments are in the process list.
