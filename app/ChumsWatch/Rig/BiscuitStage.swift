import SwiftUI

/// Biscuit: an orange cat sitting and looking at you, drawn flat in two oranges on black: the chest, the front legs and
/// the head in the lighter one, the haunches and the tail behind them in the darker one. No mouth: its mood is in the
/// tilt of the head, the ears, the tail and the paws. Every shape is a circle, the hull of two circles or a rounded
/// rectangle (BiscuitArt), and every state is a loop of keys at 24 frames a second (design/characters/fetch/SPEC-front.md),
/// entered by a short blend from the pose on screen.
///   idle: the head rocks, the tail swishes · listening: tall, the head flips from one tilt to the other, the ears turn
///   in with your voice · thinking: looks up at one corner then the other, tapping its tail · working: kneading, a tail
///   flick at each tool call · subagents: three dots hop past, it bats at them · speaking: the head bobs with its voice ·
///   done: a jump with both paws up · needs you: a wave and a bounce · approval: small, at the top, crouched, then the
///   wiggle before a pounce · blocked: ears flat, head down · dizzy: the head goes round · sleep: head down, eyes shut,
///   dimmed
///   tap: a flinch; four in 3 s and it gets dizzy · stroke: the head leans into your finger, eyes shut · crown: up it
///   stretches tall, down it crouches and wiggles; turned fast it gets dizzy
final class BiscuitStage: Staged {
    static let shared = BiscuitStage()

    let ownsApproval = false
    let holds: [ChumsState: Double] = [.done: 3.0, .dizzy: 3.0]             // two turns of each loop
    func captionTop(_ r: Rig) -> Bool { false }
    func ownsCaption(_ r: Rig) -> Bool { false }
    /// The keys are 24 a second: 30 frames will do unless a finger or the crown is moving it.
    var calm: Bool { t - busyAt > 0.6 }

    private typealias C = BiscuitArt.Ch

    // MARK: the keys

    private enum Ease: String {
        case lin, easeInOut = "inout", easeIn = "in", out, antic, over, spring, hold
        func at(_ t: Double) -> Double {
            switch self {
            case .lin: return t
            case .easeInOut: return BiscuitStage.bez(0.45, 0, 0.55, 1, t)
            case .easeIn: return BiscuitStage.bez(0.55, 0, 1, 0.45, t)
            case .out: return BiscuitStage.bez(0.16, 1, 0.3, 1, t)
            case .antic: return BiscuitStage.bez(0.36, 0, 0.66, -0.56, t)
            case .over: return BiscuitStage.bez(0.34, 1.56, 0.64, 1, t)
            case .spring: return t >= 1 ? 1 : 1 - exp(-6 * t) * cos(4 * Double.pi * t)
            case .hold: return t >= 1 ? 1 : 0
            }
        }
    }
    private struct Key { let f: Double, v: Double, e: Ease }
    /// A loop: its length in frames, the frames and ease of the blend into it, and its keys per channel (none: at rest).
    private struct Clip { var frames = 24.0, enter = 6.0, enterEase = Ease.easeInOut, tracks: [[Key]] = [] }

    /// y of a cubic Bézier ease (0,0) (x1,y1) (x2,y2) (1,1) at x.
    private static func bez(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double) -> Double {
        let cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx
        let cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by
        var lo = 0.0, hi = 1.0, t = x
        for _ in 0..<24 {
            let v = ((ax * t + bx) * t + cx) * t
            if abs(v - x) < 1e-5 { break }
            if v < x { lo = t } else { hi = t }
            t = (lo + hi) / 2
        }
        return ((ay * t + by) * t + cy) * t
    }

