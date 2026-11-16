import SwiftUI

/// Camera, physics, launch, crown scrub and the per-character worlds.
struct WorldState {
    var padX = 0.0, padY = 1.0            // gravity across the screen (x right, y down); length 0 flat … 1 hanging
    /// rot: the body's angle (it spins free while thrown, then rights itself against gravity)
    struct Body { var x = 99.0, y = 118.0, vx = 0.0, vy = 0.0, sq = 0.0, sqv = 0.0, rot = 0.0, rotV = 0.0, surprised = 0.0 }
    var body = Body()
    /// The Physics stage, shaken hard: the body flies, spinning, until it comes to rest, then sits dazed with stars
    /// circling and shakes it off where it lies.
    struct Loose {
        enum Phase { case fly, daze }
        var phase = Phase.fly
        var t = 0.0, still = 0.0, sinceKick = 0.0
    }
    var loose: Loose?
    /// Shaken in Portrait or the level: the character stays where it is (the game doesn't move) and is jolted in
    /// place. While you shake, the scene judders and it hops and flails, eyes squeezed shut; when you stop, it sits
    /// dazed with spinning eyes and stars circling its head, then shakes it off. Everything moves on its pixel grid.
    struct Jolt {
        var t = 0.0, sinceKick = 0.0
        var h = 0.0, vh = 0.0                                    // the hop, in the character's own pixels
        var quake = 0.0                                          // how hard the scene judders, 0…1
        var peak = 0.0                                           // the hardest the shake got (Rig.trauma)
        var daze: Double { 1.4 + 1.2 * peak }                    // a hard shake leaves it dazed longer
        static let rattle = 0.45, off = 0.5                      // after the last shake: rattle, then sit dazed, then shake it off
    }
    var jolt: Jolt?
    var drag: CGPoint?
    var hardHits: [Double] = []
    struct Tick { var x: Double, y: Double, age: Double, strong: Bool }
    var ticks: [Tick] = []
    struct Dust { var x: Double, y: Double, vx: Double, vy: Double, age: Double }
    var dust: [Dust] = []
    var pokes: [Double] = []
    struct Launch { var t = 0.0, y = -60.0, vy = 0.0, world = false }
    var launch: Launch?
    var zoom = 0.0, zoomV = 0.0
    var wp = WorldPose(cx: 99, cy: 112, scale: 1)
    var subCalls = 0
    var history: [String] = []
    var label = "", labelT = 0.0
    var busy = false
    var scrub = 0.0, scrubTarget = 0.0, scrubIdle = 99.0, crownAcc = 0.0
    var petDist = 0.0, annoyedT = 0.0
}

protocol WorldImpl: AnyObject {
    func reset()
    func onState(_ r: Rig, _ s: ChumsState, _ v: Bool)
    func step(_ r: Rig, _ dt: Double)
    func pose(_ r: Rig, _ p: Pose) -> WorldPose
    func back(_ r: Rig, _ ctx: GraphicsContext, _ p: Pose)
    func front(_ r: Rig, _ ctx: GraphicsContext, _ p: Pose)
    func scrubLabel(_ r: Rig) -> String?
}

/// The camera is wide in these states and close in the rest (listening, speaking, needs you). Shaken (dizzy), it
/// stays where it is: in the level, the jolt plays in the level.
let WIDE: Set<ChumsState> = [.idle, .thinking, .working, .subagents, .approval, .blocked, .done, .sleep, .dizzy]

/// Tool calls used when there's no hub to watch (demo mode and previews).
private let DEMO_CALLS: [(ToolCall.Kind, String)] = [
    (.read, "Read hub.mjs"), (.read, "Read chums-channel.mjs"), (.edit, "Edit hub.mjs"), (.bash, "Bash npm run build"),
    (.edit, "Edit Voice.swift"), (.test, "Bash npm test"), (.read, "Read project.yml"), (.edit, "Edit HubClient.swift"),
    (.bash, "Bash git diff"), (.test, "Bash swift test"),
]

