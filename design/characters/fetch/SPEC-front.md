# Fetch and Biscuit, facing the screen

A second view of the two characters: sitting, looking straight at you, with two eyes. The motion is written to be
playful: a head that flips from one tilt to the other, a paw that waves, kneading, and the wiggle before a pounce.
The side view in [SPEC.md](SPEC.md) and [../biscuit/SPEC.md](../biscuit/SPEC.md) is unchanged; the two views are
separate rigs with separate timelines, and an app would use one of them.

Neither view is in the watch app yet, and the files were not imported into Fable while writing this.

| File | What it is |
| --- | --- |
| [fetch-front.svg](fetch-front.svg), [../biscuit/biscuit-front.svg](../biscuit/biscuit-front.svg) | The resting poses, one group per rig layer |
| [motion-front.json](motion-front.json) | Every state's keys for this view. The tables below are generated from it |
| [preview.html](preview.html) | Plays both views of both characters. It opens on the front view |
| [build.py](build.py) | Writes all of the above from the `FRONT` numbers and `motion-front.json` |

## 1. Anatomy

The canvas (396 × 484 pt, black), the ground line (y = 418), the palettes and the three kinds of shape are the side
view's. What changes:

- The figure is symmetrical about a centre line, except the tail, which comes out on the right. The centre line is
  8 to 10 pt left of the canvas's centre to leave the tail room.
- The far colour (Stone, Rust) is what sits behind: the haunches and the tail. The chest, the front legs and the
  head are the near colour, so the body reads as a bright column on a darker base from 2 m.
- The 4 pt of black between the front legs is the only line inside the figure. It ends where the chest circle
  begins.
- There are two eyes. Both follow the same channels, and each one closes about its own centre.
- Fetch keeps its collar. Biscuit's neck is hidden behind its head, so it has none.

### Layers

| # in the brief | Group id | Parent | Pivot | Moves |
| --- | --- | --- | --- | --- |
| 1. Torso / base | the root (`fetch` or `biscuit`), holding `torso` | none | the ground under the body | moves, squashes, sways |
| 6. Tail | `tail` | root | where it leaves the haunch | -26° to +28° |
| 5. Front legs | `paw-left`, `paw-right` | root | the shoulder, inside the chest circle | lift 10 pt; swing out to 138° |
| 2. Head and neck | `head` | root | the chin | tilts 14° each way, moves 14 pt |
| 3. Ears | `ear-left`, `ear-right` | `head` | the centre of the ear's base | 62° outward to 14° inward |
| 4. Snout / eye accent | `accent`, holding `nose`, `eye-left`, `eye-right` | `head` | each eye's centre | the eyes scale and move 4 pt |
| (Subagents) | `flock` | root | none | each dot moves up 14 pt |

The neck is drawn in `torso` and stays still; the head slides and tilts over it. Left and right are screen left
and screen right.

### Fetch

<!-- gen:anatomy-fetch -->
| Part | Construction | Fill |
| --- | --- | --- |
| Haunches | Hull of (148, 366, 52) and (232, 366, 52) | Stone |
| Tail | A band 22 pt wide at the base and 13 at the tip around the quadratic curve (248, 398) (334, 402) (322, 310), behind the haunches | Stone |
| Chest | Hull of (190, 286, 42) and (190, 330, 42) | Chalk |
| Neck | Hull of (190, 250, 36) and (190, 196, 28) | Chalk |
| Collar | A black band 9 pt wide across the neck at y = 228 | Black |
| Front legs | Two rounded rectangles 38 wide from y = 300 to the ground, radius 18, 4 pt apart about x = 190 | Chalk |
| Head | Hull of (190, 148, 46) and (190, 186, 30) | Chalk |
| Ears | Hull of the base and the tip, (158, 118, 18) and (142, 70, 5), and its mirror image about x = 190 | Chalk |
| Eyes | Two circles of radius 6, 21 pt either side of x = 190 at y = 148 | Black |
| Nose | Circle (190, 198, 9) | Black |

| Group id | Pivot |
| --- | --- |
| `fetch` | 190, 418 |
| `tail` | 248, 398 |
| `paw-left` | 169, 312 |
| `paw-right` | 211, 312 |
| `head` | 190, 214 |
| `ear-left` | 158, 118 |
| `ear-right` | 222, 118 |
| `eye-left` | 169, 148 |
| `eye-right` | 211, 148 |
<!-- /gen:anatomy-fetch -->

