#!/usr/bin/env python3
"""Builds the resting poses (side and front, for Fetch and Biscuit), the motion preview (preview.html) and the
generated parts of the specification files from the numbers below, motion.json and motion-front.json.

    python3 design/characters/fetch/build.py

Every body part is the hull of two circles (a tapered capsule), a circle, or a rounded rectangle.
Coordinates are points on the 396 x 484 canvas, y down. Both animals face right and share one rig.
"""
import json, math, os

HERE = os.path.dirname(os.path.abspath(__file__))
W, H, GROUND = 396, 484, 418
BLACK = "#000000"

# ---- construction: circles are (cx, cy, r) ---------------------------------------------------------------------
FETCH = dict(
    id="fetch", name="Fetch", near="#F2EEE6", far="#A89B89", near_name="Chalk", far_name="Stone",
    haunch=(144, 349, 69), chest=(240, 262, 40),
    neck_base=(242, 252, 30),            # the neck's pivot
    neck_top=(258, 172, 24),             # the skull's pivot
    cranium=(264, 152, 36), snout=(330, 168, 15),
    ear_near=((247, 130, 16), (236, 78, 5)),     # base (the pivot), tip
    ear_far=((272, 124, 16), (268, 72, 5)),
    eye=(287, 147, 5.5), nose=(340, 159, 7),
    leg=dict(x=254, w=28, top=266, paw=46, pawh=22), leg_far_dx=17,   # near front leg; the far one is to its right
    shoulder=(268, 280),                 # the front legs' pivot
    hind=dict(x=142, w=88, h=20),        # hind foot
    tail=dict(p0=(100, 388), p1=(40, 390), p2=(44, 310), w0=22, w1=13),   # centre line (quadratic), widths
    root=(169, GROUND),                  # the ground point: the root's pivot
    collar=dict(t=0.30, w=9),            # along the neck from its base, and its width
    flock=[(336, 404), (356, 404), (376, 404)],   # subagents: up to three dots on the ground, r 7.5
    gain={},
)
BISCUIT = dict(
    id="biscuit", name="Biscuit", near="#F28A2E", far="#B45F1B", near_name="Orange", far_name="Rust",
    haunch=(150, 354, 64), chest=(222, 300, 44),
    neck_base=(228, 284, 36), neck_top=(240, 236, 32),
    cranium=(250, 196, 54), snout=(292, 214, 22),
    ear_near=((218, 160, 19), (206, 112, 4)),
    ear_far=((262, 150, 19), (270, 102, 4)),
    eye=(280, 190, 6.5), nose=(311, 206, 5),
    leg=dict(x=234, w=26, top=304, paw=40, pawh=20), leg_far_dx=15,
    shoulder=(247, 316),
    hind=dict(x=150, w=74, h=20),
    tail=dict(p0=(104, 396), p1=(22, 402), p2=(46, 292), w0=20, w1=16),
    root=(170, GROUND),
    collar=dict(t=0.42, w=9),
    flock=[(318, 404), (340, 404), (362, 404)],
    gain={"head.rot": 0.5, "skull.rot": 0.6, "torso.rot": 0.7, "tail.rot": 0.6, "pawL.rot": 0.9},
)
CHARACTERS = [FETCH, BISCUIT]


def f(v):
    s = f"{v:.1f}"
    return s[:-2] if s.endswith(".0") else s


def hull(a, b):
    """Path of the convex hull of two circles."""
    (x1, y1, r1), (x2, y2, r2) = a, b
    d = math.hypot(x2 - x1, y2 - y1)
    ang = math.atan2(y2 - y1, x2 - x1)
    off = math.acos((r1 - r2) / d)
    pt = lambda c, r, t: (c[0] + r * math.cos(t), c[1] + r * math.sin(t))
    a_hi, a_lo = pt(a, r1, ang + off), pt(a, r1, ang - off)
    b_hi, b_lo = pt(b, r2, ang + off), pt(b, r2, ang - off)
    big_b = 1 if 2 * off > math.pi else 0
    return (f"M{f(a_hi[0])} {f(a_hi[1])} L{f(b_hi[0])} {f(b_hi[1])} "
            f"A{f(r2)} {f(r2)} 0 {big_b} 0 {f(b_lo[0])} {f(b_lo[1])} "
            f"L{f(a_lo[0])} {f(a_lo[1])} A{f(r1)} {f(r1)} 0 {1 - big_b} 0 {f(a_hi[0])} {f(a_hi[1])} Z")


