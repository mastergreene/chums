# ChumsOG-for-android

This branch starts from upstream `lcoutodemos/chums` commit
`6821bd6e1aa3d3fb41ca77bd0cc4de0dbbb8097a` (use `git merge-base HEAD upstream/main` for the full pinned ancestor),
not the Garfield/Hermes fork. Its goal is to port the original Chums phone
experience to Android while preserving the original Mac hub, agents, character
identities and setup model.

## Design reference

- `app/ChumsPhone/HomeView.swift`: black conversation screen, gray message
  bubbles, agent drawer, character in the middle before a conversation and at
  its foot afterwards, bottom text/voice/file composer.
- `app/ChumsPhone/AgentSheet.swift`: runtime, model, permissions, voice, and
  editable soul fields with undo.
- `app/ChumsPhone/Design.swift`: white and grays, amber for needs-you, 44-point
  touch targets, rounded bubbles and sheets.
- Original characters: **Noodle, Stretch, Peeps, Squint, Ray, Tangle, Crunch,
  Nana**. Garfield and Hermes are outside this branch's scope.

The Mac hub, channel, tools and Apple clients are unchanged. Android calls the
same authenticated `/v1/` endpoints as the iPhone. It does not require a watch
or iPhone, and it does not require the receipt extensions from the earlier
Android experiment.

## Current implementation

| Area | Status |
| --- | --- |
| Conversation screen, agent drawer, bottom composer | Native Compose implementation following the original layout |
| Text, hold-to-talk, tap-to-record, volume-up shortcut | Implemented; physical microphone/gesture testing required |
| Tool activity and scoped allow/deny | Implemented; real Claude Code/Codex acceptance testing required |
| Photos and files | Android picker, authenticated upload, attachment removal and send |
| Results | Pictures with zoom, self-contained HTML/text, PDF preview, video and external document opening |
| Agent card | Runtime/model/permission/voice pickers, editable soul fields and undo |
| Read-aloud and connection settings | Implemented, including optional Android foreground connection/notifications |
| All eight character identities and artwork | Original assets packaged directly from upstream, not redrawn replacements |
| Live character states and interactions | Live local canvas for seven characters; Stretch uses 76 original state-specific clips. Touch reactions, drag gaze and two-finger rotation are implemented |
| Source fidelity | Original Pix renderer, Ray outlines, Tangle face glyphs and Noodle shape journeys are reused. Other simulation and face logic is ported with reduced physics; exact Swift parity is not established |
| Widgets, camera capture, ordered/reorderable drawer, full Markdown styling | Remaining Android parity work |
| Full-screen visual/device comparison | Not yet performed |

Promotional watch-frame loops are no longer shipped or used. The local renderer
receives the selected identity, live agent state, tool events and reply captions.
It has no hub credentials, Android JavaScript bridge or remote resources.
Stretch is decoded from the original HEIC sequences at build time, without the
watch shell. All characters render on the same local WebView path, including
Android 8 with an updated Android System WebView. Canvas checks verify rendering
and state changes; they do not establish exact Swift physics or device parity.

## Build and install

Use JDK 17, Android SDK 34, Python 3 with Pillow, and `heif-convert` with
sequence support (`-S`). Pillow is available as `python3-pil` on Ubuntu. Older Ubuntu libheif packages
 lack sequence support; CI builds pinned libheif 1.23.2 from source.
The build converts the original character assets automatically:

```sh
git clone --branch ChumsOG-for-android https://github.com/mastergreene/chums.git
cd chums/app/ChumsAndroid
./gradlew assembleDebug assembleRelease testDebugUnitTest lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Application ID: `club.chums.ogandroid` (`club.chums.ogandroid.debug` for debug).
This keeps the OG port separate from the earlier Android experiment on a phone.
Generated artwork is copied by Gradle from the existing upstream assets; source
artwork stays in its canonical repository locations.

## Connect the original hub

1. Follow the Mac hub/agent/Tailscale portions of [setup.md](setup.md). Keep the
   original hub and your Claude Code/Codex sessions running. Skip iPhone/watch
   pairing, Apple mobile signing and Apple device installation for Android use.
   Mac-side tooling prerequisites still apply; this is not a Linux hub port.
2. Install the Android APK. Enter the Mac hub's HTTPS root URL in Settings and
   its `watchToken` as the device token. Do not share or commit that token.
3. Open the agent drawer. Select Noodle or another original agent, then send
   text first. Open the agent's name for runtime/model/voice/soul configuration.
4. Allow the microphone, then tap to record hands-free or hold and release to
   send. Add photos/files through +. Open generated result tiles in the chat.
5. Optionally enable background connection and notifications in Settings.
   Android may still suspend or stop the service; this is not a push service.

## Message recovery with the original hub

Messages and recordings are saved privately before sending. An unsent offline
message can be sent after reconnection. After an attempt with the original hub,
the client does not automatically retry: a lost HTTP response may mean the
agent already received it. Check the agent's real conversation before discarding
and sending a new message. This avoids claiming duplicate protection that the
unchanged upstream hub does not provide. Hubs that explicitly advertise request
receipts can additionally confirm stable-ID retries.

## Acceptance before calling this an original-equivalent port

- Compare the original iPhone screen and Android side by side for each layout.
- Select each of the eight agents; check its identity, runtime and conversation.
- Text and speak to real Claude Code and Codex; approve and deny an action.
- Upload a photo/file, open each artifact type and test errors without a viewer.
- Change a soul field, undo it, switch models and voices, and confirm the Mac
  receives the changes without replacing the agent's identity.
- Test network loss, hub restart, microphone denial, rotation, switching agents
  during recording/upload and background notifications on a physical phone.
- Port and compare each character rig's idle/listening/thinking/working/
  subagents/approval/needs/done/blocked/speaking/sleep reactions and interactions.

Build success and JVM unit tests do not establish these device or visual checks.

## Development validation

Validation includes debug and unsigned release APK builds, eleven JVM unit
tests, Android lint, and 112 real-canvas character/state cases. Run `npm ci` and
`npm run test:characters` after the Gradle asset export to reproduce canvas
checks and PNG snapshots. These checks run in CI before publishing the APK.
No physical Android device is attached; microphone, notification, WebView and
live-agent acceptance checks above remain outstanding. The upstream hub and
Apple client source trees are unchanged.