<!-- gen:svg-fetch -->
```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 396 484" width="396" height="484">
<title>Fetch, facing the screen, resting pose</title>
<rect id="background" width="396" height="484" fill="#000000"/>
<g id="fetch" data-pivot="190 418">
  <g id="flock" opacity="0"><circle id="flock-1" cx="34" cy="404" r="7.5" fill="#A89B89"/><circle id="flock-2" cx="56" cy="404" r="7.5" fill="#A89B89"/><circle id="flock-3" cx="78" cy="404" r="7.5" fill="#A89B89"/></g>
  <g id="tail" data-pivot="248 398"><path d="M247.5 409 Q342 413.4 328.4 309.2 A6.5 6.5 0 0 0 315.6 310.8 Q326 390.6 248.5 387 A11 11 0 0 0 247.5 409 Z" fill="#A89B89"/></g>
  <g id="torso">
    <path id="haunch" d="M148 418 L232 418 A52 52 0 0 0 232 314 L148 314 A52 52 0 1 0 148 418 Z" fill="#A89B89"/>
    <path id="chest" d="M148 286 L148 330 A42 42 0 0 0 232 330 L232 286 A42 42 0 1 0 148 286 Z" fill="#F2EEE6"/>
    <path id="neck" d="M225.6 244.7 L217.7 191.9 A28 28 0 0 0 162.3 191.9 L154.4 244.7 A36 36 0 1 0 225.6 244.7 Z" fill="#F2EEE6"/>
    <rect id="collar" x="140" y="223.5" width="100" height="9" fill="#000000"/>
  </g>
  <g id="paw-left" data-pivot="169 312"><rect x="150" y="300" width="38" height="118" rx="18" fill="#F2EEE6"/></g>
  <g id="paw-right" data-pivot="211 312"><rect x="192" y="300" width="38" height="118" rx="18" fill="#F2EEE6"/></g>
  <g id="head" data-pivot="190 214">
    <g id="ear-left" data-pivot="158 118"><path d="M173 108.1 L146.2 67.3 A5 5 0 0 0 137 70.3 L140 119.1 A18 18 0 1 0 173 108.1 Z" fill="#F2EEE6"/></g>
    <g id="ear-right" data-pivot="222 118"><path d="M240 119.1 L243 70.3 A5 5 0 0 0 233.8 67.3 L207 108.1 A18 18 0 1 0 240 119.1 Z" fill="#F2EEE6"/></g>
    <path id="head-shape" d="M148.3 167.4 L162.8 198.6 A30 30 0 0 0 217.2 198.6 L231.7 167.4 A46 46 0 1 0 148.3 167.4 Z" fill="#F2EEE6"/>
    <g id="accent">
      <circle id="nose" cx="190" cy="198" r="9" fill="#000000"/>
      <g id="eye-left" data-pivot="169 148"><circle cx="169" cy="148" r="6" fill="#000000"/></g>
      <g id="eye-right" data-pivot="211 148"><circle cx="211" cy="148" r="6" fill="#000000"/></g>
    </g>
  </g>
</g>
</svg>
```
<!-- /gen:svg-fetch -->

### Biscuit

<!-- gen:anatomy-biscuit -->
| Part | Construction | Fill |
| --- | --- | --- |
| Haunches | Hull of (140, 362, 56) and (236, 362, 56) | Rust |
| Tail | A band 20 pt wide at the base and 16 at the tip around the quadratic curve (254, 398) (346, 404) (326, 296), behind the haunches | Rust |
| Chest | Hull of (188, 290, 48) and (188, 334, 46) | Orange |
| Neck | Hull of (188, 262, 40) and (188, 226, 36) | Orange |
| Front legs | Two rounded rectangles 42 wide from y = 304 to the ground, radius 20, 4 pt apart about x = 188 | Orange |
| Head | Hull of (160, 184, 52) and (216, 184, 52) | Orange |
| Ears | Hull of the base and the tip, (136, 148, 20) and (116, 92, 4), and its mirror image about x = 188 | Orange |
| Eyes | Two circles of radius 7, 28 pt either side of x = 188 at y = 184 | Black |
| Nose | Circle (188, 203, 5) | Black |

| Group id | Pivot |
| --- | --- |
| `biscuit` | 188, 418 |
| `tail` | 254, 398 |
| `paw-left` | 165, 316 |
| `paw-right` | 211, 316 |
| `head` | 188, 236 |
| `ear-left` | 136, 148 |
| `ear-right` | 240, 148 |
| `eye-left` | 160, 184 |
| `eye-right` | 216, 184 |
<!-- /gen:anatomy-biscuit -->