def tail_path(t):
    """A tapered band around a quadratic centre line, with round ends."""
    (x0, y0), (x1, y1), (x2, y2) = t["p0"], t["p1"], t["p2"]
    h0, h1 = t["w0"] / 2, t["w1"] / 2

    def unit(ax, ay):
        n = math.hypot(ax, ay)
        return ax / n, ay / n

    t0, t2 = unit(x1 - x0, y1 - y0), unit(x2 - x1, y2 - y1)
    n0, n2 = (-t0[1], t0[0]), (-t2[1], t2[0])

    def side(s):
        a = (x0 + s * h0 * n0[0], y0 + s * h0 * n0[1])
        b = (x2 + s * h1 * n2[0], y2 + s * h1 * n2[1])
        # control point: where the two offset tangents cross
        den = t0[0] * t2[1] - t0[1] * t2[0]
        k = ((b[0] - a[0]) * t2[1] - (b[1] - a[1]) * t2[0]) / den
        return a, (a[0] + k * t0[0], a[1] + k * t0[1]), b

    a, c, b = side(1)
    a2, c2, b2 = side(-1)
    return (f"M{f(a[0])} {f(a[1])} Q{f(c[0])} {f(c[1])} {f(b[0])} {f(b[1])} "
            f"A{f(h1)} {f(h1)} 0 0 0 {f(b2[0])} {f(b2[1])} "
            f"Q{f(c2[0])} {f(c2[1])} {f(a2[0])} {f(a2[1])} "
            f"A{f(h0)} {f(h0)} 0 0 0 {f(a[0])} {f(a[1])} Z")


def leg(c, dx, fill):
    L = c["leg"]
    x, w, top, r = L["x"] + dx, L["w"], L["top"], L["w"] / 2
    ph, pr = L["pawh"], L["pawh"] / 2
    return (f'<rect x="{f(x)}" y="{f(top)}" width="{f(w)}" height="{f(GROUND - top)}" rx="{f(r)}" fill="{fill}"/>'
            f'<rect x="{f(x)}" y="{f(GROUND - ph)}" width="{f(L["paw"])}" height="{f(ph)}" rx="{f(pr)}" fill="{fill}"/>')


def collar(c):
    (x1, y1, r1), (x2, y2, r2) = c["neck_base"], c["neck_top"]
    t = c["collar"]["t"]
    cx, cy = x1 + (x2 - x1) * t, y1 + (y2 - y1) * t
    deg = math.degrees(math.atan2(y2 - y1, x2 - x1)) + 90
    return (f'<rect id="collar" x="{f(cx - 50)}" y="{f(cy - c["collar"]["w"] / 2)}" width="100" height="{f(c["collar"]["w"])}" '
            f'fill="{BLACK}" transform="rotate({f(deg)} {f(cx)} {f(cy)})"/>')


def pivots(c):
    sh = c["shoulder"]
    return {
        "root": c["root"], "torso": c["haunch"][:2], "head": c["neck_base"][:2], "skull": c["neck_top"][:2],
        "ear-left": c["ear_near"][0][:2], "ear-right": c["ear_far"][0][:2], "eye": c["eye"][:2],
        "paw-left": sh, "paw-right": (sh[0] + c["leg_far_dx"], sh[1]), "tail": c["tail"]["p0"],
    }


