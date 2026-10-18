# AGENTS.md

Instructions for a coding agent (Claude Code, Codex or another) working in this repository: most often because its user
asked it to "set up Chums", sometimes to change the code.

Chums is an Apple Watch app, an iPhone app and a hub on the Mac for talking to coding agents by voice. The user builds
the apps from source in Xcode, signs them with their own Apple ID, and puts the hub on the internet with Tailscale
Funnel. Some steps need the user's hands, accounts and devices; you do the rest and check each step.

- The steps, in order, with "How to know it worked" for each: [docs/setup.md](docs/setup.md). Follow it; this file only
  says who does what.
- Known problems and their fixes: [docs/troubleshooting.md](docs/troubleshooting.md).
- The check for every step: `bin/chums-doctor`. It is read-only, never prints tokens, and exits 0 when nothing fails.
  Run it first, after each step, and at the end. Until the last step, the sections for later steps fail; each step's
  "How to know it worked" in setup.md names the section to read.

## Rules

- **Never print, paste, log or commit the tokens.** They are `token` and `watchToken` in `~/.chums/config.json`, and
  the watch token is also in `app/ChumsWatch/HubConfig.swift`. Don't `cat` either file or show them in a diff. To see
  which keys are set: `python3 -c 'import json,os; print(sorted(json.load(open(os.path.expanduser("~/.chums/config.json")))))'`.
  If a token was shown anywhere, tell the user and follow "Rotating a token" in [SECURITY.md](SECURITY.md).
- Don't commit the generated files: `app/ChumsWatch/HubConfig.swift`, `channel/mcp.json`, `channel/hooks.json` (all
  git-ignored).
- Don't paste the output of `claude auth status`: it contains the account's email. Report only whether it is signed in.
- Ask before anything outside this checkout that is hard to undo, puts something on the internet or changes what runs
  at login: `bin/chums-setup --launchd`, `bin/chums-agents install`, `bin/chums-tunnel` (it publishes the hub's watch
  API with Tailscale Funnel), `launchctl bootout`, `tailscale … funnel reset`, deleting `~/.chums`,
  removing an app from the watch, changing `team` or `bundleId` after the app is installed, and any `sudo`.
- `bin/chums-hub` runs until stopped. Prefer `bin/chums-setup --launchd` (ask first): a hub you start in your own
  shell stops when your shell does, and the permissions on the Mac and Codex's screenshots need the hub to run as
  Chums. Until they agree, run `bin/chums-hub` in the background.
- On a Mac without Xcode or its Command Line Tools, the first `python3` (which every `bin/` script but the doctor
  uses) opens an install dialog. Have the user install Xcode (step 1) before you run `bin/` scripts.
- Don't run `bin/chums-claude` inside your own session: it starts an interactive Claude Code session for the user's
  terminal.
- If you are yourself running as a Chums agent, restarting the hub or `bin/chums-agents` cuts off the watch's
  conversation: ask first.

## Who does what