final class Worlds {
    var queue: [ToolCall] = []
    var results: [String: Bool] = [:]
    var demo = false
    private var demoIdx = 0, demoT = 0.0
    let page = PageWorld()

    func current(_ r: Rig) -> WorldImpl {
        return page      // staged characters have no world
    }
    func reset(_ r: Rig) {
        page.reset(); _ = r
    }
    /// Worlds only hold a state (the flag rising, the gate opening) while they are on screen.
    func onState(_ r: Rig, _ s: ChumsState, _ v: Bool) { r.W.busy = false; if r.mode == .world && !r.character.staged { current(r).onState(r, s, v) } }

    func worldOn(_ r: Rig) -> Bool { r.mode == .world && !r.character.staged && r.W.zoom > 0.04 }
    func physicsOn(_ r: Rig) -> Bool { r.mode == .physics && !r.character.staged && r.state != .listening && r.state != .approval && r.W.launch == nil }
    /// The body is simulated: the Physics stage (thrown or not).
    func bodyOn(_ r: Rig) -> Bool { physicsOn(r) || r.W.loose != nil }

    // MARK: shaken (Physics: thrown; Portrait and the level: jolted in place)

    /// The Physics stage, shaken hard: the body is thrown, `dx, dy` the way it flies, `g` how hard the watch moved.
    func kick(_ r: Rig, _ dx: Double, _ dy: Double, _ g: Double) {
        guard !r.character.staged else { return }
        if r.W.loose == nil { r.W.loose = WorldState.Loose() }
        let v = clamp((g - 1.1) * 560, 300, 1300)
        r.W.body.vx += dx * v; r.W.body.vy += dy * v
        r.W.body.rotV += (dx >= 0 ? 1 : -1) * rnd(5, 11)                 // it tumbles the way it's thrown
        r.W.body.surprised = 0.6
        r.W.loose?.phase = .fly; r.W.loose?.sinceKick = 0; r.W.loose?.still = 0
    }

    /// The Physics stage, shaken gently: a shove without the daze.
    func kickBody(_ r: Rig, _ dx: Double, _ dy: Double, _ g: Double) {
        let v = clamp((g - 1.1) * 520, 200, 1000)
        r.W.body.vx += dx * v; r.W.body.vy += dy * v; r.W.body.surprised = 0.5
    }

    /// Portrait or the level, shaken: one more jolt (a hop if it's on its feet, the scene judders again).
    func jolt(_ r: Rig, _ g: Double) {
        guard !r.character.staged else { return }
        var J = r.W.jolt ?? WorldState.Jolt()
        J.peak = max(J.peak, r.trauma)
        // half pixels a second: a light shake hops it 1½ pixels, a hard one throws it up 6
        if J.h <= 0.01 { J.vh = 38 + 42 * r.trauma }
        J.quake = 1; J.sinceKick = 0
        r.W.jolt = J
    }

    /// Something else needs the character (you talk, an approval comes): the shake is over.
    func calm(_ r: Rig) {
        r.W.jolt = nil
        r.W.loose = nil
    }

    private func stepShaken(_ r: Rig, _ dt: Double) {
        if var L = r.W.loose {
            L.t += dt; L.sinceKick += dt
            let b = r.W.body
            switch L.phase {
            case .fly:
                L.still = hypot(b.vx, b.vy) < 45 ? L.still + dt : 0
                if L.still > 0.3 && L.sinceKick > 0.5 { L.phase = .daze; L.t = 0 }
            case .daze:
                // it lies there seeing stars, then shakes it off
                if L.t > 1.5 && L.t - dt <= 1.5 { r.clip = .shakeOffHard() }
                if L.t > 2.15 { r.W.loose = nil; if r.state == .dizzy { r.settle() }; return }
            }
            r.W.loose = L
        }
        if var J = r.W.jolt {
            J.t += dt; J.sinceKick += dt
            J.quake = max(0, J.quake - dt * 4)
            if J.h > 0 || J.vh > 0 {
                J.vh -= 240 * dt; J.h += J.vh * dt
                if J.h <= 0 { J.h = 0; J.vh = 0 }
            }
            if J.sinceKick > WorldState.Jolt.rattle + J.daze + WorldState.Jolt.off && J.h <= 0 {
                r.W.jolt = nil; if r.state == .dizzy { r.settle(); r.startBlink() }; return
            }
            r.W.jolt = J
        }
    }

