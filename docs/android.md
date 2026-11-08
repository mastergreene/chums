# Android client: Claude Code and Codex first

This branch builds an Android client for the existing Chums hub on a Mac. The
Mac runs the hub, speech services and Claude Code/Codex. Android records speech,
sends text, shows tool activity, displays approvals and opens results. A watch
is not required. Hermes integration is deferred; this PR does not add the native
Hermes adapter from PR #1. Existing standalone Hermes code remains unchanged.

## Connect a phone

1. Set up the Mac using [setup.md](setup.md), including the hub, agents and
   Tailscale HTTPS address. Skip the Apple device build/install steps when using
   Android only. Keep the Mac awake and online.
2. Use the hub from this branch. Android checks its `phoneRequestReceipts` flag
   before sending: older hubs do not provide the duplicate protection needed by
   saved messages. An older hub can still be viewed, but sending is disabled.
3. Build the Android debug APK with JDK 17, Android SDK 34 and this repository's
   Gradle wrapper:

   ```sh
   cd app/ChumsAndroid
   ./gradlew assembleDebug testDebugUnitTest lintDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

   The pull request's Android CI job also uploads `chums-android-debug` after a
   successful build. Extract its ZIP and install the APK on your Android phone
   (Android 8 or later). Debug and release use different app IDs and settings.
4. In Android Settings in Chums, enter the Mac's HTTPS hub address and its
   `watchToken` as the device token. The address is a root URL without a path,
   query, password or token. Never post the token in chat, screenshots or logs.
5. Tap the agent name to select a Claude Code or Codex agent. The dot and label
   show hub reachability and agent status separately. Start an offline agent on
   the Mac. Runtime switching is offered only when the hub advertises support.
6. Send a text message first, then hold the microphone button to record. Android
   microphone permission is required for voice. Text works without it. The
   volume-up shortcut works while Chums is in front.
7. Open **Conversation and text message** for recent hub messages and result
   tiles. Select a tile to view a picture, self-contained interactive HTML,
   text, PDF or video. Other documents open through an installed Android app.
   Website artifacts offer their live URL in the browser, without the hub token.
8. Optionally enable **Keep connected in background** in Settings. This starts
   an Android foreground service with a persistent notification. Grant
   notification permission to receive approval/reply alerts. Tap an alert to
   open Chums and select the agent; approvals are reviewed inside the app.
   Android battery policies, force-stop and service termination can interrupt
   this connection. It is not a push-notification service.

## Saved messages and receipts

Text and recordings are saved privately on the phone before upload, bound to the
original agent and hub address. Closing a dialog, losing the network or rotating
the device does not discard an unconfirmed message. Open the conversation to
retry or discard it. There is no automatic replay on reconnect.

Every retry uses the same request ID and identical payload. The updated hub
stores admission and result receipts in `~/.chums/phone-receipts.json` (mode 0600),
and returns the stored result rather than enqueueing a duplicate order. A
conflicting payload is refused. If the hub restarts with an unfinished admission,
delivery is reported as uncertain and the message is not dispatched again.
Check the real agent's history before discarding and sending a new message.
An HTTP response that says the agent is offline is retained on the phone, with
instructions to check the conversation and send a new message after it is ready.

Receipts are not the agent's durable work queue: an accepted but not yet consumed
order can still be lost if the hub crashes. A receipt confirms dispatch, not task
completion. Do not delete receipts as a way to retry a command. At 10,000 receipts
the hub refuses new identified messages instead of forgetting old IDs; archival
and reconciliation are future work. Old watch/iPhone requests without IDs retain
their previous behavior.

## First device acceptance test

- Text Ray a request and confirm it reaches the same Codex/Claude Code session
  on the Mac. Check its tools, final reply and separate conversations for two agents.
- Ask for an action that requires permission. Check the complete action preview,
  deny it, then test an allowed action. A closed approval must not say confirmed.
- Record a message; check transcription, reply playback and the phone TTS
  fallback if hub speech is unavailable. Test denied microphone permission.
- Request an image, HTML page, PDF and video; open each tile. Check document
  opening with and without a suitable installed app. Interactive HTML supports
  inline scripts but blocks external network resources, navigation and hub access.
- Disable the network before sending, reconnect and manually retry the saved
  message. Test a lost HTTP response and a hub restart; do not duplicate work.
- Switch agents while recording. The recording must go to the agent selected
  when recording started. Rotate the phone and interrupt a press with app switching.
- Enable background connection, put Chums in the background, generate an approval
  and a reply, then tap the notifications. Test notification denial and Android
  battery restrictions on the actual device.

## Limits of this first Android implementation

The client shows the hub's recent messages, not a full transcript browser.
Attachment upload, soul/model/voice editors, widgets, Wear OS and custom pendant
clients are not implemented. Unported upstream characters use the Ray fallback;
changing a visual face does not change the selected agent. PDF previews are
rendered page by page; live sites open in the browser. Self-contained HTML does
not load remote libraries. This branch targets a Mac hub; Linux artifact and
voice parity is separate work. Real agent/device testing is required before
calling the Android experience equivalent to the original.

Backend delivery contract: `npm run test:phone`. Android tests cover saved text
and audio across restart, agent/destination binding and upstream artifact decoding.

## Validation for this draft

Passed in the development environment: Android debug and unsigned release APK
builds, all six JVM unit tests, `lintDebug` (no issues), debug APK signature
verification, phone delivery contract, channel suite and standalone Hermes
regression suite. No Android device is attached here; live voice, rendering,
notifications, battery behavior and Claude Code/Codex end-to-end checks remain
device acceptance work. macOS artifact-rendering tests require a Mac runner.