| Step ([setup.md](docs/setup.md)) | You can do it | Only the user can do it |
| --- | --- | --- |
| 1. What you need | `brew install node xcodegen whisperkit-cli ffmpeg tailscale uv` (uv is for the agents' voices). Claude Code: `curl -fsSL https://claude.ai/install.sh \| bash`. Codex: `npm install -g @openai/codex` | Install Xcode from the App Store (their Apple ID). Install Homebrew if it's missing (it asks for their password) |
| 2. Pick your agent | Ask which agent they use: Claude Code, Codex or both | Sign in: `claude` then `/login`, and/or `codex login` (both open a browser). Check with `codex login status`, or `claude auth status` without showing its output |
| 3. Get the code and check the Mac | `bin/chums-setup`: npm packages, the Whisper model (about 150 MB), and the agents' voices when uv is installed (about 1.5 GB). Run it until it prints `Ready`. If only the Xcode lines are `MISSING`, go on to step 4 and come back after step 6 | |
| 4. Start the hub | `bin/chums-setup --launchd` once they agree, else `bin/chums-hub` in the background. Ask what the agents should call them and set `ownerName` (below) before step 10 | |
| 5. Tailscale Funnel | Run `bin/chums-tunnel` in the background and read its output. When it prints a `login.tailscale.com` link and waits, stop and give the user the link | Create a Tailscale account, open the sign-in link, and approve the Funnel link if a second one appears |
| 6. Apple ID in Xcode | Check with `bin/chums-doctor` (the `signing team` line) | Xcode › Settings › Apple Accounts › + › Apple ID |
| 7. Connect the devices | Watch progress with `bin/chums-doctor` (section 7) | Plug the iPhone in by USB, tap Trust on the iPhone and the watch, keep the watch unlocked, open Window › Devices and Simulators and wait for "Preparing" to finish |
| 8. Developer Mode | Check with `bin/chums-doctor` (section 7) | Turn it on in Settings › Privacy & Security on the iPhone and on the watch; each restarts and asks to confirm |
| 9. Build and install | `bin/chums-install`, once the doctor shows the watch with Developer Mode on. It installs the watch app, then the iPhone app when Xcode knows the iPhone and its Developer Mode is on. Tell the user to keep the watch and the iPhone unlocked while it runs | Register the devices from Xcode if the build stops at "Your team has no devices" ([troubleshooting](docs/troubleshooting.md#your-team-has-no-devices)) |
| 10. Start the agents | `bin/chums-agents install` once they agree (Chums.app is signed for the same team as the apps, so it keeps its permissions across rebuilds): eight Claude Code agents, one per character, about 200 MB of memory each. Skip it if they use only Codex. Change the agents' folders only if they ask (below) | Switch an agent to Codex: on the watch (swipe its row left, Runs on) or on its card in the iPhone app (Runs on). Answer Claude Code's first-start screens if they run `bin/chums-claude` themselves |
| 11. Talk to it | Read the hub log (the output of `bin/chums-hub`, or `~/.chums/hub.log` under launchd) to confirm the clip arrived (`utterance … -> order`) | Allow the microphone on the watch, hold the screen and speak |
| Keyring mode (optional) | | Allow Health › Workouts on the watch; change the watch settings listed in setup.md |
| Permissions on the Mac (once) | Check with `bin/chums-permissions check` (asks nothing); the hub and the agents must run through Chums.app first (`bin/chums-setup --launchd`, `bin/chums-agents install`, once they agree) | Open Chums from `~/Applications` (`bin/chums-permissions` opens it) and click Allow All: Allow or OK on each macOS dialog, and switch Chums on in the System Settings panes macOS's alerts open (Accessibility, Screen Recording) ([setup](docs/setup.md#permissions-on-the-mac)) |

When you stop for the user, say exactly what to do, on which device, and what they will see when it worked. Then wait,
and check with `bin/chums-doctor` before you go on.

## What to say at each hand-over

Use these words, with the parts in brackets filled in. Then wait for the user, and run the check.

| When | Say | Check |
| --- | --- | --- |
| Xcode is missing | "Install Xcode from the Mac App Store, open it once, and let it install its components. Tell me when that's done." | doctor, "6.": Xcode 26 `PASS` |
| Claude Code isn't signed in | "In a terminal, run `claude`, type `/login`, and sign in in the browser. Tell me when that's done." | doctor, "2.": Claude Code signed in |
| Codex isn't signed in | "In a terminal, run `codex login` and sign in in the browser. Tell me when that's done." | doctor, "2.": Codex signed in |
| `bin/chums-tunnel` printed a `login.tailscale.com/a/…` link | "Open [link], sign in to Tailscale (a free account works), and connect the machine. Tell me when the page says it's connected." | the script goes on; doctor, "5.": signed in |
| It printed a `login.tailscale.com/f/funnel…` link | "Open [link] and approve Funnel for your tailnet. Tell me when that's done." | doctor, "5.": the tailnet allows HTTPS certificates and Funnel |
| No Apple ID in Xcode | "In Xcode, open Settings › Apple Accounts, click +, choose Apple ID, and sign in. A free Apple ID works." | doctor, "6.": a `signing team` line |
| Xcode doesn't know the watch | "Connect your iPhone to this Mac with a USB cable and unlock it; tap Trust if it asks. Unlock your watch, keep it near the iPhone, and tap Trust on it if it asks. In Xcode, open Window › Devices and Simulators and wait until the watch is listed and preparing has finished; the first time it can take 10 minutes or more. Keep the watch unlocked until then." | doctor, "7.": `Apple Watch [name]` |
| Developer Mode is off | "On the iPhone: Settings › Privacy & Security › Developer Mode, turn it on, and restart when it asks; after the restart, unlock it and tap Turn On. Then on the watch: Settings › Privacy & Security › Developer Mode, turn it on, and restart it from the prompt it shows; after the restart, unlock it and confirm Turn On." | doctor, "7.": `Developer Mode on` for the watch, `Developer Mode enabled` for the iPhone |
| The build stops at "Your team has no devices" | "Open app/Chums.xcodeproj in Xcode. Choose the Chums scheme and your iPhone, and press Run (⌘R). Then choose the ChumsWatch scheme and your watch, and press Run. Tell me when both have run." | `bin/chums-install` again |
| Before `bin/chums-install` | "Keep your watch and your iPhone unlocked and awake until I say the install is done. Tap the watch's screen now and then." | it prints `Chums is running on the watch.` |
| Permissions on the Mac | "At the Mac, in the Chums window I opened, click Allow All. Click Allow or OK on each macOS dialog, and switch Chums on in each System Settings pane it opens (Accessibility, Screen Recording). Tell me when every row says Allowed." | `bin/chums-permissions check` |
| First talk | "On the watch, allow the microphone, notifications and Workouts when Chums asks. Then hold the screen, say 'What folder are you in?', and let go." | the hub log: `utterance … -> order` |

To set one key in `~/.chums/config.json` without printing the file (here `ownerName`; it keeps the file's mode 0600):

```bash
python3 -c 'import json,os,sys; p=os.path.expanduser("~/.chums/config.json"); c=json.load(open(p)); c[sys.argv[1]]=sys.argv[2]; json.dump(c,open(p,"w"),indent=2); os.chmod(p,0o600)' ownerName Sam
```

## Writing ~/.chums/agents.json

Every character is its own agent. The hub writes one per character on its first start (the id is the character's:
`noodle`, `stretch`, `peeps`, `squint`, `ray`, `tangle`, `crunch`, `nana`), all on Claude Code. The user switches an
agent to Codex, and picks its model, on the watch (its soul screen, Runs on) or on its card in the iPhone app (Runs
on, Model); don't do that by editing the file. To change their folders:

1. Ask the user which folders each agent may work in (`cwd`, and `addDirs` for more).
2. Copy the file to `~/.chums/agents.json.bak` first.
3. Keep one entry per character, its `id` the character's, and an absolute `cwd`.
   [tools/agents.example.json](tools/agents.example.json) shows the shape.
4. `"model"` is the model the user picks on the iPhone; set it in the file only if they ask (for example when
   Codex's default model isn't on their plan). Every field: [docs/other-agents.md](docs/other-agents.md#agentsjson).
5. Keep the `sessionId` of existing agents: it is the agent's conversation.
6. Run `bin/chums-doctor` (section 3 validates the file), then restart the hub and, for Claude Code agents,
   `bin/chums-agents install`.

## Working on the code

- Tests, which run a private hub with its own temporary home and ports, never the user's `~/.chums` or hub:

  ```bash
  node tools/test-channel.mjs
  CHUMS_TEST_NO_MODEL=1 node tools/test-hub.mjs      # without CHUMS_TEST_NO_MODEL it also calls a model (claude -p)
  node tools/test-artifacts.mjs                      # every kind of file an agent can show
  node tools/test-runtimes.mjs
  node tools/test-hermes.mjs                         # runtime "hermes": a stand-in chat API and Home Assistant
  ```

- The watch app builds for the Simulator with `bin/chums-sim`, with no signing. Shut the simulator down afterwards
  (`xcrun simctl shutdown "Chums Watch"`): a simulator left running without a window plays sounds on the Mac.
- Where things are, and how to propose a change: [CONTRIBUTING.md](CONTRIBUTING.md). How it works:
  [docs/architecture.md](docs/architecture.md).
- Copy in docs, the app and the agents' prompts is plain and literal: every line states a fact.