    /// The scene's judder while the jolt lasts, in whole pixels of the character's own grid.
    func quake(_ r: Rig) -> CGPoint {
        guard let J = r.W.jolt, J.quake > 0.05 else { return .zero }
        let px = pixel(r), i = Int(r.t * 30)
        let xs = [1.0, -1, 0, 1, -1, 1, 0, -1], ys = [0.0, 1, -1, 0, 1, -1, 1, 0]
        // half a pixel for a light shake, one for a firm one, two for a hard one; half that as it fades
        let k = (r.trauma < 0.25 ? 0.5 : r.trauma < 0.6 ? 1 : 2) * (J.quake > 0.5 ? 1.0 : 0.5)
        return CGPoint(x: (xs[i % 8] * px * k).rounded(), y: (ys[(i * 3) % 8] * px * k).rounded())
    }

    /// One pixel of the character as drawn now: a gridded character's level grid or its own cell up close; the
    /// others have none, so 4 pt.
    private func pixel(_ r: Rig) -> Double {
        return 4
    }

    /// The pose while shaken: thrown (Physics), or jolted where it stands.
    private func shakenPose(_ r: Rig, _ p: inout Pose) {
        let gridded = r.character.gridded
        if let L = r.W.loose {
            switch L.phase {
            case .fly:
                p.daze = 1
                // arms and legs flail while it flies
                p.arms = Int(r.t * 7) % 2 == 0 ? .raised : .rest
                p.legs = Int(r.t * 9) % 2 == 0 ? .stepA : .stepB
            case .daze:
                p.daze = 2; p.stars = min(1, L.t * 4) * (L.t > 1.6 ? max(0, 1 - (L.t - 1.6) / 0.3) : 1)
                p.legs = .crouch; p.arms = .rest
                let wob = sin(L.t * TAU * 1.3) * 7 * DEG * max(0, 1 - L.t / 1.5) + (r.clip?.frame().tilt ?? 0)
                if gridded { p.roll += wob } else { p.tilt += wob }
            }
            if !gridded && r.W.body.surprised > 0 { p.eye.wide = max(p.eye.wide, 1) }
        }
        guard let J = r.W.jolt else { return }
        // a gridded character's own pixel as drawn now: every move is a whole pixel or half of one, like its eyes
        let u = gridded ? lerp(8.4, 9.4, r.cute) * p.unitScale : 9 * p.scale, half = u / 2
        let s = J.sinceKick, R = WorldState.Jolt.rattle, D = J.daze
        p.cy -= (J.h.rounded() * half)                                 // the hop, half a pixel at a time
        if s < R || J.h > 0 {
            // rattled: shaking side to side half a pixel, eyes squeezed shut, arms and legs scrambling
            p.cx += (Int(r.t * 12) % 2 == 0 ? -half : half)
            p.daze = 1
            p.arms = Int(r.t * 8) % 2 == 0 ? .raised : .rest
            p.legs = J.h > 0 ? .stand : (Int(r.t * 10) % 2 == 0 ? .stepA : .stepB)
            if !gridded { p.eye.intent = 1; p.eye.wide = 0 }
        } else if s < R + D {
            // dazed: it sits down, sways half a pixel each way, eyes spinning, stars circling its head
            let k = s - R
            p.daze = 2; p.stars = min(1, k * 4)
            if k < 1.0 { p.legs = .crouch; p.cy += u }                  // sitting: knees bent, its feet stay on the ground
            p.arms = .rest
            p.cx += [0, -half, 0, half][Int(k * 2.4) % 4]
            p.eye.spiral = true
        } else {
            // shakes it off: a quick side to side, eyes shut, the stars gone
            let k = s - R - D
            p.cx += (Int(k * 16) % 2 == 0 ? -half : half) * (k < 0.35 ? 1 : 0)
            p.stars = max(0, 1 - k / 0.15)
            p.eye.closed = k < 0.3 ? 1 : 0; p.eye.spiral = false
            p.legs = .stand; p.arms = .rest
        }
    }