    private static let lib: (states: [ChumsState: Clip], poke: Clip) = load()
    private static func load() -> (states: [ChumsState: Clip], poke: Clip) {
        func num(_ a: Any?) -> Double { (a as? Double) ?? (a as? NSNumber)?.doubleValue ?? 0 }
        func ease(_ a: Any?) -> Ease { Ease(rawValue: a as? String ?? "") ?? .easeInOut }
        func clip(_ d: [String: Any]) -> Clip {
            var tracks = [[Key]](repeating: [], count: BiscuitArt.names.count)
            for (name, v) in d["tracks"] as? [String: Any] ?? [:] {
                guard let i = BiscuitArt.names.firstIndex(of: name), let keys = v as? [[Any]] else { continue }
                tracks[i] = keys.filter { $0.count >= 2 }.map { Key(f: num($0[0]), v: num($0[1]), e: $0.count > 2 ? ease($0[2]) : .easeInOut) }
            }
            let enter = d["enter"] as? [String: Any] ?? [:]
            return Clip(frames: max(1, num(d["frames"])), enter: max(1, num(enter["frames"])), enterEase: ease(enter["ease"]), tracks: tracks)
        }
        let root = (try? JSONSerialization.jsonObject(with: Data(BiscuitArt.motion.utf8))) as? [String: Any] ?? [:]
        var states: [ChumsState: Clip] = [:]
        for (id, v) in root["states"] as? [String: Any] ?? [:] {
            if let s = ChumsState(rawValue: id), let d = v as? [String: Any] { states[s] = clip(d) }
        }
        return (states, clip(root["poke"] as? [String: Any] ?? [:]))
    }

    private static func sample(_ keys: [Key], _ f: Double) -> Double {
        if f <= keys[0].f { return keys[0].v }
        for i in 1..<keys.count where f <= keys[i].f {
            let a = keys[i - 1], b = keys[i]
            return a.v + (b.v - a.v) * b.e.at((f - a.f) / (b.f - a.f))
        }
        return keys[keys.count - 1].v
    }
    /// Every channel at frame `f` of a clip. `additive`: channels without keys are 0 (a reaction's offsets), not at rest.
    private static func pose(_ c: Clip, _ f: Double, additive: Bool = false) -> [Double] {
        var p = additive ? [Double](repeating: 0, count: BiscuitArt.rest.count) : BiscuitArt.rest
        for i in p.indices where i < c.tracks.count && !c.tracks[i].isEmpty { p[i] = sample(c.tracks[i], f) }
        return p
    }

    // MARK: state

    private var state: ChumsState = .idle
    private var t = 0.0, frame = 0.0, busyAt = 0.0
    /// The pose it's blending from, while it enters a state.
    private var from: [Double]?
    private var shown = BiscuitArt.rest
    private var pokeAt = -9.0, pokes: [Double] = []
    private var petT = 0.0, petW = 0.0, petX = 0.0
    private var crownV = 0.0, crownIdle = 9.0, spin = 0.0
    private var nextTool = 0.8, toolT = 9.0
    /// Where it sits (the ground under it, in design points) and how big: small and at the top for an approval card.
    private var place = (y: 209.0, k: 1.0)

    private func add(_ p: inout [Double], _ c: C, _ v: Double) { p[c.rawValue] += v }
    private func ease(_ p: inout [Double], _ c: C, to v: Double, _ w: Double) { p[c.rawValue] += (v - p[c.rawValue]) * w }

    // MARK: Staged

    func onState(_ r: Rig, _ s: ChumsState, prev: ChumsState?, variant: Bool) {
        from = prev == nil ? nil : shown
        state = s; frame = 0; busyAt = t
    }