<!-- gen:svg-biscuit -->
```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 396 484" width="396" height="484">
<title>Biscuit, facing the screen, resting pose</title>
<rect id="background" width="396" height="484" fill="#000000"/>
<g id="biscuit" data-pivot="188 418">
  <g id="flock" opacity="0"><circle id="flock-1" cx="22" cy="404" r="7.5" fill="#B45F1B"/><circle id="flock-2" cx="44" cy="404" r="7.5" fill="#B45F1B"/><circle id="flock-3" cx="66" cy="404" r="7.5" fill="#B45F1B"/></g>
  <g id="tail" data-pivot="254 398"><path d="M253.3 408 Q356.1 414.7 333.9 294.5 A8 8 0 0 0 318.1 297.5 Q335.9 393.3 254.7 388 A10 10 0 0 0 253.3 408 Z" fill="#B45F1B"/></g>
  <g id="torso">
    <path id="haunch" d="M140 418 L236 418 A56 56 0 0 0 236 306 L140 306 A56 56 0 1 0 140 418 Z" fill="#B45F1B"/>
    <path id="chest" d="M140 292.2 L142 336.1 A46 46 0 0 0 234 336.1 L236 292.2 A48 48 0 1 0 140 292.2 Z" fill="#F28A2E"/>
    <path id="neck" d="M227.8 257.6 L223.8 222 A36 36 0 0 0 152.2 222 L148.2 257.6 A40 40 0 1 0 227.8 257.6 Z" fill="#F28A2E"/>
  </g>
  <g id="paw-left" data-pivot="165 316"><rect x="144" y="304" width="42" height="114" rx="20" fill="#F28A2E"/></g>
  <g id="paw-right" data-pivot="211 316"><rect x="190" y="304" width="42" height="114" rx="20" fill="#F28A2E"/></g>
  <g id="head" data-pivot="188 236">
    <g id="ear-left" data-pivot="136 148"><path d="M152.3 136.5 L119.3 89.7 A4 4 0 0 0 112 92.3 L116 149.4 A20 20 0 1 0 152.3 136.5 Z" fill="#F28A2E"/></g>
    <g id="ear-right" data-pivot="240 148"><path d="M260 149.4 L264 92.3 A4 4 0 0 0 256.7 89.7 L223.7 136.5 A20 20 0 1 0 260 149.4 Z" fill="#F28A2E"/></g>
    <path id="head-shape" d="M160 236 L216 236 A52 52 0 0 0 216 132 L160 132 A52 52 0 1 0 160 236 Z" fill="#F28A2E"/>
    <g id="accent">
      <circle id="nose" cx="188" cy="203" r="5" fill="#000000"/>
      <g id="eye-left" data-pivot="160 184"><circle cx="160" cy="184" r="7" fill="#000000"/></g>
      <g id="eye-right" data-pivot="216 184"><circle cx="216" cy="184" r="7" fill="#000000"/></g>
    </g>
  </g>
</g>
</svg>
```
<!-- /gen:svg-biscuit -->

## 2. Motion

The rules and the eases are the side view's ([SPEC.md](SPEC.md#2-motion)): 24 frames a second, frame 0 is the key
pose, a state is entered by a timed blend, and a key's ease shapes the move that arrives at it. Both characters play
the same keys with nothing scaled.

What makes it playful, and should hold for any new clip:

- The head is never level for long. It rocks in Idle and flips between two tilts in Listening and Thinking.
- Moves start fast (`out`) and land with overshoot (`over`); ears and tail arrive late and `spring`.
- Each state has one thing only it does: the tilt flip, the tail tap, kneading, the bat, the wave, the wiggle,
  flat ears, the jump.

### Channels

<!-- gen:channels -->
| Channel | Unit and direction |
| --- | --- |
| `root.x` | pt, + right |
| `root.y` | pt, + down |
| `root.rot` | deg about the ground point, + leans to screen right |
| `root.sx` | scale about the ground point |
| `root.sy` | scale about the ground point |
| `head.rot` | deg about the chin, + tilts to screen right |
| `head.x` | pt |
| `head.y` | pt, + down |
| `earL.rot` | deg, screen-left ear, - splays outward |
| `earR.rot` | deg, screen-right ear, + splays outward |
| `eye.sy` | scale of both eyes, 1 open, 0.12 shut |
| `eye.x` | pt, both eyes |
| `eye.y` | pt, both eyes |
| `tail.rot` | deg, - curls in and up, + drops outward |
| `pawL.rot` | deg about the shoulder, screen-left leg, + lifts outward |
| `pawL.y` | pt, - lifts |
| `pawR.rot` | deg about the shoulder, screen-right leg, - lifts outward |
| `pawR.y` | pt, - lifts |
| `dim` | 0 to 1, 1 - opacity of the figure |
| `flock.on` | opacity of the three dots |
| `f1.y` | pt |
| `f2.y` | pt |
| `f3.y` | pt |
<!-- /gen:channels -->