def svg(c, standalone=True):
    P, near, far = pivots(c), c["near"], c["far"]
    piv = lambda k: f'data-pivot="{f(P[k][0])} {f(P[k][1])}"'
    circ = lambda o, fill, extra="": f'<circle cx="{f(o[0])}" cy="{f(o[1])}" r="{f(o[2])}" fill="{fill}"{extra}/>'
    hx, hw, hh = c["hind"]["x"], c["hind"]["w"], c["hind"]["h"]
    flock = "".join(f'<circle id="flock-{i + 1}" cx="{x}" cy="{y}" r="7.5" fill="{far}"/>'
                    for i, (x, y) in enumerate(c["flock"]))
    body = f'''<g id="{c["id"]}" {piv("root")}>
  <g id="flock" opacity="0">{flock}</g>
  <g id="tail" {piv("tail")}><path d="{tail_path(c["tail"])}" fill="{near}"/></g>
  <g id="paw-right" {piv("paw-right")}>{leg(c, c["leg_far_dx"], far)}</g>
  <g id="torso" {piv("torso")}>
    <path id="torso-shape" d="{hull(c["haunch"], c["chest"])}" fill="{near}"/>
    <rect id="hind-foot" x="{hx}" y="{GROUND - hh}" width="{hw}" height="{hh}" rx="{hh / 2:g}" fill="{near}"/>
    <g id="head" {piv("head")}>
      <path id="neck" d="{hull(c["neck_base"], c["neck_top"])}" fill="{near}"/>
      {collar(c)}
      <g id="skull" {piv("skull")}>
        <g id="ear-right" {piv("ear-right")}><path d="{hull(*c["ear_far"])}" fill="{far}"/></g>
        <path id="head-shape" d="{hull(c["cranium"], c["snout"])}" fill="{near}"/>
        <g id="ear-left" {piv("ear-left")}><path d="{hull(*c["ear_near"])}" fill="{near}"/></g>
        <g id="accent">
          {circ(c["nose"], BLACK, ' id="nose"')}
          <g id="eye" {piv("eye")}>{circ(c["eye"], BLACK)}</g>
        </g>
      </g>
    </g>
  </g>
  <g id="paw-left" {piv("paw-left")}>{leg(c, 0, near)}</g>
</g>'''
    if not standalone:
        return body
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}">\n'
            f'<title>{c["name"]}, resting pose</title>\n<rect id="background" width="{W}" height="{H}" fill="{BLACK}"/>\n'
            f'{body}\n</svg>\n')


def fmt(v):
    return f"{v:g}"


def keys_table(tracks):
    rows = ["| Channel | Keys, as frame: value ease |", "| --- | --- |"]
    for ch, keys in tracks.items():
        cells = " · ".join(f"{fmt(k[0])}: {fmt(k[1])}" + (f" `{k[2]}`" if len(k) > 2 else "") for k in keys)
        rows.append(f"| `{ch}` | {cells} |")
    return "\n".join(rows)


def states_md(motion):
    out, fps = [], motion["fps"]
    for i, (sid, s) in enumerate(motion["states"].items(), 1):
        out.append(f"### {i}. {s['name']}\n\n{s['intent']}\n\n"
                   f"{s['frames']} frames ({s['frames'] / fps:g} s). Enter: {s['enter']['frames']} frames, "
                   f"`{s['enter']['ease']}`. Id in `motion.json`: `{sid}`.\n\n"
                   + "\n".join(f"- {b}" for b in s["beats"]) + "\n\n" + keys_table(s["tracks"]))
    return "\n\n".join(out)


def poke_md(motion):
    p = motion["reactions"]["poke"]
    return (f"{p['frames']} frames, once. The values are added to the state's own.\n\n"
            + "\n".join(f"- {b}" for b in p["beats"]) + "\n\n" + keys_table(p["tracks"]))


def channels_md(motion):
    return "\n".join(["| Channel | Unit and direction |", "| --- | --- |"]
                     + [f"| `{c}` | {d} |" for c, d in motion["channels"].items()])


def fill(text, tag, body):
    a, b = f"<!-- gen:{tag} -->", f"<!-- /gen:{tag} -->"
    i, j = text.index(a) + len(a), text.index(b)
    return text[:i] + "\n" + body + "\n" + text[j:]