    func step(_ r: Rig, _ dt0: Double) {
        let dt = min(dt0, 0.05)
        t += dt
        guard let clip = Self.lib.states[state] else { return }
        let goal: (y: Double, k: Double) = state == .approval ? (118, 0.55) : state == .listening ? (214, 0.95) : (209, 1)
        guard !r.ao else {
            // dimmed: drawn about once a second, so nothing in flight; it rests in its state's first pose
            frame = 0; from = nil; petW = 0; petT = 0; crownV = 0; spin = 0; pokeAt = -9
            shown = Self.pose(clip, 0); place = goal
            return
        }
        let before = frame
        frame += dt * 24
        var p = Self.pose(clip, frame.truncatingRemainder(dividingBy: clip.frames))
        if let f0 = from {
            let k = frame / clip.enter
            if k >= 1 { from = nil } else {
                let e = clip.enterEase.at(k)
                for i in p.indices { p[i] = f0[i] + (p[i] - f0[i]) * e }
            }
        }
        // your voice turns the ears in; its own voice bobs the head
        if state == .listening { add(&p, .earLRot, r.voice * 6); add(&p, .earRRot, -r.voice * 6) }
        if state == .speaking { add(&p, .headY, r.voice * 4) }

        // a tap: a flinch, added on top
        let poke = Self.lib.poke, pf = (t - pokeAt) * 24
        if pf < poke.frames {
            let a = Self.pose(poke, pf, additive: true)
            for i in p.indices { p[i] += a[i] }
        }
        // a tool call: the tail flicks in and the ears twitch
        nextTool -= dt
        if nextTool <= 0 && (!r.worlds.queue.isEmpty || state == .working), let call = r.worlds.nextEvent(r) {
            nextTool = r.worlds.queue.isEmpty ? rnd(1.4, 2.4) : 0.5
            if call.kind != .stop { toolT = 0 }
        }
        toolT += dt
        if toolT < 0.25 {
            let k = sin(toolT / 0.25 * Double.pi)
            add(&p, .tailRot, -14 * k); add(&p, .earLRot, 8 * k); add(&p, .earRRot, -8 * k)
        }
        // a stroke: the head leans into the finger, the ears go out, the eyes shut; about 18 frames to let go
        petT = max(0, petT - dt)
        petW += ((petT > 0 ? 1 : 0) - petW) * min(1, dt * (petT > 0 ? 12 : 4))
        if petW > 0.002 {
            ease(&p, .eyeSy, to: 0.12, petW); add(&p, .rootSy, -0.02 * petW)
            ease(&p, .earLRot, to: -40, petW); ease(&p, .earRRot, to: 40, petW)
            add(&p, .headY, 6 * petW); add(&p, .headRot, petX * 36 * petW); add(&p, .headX, petX * 12 * petW)
            add(&p, .tailRot, (-10 + 16 * sin(t * Double.pi * 4)) * petW)
        }
        // the crown: up it stretches tall, down it crouches and wiggles; let go and it comes back
        crownIdle += dt
        if crownIdle > 0.5 && crownV != 0 { crownV *= exp(-dt * 7); if abs(crownV) < 0.004 { crownV = 0 } }
        spin *= exp(-dt * 2.5)
        let up = max(0, crownV), down = max(0, -crownV)
        if up > 0 {
            add(&p, .headY, -14 * up); add(&p, .rootSy, 0.05 * up); add(&p, .earLRot, 8 * up); add(&p, .earRRot, -8 * up)
        }
        if down > 0 {
            let wiggle = sin(t * Double.pi * 12) * down
            add(&p, .headY, 12 * down); add(&p, .rootSy, -0.08 * down); add(&p, .rootSx, 0.05 * down); add(&p, .eyeSy, 0.2 * down)
            add(&p, .rootX, 4 * wiggle); add(&p, .rootRot, -2 * wiggle); add(&p, .tailRot, 10 * wiggle)
        }
        if spin > 3.2 && state != .dizzy { spin = 0; r.onHaptic?(.strong); r.setState(.dizzy) }
        // needs you: a tap on the wrist as the paw goes up, every other wave
        if state == .needs {
            let two = clip.frames * 2
            if before.truncatingRemainder(dividingBy: two) < 8 && frame.truncatingRemainder(dividingBy: two) >= 8 { r.onHaptic?(.strong) }
        }
        let e = min(1, dt * 7)
        place.y += (goal.y - place.y) * e; place.k += (goal.k - place.k) * e
        shown = p
        if from != nil || pf < poke.frames || petW > 0.01 || crownV != 0 || abs(place.k - goal.k) > 0.005 { busyAt = t }
    }

    // MARK: input

    func motion(_ r: Rig, _ ax: Double, _ ay: Double, _ az: Double) {}       // a hard shake makes it dizzy: the rig does that
    func crown(_ r: Rig, _ delta: Double) {
        crownV = clamp(crownV + delta * 0.25, -1, 1); spin += abs(delta) * 0.25; crownIdle = 0; busyAt = t
    }
    private func follow(_ pt: CGPoint) { petX = clamp((Double(pt.x) - 99) / 198, -0.5, 0.5); busyAt = t }
    func touched(_ r: Rig, at pt: CGPoint) { follow(pt) }
    func dragged(_ r: Rig, at pt: CGPoint) { follow(pt) }
    func released(_ r: Rig) {}
    func poke(_ r: Rig) {
        pokeAt = t; busyAt = t
        pokes = pokes.filter { t - $0 < 3 } + [t]
        if pokes.count >= 4 && state != .dizzy && state != .approval && state != .listening { pokes = []; r.setState(.dizzy) }
    }
    func pet(_ r: Rig) { petT = 0.45; busyAt = t }
    func deny(_ r: Rig) { pokeAt = t; busyAt = t }
    func hit(_ pt: CGPoint) -> Bool {
        abs(Double(pt.x) - 99) < 64 * place.k && Double(pt.y) > place.y - 170 * place.k && Double(pt.y) < place.y + 6
    }