    /// The body's box (its arms and legs included) turned by its spin: how far it reaches from its center each way.
    private func reach(_ r: Rig, _ rot: Double) -> (l: Double, r: Double, t: Double, b: Double) {
        let e = extents(r), c = cos(rot), s = sin(rot)
        var l = 0.0, rr = 0.0, t = 0.0, bb = 0.0
        for (x, y) in [(-e.hx, -e.top), (e.hx, -e.top), (-e.hx, e.bottom), (e.hx, e.bottom)] {
            let X = x * c - y * s, Y = x * s + y * c
            l = min(l, X); rr = max(rr, X); t = min(t, Y); bb = max(bb, Y)
        }
        return (-l, rr, -t, bb)
    }

    /// The next tool call for the world to show: a real one from the hub, or in demo mode a made-up one.
    func nextEvent(_ r: Rig) -> ToolCall? {
        if !queue.isEmpty { return queue.removeFirst() }
        guard demo else { return nil }
        let d = DEMO_CALLS[demoIdx % DEMO_CALLS.count]; demoIdx += 1
        let id = "demo-\(demoIdx)"
        if d.0 == .test { results[id] = true }
        return ToolCall(id: id, kind: d.0, text: d.1)
    }
    func announce(_ r: Rig, _ text: String) {
        r.W.label = text; r.W.labelT = 0
        r.W.history.append(text); if r.W.history.count > 80 { r.W.history.removeFirst() }
    }

    // MARK: gravity

    func watchRot(_ r: Rig) -> Double { atan2(r.W.padX, r.W.padY) }
    func gravMag(_ r: Rig) -> Double { min(1, hypot(r.W.padX, r.W.padY)) }

    func extents(_ r: Rig) -> (hx: Double, top: Double, bottom: Double) {
        let c = r.cute
        switch r.character {
        case .peeps: let s = 0.8; return ((lerp(25, 26, c) + lerp(11, 13, c)) * s, lerp(17, 19, c) * s, lerp(17, 19, c) * s)
        case .stretch, .crunch, .nana, .squint, .ray, .tangle, .noodle, .biscuit: return (50, 60, 60)
        }
    }

    private func stepPhysics(_ r: Rig, _ dt: Double) {
        var b = r.W.body
        let a = watchRot(r), m = gravMag(r) * 1200
        let ax = sin(a) * m, ay = cos(a) * m
        let flying = r.W.loose?.phase == .fly && r.W.drag == nil
        if let d = r.W.drag {
            b.vx += ((d.x - b.x) * 250 - b.vx * 24) * dt; b.vy += ((d.y - b.y) * 250 - b.vy * 24) * dt
        } else { b.vx += ax * dt; b.vy += ay * dt }
        let air = 1 - 0.6 * dt; b.vx *= air; b.vy *= air
        b.x += b.vx * dt; b.y += b.vy * dt
        // thrown, it bounces livelier and spins from the walls it scrapes along (a roll: speed along the wall over its size)
        let e = reach(r, b.rot), bounce = flying ? 0.58 : 0.45, roll = 1 / 30.0
        let lo = 8 + e.l, hi = 190 - e.r, top = 36 + e.t, bot = 234 - e.b
        if b.x < lo { impact(r, &b, abs(b.vx), vertical: false, 8, b.y); b.x = lo; b.vx = abs(b.vx) * bounce; b.vy *= 0.7; if flying { b.rotV = lerp(b.rotV, b.vy * roll, 0.6) } }
        if b.x > hi { impact(r, &b, abs(b.vx), vertical: false, 190, b.y); b.x = hi; b.vx = -abs(b.vx) * bounce; b.vy *= 0.7; if flying { b.rotV = lerp(b.rotV, -b.vy * roll, 0.6) } }
        if b.y < top { impact(r, &b, abs(b.vy), vertical: true, b.x, 36); b.y = top; b.vy = abs(b.vy) * bounce; b.vx *= 0.7; if flying { b.rotV = lerp(b.rotV, -b.vx * roll, 0.6) } }
        if b.y > bot { impact(r, &b, abs(b.vy), vertical: true, b.x, 234); b.y = bot; b.vy = -abs(b.vy) * bounce; b.vx *= 0.7; if flying { b.rotV = lerp(b.rotV, b.vx * roll, 0.6) } }
        b.sqv += (-400 * b.sq - 2 * 0.35 * 20 * b.sqv) * dt; b.sq += b.sqv * dt
        if flying {
            b.rotV *= 1 - 0.9 * dt; b.rot += b.rotV * dt
        } else {
            // it rights itself: upright against gravity (or leaning into a drag), by the shortest way round
            let upT = r.W.drag != nil ? clamp(b.vx / 900, -0.5, 0.5) : -a * (gravMag(r) > 0.2 ? 1 : 0)
            let goal = upT + TAU * ((b.rot - upT) / TAU).rounded()
            b.rotV += ((goal - b.rot) * 90 - b.rotV * 14) * dt; b.rot += b.rotV * dt
        }
        b.surprised = max(b.surprised - dt, (r.W.drag != nil || hypot(b.vx, b.vy) > 420) ? 0.5 : 0)
        r.W.body = b
    }