# ---- the front view: the same animals facing the screen ---------------------------------------------------------
# "L" and "R" are screen left and screen right. The far colour is what sits behind: the haunches and the tail.
FRONT = {
    "fetch": dict(
        haunch=((148, 366, 52), (232, 366, 52)),          # far colour, behind the chest
        chest=((190, 286, 42), (190, 330, 42)),
        neck=((190, 250, 36), (190, 196, 28)), collar=228,
        legs=dict(w=38, gap=4, top=300, r=18), shoulder_y=312,
        head=((190, 148, 46), (190, 186, 30)), chin=(190, 214),
        ear=((158, 118, 18), (142, 70, 5)),               # screen-left ear: base (the pivot), tip; the right one mirrors it
        eye=(21, 148, 6), nose=(190, 198, 9),             # eye: distance from the centre line, y, r
        tail=dict(p0=(248, 398), p1=(334, 402), p2=(322, 310), w0=22, w1=13),
        root=(190, GROUND), flock=[(34, 404), (56, 404), (78, 404)],
    ),
    "biscuit": dict(
        haunch=((140, 362, 56), (236, 362, 56)),
        chest=((188, 290, 48), (188, 334, 46)),
        neck=((188, 262, 40), (188, 226, 36)), collar=None,
        legs=dict(w=42, gap=4, top=304, r=20), shoulder_y=316,
        head=((160, 184, 52), (216, 184, 52)), chin=(188, 236),
        ear=((136, 148, 20), (116, 92, 4)),
        eye=(28, 184, 7), nose=(188, 203, 5),
        tail=dict(p0=(254, 398), p1=(346, 404), p2=(326, 296), w0=20, w1=16),
        root=(188, GROUND), flock=[(22, 404), (44, 404), (66, 404)],
    ),
}


def svg_front(c, standalone=True):
    F, near, far = FRONT[c["id"]], c["near"], c["far"]
    cx = F["root"][0]
    mirror = lambda o: (2 * cx - o[0],) + tuple(o[1:])
    L = F["legs"]
    lx, rx = cx - L["gap"] / 2 - L["w"], cx + L["gap"] / 2
    sh_l, sh_r = (lx + L["w"] / 2, F["shoulder_y"]), (rx + L["w"] / 2, F["shoulder_y"])
    earl, earr = F["ear"], tuple(mirror(o) for o in F["ear"])
    ex, ey, er = F["eye"]
    pv = lambda p: f'data-pivot="{f(p[0])} {f(p[1])}"'
    legrect = lambda x: (f'<rect x="{f(x)}" y="{f(L["top"])}" width="{f(L["w"])}" height="{f(GROUND - L["top"])}" '
                         f'rx="{f(L["r"])}" fill="{near}"/>')
    flock = "".join(f'<circle id="flock-{i + 1}" cx="{x}" cy="{y}" r="7.5" fill="{far}"/>'
                    for i, (x, y) in enumerate(F["flock"]))
    collar = (f'\n    <rect id="collar" x="{f(cx - 50)}" y="{f(F["collar"] - 4.5)}" width="100" height="9" fill="{BLACK}"/>'
              if F["collar"] else "")
    body = f'''<g id="{c["id"]}" {pv(F["root"])}>
  <g id="flock" opacity="0">{flock}</g>
  <g id="tail" {pv(F["tail"]["p0"])}><path d="{tail_path(F["tail"])}" fill="{far}"/></g>
  <g id="torso">
    <path id="haunch" d="{hull(*F["haunch"])}" fill="{far}"/>
    <path id="chest" d="{hull(*F["chest"])}" fill="{near}"/>
    <path id="neck" d="{hull(*F["neck"])}" fill="{near}"/>{collar}
  </g>
  <g id="paw-left" {pv(sh_l)}>{legrect(lx)}</g>
  <g id="paw-right" {pv(sh_r)}>{legrect(rx)}</g>
  <g id="head" {pv(F["chin"])}>
    <g id="ear-left" {pv(earl[0])}><path d="{hull(*earl)}" fill="{near}"/></g>
    <g id="ear-right" {pv(earr[0])}><path d="{hull(*earr)}" fill="{near}"/></g>
    <path id="head-shape" d="{hull(*F["head"])}" fill="{near}"/>
    <g id="accent">
      <circle id="nose" cx="{f(F["nose"][0])}" cy="{f(F["nose"][1])}" r="{f(F["nose"][2])}" fill="{BLACK}"/>
      <g id="eye-left" {pv((cx - ex, ey))}><circle cx="{f(cx - ex)}" cy="{f(ey)}" r="{f(er)}" fill="{BLACK}"/></g>
      <g id="eye-right" {pv((cx + ex, ey))}><circle cx="{f(cx + ex)}" cy="{f(ey)}" r="{f(er)}" fill="{BLACK}"/></g>
    </g>
  </g>
</g>'''
    if not standalone:
        return body
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}">\n'
            f'<title>{c["name"]}, facing the screen, resting pose</title>\n'
            f'<rect id="background" width="{W}" height="{H}" fill="{BLACK}"/>\n{body}\n</svg>\n')