## 3. The 12 states

<!-- gen:states -->
### 1. Idle

Sitting, looking at you. The head rocks from side to side and the tail swishes when it feels like it.

60 frames (2.5 s). Enter: 10 frames, `inout`. Id in `motion.json`: `idle`.

- 0-60: one breath, 1.8% of its height.
- 0-60: the head rocks 4 degrees each way, once.
- 6-16: the tail flicks in 14 degrees in 4 frames, then swings out past rest.
- 30-44: a second, smaller swish.
- 36-46: the right ear flicks out 16 degrees in 2 frames and springs back.
- 50-55: one blink.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `root.sy` | 0: 1 · 30: 1.018 · 60: 1 |
| `head.rot` | 0: -4 · 30: 4 · 60: -4 |
| `tail.rot` | 0: 0 · 6: 0 `hold` · 10: -14 `out` · 20: 6 · 30: 0 · 34: -8 `out` · 44: 3 · 60: 0 |
| `earR.rot` | 0: 0 · 36: 0 `hold` · 38: 16 `out` · 46: 0 `spring` · 60: 0 `hold` |
| `eye.sy` | 0: 1 · 50: 1 `hold` · 52: 0.12 `in` · 55: 1 `out` · 60: 1 |

### 2. Listening

Pops up tall with the ears in, and flips its head from one tilt to the other while you talk.

36 frames (1.5 s). Enter: 5 frames, `over`. Id in `motion.json`: `listening`.

- Enter, 5 frames, overshoot: the head rises 10 pt, the ears turn in, the tail goes up and holds.
- 0-14: head tilted 11 degrees to the right. Held.
- 14-18: the head flips to 11 degrees left in 4 frames, with overshoot. The ears follow 2 frames late and spring.
- 18-32: held.
- 32-36: it flips back.
- Live: add the microphone level times 6 degrees to both ears, turning in.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `root.sy` | 0: 1.03 · 36: 1.03 |
| `head.y` | 0: -10 · 36: -10 |
| `head.rot` | 0: 11 · 14: 11 `hold` · 18: -11 `over` · 32: -11 `hold` · 36: 11 `over` |
| `earL.rot` | 0: 8 · 16: 8 `hold` · 24: 14 `spring` · 34: 14 `hold` · 36: 8 |
| `earR.rot` | 0: -14 · 16: -14 `hold` · 24: -8 `spring` · 34: -8 `hold` · 36: -14 |
| `tail.rot` | 0: -12 · 36: -12 |

### 3. Thinking

Looks up at one corner, then the other, tapping its tail to count.

48 frames (2 s). Enter: 10 frames, `inout`. Id in `motion.json`: `thinking`.

- 0-20: the eyes look up and left, the head tilts right, the left ear droops. The tail taps 3 times, 10 degrees, every 6 frames.
- 20-26: the eyes cross to the other corner; the head and the ears swap sides.
- 26-42: held, with 2 more taps.
- 42-48: back to the first side.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `head.rot` | 0: 9 · 20: 9 `hold` · 26: -9 · 42: -9 `hold` · 48: 9 |
| `head.y` | 0: -3 · 48: -3 |
| `eye.x` | 0: -3 · 20: -3 `hold` · 26: 3 · 42: 3 `hold` · 48: -3 |
| `eye.y` | 0: -3 · 48: -3 |
| `earL.rot` | 0: -34 · 22: -34 `hold` · 30: 0 `spring` · 44: 0 `hold` · 48: -34 `out` |
| `earR.rot` | 0: 0 · 22: 0 `hold` · 30: 34 `spring` · 44: 34 `hold` · 48: 0 `out` |
| `tail.rot` | 0: 0 · 3: 10 `out` · 6: 0 · 9: 10 `out` · 12: 0 · 15: 10 `out` · 18: 0 · 28: 0 `hold` · 31: 10 `out` · 34: 0 · 37: 10 `out` · 40: 0 · 48: 0 `hold` |

