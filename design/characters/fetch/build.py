#!/usr/bin/env python3
"""Builds the resting poses (fetch.svg, ../biscuit/biscuit.svg), the motion preview (preview.html) and the generated
parts of the two SPEC.md files from the numbers below and motion.json.

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


if __name__ == "__main__":
    motion = json.load(open(os.path.join(HERE, "motion.json")))
    rigs = {}
    for c in CHARACTERS:
        folder = os.path.join(HERE, "..", c["id"])
        os.makedirs(folder, exist_ok=True)
        open(os.path.join(folder, c["id"] + ".svg"), "w").write(svg(c))
        rigs[c["id"]] = dict(name=c["name"], svg=svg(c, False), gain=c["gain"],
                             colors=[[c["near_name"], c["near"]], [c["far_name"], c["far"]], ["Black", BLACK]])
        spec_path = os.path.join(folder, "SPEC.md")
        spec = open(spec_path).read()
        spec = fill(spec, "svg", "```svg\n" + svg(c) + "```")
        if c is FETCH:
            spec = fill(spec, "channels", channels_md(motion))
            spec = fill(spec, "states", states_md(motion))
            spec = fill(spec, "poke", poke_md(motion))
        open(spec_path, "w").write(spec)
    page = open(os.path.join(HERE, "player.part.html")).read()
    page = page.replace("/*RIGS*/", json.dumps(rigs)).replace("/*MOTION*/", json.dumps(motion, separators=(",", ":")))
    open(os.path.join(HERE, "preview.html"), "w").write(page)
    print("wrote the two SVGs, preview.html and the generated parts of the two SPEC.md files")