def front_anatomy_md(c):
    F = FRONT[c["id"]]
    cx = F["root"][0]
    L = F["legs"]
    two = lambda t: f"({f(t[0][0])}, {f(t[0][1])}, {f(t[0][2])}) and ({f(t[1][0])}, {f(t[1][1])}, {f(t[1][2])})"
    rows = [
        ("Haunches", f"Hull of {two(F['haunch'])}", c["far_name"]),
        ("Tail", f"A band {F['tail']['w0']} pt wide at the base and {F['tail']['w1']} at the tip around the quadratic curve "
                 f"{F['tail']['p0']} {F['tail']['p1']} {F['tail']['p2']}, behind the haunches", c["far_name"]),
        ("Chest", f"Hull of {two(F['chest'])}", c["near_name"]),
        ("Neck", f"Hull of {two(F['neck'])}", c["near_name"]),
        ("Front legs", f"Two rounded rectangles {L['w']} wide from y = {L['top']} to the ground, radius {L['r']}, "
                       f"{L['gap']} pt apart about x = {cx}", c["near_name"]),
        ("Head", f"Hull of {two(F['head'])}", c["near_name"]),
        ("Ears", f"Hull of the base and the tip, {two(F['ear'])}, and its mirror image about x = {cx}", c["near_name"]),
        ("Eyes", f"Two circles of radius {f(F['eye'][2])}, {F['eye'][0]} pt either side of x = {cx} at y = {F['eye'][1]}", "Black"),
        ("Nose", f"Circle ({F['nose'][0]}, {F['nose'][1]}, {f(F['nose'][2])})", "Black"),
    ]
    if F["collar"]:
        rows.insert(4, ("Collar", f"A black band 9 pt wide across the neck at y = {F['collar']}", "Black"))
    return "\n".join(["| Part | Construction | Fill |", "| --- | --- | --- |"] + [f"| {a} | {b} | {d} |" for a, b, d in rows])


def pivots_md(body):
    import re
    rows = ["| Group id | Pivot |", "| --- | --- |"]
    for gid, x, y in re.findall(r'<g id="([\w-]+)" data-pivot="([\d.]+) ([\d.]+)"', body):
        rows.append(f"| `{gid}` | {x}, {y} |")
    return "\n".join(rows)


def swift_art(c, motion):
    """The front view's numbers and keys as Swift, for app/ChumsWatch/Rig/BiscuitStage.swift."""
    F = FRONT[c["id"]]
    num = lambda v: f"{v:g}"
    arr = lambda t: "[" + ", ".join(num(v) for v in t) + "]"
    arr2 = lambda t: "[" + ", ".join(arr(o) for o in t) + "]"
    case = lambda ch: ch.split(".")[0] + "".join(p.capitalize() for p in ch.split(".")[1:])
    chans = list(motion["channels"])
    rest = ["1" if ch.endswith((".sx", ".sy")) else "0" for ch in chans]
    slim = lambda s: {"frames": s["frames"], "enter": s.get("enter", {"frames": 1, "ease": "lin"}), "tracks": s["tracks"]}
    states = ",\n".join(f'"{sid}":' + json.dumps(slim(s), separators=(",", ":")) for sid, s in motion["states"].items())
    poke = json.dumps(slim(motion["reactions"]["poke"]), separators=(",", ":"))
    L = F["legs"]
    return f'''// Written by design/characters/fetch/build.py from its FRONT numbers and motion-front.json. Change those, not this file.

/// {c["name"]} facing the screen: its shapes and the keys of its 12 states (design/characters/fetch/SPEC-front.md).
/// Lengths are points on the 396 × 484 design canvas, twice the rig's 198 × 242. Circles are [x, y, r].
enum BiscuitArt {{
    static let near: UInt32 = 0x{c["near"][1:]}, far: UInt32 = 0x{c["far"][1:]}
    static let ground = {num(GROUND)}.0, centre = {num(F["root"][0])}.0
    static let haunch: [[Double]] = {arr2(F["haunch"])}
    static let chest: [[Double]] = {arr2(F["chest"])}
    static let neck: [[Double]] = {arr2(F["neck"])}
    static let head: [[Double]] = {arr2(F["head"])}
    /// The screen-left ear, base then tip; the right one is its mirror image about `centre`.
    static let ear: [[Double]] = {arr2(F["ear"])}
    static let chin: [Double] = {arr(F["chin"])}
    /// The eyes: how far each is from `centre`, their y, their radius.
    static let eye: [Double] = {arr(F["eye"])}
    static let nose: [Double] = {arr(F["nose"])}
    static let legWidth = {num(L["w"])}.0, legGap = {num(L["gap"])}.0, legTop = {num(L["top"])}.0, legRadius = {num(L["r"])}.0, shoulderY = {num(F["shoulder_y"])}.0
    /// The tail's centre line (a quadratic curve), and its width at the base and at the tip.
    static let tail: [[Double]] = {arr2([F["tail"]["p0"], F["tail"]["p1"], F["tail"]["p2"]])}
    static let tailWidth: [Double] = {arr([F["tail"]["w0"], F["tail"]["w1"]])}
    static let flock: [[Double]] = {arr2(F["flock"])}

    /// What the keys move, in the order of `names` and `rest`.
    enum Ch: Int, CaseIterable {{ case {", ".join(case(ch) for ch in chans)} }}
    static let names: [String] = [{", ".join(json.dumps(ch) for ch in chans)}]
    static let rest: [Double] = [{", ".join(rest)}]

    /// The keys, as JSON: for each state its length in frames (24 a second), how it's entered, and per channel
    /// [frame, value, ease] (no ease: "inout").
    static let motion = #"""
{{"states":{{
{states}
}},
"poke":{poke}}}
"""#
}}
'''