### 4. Working

Kneading. The front paws press in turn, twice a second each, and the body rocks with them.

24 frames (1 s). Enter: 8 frames, `out`. Id in `motion.json`: `working`.

- Enter, 8 frames: the head drops 8 pt and the eyes look down at the paws.
- 0-6: the left paw lifts 10 pt and presses down. 6-12: the right paw.
- 12-24: the same again.
- The body leans 2 degrees toward the paw that presses.
- 0-24: the tail swings 12 degrees each way, twice.
- 18-22: one blink.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `head.y` | 0: 8 · 24: 8 |
| `eye.y` | 0: 3 · 24: 3 |
| `pawL.y` | 0: 0 · 3: -10 `out` · 6: 0 `in` · 12: 0 `hold` · 15: -10 `out` · 18: 0 `in` · 24: 0 `hold` |
| `pawR.y` | 0: 0 · 6: 0 `hold` · 9: -10 `out` · 12: 0 `in` · 18: 0 `hold` · 21: -10 `out` · 24: 0 `in` |
| `root.rot` | 0: 2 · 6: -2 · 12: 2 · 18: -2 · 24: 2 |
| `head.rot` | 0: -3 · 6: 3 · 12: -3 · 18: 3 · 24: -3 |
| `earL.rot` | 0: -6 · 24: -6 |
| `earR.rot` | 0: 6 · 24: 6 |
| `tail.rot` | 0: -12 · 6: 12 · 12: -12 · 18: 12 · 24: -12 |
| `eye.sy` | 0: 1 · 18: 1 `hold` · 20: 0.12 `in` · 22: 1 `out` · 24: 1 |

### 5. Subagents

Three dots hop past on its left, one for each subagent running. It watches them and bats at the nearest.

48 frames (2 s). Enter: 8 frames, `over`. Id in `motion.json`: `subagents`.

- Enter, 8 frames: the dots fade in; the head tilts toward them and the eyes go left.
- 0-8, 12-20, 24-32: each dot hops 14 pt in turn.
- 8-18: the left paw swipes out 34 degrees with overshoot and comes back.
- 32-42: a second, smaller swipe.
- 0-48: the tail swings 14 degrees each way, twice.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `flock.on` | 0: 1 · 48: 1 |
| `f1.y` | 0: 0 · 4: -14 `out` · 8: 0 `in` · 48: 0 `hold` |
| `f2.y` | 0: 0 · 12: 0 `hold` · 16: -14 `out` · 20: 0 `in` · 48: 0 `hold` |
| `f3.y` | 0: 0 · 24: 0 `hold` · 28: -14 `out` · 32: 0 `in` · 48: 0 `hold` |
| `head.rot` | 0: -10 · 24: -14 · 48: -10 |
| `head.y` | 0: 4 · 48: 4 |
| `eye.x` | 0: -4 · 48: -4 |
| `eye.y` | 0: 3 · 48: 3 |
| `pawL.rot` | 0: 0 · 8: 0 `hold` · 12: 34 `over` · 18: 0 `in` · 32: 0 `hold` · 36: 22 `over` · 42: 0 `in` · 48: 0 `hold` |
| `earL.rot` | 0: 6 · 48: 6 |
| `earR.rot` | 0: -4 · 48: -4 |
| `tail.rot` | 0: 0 · 6: -14 · 18: 14 · 30: -14 · 42: 14 · 48: 0 |

### 6. Speaking

No mouth. The head bobs on each syllable and tips a little the other way each time.

24 frames (1 s). Enter: 6 frames, `out`. Id in `motion.json`: `speaking`.

- 0, 6, 12, 19: four syllables in an uneven rhythm. The head drops 3 to 6 pt in 3 frames and eases back up.
- The tilt changes side on each syllable, 4 degrees.
- The ears bounce outward 2 frames after each bob.
- The body rises 1.2% on the two strong syllables (3 and 15).
- Live: scale the bob by the speech level instead of playing the loop.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `head.y` | 0: 0 · 3: 5 `out` · 6: 0 · 9: 3 `out` · 12: 0 · 15: 6 `out` · 19: 0 · 21: 3 `out` · 24: 0 |
| `head.rot` | 0: -4 · 3: 4 `out` · 9: -4 `out` · 15: 4 `out` · 21: -4 `out` · 24: -4 |
| `earL.rot` | 0: 0 · 5: -8 · 8: 0 · 11: -5 · 14: 0 · 17: -9 · 21: 0 · 24: 0 |
| `earR.rot` | 0: 0 · 5: 8 · 8: 0 · 11: 5 · 14: 0 · 17: 9 · 21: 0 · 24: 0 |
| `root.sy` | 0: 1 · 3: 1.012 `out` · 8: 1 · 12: 1 `hold` · 15: 1.012 `out` · 20: 1 · 24: 1 |
| `tail.rot` | 0: -4 · 12: 6 · 24: -4 |