    // MARK: drawing

    /// The hull of two circles [x, y, r], as an outline.
    private static func hull(_ a: [Double], _ b: [Double]) -> Path {
        let d = hypot(b[0] - a[0], b[1] - a[1]), ang = atan2(b[1] - a[1], b[0] - a[0])
        let off = acos(clamp((a[2] - b[2]) / max(d, 1e-6), -1, 1))
        let n = 28
        var pts: [CGPoint] = []
        for i in 0...n {                                                   // round b, on the side away from a
            let th = ang + off - 2 * off * Double(i) / Double(n)
            pts.append(CGPoint(x: b[0] + b[2] * cos(th), y: b[1] + b[2] * sin(th)))
        }
        for i in 0...n {                                                   // round a, on the side away from b
            let th = ang - off - (TAU - 2 * off) * Double(i) / Double(n)
            pts.append(CGPoint(x: a[0] + a[2] * cos(th), y: a[1] + a[2] * sin(th)))
        }
        var p = Path(); p.addLines(pts); p.closeSubpath()
        return p
    }
    /// A band around a quadratic curve, `w[0]` wide at its start and `w[1]` at its end, with round ends.
    private static func band(_ q: [[Double]], _ w: [Double]) -> Path {
        let n = 24
        var left: [CGPoint] = [], right: [CGPoint] = [], caps: [(c: P2, th: Double, h: Double)] = []
        for i in 0...n {
            let u = Double(i) / Double(n), v = 1 - u
            let x = v * v * q[0][0] + 2 * v * u * q[1][0] + u * u * q[2][0], y = v * v * q[0][1] + 2 * v * u * q[1][1] + u * u * q[2][1]
            let tx = 2 * v * (q[1][0] - q[0][0]) + 2 * u * (q[2][0] - q[1][0]), ty = 2 * v * (q[1][1] - q[0][1]) + 2 * u * (q[2][1] - q[1][1])
            let len = max(hypot(tx, ty), 1e-6), nx = -ty / len, ny = tx / len, h = (w[0] + (w[1] - w[0]) * u) / 2
            left.append(CGPoint(x: x + nx * h, y: y + ny * h)); right.append(CGPoint(x: x - nx * h, y: y - ny * h))
            if i == 0 || i == n { caps.append((P2(x, y), atan2(ny, nx), h)) }
        }
        func cap(_ k: (c: P2, th: Double, h: Double), from th0: Double) -> [CGPoint] {
            (1..<10).map { i in let th = th0 - Double.pi * Double(i) / 10; return CGPoint(x: k.c.x + k.h * cos(th), y: k.c.y + k.h * sin(th)) }
        }
        var p = Path()
        // along one side, round the tip, back along the other, round the base
        p.addLines(left + cap(caps[1], from: caps[1].th) + right.reversed() + cap(caps[0], from: caps[0].th - Double.pi))
        p.closeSubpath()
        return p
    }
    private static func mirrored(_ c: [Double]) -> [Double] { [2 * BiscuitArt.centre - c[0], c[1], c[2]] }

    private static let haunch = hull(BiscuitArt.haunch[0], BiscuitArt.haunch[1])
    private static let chest = hull(BiscuitArt.chest[0], BiscuitArt.chest[1])
    private static let neck = hull(BiscuitArt.neck[0], BiscuitArt.neck[1])
    private static let head = hull(BiscuitArt.head[0], BiscuitArt.head[1])
    private static let earL = hull(BiscuitArt.ear[0], BiscuitArt.ear[1])
    private static let earR = hull(mirrored(BiscuitArt.ear[0]), mirrored(BiscuitArt.ear[1]))
    private static let tail = band(BiscuitArt.tail, BiscuitArt.tailWidth)

