# Fetch

A character for Chums: a sitting kelpie in profile, chalk white on black. It has no mouth, no eyebrows and no
expression lines. Its mood is in the angle of the neck, the ears, the tail and how tall it sits.

Fetch is a design. It is not in the watch app yet; "Building it in Chums" at the end lists what that takes.

| File | What it is |
| --- | --- |
| [fetch.svg](fetch.svg) | The resting pose, one group per rig layer |
| [motion.json](motion.json) | Every state's keys, the eases and the channels. The tables below are generated from it |
| [preview.html](preview.html) | Plays every state and the touch and crown reactions, for Fetch and Biscuit. Open it in a browser |
| [build.py](build.py) | Writes both SVGs, `preview.html` and the generated parts of the two `SPEC.md` files from the numbers at its top and `motion.json` |
| [../biscuit/SPEC.md](../biscuit/SPEC.md) | Biscuit, an orange cat on the same rig and timelines |

## 1. Anatomy and proportions

### Canvas

- 396 × 484 pt, black (#000000). One unit is 22 pt, so the canvas is 18 × 22 units.
- The ground line is at y = 418 (19 units). The figure at rest is 352 pt tall (16 units) and 310 pt wide, from x = 37
  to x = 347. The nose has 49 pt of empty screen in front of it; the head-down states use that space.
- 396 × 484 is the 45 mm screen in pixels. The rigs in `app/ChumsWatch/Rig/` draw in 198 × 242 pt, so halve every
  length here. On a 40 mm watch (162 pt wide) the eye is 4.5 pt across and the collar 3.7 pt wide; nothing smaller
  than those two carries meaning.

### Construction

Every part is one of three things: the hull of two circles (a tapered capsule), a circle, or a rounded rectangle.
There are no free curves except the tail's centre line. Circles are (centre x, centre y, radius).

| Part | Construction | Fill |
| --- | --- | --- |
| Torso | Hull of the haunch (144, 349, 69) and the chest (240, 262, 40). The haunch sits on the ground line | Chalk |
| Hind foot | Rounded rectangle 88 × 20 at (142, 398), radius 10 | Chalk |
| Neck | Hull of (242, 252, 30) and (258, 172, 24). It leans 11° forward of vertical | Chalk |
| Collar | A black band 9 pt wide across the neck, 30% of the way up it, square to the neck's axis | Black |
| Head | Hull of the cranium (264, 152, 36) and the snout tip (330, 168, 15) | Chalk |
| Near ear | Hull of the base (247, 130, 16) and the tip (236, 78, 5) | Chalk |
| Far ear | Hull of the base (272, 124, 16) and the tip (268, 72, 5), behind the head | Stone |
| Eye | Circle (287, 147, 5.5) | Black |
| Nose | Circle (340, 159, 7). It cuts a notch out of the snout's top corner | Black |
| Near front leg | Rounded rectangle 28 wide from y = 266 to the ground, radius 14, and a paw 46 × 22, radius 11 | Chalk |
| Far front leg | The same, 17 pt to the right, behind the torso | Stone |
| Tail | A band 22 pt wide at the base and 13 at the tip around the quadratic curve (100, 388) (40, 390) (44, 310), round ends | Chalk |
| Flock (Subagents only) | Three circles of radius 7.5 on the ground at x = 336, 356 and 376 | Stone |

### Ratios

| Ratio | Value |
| --- | --- |
| Head length (117) to figure height (352) | 1 : 3 |
| Cranium diameter (72) to haunch diameter (138) | 0.52 |
| Snout beyond the cranium (45) to head length (117) | 0.38 |
| Snout tip depth (30) to cranium diameter (72) | 0.42 |
| Ear, base centre to tip centre (53) to cranium diameter (72) | 0.74 |
| Ear base width (32) to cranium diameter (72) | 0.44 |
| Neck length between pivots (82) to cranium diameter (72) | 1.13 |
| Neck width, base to top | 60 to 48 |
| Front leg width (28) to chest diameter (80) | 0.35 |
| Eye diameter (11) to cranium diameter (72) | 0.15 |

The triangle of black between the belly, the front leg and the hind foot is part of the drawing. Keep it open: it is
what makes the figure read as sitting from 2 m.

### Palette

| Name | Hex | Used for | Contrast on black |
| --- | --- | --- | --- |
| Chalk | `#F2EEE6` | The body, the head, the near ear, the near leg, the tail | 18.1 : 1 |
| Stone | `#A89B89` | The far ear, the far front leg, the flock | 7.7 : 1 |
| Black | `#000000` | The screen, the eye, the nose, the collar | |

Stone only ever means "the far side". No gradients, shadows, strokes or textures. Sleep dims the whole figure to 50%
opacity; nothing else changes a colour.

### Layers and pivots

The groups in `fetch.svg` are nested the way they are parented. Each group carries its pivot in a `data-pivot`
attribute, in canvas points. Listed back to front.

| # in the brief | Group id | Parent | Pivot | Rotates |
| --- | --- | --- | --- | --- |
| 1. Torso / base | `fetch` (root) | none | 169, 418: the ground under the body | moves, squashes, sways |
| 6. Tail | `tail` | `fetch` | 100, 388: the base of the spine, inside the haunch | -26° to +38° |
| 5. Front legs | `paw-right` (far) | `fetch` | 285, 280: the shoulder | 0° to +6° |
| 1. Torso / base | `torso` | `fetch` | 144, 349: the centre of the haunch | -2° to +6.5° |
| 2. Head and neck | `head` | `torso` | 242, 252: the base of the neck, inside the chest | -12° to +28° |
| 2. Head and neck | `skull` | `head` | 258, 172: the top of the neck, inside the cranium | -17° to +36° |
| 3. Ears | `ear-right` (far) | `skull` | 272, 124: the centre of the ear's base | -70° to +22° |
| 3. Ears | `ear-left` (near) | `skull` | 247, 130: the centre of the ear's base | -75° to +20° |
| 4. Snout / eye accent | `accent`, holding `nose` and `eye` | `skull` | 287, 147: the eye's centre | the eye scales and moves 2 pt |
| 5. Front legs | `paw-left` (near) | `fetch` | 268, 280: the shoulder | -34° to +3° |
| (Subagents) | `flock` | `fetch` | none | each dot moves up 14 pt |

Three things differ from the six layers in the brief:

- The head and neck are two groups. `head` turns the neck at its base; `skull` nods the head at the top of the neck.
  One pivot can't hold a tilted head on an upright neck, and that pose is Thinking.
- The front legs are parented to the root, not the torso, so the paws stay planted when the torso leans. Each
  shoulder pivot is inside the chest circle, which hides the joint for every lean in this file.
- The flock is a seventh layer that only Subagents shows.

Every pivot is the centre of a circle of the part's own colour that overlaps its parent, so no joint opens a gap at
any angle in the table.

The file was not imported into Fable while writing this. The group ids, the nesting and the pivots are what it
needs; if Fable doesn't read `data-pivot`, set each layer's anchor from the table.

### SVG, resting pose

<!-- gen:svg -->
```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 396 484" width="396" height="484">
<title>Fetch, resting pose</title>
<rect id="background" width="396" height="484" fill="#000000"/>
<g id="fetch" data-pivot="169 418">
  <g id="flock" opacity="0"><circle id="flock-1" cx="336" cy="404" r="7.5" fill="#A89B89"/><circle id="flock-2" cx="356" cy="404" r="7.5" fill="#A89B89"/><circle id="flock-3" cx="376" cy="404" r="7.5" fill="#A89B89"/></g>
  <g id="tail" data-pivot="100 388"><path d="M99.6 377 Q47.1 378.8 50.5 310.3 A6.5 6.5 0 0 0 37.5 309.7 Q32.9 401.2 100.4 399 A11 11 0 0 0 99.6 377 Z" fill="#F2EEE6"/></g>
  <g id="paw-right" data-pivot="285 280"><rect x="271" y="266" width="28" height="152" rx="14" fill="#A89B89"/><rect x="271" y="396" width="46" height="22" rx="11" fill="#A89B89"/></g>
  <g id="torso" data-pivot="144 349">
    <path id="torso-shape" d="M200.6 388.5 L272.8 284.9 A40 40 0 0 0 220.5 227.1 L110.3 288.8 A69 69 0 1 0 200.6 388.5 Z" fill="#F2EEE6"/>
    <rect id="hind-foot" x="142" y="398" width="88" height="20" rx="10" fill="#F2EEE6"/>
    <g id="head" data-pivot="242 252">
      <path id="neck" d="M271.8 255.7 L281.8 175 A24 24 0 0 0 234.9 165.6 L213.1 244 A30 30 0 1 0 271.8 255.7 Z" fill="#F2EEE6"/>
      <rect id="collar" x="196.8" y="223.5" width="100" height="9" fill="#000000" transform="rotate(11.3 246.8 228)"/>
      <g id="skull" data-pivot="258 172">
        <g id="ear-right" data-pivot="272 124"><path d="M287.3 119.4 L272.8 70.6 A5 5 0 0 0 263 71.3 L256.1 121.8 A16 16 0 1 0 287.3 119.4 Z" fill="#A89B89"/></g>
        <path id="head-shape" d="M266.8 187.9 L331.1 183 A15 15 0 0 0 337.9 155.2 L282.9 121.4 A36 36 0 1 0 266.8 187.9 Z" fill="#F2EEE6"/>
        <g id="ear-left" data-pivot="247 130"><path d="M261.6 123.5 L240.6 76 A5 5 0 0 0 231 78 L231 130 A16 16 0 1 0 261.6 123.5 Z" fill="#F2EEE6"/></g>
        <g id="accent">
          <circle cx="340" cy="159" r="7" fill="#000000" id="nose"/>
          <g id="eye" data-pivot="287 147"><circle cx="287" cy="147" r="5.5" fill="#000000"/></g>
        </g>
      </g>
    </g>
  </g>
  <g id="paw-left" data-pivot="268 280"><rect x="254" y="266" width="28" height="152" rx="14" fill="#F2EEE6"/><rect x="254" y="396" width="46" height="22" rx="11" fill="#F2EEE6"/></g>
</g>
</svg>
```
<!-- /gen:svg -->

## 2. Motion

### Rules

- 24 frames a second. Frame 0 of each loop is the state's key pose, and the last frame equals frame 0.
- Changing state: blend every channel from the pose on screen to the new loop over the state's "enter" frames with
  its "enter" ease, while the new loop already plays. The snap into Listening and the freeze into Approval are
  these blends.
- Values are offsets from the resting pose. Rotations are in degrees, clockwise on screen. The dog faces right, so
  a positive neck angle lowers the nose and a positive ear angle points the ear forward.
- A key's ease shapes the move that arrives at that key. Keys with no ease use `inout`.
- Overlap: the ears and the tail arrive 2 to 4 frames after the head and settle with `spring`. The body never
  uses `spring`.
- The eye blinks in Idle and Working only. It stays open in Listening and Approval, half shut in Blocked, shut in
  Sleep.

### Eases

| Name | Curve | Used for |
| --- | --- | --- |
| `inout` | cubic-bezier(0.45, 0, 0.55, 1) | Breathing, sways, anything unhurried |
| `in` | cubic-bezier(0.55, 0, 1, 0.45) | Falling, straining, a lid closing |
| `out` | cubic-bezier(0.16, 1, 0.3, 1) | The snap: 90% of the move in the first third |
| `over` | cubic-bezier(0.34, 1.56, 0.64, 1) | Overshoot: goes about 10% past and settles |
| `antic` | cubic-bezier(0.36, 0, 0.66, -0.56) | Anticipation: backs off before the move. For new clips; the loops key their anticipation as a pose |
| `spring` | 1 − e^(−6t) · cos(4πt), t from 0 to 1 | Damped follow-through of ears and tail: two swings, the first 22% past |
| `lin` | linear | Shudders and quivers 2 frames apart |
| `hold` | stays at the key before | Stillness |

### Channels

<!-- gen:channels -->
| Channel | Unit and direction |
| --- | --- |
| `root.x` | pt, + right |
| `root.y` | pt, + down |
| `root.rot` | deg about the ground point |
| `root.sx` | scale about the ground point |
| `root.sy` | scale about the ground point |
| `torso.rot` | deg, + leans forward |
| `head.rot` | deg, + neck forward and down |
| `skull.rot` | deg, + nose down |
| `earL.rot` | deg, + forward (near ear) |
| `earR.rot` | deg, + forward (far ear) |
| `eye.sy` | scale, 1 open, 0.12 shut |
| `eye.x` | pt |
| `eye.y` | pt |
| `tail.rot` | deg, + up over the back |
| `pawL.rot` | deg, - lifts forward (near leg) |
| `pawR.rot` | deg (far leg) |
| `dim` | 0 to 1, 1 - opacity of the figure |
| `flock.on` | opacity of the three dots |
| `f1.y` | pt |
| `f2.y` | pt |
| `f3.y` | pt |
<!-- /gen:channels -->

## 3. The 12 states

<!-- gen:states -->
### 1. Idle

Sitting, breathing, at ease. Ears a little back.

60 frames (2.5 s). Enter: 10 frames, `inout`. Id in `motion.json`: `idle`.

- 0-60: one breath. The body rises 1.8% by frame 30 and falls; the head follows 4 frames late.
- 0-60: the tail sways 5 degrees each way, once.
- 38-50: the near ear flicks forward in 2 frames and springs back.
- 50-55: one blink.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `root.sy` | 0: 1 · 30: 1.018 · 60: 1 |
| `head.rot` | 0: 0 · 34: -2 · 60: 0 |
| `earL.rot` | 0: -8 · 38: -8 `hold` · 40: 8 `out` · 50: -8 `spring` · 60: -8 `hold` |
| `earR.rot` | 0: -6 · 60: -6 |
| `tail.rot` | 0: 0 · 15: 5 · 45: -5 · 60: 0 |
| `eye.sy` | 0: 1 · 50: 1 `hold` · 52: 0.12 `in` · 55: 1 `out` · 60: 1 |

### 2. Listening

Snaps tall when you hold the screen. The body is still; only the ears work.

36 frames (1.5 s). Enter: 5 frames, `over`. Id in `motion.json`: `listening`.

- Enter, 5 frames, overshoot: the neck goes upright, both ears forward, the tail up and still.
- 0-36: the neck drifts 1.5 degrees and back. No blink.
- 8-16: the near ear turns 7 degrees further forward in 2 frames and springs back.
- 20-30: the far ear swings 9 degrees back in 2 frames and springs forward.
- Live: add the microphone level times 6 degrees to both ears.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `root.sy` | 0: 1.03 · 36: 1.03 |
| `torso.rot` | 0: -2 · 36: -2 |
| `head.rot` | 0: -7 · 18: -8.5 · 36: -7 |
| `skull.rot` | 0: -4 · 36: -4 |
| `earL.rot` | 0: 10 · 8: 10 `hold` · 10: 17 `out` · 16: 10 `spring` · 36: 10 `hold` |
| `earR.rot` | 0: 12 · 20: 12 `hold` · 22: 3 `out` · 30: 12 `spring` · 36: 12 `hold` |
| `tail.rot` | 0: 14 · 36: 14 |

### 3. Thinking

The head tilt of a dog working something out. Two tilts, with the ears split.

48 frames (2 s). Enter: 10 frames, `inout`. Id in `motion.json`: `thinking`.

- 0-20: nose up 16 degrees, near ear back, far ear forward, eye up. Held.
- 20-26: the head rolls to the second tilt; the ears swap, 2 frames late, and spring.
- 26-42: held. The tail tip flicks once at 32.
- 42-48: back to the first tilt.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `head.rot` | 0: -4 · 20: -4 `hold` · 26: 2 · 42: 2 `hold` · 48: -4 |
| `skull.rot` | 0: -16 · 20: -17 · 26: -5 · 42: -5 `hold` · 48: -16 |
| `torso.rot` | 0: 0 · 20: 0 `hold` · 26: 2 · 42: 2 `hold` · 48: 0 |
| `earL.rot` | 0: -22 · 22: -22 `hold` · 30: 12 `spring` · 44: 12 `hold` · 48: -22 `out` |
| `earR.rot` | 0: 14 · 22: 14 `hold` · 30: -18 `spring` · 44: -18 `hold` · 48: 14 `out` |
| `eye.y` | 0: -1.5 · 20: -1.5 `hold` · 26: 0.5 · 42: 0.5 `hold` · 48: -1.5 |
| `tail.rot` | 0: -10 · 32: -10 `hold` · 34: 0 `out` · 40: -10 `spring` · 48: -10 `hold` |

### 4. Working

Nose down, on a scent. The tail keeps time at two beats a second.

24 frames (1 s). Enter: 8 frames, `out`. Id in `motion.json`: `working`.

- Enter, 8 frames, snap: the body leans in and the nose goes down.
- 0-24: the neck sweeps 6 degrees along the ground and back.
- 0-12: two sniffs. The nose dips 6 degrees in 3 frames and eases back, twice.
- 0-24: the tail, held high, swings 16 degrees twice.
- 0-12: the ears, held back, bob 6 degrees with each sniff. One blink at 18.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `torso.rot` | 0: 4 · 24: 4 |
| `head.rot` | 0: 16 · 12: 22 · 24: 16 |
| `skull.rot` | 0: 24 · 3: 30 `out` · 6: 24 · 9: 30 `out` · 12: 24 · 24: 24 |
| `earL.rot` | 0: -12 · 4: -6 · 8: -14 · 12: -8 · 24: -12 |
| `earR.rot` | 0: -8 · 5: -2 · 9: -10 · 13: -4 · 24: -8 |
| `tail.rot` | 0: 20 · 6: 36 · 12: 20 · 18: 36 · 24: 20 |
| `eye.sy` | 0: 1 · 18: 1 `hold` · 20: 0.12 `in` · 22: 1 `out` · 24: 1 |

### 5. Subagents

A herding dog minding its flock. One dot on the ground for each subagent running, three at most.

48 frames (2 s). Enter: 8 frames, `over`. Id in `motion.json`: `subagents`.

- Enter, 8 frames: the dots fade in on the ground line; the head turns down to them.
- 0-8, 12-20, 24-32: each dot hops 14 pt in turn (4 frames up, 4 down).
- 8, 20, 32: the head nods 5 degrees as each dot lands, counting them.
- 36-48: the head lifts 4 degrees, then goes back to the first dot.
- 0-48: the tail sways 8 degrees each way, twice.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `flock.on` | 0: 1 · 48: 1 |
| `f1.y` | 0: 0 · 4: -14 `out` · 8: 0 `in` · 48: 0 `hold` |
| `f2.y` | 0: 0 · 12: 0 `hold` · 16: -14 `out` · 20: 0 `in` · 48: 0 `hold` |
| `f3.y` | 0: 0 · 24: 0 `hold` · 28: -14 `out` · 32: 0 `in` · 48: 0 `hold` |
| `torso.rot` | 0: 2 · 48: 2 |
| `head.rot` | 0: 12 · 36: 16 · 42: 8 · 48: 12 |
| `skull.rot` | 0: 12 · 6: 12 `hold` · 9: 17 `out` · 13: 12 · 18: 12 `hold` · 21: 17 `out` · 25: 12 · 30: 12 `hold` · 33: 17 `out` · 37: 12 · 48: 12 |
| `earL.rot` | 0: 12 · 48: 12 |
| `earR.rot` | 0: 14 · 48: 14 |
| `tail.rot` | 0: 10 · 6: 18 · 18: 2 · 30: 18 · 42: 2 · 48: 10 |

### 6. Speaking

No mouth. The chin lifts on each syllable and the ears follow 2 frames behind.

24 frames (1 s). Enter: 6 frames, `out`. Id in `motion.json`: `speaking`.

- 0, 6, 12, 19: four syllables in an uneven rhythm. The chin snaps up 4 to 8 degrees in 3 frames and eases down.
- The ears swing the other way at about half the size, 2 frames late.
- The body rises 1.2% on the two strong syllables (3 and 15).
- Live: scale the chin lift by the speech level instead of playing the loop.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `head.rot` | 0: -3 · 24: -3 |
| `skull.rot` | 0: -2 · 3: -9 `out` · 6: -3 · 9: -7 `out` · 12: -2 · 15: -10 `out` · 19: -3 · 21: -6 `out` · 24: -2 |
| `earL.rot` | 0: -4 · 5: 4 · 8: -3 · 11: 2 · 14: -4 · 17: 5 · 21: -2 · 24: -4 |
| `earR.rot` | 0: -2 · 6: 5 · 9: -2 · 12: 3 · 15: -3 · 18: 6 · 22: -1 · 24: -2 |
| `root.sy` | 0: 1 · 3: 1.012 `out` · 8: 1 · 12: 1 `hold` · 15: 1.012 `out` · 20: 1 · 24: 1 |
| `tail.rot` | 0: 6 · 12: 12 · 24: 6 |

### 7. Needs You

Asks without insisting: a paw lifted, pawing the air twice, the head tilted at you.

48 frames (2 s). Enter: 8 frames, `over`. Id in `motion.json`: `needs`.

- 0-2: the paw presses 3 degrees into the ground (anticipation).
- 2-8: the paw lifts 34 degrees with overshoot; the head tilts to nose up 12.
- 8-26: two pawing strokes, 14 degrees each, snapping up and easing down.
- 26-34: held.
- 34-40: the paw comes down; 40-48: at rest.
- 0-48: the tail sways 9 degrees each way, twice.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `pawL.rot` | 0: 0 · 2: 3 · 8: -34 `over` · 13: -20 · 17: -34 `out` · 22: -20 · 26: -34 `out` · 34: -34 `hold` · 40: 0 `in` · 48: 0 `hold` |
| `head.rot` | 0: -3 · 48: -3 |
| `skull.rot` | 0: -6 · 8: -12 `over` · 34: -12 `hold` · 42: -6 · 48: -6 |
| `earL.rot` | 0: 8 · 8: 14 `over` · 34: 14 `hold` · 42: 8 · 48: 8 |
| `earR.rot` | 0: -10 · 48: -10 |
| `tail.rot` | 0: -4 · 12: 14 · 24: -4 · 36: 14 · 48: -4 |

### 8. Approval

A dog told to stay. Rigid, pointing at the card, straining to go.

48 frames (2 s). Enter: 4 frames, `out`. Id in `motion.json`: `approval`.

- Enter, 4 frames, snap, no overshoot: it freezes leaning forward, neck out, nose level, ears locked, tail low and straight.
- 0-36: the body creeps 2.5 degrees further forward. No blink.
- 30-40: the tail quivers, 2 to 3 degrees, every 2 frames.
- 36-40: it catches itself and snaps back 3 degrees.
- 40-48: settles into the hold.
- Allowed: play Done. Denied: play the entry to Idle.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `torso.rot` | 0: 4 · 36: 6.5 `in` · 40: 3.5 `out` · 48: 4 |
| `head.rot` | 0: 16 · 36: 19 `in` · 40: 15 `out` · 48: 16 |
| `skull.rot` | 0: -12 · 48: -12 |
| `earL.rot` | 0: 16 · 36: 16 `hold` · 40: 20 `out` · 48: 16 |
| `earR.rot` | 0: 18 · 36: 18 `hold` · 40: 22 `out` · 48: 18 |
| `tail.rot` | 0: -24 · 30: -24 `hold` · 32: -21 `lin` · 34: -26 `lin` · 36: -22 `lin` · 38: -25 `lin` · 40: -24 `lin` · 48: -24 `hold` |

### 9. Blocked

Something failed. The head hangs, the ears lie flat, the tail drops.

48 frames (2 s). Enter: 12 frames, `inout`. Id in `motion.json`: `blocked`.

- Enter, 12 frames: the whole figure sinks 5%.
- 0-10: a shudder, 3 pt side to side, dying out.
- 10-30: a slow breath in, the head lifting 3 degrees.
- 30-48: the sigh out, the body sinking to 94%.
- 36-44: the near ear lifts 12 degrees and springs flat again.
- The eye stays half shut.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `root.x` | 0: 0 · 2: -3 `lin` · 4: 3 `lin` · 6: -2 `lin` · 8: 1 `lin` · 10: 0 `lin` · 48: 0 `hold` |
| `root.sy` | 0: 0.95 · 10: 0.95 `hold` · 30: 0.975 · 40: 0.94 · 48: 0.95 |
| `torso.rot` | 0: 3 · 48: 3 |
| `head.rot` | 0: 26 · 10: 26 `hold` · 30: 23 · 40: 28 · 48: 26 |
| `skull.rot` | 0: 20 · 48: 20 |
| `earL.rot` | 0: -72 · 36: -72 `hold` · 38: -60 `out` · 44: -72 `spring` · 48: -72 `hold` |
| `earR.rot` | 0: -66 · 48: -66 |
| `tail.rot` | 0: -26 · 48: -26 |
| `eye.sy` | 0: 0.45 · 48: 0.45 |

### 10. Done

One hop on the spot, then sitting proud. The tail wags four times a second throughout.

36 frames (1.5 s). Enter: 4 frames, `out`. Id in `motion.json`: `done`.

- 0-4: squash to 90% (anticipation).
- 4-10: launch. The body stretches to 105% and leaves the ground by 24 pt; the ears drag back.
- 10-16: the fall.
- 16-17: the landing, squashed to 92%; the ears whip forward.
- 17-27: recovery with overshoot to 103%, then rest.
- 27-36: sitting tall, the tail still going.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `root.y` | 0: 0 · 4: 0 `hold` · 10: -24 `out` · 16: 0 `in` · 36: 0 `hold` |
| `root.sy` | 0: 1 · 4: 0.9 · 8: 1.05 `out` · 13: 1 · 17: 0.92 `in` · 22: 1.03 `over` · 27: 1 · 36: 1 |
| `root.sx` | 0: 1 · 4: 1.06 · 8: 0.96 `out` · 13: 1 · 17: 1.05 `in` · 22: 0.985 `over` · 27: 1 · 36: 1 |
| `head.rot` | 0: -6 · 4: 2 · 10: -12 `out` · 17: 4 `in` · 23: -6 `over` · 36: -6 |
| `skull.rot` | 0: -6 · 36: -6 |
| `earL.rot` | 0: 6 · 5: 14 · 10: -26 `out` · 17: 16 `in` · 26: 6 `spring` · 36: 6 `hold` |
| `earR.rot` | 0: 8 · 6: 16 · 11: -24 `out` · 18: 18 `in` · 27: 8 `spring` · 36: 8 `hold` |
| `tail.rot` | 0: 10 · 3: 38 · 6: 10 · 9: 38 · 12: 10 · 15: 38 · 18: 10 · 21: 38 · 24: 10 · 27: 38 · 30: 10 · 33: 38 · 36: 10 |

### 11. Dizzy

The nose draws a circle. Everything else lags behind it.

36 frames (1.5 s). Enter: 6 frames, `inout`. Id in `motion.json`: `dizzy`.

- 0-36: the body sways 5 degrees each way about the ground point, once.
- 0-36: the neck and the skull turn a quarter of a cycle apart, so the nose goes round once.
- The ears swing 20 degrees the other way, the far ear 4 frames after the near one.
- The eye goes round twice, 2 pt from its centre.
- The front legs brace apart; the tail hangs.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `root.rot` | 0: 0 · 9: 5 · 18: 0 · 27: -5 · 36: 0 |
| `head.rot` | 0: 8 · 9: 0 · 18: -8 · 27: 0 · 36: 8 |
| `skull.rot` | 0: 0 · 9: -11 · 18: 0 · 27: 11 · 36: 0 |
| `earL.rot` | 0: -20 · 9: -4 · 18: 20 · 27: 4 · 36: -20 |
| `earR.rot` | 0: -6 · 4: -20 · 13: -4 · 22: 20 · 31: 4 · 36: -6 |
| `eye.x` | 0: 2 · 4.5: 0 · 9: -2 · 13.5: 0 · 18: 2 · 22.5: 0 · 27: -2 · 31.5: 0 · 36: 2 |
| `eye.y` | 0: 0 · 4.5: 2 · 9: 0 · 13.5: -2 · 18: 0 · 22.5: 2 · 27: 0 · 31.5: -2 · 36: 0 |
| `tail.rot` | 0: -20 · 18: -12 · 36: -20 |
| `pawL.rot` | 0: -7 · 36: -7 |
| `pawR.rot` | 0: 6 · 36: 6 |

### 12. Sleep

Chin on the chest, eye shut, dimmed to half to spare the screen.

60 frames (2.5 s). Enter: 24 frames, `inout`. Id in `motion.json`: `sleep`.

- Enter, 24 frames: the head sinks, the ears fold, the figure dims to 50%.
- 0-60: one slow breath, 2.5% of the body's height.
- 44-52: the near ear twitches 11 degrees and springs flat (a dream).

| Channel | Keys, as frame: value ease |
| --- | --- |
| `dim` | 0: 0.5 · 60: 0.5 |
| `root.sy` | 0: 0.93 · 26: 0.955 · 60: 0.93 |
| `torso.rot` | 0: 3 · 60: 3 |
| `head.rot` | 0: 26 · 28: 24 · 60: 26 |
| `skull.rot` | 0: 36 · 60: 36 |
| `earL.rot` | 0: -75 · 44: -75 `hold` · 46: -64 `out` · 52: -75 `spring` · 60: -75 `hold` |
| `earR.rot` | 0: -70 · 60: -70 |
| `tail.rot` | 0: -24 · 60: -24 |
| `eye.sy` | 0: 0.12 · 60: 0.12 |
<!-- /gen:states -->

### Working, by kind of tool

The same loop with three numbers changed. [STATES.md](../../STATES.md) lists which tools are which kind.

| Kind | Change |
| --- | --- |
| read | The neck sweep takes 48 frames instead of 24; one sniff per sweep |
| search, fetch | The neck sweep is 10° wide instead of 6° |
| edit, delete, move | Four sniffs of 4° instead of two of 6° |
| execute | No sweep. The nose holds at its lowest and the tail swings three times a second |

A tool call (the reaction) is one extra sniff: `skull.rot` +6° in 3 frames with `out`, back in 3.

## 4. Touch and crown

All three are added on top of the state that is playing. The preview implements them with these numbers.

### Poke (a tap)

<!-- gen:poke -->
14 frames, once. The values are added to the state's own.

- 0-2: a flinch. The figure squashes 6% sideways, the nose jerks up 8 degrees, the ears pop 22 degrees forward, the eye widens.
- 2-8: the rebound overshoots to 103%.
- 8-14: the ears and tail spring to rest.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `root.sx` | 0: 0 · 2: -0.06 `out` · 7: 0.03 `over` · 14: 0 |
| `root.sy` | 0: 0 · 2: 0.04 `out` · 7: -0.02 `over` · 14: 0 |
| `skull.rot` | 0: 0 · 2: -8 `out` · 8: 2 `over` · 14: 0 |
| `earL.rot` | 0: 0 · 2: 22 `out` · 14: 0 `spring` |
| `earR.rot` | 0: 0 · 3: 22 `out` · 14: 0 `spring` |
| `eye.sy` | 0: 0 · 2: 0.3 `out` · 8: 0 · 14: 0 `hold` |
| `tail.rot` | 0: 0 · 3: 18 `out` · 14: 0 `spring` |
<!-- /gen:poke -->

Four pokes within 3 s: Fetch plays Dizzy for 36 frames and goes back to its state.

### Pet (a stroke)

While the finger moves on the figure (more than 8 pt from where it went down), a weight `w` rises toward 1 at
12 per second and falls back at 4 per second after the finger lifts, which takes about 18 frames.

| Channel | While petted |
| --- | --- |
| `earL.rot`, `earR.rot` | go to -52° and -46°: flat back under the hand |
| `eye.sy` | goes to 0.12: shut |
| `skull.rot` | + 6°, plus up to 5° more or less with the finger's x, so the head leans into the hand |
| `head.rot` | + 5° |
| `root.sy` | − 2% |
| `tail.rot` | + 16°, swinging 14° each way twice a second |

Each is multiplied by `w`. When the finger lifts, the tail finishes its swing as the weight falls.

### Digital Crown

The crown sets a value `c` from -1 to 1. Half a turn covers the range; play a haptic tick every eighth of it.

| Turn | Channel changes, times the size of c |
| --- | --- |
| Up (c > 0): it sits tall | `head.rot` − 10°, `skull.rot` − 9°, `root.sy` + 4%, both ears + 10° |
| Down (c < 0): it bows | `head.rot` + 28°, `skull.rot` + 6°, `torso.rot` + 4°, `tail.rot` + 18° |

- 0.5 s after the last movement, `c` falls back to 0, losing 63% every 1/7 s.
- Spun fast, it gets dizzy, as Noodle and Ray do. Add up the size of every change in `c`, let the sum lose 63%
  every 0.4 s, and when it passes 3.2 (about a turn and a half within a second) play Dizzy for 2.2 s, then go back.

## 5. Building it in Chums

"Adding a character" in [docs/characters.md](../../../docs/characters.md) has the steps: a `fetch` case in
`CharacterKind`, a `FetchStage.swift`, the ids in the hub and the widgets, and an `avatar-fetch` image.

- The stage needs no assets. It can draw the three kinds of shape with `Path` and sample `motion.json`'s keys,
  the way `preview.html` does in about 40 lines.
- Waiting and Compacting are not in the 12. Until the rig has them, they show as Blocked and Thinking, as for the
  other characters.
- Listening and Speaking are written to take a live level (the microphone, the speech) in place of the loop.