    private func impact(_ r: Rig, _ b: inout WorldState.Body, _ speed: Double, vertical: Bool, _ x: Double, _ y: Double) {
        guard speed >= 60 else { return }
        let s = min(speed / 600, 1) * 0.25
        b.sq = vertical ? s : -s; b.sqv = 0
        if speed > 250 {
            r.W.ticks.append(.init(x: x, y: y, age: 0, strong: speed > 700)); if r.W.ticks.count > 8 { r.W.ticks.removeFirst() }
            if r.trauma < 0.15 { r.onHaptic?(speed > 700 ? .strong : .click) }   // shaking it, you feel the shake already
        }
        if speed > 160 { for _ in 0..<4 { r.W.dust.append(.init(x: x, y: y, vx: rnd(-30, 30), vy: rnd(-40, 0), age: 0)) } }
        if speed > 700 {
            r.W.hardHits = r.W.hardHits.filter { r.t - $0 < 4 } + [r.t]
            if r.W.hardHits.count >= 3 && r.state != .dizzy { r.W.hardHits = []; r.setState(.dizzy) }
        }
    }

    // MARK: launch

    func startLaunch(_ r: Rig) {
        if r.mode == .world {
            current(r).reset(); r.W.launch = .init(world: true); r.W.zoom = 1; r.W.zoomV = 0; r.W.label = ""
        } else { r.W.launch = .init() }
    }

    private func stepLaunch(_ r: Rig, _ dt: Double) {
        guard var L = r.W.launch else { return }
        L.t += dt
        if L.world { r.W.launch = L.t > 1.8 ? nil : L; return }
        if L.t > 0.3 {
            L.vy += 1400 * dt; L.y += L.vy * dt
            if L.y > 118 {
                L.y = 118
                if L.vy > 90 {
                    let v = L.vy; L.vy = -v * 0.45; r.clip = nil
                    r.W.body.sq = min(v / 600, 1) * 0.25; r.W.body.sqv = 0
                    r.onHaptic?(v > 700 ? .strong : .click)
                    for i in 0..<5 { r.W.dust.append(.init(x: 99 + Double(i - 2) * 10, y: 118 + extents(r).bottom, vx: Double(i - 2) * 22, vy: rnd(-40, -20), age: 0)) }
                } else { L.vy = 0 }
            }
        }
        if L.t > 1.6 { r.W.launch = nil; r.W.body = WorldState.Body() } else { r.W.launch = L }
    }

    // MARK: frame