    /// One of its two oranges, darkened toward black by `dim` (asleep it's at half).
    private static func shade(_ hex: UInt32, _ dim: Double) -> Color {
        let k = 1 - dim
        return Color(.sRGB, red: Double((hex >> 16) & 0xFF) / 255 * k, green: Double((hex >> 8) & 0xFF) / 255 * k,
                     blue: Double(hex & 0xFF) / 255 * k, opacity: 1)
    }
    /// A context moved by (x, y), then turned and scaled about the point `at`.
    private static func moved(_ c0: GraphicsContext, x: Double = 0, y: Double = 0, rot: Double = 0, sx: Double = 1, sy: Double = 1,
                              at: (x: Double, y: Double)) -> GraphicsContext {
        var c = c0
        c.translateBy(x: x + at.x, y: y + at.y); c.rotate(by: .degrees(rot)); c.scaleBy(x: sx, y: sy); c.translateBy(x: -at.x, y: -at.y)
        return c
    }

    func draw(_ r: Rig, _ ctx: GraphicsContext) {
        let A = BiscuitArt.self, p = shown
        func v(_ c: C) -> Double { p[c.rawValue] }
        let dim = clamp(v(.dim), 0, 1)
        let near = Self.shade(A.near, dim), far = Self.shade(A.far, dim)

        // the design canvas is twice the rig's: its middle (x 198) at the screen's, its ground line where the cat sits
        var canvas = ctx
        canvas.translateBy(x: 99, y: place.y); canvas.scaleBy(x: place.k * 0.5, y: place.k * 0.5); canvas.translateBy(x: -198, y: -A.ground)
        let root = Self.moved(canvas, x: v(.rootX), y: v(.rootY), rot: v(.rootRot), sx: v(.rootSx), sy: max(0.1, v(.rootSy)), at: (A.centre, A.ground))

        // the subagents' dots, on the ground to its left
        let dots = clamp(v(.flockOn), 0, 1)
        if dots > 0.01 {
            let hops = [v(.f1Y), v(.f2Y), v(.f3Y)], c = root.faded(dots)
            for (i, d) in A.flock.enumerated() where i < hops.count { c.fillCircle(d[0], d[1] + hops[i], 7.5, far) }
        }
        // behind: the tail and the haunches
        Self.moved(root, rot: v(.tailRot), at: (A.tail[0][0], A.tail[0][1])).fill(Self.tail, with: .color(far))
        root.fill(Self.haunch, with: .color(far))
        // the chest, the neck and the two front legs, each turning at its shoulder
        root.fill(Self.chest, with: .color(near))
        root.fill(Self.neck, with: .color(near))
        let legL = A.centre - A.legGap / 2 - A.legWidth, legR = A.centre + A.legGap / 2
        Self.moved(root, y: v(.pawLY), rot: v(.pawLRot), at: (legL + A.legWidth / 2, A.shoulderY))
            .fillRoundRect(legL, A.legTop, A.legWidth, A.ground - A.legTop, A.legRadius, near)
        Self.moved(root, y: v(.pawRY), rot: v(.pawRRot), at: (legR + A.legWidth / 2, A.shoulderY))
            .fillRoundRect(legR, A.legTop, A.legWidth, A.ground - A.legTop, A.legRadius, near)
        // the head, tilting about the chin: the ears, the face, the nose and the eyes
        let head = Self.moved(root, x: v(.headX), y: v(.headY), rot: v(.headRot), at: (A.chin[0], A.chin[1]))
        Self.moved(head, rot: v(.earLRot), at: (A.ear[0][0], A.ear[0][1])).fill(Self.earL, with: .color(near))
        Self.moved(head, rot: v(.earRRot), at: (2 * A.centre - A.ear[0][0], A.ear[0][1])).fill(Self.earR, with: .color(near))
        head.fill(Self.head, with: .color(near))
        head.fillCircle(A.nose[0], A.nose[1], A.nose[2], .black)
        let rx = A.eye[2], ry = A.eye[2] * clamp(v(.eyeSy), 0.1, 1.6)
        for side in [-1.0, 1.0] {
            let ex = A.centre + side * A.eye[0] + v(.eyeX), ey = A.eye[1] + v(.eyeY)
            head.fill(Path(ellipseIn: CGRect(x: ex - rx, y: ey - ry, width: rx * 2, height: ry * 2)), with: .color(.black))
        }
    }

    func front(_ r: Rig, _ ctx: GraphicsContext) {}
}
