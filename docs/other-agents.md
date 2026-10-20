# Codex (and other agents)

Chums started as a remote for Claude Code. An agent can now also run on **OpenAI Codex**, and gets the same things on
the watch:

- live status: working, idle, waiting for you, blocked, limited, compacting;
- tool calls as events;
- spoken orders in;
- short spoken replies out;
- approvals answered on the watch.

Supported: **Claude Code and Codex.** GitHub Copilot and other Agent Client Protocol (ACP) agents also run, but they
are **experimental**: built and tried once, not part of what Chums claims to support (see the end).

Checked on 2026-10-10 with Codex CLI 0.160.0 (model `gpt-6-astra`).

## The decision

Codex is driven through **`codex app-server`**, OpenAI's own JSON-RPC protocol. It is the one Codex's IDE extension,
desktop app and `--remote` terminal use. The hub is a client of it, and runs the agent's app-server itself
(`hub/runtimes.mjs`). Claude Code keeps its own path: an interactive session in a terminal, with the channel and hooks.

| Runtime | What runs it | Protocol | Status |
| --- | --- | --- | --- |
| `claude` (default) | `claude` in its own terminal (LaunchAgent, `bin/chums-agents`) | Claude Code channel + 15 hooks | supported (channels are a research preview) |
| `codex` | the hub: `codex app-server --listen unix://…`, one per agent | Codex app-server JSON-RPC (WebSocket on a Unix socket) | supported. The CLI labels the app-server `[experimental]`. |
| `copilot` | the hub, through `@github/copilot-sdk` | Copilot SDK JSON-RPC | experimental |
| `acp` | the hub: any `command` that speaks ACP on stdio | Agent Client Protocol v1 | experimental |

The hub turns Codex's events into Claude Code's hook vocabulary (`UserPromptSubmit`, `PreToolUse`, `PostToolUse`,
`Stop`, `StopFailure`, `PreCompact`, `SubagentStart`…), and Codex's tools into Claude Code's tool names (`Bash`,
`Read`, `Edit`, `Grep`…). So the hub's status logic (`onHook`), the watch's tool events, and approval-card summaries
are the same code for both.

### Why the app-server

Codex has five first-party surfaces. Only the app-server does all five jobs.

| Surface | Status | Tool events | Approvals | Orders in | Replies out | Stability |
| --- | --- | --- | --- | --- | --- | --- |
| **app-server** (chosen) | `turn/*`, `thread/status/changed` (`waitingOnApproval`) | `item/started`, `item/completed` (commands, file changes, MCP, web search, compaction, subagents) | `item/commandExecution/requestApproval`, `item/fileChange/requestApproval`, `item/permissions/requestApproval`, answered by the client | `turn/start`, and `turn/steer` mid-turn | `agentMessage` `final_answer`, or the Chums MCP tool | `[experimental]` label, but it is what OpenAI's own Codex clients use |
| Codex SDK (`@openai/codex-sdk`) | per run | `item.*` while streaming | none: a fixed `approvalPolicy`, no callback | a new `codex exec` per turn | `finalResponse` | It wraps `codex exec --experimental-json`, so it can't ask the watch. |
| `codex exec --json` | per run | yes | none | one-shot | yes | Same limits as the SDK. |
| hooks (`hooks.json`) + `notify` | yes, from an interactive session | `PreToolUse`, `PostToolUse` | a `PermissionRequest` hook can answer | **none**: nothing can put a prompt into a running terminal session, and Codex has no equivalent of Claude Code's channels | `Stop` carries the last message | Hooks need trust per hash. Only one `notify` program is allowed, and you may already have one. |
| `codex mcp-server` | | | | | | Removed (it isn't in 0.160). |
| ACP | | | | | | Codex has no native ACP. `@agentclientprotocol/codex-acp` wraps the app-server in a process of its own, which takes the thread's writer lock, so a Codex terminal can't share the thread. |

Codex Remote / `codex remote-control` is OpenAI's channel to the ChatGPT apps, not an API. "Sign in with ChatGPT",
the Apps SDK and AgentKit are for building apps on OpenAI's cloud; none of them drives a local Codex agent.

### What works, tested against the real Codex (`node tools/e2e-runtimes.mjs codex`: 26 checks, all passing)

- **It starts with the hub and shows on the watch as idle.**
  - Its thread is kept in agents.json (`sessionId`).
  - The thread is named "Chums: <agent>" in Codex's own lists.
- **A question gets a spoken answer.**
- **Commands ask on the watch.** Two runs:
  - In the e2e (`approvalPolicy: "untrusted"`) a command shows an approval card with the command itself. "Yes" runs
    it. "No" declines it: the command doesn't run, and the agent goes on and says so.
  - With the default policy (`on-request`, `workspace-write` sandbox), it writes in its folder without asking. A
    network call failed in the sandbox, so it asked to run it outside; the card carried its reason ("May I allow curl
    network access…"), and on yes it ran.
- **Tool calls are watch events in Claude Code's words.**
  - Commands are `Bash …` (or `Read`, `Grep` when Codex parses them that way), and failures are marked.
  - Patches are `Write notes.txt` / `Edit …`.
- **Replies.**
  - `chums_reply` (the Chums MCP server) speaks on the watch.
  - When a turn from the watch ends without it, the hub speaks the first sentence of Codex's final answer (no
    markdown, links or paths, 15 words at most; to an order from the iPhone, whole sentences up to 60 words).
  - In the last run, 6 of 9 replies were Codex's own `chums_reply`.
- **`chums_show` puts a picture on the watch.** It wrote an SVG and showed it; the watch fetched the thumbnail.
- **An order said while it works joins the turn** (`turn/steer`): one turn, and both questions answered.
- **`bin/chums-codex <id>` opens a Codex terminal on the agent's thread,** live, while the watch stays connected (see
  below).
