# The characters

Every character acts out the same states: idle, listening, thinking, working, subagents, speaking, done, needs you,
approval, blocked, dizzy and sleep, plus reactions to a tool call and to your touches.
[design/STATES.md](../design/STATES.md) maps what Claude Code and Codex report to each of them.

| Character | What it is | Code |
| --- | --- | --- |
| Noodle | A soft 3D tube, blue at the tail through purple and pink to a yellow-orange head with two eyes. 64 points a fixed distance apart, moved between a library of shapes, lit and sliced live | `Rig/NoodleStage.swift`, `Rig/NoodleRest.swift`, `noodle-shapes.bin` |
| Stretch | A glowing gel puppet rendered in Blender (Cycles). Each state is a pool of clips at 24 fps chained at random; per-frame data says where its hair, face and glass contact are, so the screen plays along | `Rig/StretchStage.swift`, `Rig/StretchFrames.swift`, `Noodle/`, `design/noodle/` |
| Peeps | Two eyes on black. Everything is in how they move; they follow your finger | `Rig/PeepsStage.swift` |
| Squint | A face drawn in white marker strokes, like a kaomoji. Each part is one stroke that changes shape and hangs on its own spring | `Rig/SquintStage.swift`, `Rig/Chalk.swift` |
| Ray | A flat golden sun with ten wavy arms; the mood changes the arms' shape and speed | `Rig/RayStage.swift` |
| Tangle | A ball of yarn drawn as one pale-blue marker line, with a face | `Rig/TangleStage.swift`, `Rig/Yarn.swift` |
| Crunch | A deadpan pixel apple whose moods are render settings: low resolution while thinking, HD when done, a frozen window when stuck, a bouncing screensaver asleep | `Rig/Pix.swift` (port of `design/pix/pix.js`) |
| Nana | The same as Crunch, as a pixel banana | `Rig/Pix.swift` |

Paths are under `app/ChumsWatch/` unless they start with `design/`.

## Gestures

| Do this | It does |
| --- | --- |
| Hold the screen | Talk. Let go to send; slide onto × to cancel |
| Tap the character | Poke it. Four in 3 s: Peeps, Squint, Ray and Tangle get annoyed, Noodle gets dizzy |
| Stroke across it | Pet it |
| Hold the approval card | Allow the command (1 s); tap × to deny |
| Turn the Digital Crown | Each character reacts its own way: Stretch stretches, Nana ripens, Squint picks a face (and keeps it), Tangle unspools or winds itself, Peeps get bubbles (up) or a wind (down); Noodle and Ray, spun fast, get dizzy |
| Tilt or shake the watch | Peeps and Tangle follow a tilt; a hard shake makes any character dizzy |
| Tap the faces at the top left | The agents list (press and hold a row, then drag it, to change the order), with Settings below |


## Stretch: the Blender pipeline

Every clip is keyed acting in `design/noodle/animate.py`: a rest loop and beats per state, chained at random by the
player. `noodle_rig.py` (also stored in the `.blend`) is the pose API.

```bash
cd design/noodle
/Applications/Blender.app/Contents/MacOS/Blender -b noodle.blend -P animate.py -- --preview --step 4 speak_a   # quick motion check
/Applications/Blender.app/Contents/MacOS/Blender -b noodle.blend -P animate.py -- all                          # final: 396 × 484, 128 samples
cd ../.. && tools/noodle-watch.sh                 # pack the frames as HEIC sequences for the watch (app/ChumsWatch/Noodle)
```

The final render takes several hours on a laptop GPU. The packed HEIC clips are committed, so you only need Blender
to change Stretch.

## Crunch and Nana: checking the port

`app/ChumsWatch/Rig/Pix.swift` must render the same frames as `design/pix/pix.js`. `tools/pix-compare.sh` renders the
104 reference frames on the Mac and compares them with `design/pix/reference`, pixel for pixel.
`open design/pix/sheet.html` shows a contact sheet of the JavaScript version.

## Recording

`app/ChumsWatch/OfflineRender.swift` renders any character frame by frame in the Simulator from a JSON spec
(`-render <base64 spec>`), so a state can be checked or recorded without a hub. `xcrun simctl io booted recordVideo`
records the Simulator's screen. `tools/readme-loops.sh` renders each character's loop for the README with it (run
`bin/chums-sim` once first).

## Adding a character

1. Add a case to `CharacterKind` in `app/ChumsWatch/Rig/RigCore.swift`, with its name. If it plays frames (like
   Stretch, Crunch and Nana), make `framed` true for it there too: the screen then draws at 30 frames a second.
2. Draw it in a `<Name>Stage.swift` (see `RayStage.swift`) and return it from the stage switch in `Rig/Rig.swift`.
3. Add its id to `LOOKS` and `LOOK_NAMES` in `hub/hub.mjs`, a voice in `VOICES` there (else it gets `af_heart`), to
   `looks` in `app/ChumsWatch/Soul.swift`, and to `order` in `app/ChumsPhoneShared/WidgetHub.swift` (the widgets' order).
4. Add an `avatar-<id>` image to `app/Shared/Characters.xcassets`. If the picture goes to its edges (rendered, not pixel
   art in the middle), add the id to `fullBleed` in `app/ChumsWatch/AgentFace.swift`.
5. Check every state in the Simulator (`bin/chums-sim`, then the demo in Settings), and on the iPhone Simulator.


## Android character contract v1

`CharacterCatalog.kt` is the native Android registry: stable skin ID, display name and fallback.
Agent ID, runtime, working directory and session ID are never derived from the selected skin.
`AgentInfo.character` selects the default; the preview override changes rendering only.
Unknown skin IDs render Ray. To add a custom native character, implement a Compose renderer
accepting `ChumsState`, register its ID, and add its dispatch in `CharacterFace`.

`data/StateContract.kt` maps wire statuses. Every renderer receives a defined pose:
compacting falls back to thinking, limited/unknown to blocked, reconnecting to sleep.
The separate visible status label preserves the actual state, regardless of fallback artwork.
Provide idle, listening, thinking, working, subagents, speaking, done, needs-you, approval,
blocked, error and sleep poses, or document the fallback for each. Character art must never
hide the approval card, tool details or connection status. Test all states at phone and
wearable sizes before calling a renderer complete. Peeps/Squint remain first-pass interpretations.

This registry supports compiled native renderers. Loading downloadable image/animation packs
and shared manifests across Apple, Android and future wearable clients remains future work.