### 7. Needs You

Waves at you. One paw up, three waves, and a small bounce to make sure you saw.

48 frames (2 s). Enter: 8 frames, `over`. Id in `motion.json`: `needs`.

- 0-2: the right paw presses in 4 degrees (anticipation).
- 2-8: it swings out and up 138 degrees with overshoot, to beside the head; the head tilts toward it.
- 8-26: three waves, 20 degrees each.
- 26-32: held. 28-36: a bounce, 8 pt off the ground, landing with a 4% squash.
- 36-42: the paw comes down. 42-48: at rest.
- 0-48: the tail swings 10 degrees each way, twice.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `pawR.rot` | 0: 0 · 2: 4 · 8: -138 `over` · 11: -118 · 14: -138 `out` · 17: -118 · 20: -138 `out` · 23: -118 · 26: -138 `out` · 36: -138 `hold` · 42: 0 `in` · 48: 0 `hold` |
| `head.rot` | 0: 0 · 8: 10 `over` · 36: 10 `hold` · 44: 0 · 48: 0 |
| `root.y` | 0: 0 · 28: 0 `hold` · 31: -8 `out` · 34: 0 `in` · 48: 0 `hold` |
| `root.sy` | 0: 1 · 26: 1 `hold` · 28: 0.96 · 31: 1.03 `out` · 34: 0.96 `in` · 39: 1 `over` · 48: 1 |
| `earL.rot` | 0: 0 · 8: 8 `over` · 36: 8 `hold` · 44: 0 · 48: 0 |
| `earR.rot` | 0: 0 · 8: -8 `over` · 36: -8 `hold` · 44: 0 · 48: 0 |
| `tail.rot` | 0: 0 · 6: -10 · 18: 10 · 30: -10 · 42: 10 · 48: 0 |

### 8. Approval

The crouch before a pounce. Eyes wide and fixed on the card, then the wiggle.

48 frames (2 s). Enter: 4 frames, `out`. Id in `motion.json`: `approval`.

- Enter, 4 frames, snap, no overshoot: it drops 5% lower, the head 6 pt down, the ears turned in, the eyes 20% wider.
- 0-26: frozen. No blink.
- 26-40: the wiggle. The body shifts 4 pt left and right every 2 frames, and the tail tip twitches with it.
- 40-48: frozen again.
- Allowed: play Done (the pounce it was waiting for). Denied: play the entry to Idle.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `root.sy` | 0: 0.95 · 48: 0.95 |
| `head.y` | 0: 6 · 48: 6 |
| `eye.sy` | 0: 1.2 · 48: 1.2 |
| `earL.rot` | 0: 10 · 48: 10 |
| `earR.rot` | 0: -10 · 48: -10 |
| `root.x` | 0: 0 · 26: 0 `hold` · 28: -4 · 30: 4 · 32: -4 · 34: 4 · 36: -4 · 38: 4 · 40: 0 · 48: 0 `hold` |
| `root.rot` | 0: 0 · 26: 0 `hold` · 28: 2 · 30: -2 · 32: 2 · 34: -2 · 36: 2 · 38: -2 · 40: 0 · 48: 0 `hold` |
| `tail.rot` | 0: 14 · 26: 14 `hold` · 28: 6 `lin` · 30: 20 `lin` · 32: 6 `lin` · 34: 20 `lin` · 36: 6 `lin` · 38: 20 `lin` · 40: 14 `lin` · 48: 14 `hold` |

### 9. Blocked

Something failed. The ears go flat out to the sides, the head sinks and the tail drops.

48 frames (2 s). Enter: 12 frames, `inout`. Id in `motion.json`: `blocked`.