- **When its app-server dies,** the agent goes offline, starts again in 5 s, resumes the same thread, and remembers
  the conversation.
- **When the hub is killed hard,** the next hub stops the app-server the old one left, resumes the thread, and
  remembers.
- **The status sequence the watch saw:** idle → working → waiting → working → idle, offline and back.

Tested against a stand-in app-server only (`tools/test-runtimes.mjs`; these don't happen on demand):

- a usage limit → `limited`;
- compaction → `compacting`, and the soul is sent again;
- a card answered in a Codex terminal → the card leaves the watch;
- a file-change approval → the card says "edit a.js".

Mapped from the protocol but never seen:

- `item/permissions/requestApproval`;
- subagents (`collabAgentToolCall`, `subAgentActivity`);
- `item/tool/requestUserInput` (an experimental API Chums doesn't opt into): its question would be said on the watch,
  and the next thing you say is the answer.

### What to know before claiming it (the limits)

- **On the Mac, a Codex terminal, not the apps.**
  - Claude Code agents also show in the Claude app (Remote Control). A Codex agent shows live only in a Codex terminal
    (`bin/chums-codex`).
  - ChatGPT's Codex and the IDE extension list its thread, but can't continue it while the hub runs it (see below).
- **The app-server is labelled experimental by OpenAI,** and its methods can change between Codex releases.
  - Chums was written against 0.160's own generated schema (`codex app-server generate-ts`).
  - After updating Codex, run `node tools/e2e-runtimes.mjs codex`.
- **Codex's default model may not be on your plan.** Pick another under Model on the agent's card on the iPhone, or set
  `"model"` (`gpt-6-astra` works on Plus).
- **Approvals follow Codex's policy.**
  - The default `on-request` + `workspace-write`: it works freely in its folder (and `addDirs`) and asks to leave
    the sandbox (network, other folders).
  - `untrusted` asks for nearly every command.
  - "Yes" is always accept-once, never "for the session".
- **Its replies are sometimes its final answer, said by the hub, not a `chums_reply`.** The line is then cut to its
  first sentence, so a long answer is shortened, not summarized.
- **A turn typed in a Codex terminal** shows its status and tool calls on the watch, but isn't spoken there: it isn't
  the watch's turn.
- **Not covered:**
  - interrupting from the watch (as with Claude Code);
  - the watch shows no Codex plan or todo list;
  - permission-mode badges (planned for all agents, see `design/STATES.md`).
- **`chums_show` screenshots** come from a process the hub started, so macOS asks for Screen Recording for Chums
  when the hub runs as a LaunchAgent (`bin/chums-setup --launchd`, see
  [Permissions on the Mac](setup.md#permissions-on-the-mac)), or for the terminal app that runs `bin/chums-hub`. The
  e2e showed a file, not a screenshot.

## Seeing a Codex agent on your Mac

**A terminal: `bin/chums-codex <id>`** (also `bin/chums-agents open <id>`; with one Codex agent the id can be left out).

- It runs `codex resume <thread> --remote unix://<socket>`: Codex's own terminal UI, connected to the app-server the
  hub runs for that agent. Both are clients of one process, so there is no copy and no lock to fight over.
- Tested end to end (`tools/e2e-runtimes.mjs codex`, the terminal in a pseudo-terminal):
  - It opens on the conversation so far.
  - An order said on the watch while it's open shows in it, with the answer, as it happens.
  - A question typed in it goes to the same agent and is answered there.
  - The watch shows that turn's status and tool calls, and doesn't speak it: only turns from the watch are spoken on
    the watch. Codex knows which is which from the `[From the watch]` or `[From the iPhone]` mark the hub puts on their orders.
  - Quitting the terminal leaves the agent running for the watch.
  - Approvals show in both, and the first answer wins: answer in the terminal and the watch's card goes (tested with
    the stand-in; Codex sends the same `serverRequest/resolved` to every client).
- Like Claude Code agents, Codex agents run in the background and have no window of their own. `chums-codex` is how
  you look in, from any terminal, as often as you like. Several terminals can be open at once.

**ChatGPT's Codex (desktop app) and the Codex IDE extension:**

- Each runs its own app-server, and they read the same `~/.codex`. The hub's threads are in the list Codex gives its
  clients by default (`thread/list`, checked here): named "Chums: <agent>", from the agent's folder (Codex records
  them as IDE-extension threads). The apps' own windows weren't looked at.
- Opening one shows the conversation up to the last time it was read: it isn't live.
- Continuing it there fails while the hub runs the agent. Codex allows one writer per thread, and a second app-server
  gets "thread … already has an active writer" (tested: another app-server can `thread/read` it, not `thread/resume`).
- To use the thread in the desktop app, stop the hub (or take the agent out of agents.json) first. After that, the
  watch can't reach the agent until the hub runs it again. There is no handover like `chums-agents open` gives
  Claude Code agents.
- Neither app can be pointed at the hub's socket. So the live view is the terminal.
- The e2e test archives the threads it makes (`codex unarchive <id>` brings one back).

## agents.json

Every character is its own agent, its `id` the character's. `runtime` picks how it runs: Claude Code without it, or
Codex, switched on the watch (the agent's soul screen, Runs on) or on its card in the iPhone app, which stops one and
starts the other. The hub fills in `sessionId` (the Codex thread, or the Claude Code session) the first time, and a `cwd` in
`~/.chums/agents` when there is none, and resumes it after every restart;
the other runtime's thread (and model) waits in `byRuntime` for a switch back.

```json
[
  { "id": "noodle", "name": "Noodle", "character": "noodle", "cwd": "/Users/you/Developer/chums" },

  { "id": "ray", "name": "Ray", "character": "ray", "runtime": "codex",
    "cwd": "/Users/you/Developer/some-project", "model": "gpt-6-astra",
    "approvalPolicy": "on-request", "sandbox": "workspace-write",
    "addDirs": ["/Users/you/Developer/shared"],
    "guide": "Show what you make on the watch with chums_show." }
]
```

| Field | Meaning |
| --- | --- |
| `runtime` | `claude` (default), `codex`, `hermes` (below); experimental: `copilot`, `acp` |
| `cwd`, `addDirs` | its folder, and other folders it may write to (Codex: the sandbox's writable roots) |
| `model` | e.g. `gpt-6-astra`; set from the agent's card on the iPhone (Model). Claude Code agents take it too (`opus`, `sonnet`, …) |
| `permissionMode` | Hermes agents: `default` (Asks first: every command, and every lock, cover or alarm in Home Assistant, comes to the watch and the phone as Allow / Deny) or `auto` (Decides on its own). Claude Code agents: how the session asks before acting, set from the watch and the iPhone app: `default` (Asks first: its requests come to the watch and the iPhone as Allow / Deny), `acceptEdits` (Edits on its own), `auto` (Decides on its own). Passed as `--permission-mode`, so it holds when the session is resumed. Without it, `permissions.defaultMode` from `~/.claude/settings.json` is passed the same way |
| `approvalPolicy` | Codex agents: how it asks before acting, set from the watch and the iPhone app: `untrusted` (Asks first), `on-request` (Asks when it needs to), `never` (Never asks). Without it, `approval_policy` from `~/.codex/config.toml` when it is one of these, else `on-request` |
| `sandbox` | `read-only`, `workspace-write` (default), `danger-full-access` |
| `config` | more `-c key=value` overrides for its app-server, e.g. `["model_reasoning_effort=\"high\""]` |
| `command` | another `codex` to run, e.g. `["/opt/homebrew/bin/codex"]` |
| `chumsTools` | `false` leaves out the Chums MCP server (the hub then speaks final answers) |
| `guide` | how to use its tools, added to its standing instructions |
| `baseUrl`, `apiKey` | Hermes agents: this agent's chat API and key, instead of `hermesBaseUrl` and `hermesApiKey` in config.json |

After editing agents.json, restart the hub.

- `bin/chums-agents install` makes LaunchAgents for Claude Code agents only.
- `status` lists all agents.
- `open <id>` opens a Claude Code or Codex agent in this terminal (for Codex, the same as `chums-codex <id>`).

## How it works

- **Starting.** `hub/runtimes.mjs`, `startRunners`: one runner per agent with a `runtime`, started with the hub.
  - It runs `codex app-server --listen unix:///tmp/chums-<uid>/<hub port>-<id>.sock` (a short path: a Unix socket's
    path has room for 103 bytes), with your real `HOME` and a full `PATH`.
  - It connects (`hub/jsonrpc.mjs`, a small WebSocket client), then `initialize` and `thread/resume` (or
    `thread/start`).
  - Thread settings: the model, `approvalPolicy`, `sandbox`, and the standing instructions as developer instructions
    (`hub/standing.mjs`: who it is comes with its soul, plus the watch's rules).
- **Joining the watch.** The runner joins the hub as a session with a `driver`, so `deliver()` hands it orders and
  verdicts directly instead of a channel's inbox.
  - An order becomes `turn/start` (or `turn/steer` while a turn runs), with `[From the watch]` (or `[From the iPhone]`) in front. A Codex
    terminal on the same thread can tell which messages to answer on the watch.
- **Chums tools.** `-c mcp_servers.chums.*` gives Codex the Chums MCP server (`channel/chums-channel.mjs` with
  `CHUMS_SESSION`: tools only, bound to this session).
- **Approvals.** Codex's approval requests become cards. The answer goes back as `accept` / `decline`.
  `serverRequest/resolved` for a card still up means a Codex terminal answered it, and the card goes.
- **Restarts and logs.**
  - When the app-server exits, the agent goes offline and starts again after 5 s, doubling up to 2 min.
  - `~/.chums/agents/<id>.runtime.json` holds its thread, socket and pid, for `chums-codex` and for stopping an
    app-server a killed hub left behind.
  - Log: `~/.chums/agents/<id>.host.log` (`CHUMS_RUNTIME_DEBUG=1` adds every event).

## Tests

```bash
node tools/test-runtimes.mjs           # stand-ins for a Codex app-server and an ACP agent: 70 checks, no model calls
node tools/e2e-runtimes.mjs codex      # the real Codex (gpt-6-astra): 26 checks, about 2.5 min and 14 turns
```

Both run a private hub with its own `HOME`, ports and agents, never `~/.chums` or the hub on 7717. The e2e uses your
Codex login.

## Hermes: a model behind a chat API, with the hub's own tools

`runtime: "hermes"` runs no agent program. The hub sends each order to an OpenAI-style chat completions API
(OpenRouter by default, or a local server such as Ollama or llama.cpp) and runs the model's tool calls itself
(`HermesRunner` in `hub/runtimes.mjs`).

```json
{ "id": "garfield", "name": "Garfield", "character": "garfield", "runtime": "hermes",
  "cwd": "/home/you/chums-garfield", "model": "deepseek/deepseek-chat" }
```

| config.json | Default | What it sets |
| --- | --- | --- |
| `hermesBaseUrl` | `https://openrouter.ai/api/v1` | The chat API (`…/chat/completions` is added) |
| `hermesApiKey` | `$OPENROUTER_API_KEY` | Its key. A local server may need none |
| `hermesModel` | `deepseek/deepseek-chat` | The model, when the agent has no `model` |
| `hassUrl`, `hassToken` | `http://homeassistant.local:8123`, none | Home Assistant. Without a token there is no `home_assistant` tool |
| `hassDomains` | `light switch fan climate media_player scene script cover lock alarm_control_panel` | The domains it may call |
| `hassConfirm` | `lock cover alarm_control_panel` | The domains that ask first, like a command |
| `weather` | none | `{ "name": "Home", "latitude": 0, "longitude": 0 }`: the place for the `weather` tool (Open-Meteo). Without it there is no `weather` tool |
| `homelab` | none | `[{ "name": "NAS", "host": "192.0.2.10", "port": 80 }]`: the services `homelab_status` checks. Without it there is no such tool |

- **Tools.** `bash` (a shell command in its `cwd`, 8 s at most, output cut at 2000 characters), and the ones above
  that are configured.
- **Asking first.** In `default` mode (Asks first) every command waits for Allow on the watch or the phone; denied,
  the model is told so. `auto` (Decides on its own) runs them. Switch it on the agent's card, as for Claude Code.
- **Home Assistant.** The domain must be in `hassDomains`, the service a plain name, and the entity one entity in
  that domain: a call outside that is refused before Home Assistant is called.
- **A turn.** Up to 5 rounds of tool calls, then an answer. Each call to the model has 30 s. The last 8 turns are
  kept, whole, so a tool result is never sent without its call. Orders said while it works wait their turn.
- **Errors** are said as one sentence ("The model is rate limited…"); the detail is in
  `~/.chums/agents/<id>.host.log`.
- **Not kept across restarts.** The conversation lives in the hub; restarting the hub or the agent starts a new one.
- **Tests.** `node tools/test-hermes.mjs`: a stand-in chat API that rejects what OpenAI-style APIs reject, and a
  stand-in Home Assistant.

## Experimental: GitHub Copilot and ACP agents

Built and tried, not part of the claim.

- **`runtime: "copilot"` runs Copilot through GitHub's SDK.** The SDK is GA and first-party: `onPermissionRequest`
  for approvals, tool events, a system message, and the Chums MCP server.
  - Its runtime (Copilot 1.0.95, about 130 MB) is an optional dependency, not installed by default:
    `npm install --no-save @github/copilot-sdk`.
  - One e2e run (`node tools/e2e-runtimes.mjs copilot`, `gpt-5-mini`) passed on 2026-10-10: answer, approve, deny,
    `chums_reply` 4 of 4. In that run the model once claimed a denied `rm` had worked.
  - No terminal view while the hub runs it. Orders said while it works are queued: its "immediate" mode lost one.
  - Not tested: compaction, subagents, rate limits, the stand-in tests.
- **`runtime: "acp"` runs any Agent Client Protocol agent from `command`**, e.g. `["copilot", "--acp"]` or
  `["gemini", "--experimental-acp"]`.
  - It passes the stand-in tests (`tools/test-runtimes.mjs`).
  - Tried with `copilot --acp` (1.0.32): answer, approve and deny worked. But that version ignores a session's stdio
    MCP servers, so there is no `chums_reply`/`chums_show`, and after a denial it ends its turn without a word.
  - Gemini, `claude-agent-acp` and `codex-acp` are untested.

Copilot's other first-party surfaces don't fit:

- `copilot --acp` is in public preview;
- hooks can't take orders in;
- `-p --output-format json` can't ask for approvals;
- `--remote` is GitHub's own UI.

The research behind these tables is from GitHub's and OpenAI's docs as of 2026-10-10. Hook details (timeouts,
fail-open) are as documented, not re-tested here.


## Native Hermes Agent (ACP)

`runtime: "hermes-agent"` launches the installed `hermes acp` program. This is separate
from `runtime: "hermes"`, which remains the standalone model API fallback described above.
Hermes owns inference, tools, skills, memory and its durable ACP session. Chums forwards
messages, tool progress and permission requests. It never executes Hermes tool calls locally
or replaces the profile's identity with a Chums soul.

Verify the installation with `hermes acp --check` first. Configure one entry per agent in
`~/.chums/agents.json`, preserving existing entries and their session IDs:

```json
{
  "id": "garfield",
  "name": "Garfield",
  "character": "garfield",
  "runtime": "hermes-agent",
  "cwd": "/absolute/path/to/workspace",
  "hermesHome": "/absolute/path/to/existing/hermes/profile"
}
```

`hermesHome` is optional; when supplied it becomes the child process's `HERMES_HOME`.
Use a distinct configured Hermes profile/home for agents that require separate identity
or memory. `command` can override the executable, for example
`["/absolute/path/to/hermes", "acp"]`. A remote host can use an explicitly configured
stdio-preserving SSH command; authentication and host verification must already work.
No credentials are entered on the phone or copied into the repository.

On first connection Hermes creates an ACP session. Its ID is saved in `agents.json`.
A restart reloads that session. If Hermes refuses to load it, Chums fails rather than
silently creating a replacement. This does not attach to a live Telegram, CLI or TUI
conversation. Shared attachment to those conversations needs a separate gateway adapter.

Permission cards answer only `allow_once` or `reject_once`; an adapter offering only
permanent permission changes is cancelled. Hermes remains responsible for its tool policy;
Chums cannot force a backend configured to auto-approve tools to request permission.

Contract coverage: `node tools/test-hermes-native.mjs` uses a simulated ACP agent. A real
installation and physical Android device must still be tested before deployment.
Source protocol: https://github.com/NousResearch/hermes-agent/blob/main/website/docs/developer-guide/programmatic-integration.md