    func step(_ r: Rig, _ dt: Double) {
        let n = max(1, Int((dt * 120).rounded()))
        for _ in 0..<n where bodyOn(r) { stepPhysics(r, dt / Double(n)) }
        stepShaken(r, dt)
        if r.mode != .world { r.W.zoom = 0; r.W.zoomV = 0 }
        else {
            let zt: Double = (WIDE.contains(r.state) || r.W.launch?.world == true) ? 1 : 0
            r.W.zoomV += ((zt - r.W.zoom) * 36 - r.W.zoomV * 12) * dt       // critically damped, ω 6
            r.W.zoom = clamp(r.W.zoom + r.W.zoomV * dt, 0, 1.04)
        }
        r.W.labelT += dt; r.W.scrubIdle += dt
        if r.W.scrubIdle > 3 { r.W.scrubTarget = 0 }
        r.W.scrub += (r.W.scrubTarget - r.W.scrub) * min(1, dt * (r.W.scrubIdle > 3 ? 5 : 14))
        if r.W.annoyedT > 0 { r.W.annoyedT -= dt; if r.W.annoyedT <= 0 { r.S.worried.t = STATE_SPECS[r.state]!.worried } }
        if r.W.launch != nil { stepLaunch(r, dt) }
        if r.mode == .world && !r.character.staged { current(r).step(r, dt) }
        if demo && r.mode != .world && (r.state == .working || r.state == .subagents) {
            demoT += dt; if demoT > 2.2 { demoT = 0; if let e = nextEvent(r) { announce(r, e.text) } }
        }
        for i in r.W.ticks.indices { r.W.ticks[i].age += dt }
        r.W.ticks.removeAll { $0.age >= 0.5 }
        for i in r.W.dust.indices { r.W.dust[i].age += dt; r.W.dust[i].x += r.W.dust[i].vx * dt; r.W.dust[i].y += r.W.dust[i].vy * dt; r.W.dust[i].vy += 90 * dt }
        r.W.dust.removeAll { $0.age >= 0.6 }
    }

    // MARK: pose

    func apply(_ r: Rig, _ p0: Pose) -> Pose {
        var p = stagePose(r, p0)
        shakenPose(r, &p)
        return p
    }

    private func stagePose(_ r: Rig, _ p0: Pose) -> Pose {
        var p = p0
        if r.mode == .world {
            let w = current(r).pose(r, p); r.W.wp = w
            let e = clamp(r.W.zoom, 0, 1)
            p.cx = lerp(p.cx, w.cx, e); p.cy = lerp(p.cy, w.cy, e); p.scale = lerp(p.scale, w.scale, e)
            p.unitScale = lerp(p.unitScale, w.unitScale, e); p.world = e
            if e > 0.5 {
                let o = w.o
                if let v = o.legs { p.legs = v }; if let v = o.arms { p.arms = v }; if let v = o.tilt { p.tilt = v }
                if let v = o.open { p.eye.open = v }; if let v = o.gx { p.eye.gx = v }; if let v = o.gy { p.eye.gy = v }
                if let v = o.happy { p.eye.happy = v }; if let v = o.intent { p.eye.intent = v }
                if let v = o.sleepy { p.eye.sleepy = v }; if let v = o.wide { p.eye.wide = v }
                if let v = o.closed { p.eye.closed = v }; if let v = o.awake { p.awake = v }
            }
            return p
        }
        // portrait idle: the eyes look toward whichever way the watch is turned
        if r.state == .idle && gravMag(r) > 0.2 && abs(watchRot(r)) > 0.05 { p.eye.gx = clamp(p.eye.gx + sin(watchRot(r)) * 1.1, -1, 1) }
        if let L = r.W.launch, !L.world {
            if L.t > 0.3 { p.cy = L.y; p.cx = 99; p.sy *= 1 - r.W.body.sq } else { p.cy = -80 }
        } else if physicsOn(r) || (r.mode == .physics && r.W.drag != nil) {
            let b = r.W.body
            p.cx = b.x; p.cy = b.y; p.scale *= 0.8; p.unitScale = 0.8; p.sy *= 1 - b.sq
            if r.character.gridded { p.roll = b.rot; p.impact = b.sq } else { p.tilt += b.rot }
            if b.surprised > 0 { p.eye.wide = max(p.eye.wide, 1) }
        }
        return p
    }