- Enter, 12 frames: the figure sinks 5% and the head 10 pt.
- 0-10: a shudder, 3 pt side to side, dying out.
- 10-30: a slow breath in.
- 30-48: the sigh out, to 94% of its height.
- 36-44: the left ear lifts 14 degrees and springs flat again.
- The eyes stay half shut and look down.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `root.x` | 0: 0 · 2: -3 `lin` · 4: 3 `lin` · 6: -2 `lin` · 8: 1 `lin` · 10: 0 `lin` · 48: 0 `hold` |
| `root.sy` | 0: 0.95 · 10: 0.95 `hold` · 30: 0.975 · 40: 0.94 · 48: 0.95 |
| `head.y` | 0: 10 · 10: 10 `hold` · 30: 8 · 40: 12 · 48: 10 |
| `head.rot` | 0: -5 · 48: -5 |
| `earL.rot` | 0: -62 · 36: -62 `hold` · 38: -48 `out` · 44: -62 `spring` · 48: -62 `hold` |
| `earR.rot` | 0: 62 · 48: 62 |
| `tail.rot` | 0: 26 · 48: 26 |
| `eye.sy` | 0: 0.45 · 48: 0.45 |
| `eye.y` | 0: 3 · 48: 3 |

### 10. Done

A jump with both paws in the air, then a happy head tilt. The tail goes four times a second.

36 frames (1.5 s). Enter: 4 frames, `out`. Id in `motion.json`: `done`.

- 0-4: squash to 90% (anticipation).
- 4-10: launch. It stretches to 105%, leaves the ground by 26 pt, and both paws fly up 128 degrees, to either side of the head.
- 10-16: the fall; the paws come back in.
- 16-17: the landing, squashed to 92%; the ears flap out.
- 17-27: recovery with overshoot, and the head tips 12 degrees to the right.
- 27-36: the tilt eases back.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `root.y` | 0: 0 · 4: 0 `hold` · 10: -26 `out` · 16: 0 `in` · 36: 0 `hold` |
| `root.sy` | 0: 1 · 4: 0.9 · 8: 1.05 `out` · 13: 1 · 17: 0.92 `in` · 22: 1.03 `over` · 27: 1 · 36: 1 |
| `root.sx` | 0: 1 · 4: 1.06 · 8: 0.96 `out` · 13: 1 · 17: 1.05 `in` · 22: 0.985 `over` · 27: 1 · 36: 1 |
| `pawL.rot` | 0: 0 · 4: -4 · 10: 128 `out` · 17: 0 `in` · 36: 0 `hold` |
| `pawR.rot` | 0: 0 · 4: 4 · 10: -128 `out` · 17: 0 `in` · 36: 0 `hold` |
| `head.y` | 0: 0 · 4: 5 · 10: -6 `out` · 17: 6 `in` · 23: 0 `over` · 36: 0 |
| `head.rot` | 0: 0 · 17: 0 `hold` · 23: 12 `over` · 30: 12 `hold` · 36: 0 |
| `earL.rot` | 0: 0 · 5: 6 · 10: -20 `out` · 17: -34 `in` · 26: 0 `spring` · 36: 0 `hold` |
| `earR.rot` | 0: 0 · 5: -6 · 10: 20 `out` · 17: 34 `in` · 26: 0 `spring` · 36: 0 `hold` |
| `tail.rot` | 0: -8 · 3: 14 · 6: -8 · 9: 14 · 12: -8 · 15: 14 · 18: -8 · 21: 14 · 24: -8 · 27: 14 · 30: -8 · 33: 14 · 36: -8 |

### 11. Dizzy

The head goes round in a circle and the eyes go round the other way, twice as fast.

36 frames (1.5 s). Enter: 6 frames, `inout`. Id in `motion.json`: `dizzy`.

- 0-36: the head travels once round an oval 16 pt wide and 10 pt high, wobbling 8 degrees each way.
- 0-36: the body sways 4 degrees each way about the ground point, a quarter of a cycle behind.
- The eyes go round twice, 2.5 pt from their centres.
- The ears swing 18 degrees, the right one 4 frames after the left.
- The front legs brace apart; the tail hangs.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `head.x` | 0: 8 · 9: 0 · 18: -8 · 27: 0 · 36: 8 |
| `head.y` | 0: 0 · 9: 5 · 18: 0 · 27: -5 · 36: 0 |
| `head.rot` | 0: 8 · 9: 0 · 18: -8 · 27: 0 · 36: 8 |
| `root.rot` | 0: 0 · 9: 4 · 18: 0 · 27: -4 · 36: 0 |
| `earL.rot` | 0: -18 · 9: 0 · 18: 18 · 27: 0 · 36: -18 |
| `earR.rot` | 0: 0 · 4: -18 · 13: 0 · 22: 18 · 31: 0 · 36: 0 |
| `eye.x` | 0: 2.5 · 4.5: 0 · 9: -2.5 · 13.5: 0 · 18: 2.5 · 22.5: 0 · 27: -2.5 · 31.5: 0 · 36: 2.5 |
| `eye.y` | 0: 0 · 4.5: -2.5 · 9: 0 · 13.5: 2.5 · 18: 0 · 22.5: -2.5 · 27: 0 · 31.5: 2.5 · 36: 0 |
| `tail.rot` | 0: 20 · 18: 28 · 36: 20 |
| `pawL.rot` | 0: 8 · 36: 8 |
| `pawR.rot` | 0: -8 · 36: -8 |

