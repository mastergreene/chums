import SwiftUI

/// The characters the rig draws itself (the others draw themselves: Staged). Each reads the same Pose.
enum Characters {
    static func draw(_ r: Rig, _ ctx: GraphicsContext, _ p: Pose) {
        switch r.character {
        case .peeps: drawEyes(r, ctx, p)
        case .stretch, .crunch, .nana, .squint, .ray, .tangle, .noodle, .biscuit: break             // staged: they draw themselves (Renderer)
        }
    }

    // MARK: eyes (Peeps')

    struct EyeStyle { var y: Double, gap: Double, w: Double, h: Double, n: Double, color: Color, rx: Double, ry: Double, stroke: Bool, pulse: Double }

    static func superellipse(_ a: Double, _ b: Double, _ n: Double) -> Path {
        var p = Path()
        for i in 0...72 {
            let th = Double(i) / 72 * TAU, c = cos(th), s = sin(th)
            let pt = CGPoint(x: (c < 0 ? -1 : 1) * pow(abs(c), 2 / n) * a, y: (s < 0 ? -1 : 1) * pow(abs(s), 2 / n) * b)
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        p.closeSubpath(); return p
    }

    /// Solid eyes with no pupils: lids make every expression (sleepy flattens the top, worried slants it,
    /// intent narrows top and bottom, happy leaves an upward crescent).
    static func drawEyePair(_ ctx: GraphicsContext, _ o: EyeStyle, _ E: EyePose) {
        for side in [-1.0, 1.0] {
            let ex = side * o.gap + E.gx * o.rx + E.tremorX * 0.5, ey = o.y + E.gy * o.ry + E.tremorY * 0.5
            var c = ctx; c.translateBy(x: ex, y: ey)
            drawEye(c, o, E, side: side)
        }
    }

    /// One eye, centered on the context's origin; `side` is −1 for the left one, 1 for the right (lids slant and
    /// spirals turn the other way).
    static func drawEye(_ c0: GraphicsContext, _ o: EyeStyle, _ E: EyePose, side: Double) {
        var c = c0
        let a0 = o.w / 2 * (1 + 0.15 * E.wide) * (1 + o.pulse * 0.1), b0 = o.h / 2 * (1 + 0.15 * E.wide) * (1 + o.pulse * 0.12)
        do {
            if E.spiral {
                let sr = max(a0 * 1.05, 6.5), rot = Date().timeIntervalSinceReferenceDate * TAU * 1.2 * side
                var sp = Path()
                for k in 0...60 {
                    let u = Double(k) / 60, th = u * 2.2 * TAU + rot, rr = u * sr
                    let pt = CGPoint(x: cos(th) * rr, y: sin(th) * rr); if k == 0 { sp.move(to: pt) } else { sp.addLine(to: pt) }
                }
                c.stroke(sp, with: .color(o.color), style: StrokeStyle(lineWidth: max(1.8, sr * 0.26), lineCap: .round, lineJoin: .round))
                return
            }
            if E.closed > 0.5 || E.open < 0.12 {
                let depth = a0 * 0.36, k = E.wince ? -1.0 : 1.0
                var cp = Path(); cp.move(to: CGPoint(x: -a0 * 0.9, y: -depth * 0.3 * k))
                cp.addQuadCurve(to: CGPoint(x: a0 * 0.9, y: -depth * 0.3 * k), control: CGPoint(x: 0, y: depth * 1.2 * k))
                c.stroke(cp, with: .color(o.color), style: StrokeStyle(lineWidth: max(2.4, b0 * 0.2), lineCap: .round))
                return
            }
            let b = b0 * clamp(E.open, 0.08, 1), a = a0 * (1 + 0.06 * (1 - E.open))
            let topCover = b * 2 * (0.45 * E.sleepy + 0.22 * E.worried + 0.1 * E.intent)
            let slope = tan(18 * DEG) * E.worried
            let botY = b - b * 2 * 0.26 * E.intent
            // lids are curved like real ones, the top arched and the bottom bowed: a little lid trims the eye's rounded
            // corners instead of cutting a flat across it
            let xl = -a * 1.4, xr = a * 1.4, yl = -b + topCover + slope * xl * side, yr = -b + topCover + slope * xr * side
            var lid = Path()
            lid.move(to: CGPoint(x: xl, y: yl))
            lid.addQuadCurve(to: CGPoint(x: xr, y: yr), control: CGPoint(x: 0, y: (yl + yr) / 2 - b * 0.4))
            lid.addLine(to: CGPoint(x: xr, y: botY))
            lid.addQuadCurve(to: CGPoint(x: xl, y: botY), control: CGPoint(x: 0, y: botY + b * 0.36))
            lid.closeSubpath()
            c.clip(to: lid)
            if E.happy > 0.01 {
                // subtract an ellipse rising from below: at full happy only an upward crescent remains
                var h = Path(CGRect(x: -a * 2, y: -b * 3, width: a * 4, height: b * 6))
                let cy = lerp(2.2 * b, 0.75 * b, E.happy)
                h.addEllipse(in: CGRect(x: -a * 1.15, y: cy - b, width: a * 2.3, height: b * 2))
                c.clip(to: h, style: FillStyle(eoFill: true))
            }
            let shape = superellipse(a, b, o.n)
            if o.stroke { c.stroke(shape, with: .color(o.color), lineWidth: 1.6) } else { c.fill(shape, with: .color(o.color)) }
        }
    }

    static func ring(_ ctx: GraphicsContext, _ x: Double, _ y: Double, _ r: Double, _ amber: Double, _ t: Double) {
        guard amber > 0.02 else { return }
        ctx.faded(amber * (0.7 + 0.3 * sin(t * TAU * 1.2))).strokeCircle(x, y, r, Pal.amber, 3)
    }

    // MARK: Eyes

    static func drawEyes(_ r: Rig, _ ctx: GraphicsContext, _ p: Pose) {
        let c = p.c, w = lerp(22, 26, c), h = lerp(34, 38, c), n = lerp(3, 2.2, c)
        let gap = 30 * (1 - (p.sy - 1) * 0.5)
        let col = p.amber > 0.5 ? Pal.amber : Pal.white
        var g = p.ao ? ctx.faded(0.5) : ctx                               // dimmed (always on): its colors at half brightness
        g.translateBy(x: p.cx, y: p.cy + h / 2 * p.scale); g.rotate(by: .radians(p.tilt)); g.scaleBy(x: p.scale / p.sy, y: p.scale * p.sy); g.translateBy(x: 0, y: -h / 2)
        drawEyePair(g, EyeStyle(y: 0, gap: gap, w: w, h: h, n: n, color: p.tint ?? col, rx: 8, ry: 6, stroke: false, pulse: p.pulse), p.eye)
    }


    // MARK: props for Peeps (portrait)

    static func dropPath(x: Double, y: Double, s: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x, y: y - 6 * s))
        p.addQuadCurve(to: CGPoint(x: x, y: y + 5.5 * s), control: CGPoint(x: x + 5.5 * s, y: y + 2.5 * s))
        p.addQuadCurve(to: CGPoint(x: x, y: y - 6 * s), control: CGPoint(x: x - 5.5 * s, y: y + 2.5 * s))
        return p
    }

    static func drawProps(_ r: Rig, _ ctx: GraphicsContext, _ p: Pose) {
        for q in r.particles {
            let a = clamp(1 - q.age / q.life, 0, 1)
            let x = p.cx + q.x, y = p.cy + q.y
            switch q.kind {
            case .spark:
                var s = Path()
                for i in 0..<8 { let rr = i % 2 == 1 ? 1.6 : 4.4, th = Double(i) * .pi / 4; let pt = CGPoint(x: x + cos(th) * rr, y: y + sin(th) * rr); if i == 0 { s.move(to: pt) } else { s.addLine(to: pt) } }
                s.closeSubpath(); ctx.faded(a).fill(s, with: .color(Pal.amber))
            case .heart:
                var h = Path(); let k = 1.4
                h.move(to: CGPoint(x: x, y: y + 4 * k))
                h.addCurve(to: CGPoint(x: x, y: y - 4 * k), control1: CGPoint(x: x - 8 * k, y: y - 2.5 * k), control2: CGPoint(x: x - 4 * k, y: y - 8.5 * k))
                h.addCurve(to: CGPoint(x: x, y: y + 4 * k), control1: CGPoint(x: x + 4 * k, y: y - 8.5 * k), control2: CGPoint(x: x + 8 * k, y: y - 2.5 * k))
                ctx.faded(a).fill(h, with: .color(Pal.heart))
            case .sweat: ctx.fill(dropPath(x: x, y: y, s: 1), with: .color(Pal.sweat))
            case .ripple: let k = q.age / q.life; ctx.faded((1 - k) * 0.8).strokeCircle(x, y, 30 + k * 40, Pal.white, 1.4)
            case .z: ctx.faded(a).draw(Text("z").font(.system(size: 10 + q.age * 2.4, weight: .semibold)).foregroundColor(Pal.gray), at: CGPoint(x: x, y: y))
            }
        }
        if r.state == .thinking {
            for i in 0..<3 { ctx.faded(0.3 + 0.7 * max(0, sin(p.t * 6 - Double(i) * 0.8))).fillCircle(144 + Double(i) * 9, 62, 2.6, Pal.white) }
        }
        if r.state == .needs {
            ctx.draw(Text(r.variant ? "?" : "!").font(.system(size: 20, weight: .semibold)).foregroundColor(Pal.amber), at: CGPoint(x: 158, y: 60))
        }
        if r.state == .sleep && r.variant {
            ctx.fillCircle(44, 58, 7, Pal.gray); ctx.fillCircle(47.5, 55.5, 6.2, .black)
        }
    }

    // MARK: portrait working and subagents

    static func drawTyping(_ r: Rig, _ ctx: GraphicsContext, pixel: Bool) {
        let T = r.typing, D = r.DOC
        for i in 0...min(T.line, 2) {
            let full = D.w * T.widths[i], w = i < T.line ? full : full * T.prog
            guard w >= 1 else { continue }
            let y = D.y + Double(i) * D.gap, col = i == T.line ? Pal.gray : Pal.dim
            if pixel {
                var p = Path(); let s = 3.6
                for k in 0..<Int(w / s) { p.addRect(CGRect(x: D.x + Double(k) * s, y: y, width: s - 0.6, height: s)) }
                ctx.fill(p, with: .color(col))
            } else { ctx.fillRoundRect(D.x, y, w, 3.4, 1.7, col) }
        }
        if T.line <= 2 && Int(r.t * 2.2) % 2 == 0 { ctx.fillRect(r.caretX() + 2, D.y + Double(T.line) * D.gap - 2.2, 1.8, 7.8, Pal.white) }
    }

    static func drawHelpers(_ r: Rig, _ ctx: GraphicsContext, _ p: Pose) {
        for (i, x) in [52.0, 99, 146].enumerated() {
            let bob = max(0, sin(p.t * TAU * 1.5 + Double(i) * 2.1)) * 3
            var m = p
            m.cx = x; m.cy = 196 - bob; m.scale = r.character == .peeps ? 0.32 : 0.36; m.sy = 1; m.tilt = 0; m.amber = 0; m.pulse = 0
            m.mini = true; m.unitScale = 0.36; m.phase = Double(i) * 0.7
            m.eye = .neutral(gx: 0, gy: 0.5, intent: 0.7)
            draw(r, ctx, m)
        }
    }
}