    // MARK: input

    func crown(_ r: Rig, _ delta: Double) -> Bool {
        guard r.mode == .world, !r.character.staged else { return false }
        // one crown detent is one step back; the crown itself clicks
        r.W.scrubIdle = 0; r.W.scrubTarget = clamp(r.W.scrubTarget - delta * 34, -700, 0)
        return true
    }

    func onCharacter(_ r: Rig, _ pt: CGPoint) -> Bool {
        if let st = r.staged { return st.hit(pt) }
        let p = r.pose()
        let e = extents(r), s = (r.character.gridded ? p.unitScale : p.scale) / 0.8
        return abs(pt.x - p.cx) < e.hx * s + 8 && pt.y > p.cy - e.top * s - 8 && pt.y < p.cy + e.bottom * s + 8
    }

    // MARK: drawing

    private func camera(_ r: Rig, _ ctx: inout GraphicsContext, _ p: Pose) {
        let e = clamp(r.W.zoom, 0, 1), a0 = clamp((e - 0.45) / 0.45, 0, 1), f = 1 + (1 - e) * 0.5, w = r.W.wp
        ctx.opacity *= a0 * a0 * (3 - 2 * a0)
        ctx.translateBy(x: p.cx, y: p.cy); ctx.scaleBy(x: f, y: f); ctx.translateBy(x: -w.cx, y: -w.cy)
    }

    func drawBack(_ r: Rig, _ ctx: GraphicsContext, _ p: Pose) {
        guard worldOn(r) else { return }
        var c = r.ao ? ctx.faded(0.4) : ctx; camera(r, &c, p)          // always on: the world stays, dimmed
        current(r).back(r, c, p)
    }

    func drawFront(_ r: Rig, _ ctx: GraphicsContext, _ p: Pose) {
        if worldOn(r) {
            var c = ctx; camera(r, &c, p)
            current(r).front(r, c, p)
            // one line of fact under the world: the last tool call, or where the crown has taken you
            var text: String?, ta = 1.0
            if r.W.scrubIdle < 3 && abs(r.W.scrub) > 6 {
                let n = Int((-r.W.scrub / 34).rounded())
                if let s = current(r).scrubLabel(r) { text = "\(s), \(n) \(n == 1 ? "step" : "steps") back" }
            } else if (r.state == .working || r.state == .subagents || (r.character.gridded && r.W.labelT < 3)) && !r.W.label.isEmpty && r.caption.isEmpty {
                text = r.W.label; ta = clamp(r.W.labelT / 0.25, 0, 1)
            }
            if let text {
                let e = clamp(r.W.zoom, 0, 1), a0 = clamp((e - 0.45) / 0.45, 0, 1)
                ctx.faded(a0 * ta).draw(Text(text).font(.system(size: 10.5, weight: .medium, design: .monospaced)).foregroundColor(Pal.gray),
                                        at: CGPoint(x: 99, y: 216), anchor: .center)
            }
        }
        for k in r.W.ticks {
            ctx.faded((1 - k.age / 0.5) * 0.7).strokeCircle(k.x, k.y, 4 + k.age * 30, k.strong ? Pal.white : Pal.gray, 1.2)
        }
        for d in r.W.dust { ctx.faded(1 - d.age / 0.6).fillRect(d.x, d.y, 2, 2, Pal.dim) }
        if let L = r.W.launch, !L.world {
            let k = clamp(L.t / 0.3, 0, 1), y = 118 + extents(r).bottom
            ctx.faded(L.t > 1.2 ? max(0, 1 - (L.t - 1.2) / 0.4) : 1).fillRect(99 - 99 * k, y, 198 * k, 1.6, Color(hex: 0x3A3D41))
        }
    }
}

/// An angle brought into −π…π.
func wrapAngle(_ a: Double) -> Double { a - TAU * (a / TAU).rounded() }