### 12. Sleep

The head sinks and tips to one side, the eyes shut, the tail curls in. Dimmed to half to spare the screen.

60 frames (2.5 s). Enter: 24 frames, `inout`. Id in `motion.json`: `sleep`.

- Enter, 24 frames: the head sinks 14 pt and tips 9 degrees, the ears relax outward, the figure dims to 50%.
- 0-60: one slow breath, 2.5% of the body's height; the head rides 2 pt on it.
- 44-52: the right ear twitches 12 degrees and springs back (a dream).
- 20-40: the tail tip lifts 6 degrees and settles.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `dim` | 0: 0.5 · 60: 0.5 |
| `root.sy` | 0: 0.93 · 26: 0.955 · 60: 0.93 |
| `head.y` | 0: 14 · 28: 12 · 60: 14 |
| `head.rot` | 0: 9 · 60: 9 |
| `earL.rot` | 0: -24 · 60: -24 |
| `earR.rot` | 0: 24 · 44: 24 `hold` · 46: 12 `out` · 52: 24 `spring` · 60: 24 `hold` |
| `tail.rot` | 0: -20 · 20: -20 `hold` · 30: -26 · 40: -20 · 60: -20 `hold` |
| `eye.sy` | 0: 0.12 · 60: 0.12 |
<!-- /gen:states -->

## 4. Touch and crown

### Poke (a tap)

<!-- gen:poke -->
14 frames, once. The values are added to the state's own.

- 0-2: a flinch. The figure squashes 6% in height, the head ducks 6 pt, the ears pop out 20 degrees, the eyes widen.
- 2-8: the rebound overshoots to 103%.
- 8-14: the ears and tail spring to rest.

| Channel | Keys, as frame: value ease |
| --- | --- |
| `root.sy` | 0: 0 · 2: -0.06 `out` · 7: 0.03 `over` · 14: 0 |
| `root.sx` | 0: 0 · 2: 0.04 `out` · 7: -0.02 `over` · 14: 0 |
| `head.y` | 0: 0 · 2: 6 `out` · 8: -2 `over` · 14: 0 |
| `earL.rot` | 0: 0 · 2: -20 `out` · 14: 0 `spring` |
| `earR.rot` | 0: 0 · 3: 20 `out` · 14: 0 `spring` |
| `eye.sy` | 0: 0 · 2: 0.3 `out` · 8: 0 · 14: 0 `hold` |
| `tail.rot` | 0: 0 · 3: -18 `out` · 14: 0 `spring` |
<!-- /gen:poke -->

Four pokes within 3 s: it plays Dizzy for 36 frames and goes back to its state.

### Pet (a stroke)

The weight `w` rises and falls as in the side view (12 per second up, 4 per second down). `x` is where the finger
is across the screen, from -0.5 at the left edge to 0.5 at the right.

| Channel | While petted, times w |
| --- | --- |
| `earL.rot`, `earR.rot` | go to -40° and +40°: out to the sides under the hand |
| `eye.sy` | goes to 0.12: shut |
| `head.rot` | + 36° × x: the head leans into the finger |
| `head.x` | + 12 pt × x |
| `head.y` | + 6 pt |
| `root.sy` | − 2% |
| `tail.rot` | − 10°, swinging 16° each way twice a second |

### Digital Crown

The crown sets `c` from -1 to 1, as in the side view, and returns to 0 the same way.

| Turn | Channel changes, times the size of c |
| --- | --- |
| Up (c > 0): it stretches tall | `head.y` − 14 pt, `root.sy` + 5%, the ears 8° inward |
| Down (c < 0): it crouches and wiggles | `head.y` + 12 pt, `root.sy` − 8%, `root.sx` + 5%, `eye.sy` + 0.2, and a wiggle six times a second: `root.x` 4 pt, `root.rot` 2°, `tail.rot` 10° each way |

Spun fast, it gets dizzy, by the side view's rule.