if __name__ == "__main__":
    side = json.load(open(os.path.join(HERE, "motion.json")))
    front = json.load(open(os.path.join(HERE, "motion-front.json")))
    rigs = {"front": {}, "side": {}}
    front_path = os.path.join(HERE, "SPEC-front.md")
    front_spec = open(front_path).read()
    for c in CHARACTERS:
        folder = os.path.join(HERE, "..", c["id"])
        os.makedirs(folder, exist_ok=True)
        open(os.path.join(folder, c["id"] + ".svg"), "w").write(svg(c))
        open(os.path.join(folder, c["id"] + "-front.svg"), "w").write(svg_front(c))
        colors = [[c["near_name"], c["near"]], [c["far_name"], c["far"]], ["Black", BLACK]]
        rigs["side"][c["id"]] = dict(name=c["name"], svg=svg(c, False), gain=c["gain"], colors=colors)
        rigs["front"][c["id"]] = dict(name=c["name"], svg=svg_front(c, False), gain={}, colors=colors)
        spec_path = os.path.join(folder, "SPEC.md")
        spec = open(spec_path).read()
        spec = fill(spec, "svg", "```svg\n" + svg(c) + "```")
        if c is FETCH:
            spec = fill(spec, "channels", channels_md(side))
            spec = fill(spec, "states", states_md(side))
            spec = fill(spec, "poke", poke_md(side))
        open(spec_path, "w").write(spec)
        front_spec = fill(front_spec, "anatomy-" + c["id"], front_anatomy_md(c) + "\n\n" + pivots_md(svg_front(c, False)))
        front_spec = fill(front_spec, "svg-" + c["id"], "```svg\n" + svg_front(c) + "```")
    front_spec = fill(front_spec, "channels", channels_md(front))
    front_spec = fill(front_spec, "states", states_md(front))
    front_spec = fill(front_spec, "poke", poke_md(front))
    open(front_path, "w").write(front_spec)
    open(os.path.join(HERE, "..", "..", "..", "app", "ChumsWatch", "Rig", "BiscuitArt.swift"), "w").write(swift_art(BISCUIT, front))
    page = open(os.path.join(HERE, "player.part.html")).read()
    page = page.replace("/*RIGS*/", json.dumps(rigs)).replace("/*MOTION*/", json.dumps({"front": front, "side": side}, separators=(",", ":")))
    open(os.path.join(HERE, "preview.html"), "w").write(page)
    print("wrote the four SVGs, preview.html and the generated parts of the three specification files")
