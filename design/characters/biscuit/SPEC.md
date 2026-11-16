# Biscuit

A second character for Chums on Fetch's rig: a sitting orange cat in profile. It has the same groups, the same
channels and the same 12 timelines as the dog, so [fetch/SPEC.md](../fetch/SPEC.md) is its motion specification and
[fetch/motion.json](../fetch/motion.json) its keys. This file lists only what differs.

Biscuit is a design. It is not in the watch app yet.

| File | What it is |
| --- | --- |
| [biscuit.svg](biscuit.svg) | The resting pose, one group per rig layer |
| [../fetch/preview.html](../fetch/preview.html) | Plays every state for both characters. Choose Biscuit at the top |
| [../fetch/build.py](../fetch/build.py) | Writes `biscuit.svg` from the `BISCUIT` numbers at its top |

## Anatomy

The cat is cute by proportion only: a head a third of its height, a short neck, a short muzzle and broad ears. It
has no whiskers, no mouth and no stripes. The canvas, the ground line (y = 418) and the three kinds of shape are
Fetch's. At rest it is 320 pt tall.

| Part | Construction | Fill |
| --- | --- | --- |
| Torso | Hull of the haunch (150, 354, 64) and the chest (222, 300, 44) | Orange |
| Hind foot | Rounded rectangle 74 × 20 at (150, 398), radius 10 | Orange |
| Neck | Hull of (228, 284, 36) and (240, 236, 32) | Orange |
| Collar | A black band 9 pt wide across the neck, 42% of the way up it | Black |
| Head | Hull of the cranium (250, 196, 54) and the muzzle (292, 214, 22) | Orange |
| Near ear | Hull of the base (218, 160, 19) and the tip (206, 112, 4) | Orange |
| Far ear | Hull of the base (262, 150, 19) and the tip (270, 102, 4), behind the head | Rust |
| Eye | Circle (280, 190, 6.5) | Black |
| Nose | Circle (311, 206, 5) | Black |
| Near front leg | Rounded rectangle 26 wide from y = 304 to the ground, radius 13, and a paw 40 × 20, radius 10 | Orange |
| Far front leg | The same, 15 pt to the right, behind the torso | Rust |
| Tail | A band 20 pt wide at the base and 16 at the tip around the quadratic curve (104, 396) (22, 402) (46, 292), round ends | Orange |
| Flock (Subagents only) | Three circles of radius 7.5 on the ground at x = 318, 340 and 362 | Rust |

| Ratio | Biscuit | Fetch |
| --- | --- | --- |
| Cranium diameter to haunch diameter | 0.84 | 0.52 |
| Cranium diameter to figure height | 1 : 3 | 1 : 4.9 |
| Muzzle beyond the cranium to cranium diameter | 0.13 | 0.63 |
| Neck length between pivots to cranium diameter | 0.46 | 1.13 |
| Ear base width to cranium diameter | 0.35 | 0.44 |

## Palette

| Name | Hex | Used for | Contrast on black |
| --- | --- | --- | --- |
| Orange | `#F28A2E` | The body, the head, the near ear, the near leg, the tail | 8.5 : 1 |
| Rust | `#B45F1B` | The far ear, the far front leg, the flock | 4.6 : 1 |
| Black | `#000000` | The screen, the eye, the nose, the collar | |

## Pivots

| Group id | Parent | Pivot |
| --- | --- | --- |
| `biscuit` (root) | none | 170, 418 |
| `tail` | `biscuit` | 104, 396 |
| `paw-right` (far) | `biscuit` | 262, 316 |
| `torso` | `biscuit` | 150, 354 |
| `head` | `torso` | 228, 284 |
| `skull` | `head` | 240, 236 |
| `ear-right` (far) | `skull` | 262, 150 |
| `ear-left` (near) | `skull` | 218, 160 |
| `eye`, in `accent` | `skull` | 280, 190 |
| `paw-left` (near) | `biscuit` | 247, 316 |

## Motion

Biscuit plays Fetch's keys with these channels scaled down. Its neck is 40% shorter and its tail longer, so the
same angles would move the head and the tail tip too far. Every other channel is unchanged.

| Channel | Multiply by |
| --- | --- |
| `head.rot` | 0.5 |
| `skull.rot` | 0.6 |
| `torso.rot` | 0.7 |
| `tail.rot` | 0.6 |
| `pawL.rot` | 0.9 |

The touch and crown reactions are Fetch's, unscaled.

## SVG, resting pose

<!-- gen:svg -->
```svg
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 396 484" width="396" height="484">
<title>Biscuit, resting pose</title>
<rect id="background" width="396" height="484" fill="#000000"/>
<g id="biscuit" data-pivot="170 418">
  <g id="flock" opacity="0"><circle id="flock-1" cx="318" cy="404" r="7.5" fill="#B45F1B"/><circle id="flock-2" cx="340" cy="404" r="7.5" fill="#B45F1B"/><circle id="flock-3" cx="362" cy="404" r="7.5" fill="#B45F1B"/></g>
  <g id="tail" data-pivot="104 396"><path d="M103.3 386 Q32.5 391.2 53.8 293.7 A8 8 0 0 0 38.2 290.3 Q11.5 412.8 104.7 406 A10 10 0 0 0 103.3 386 Z" fill="#F28A2E"/></g>
  <g id="paw-right" data-pivot="262 316"><rect x="249" y="304" width="26" height="114" rx="13" fill="#B45F1B"/><rect x="249" y="398" width="40" height="20" rx="10" fill="#B45F1B"/></g>
  <g id="torso" data-pivot="150 354">
    <path id="torso-shape" d="M198.8 395.4 L255.6 328.5 A44 44 0 0 0 204.1 259.8 L123.9 295.5 A64 64 0 1 0 198.8 395.4 Z" fill="#F28A2E"/>
    <rect id="hind-foot" x="150" y="398" width="74" height="20" rx="10" fill="#F28A2E"/>
    <g id="head" data-pivot="228 284">
      <path id="neck" d="M263.5 289.9 L271.6 241.2 A32 32 0 0 0 209.7 225.8 L193.9 272.5 A36 36 0 1 0 263.5 289.9 Z" fill="#F28A2E"/>
      <rect id="collar" x="183" y="259.3" width="100" height="9" fill="#000000" transform="rotate(14 233 263.8)"/>
      <g id="skull" data-pivot="240 236">
        <g id="ear-right" data-pivot="262 150"><path d="M280.8 147.2 L274 101.4 A4 4 0 0 0 266.4 100.2 L245.1 141.3 A19 19 0 1 0 280.8 147.2 Z" fill="#B45F1B"/></g>
        <path id="head-shape" d="M269.6 246.3 L300 234.5 A22 22 0 0 0 312.3 205.6 L299.9 175.5 A54 54 0 1 0 269.6 246.3 Z" fill="#F28A2E"/>
        <g id="ear-left" data-pivot="218 160"><path d="M234.2 150 L209.4 109.9 A4 4 0 0 0 202 111.7 L199 158.8 A19 19 0 1 0 234.2 150 Z" fill="#F28A2E"/></g>
        <g id="accent">
          <circle cx="311" cy="206" r="5" fill="#000000" id="nose"/>
          <g id="eye" data-pivot="280 190"><circle cx="280" cy="190" r="6.5" fill="#000000"/></g>
        </g>
      </g>
    </g>
  </g>
  <g id="paw-left" data-pivot="247 316"><rect x="234" y="304" width="26" height="114" rx="13" fill="#F28A2E"/><rect x="234" y="398" width="40" height="20" rx="10" fill="#F28A2E"/></g>
</g>
</svg>
```
<!-- /gen:svg -->
