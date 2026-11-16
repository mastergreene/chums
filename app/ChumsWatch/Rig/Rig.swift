import SwiftUI

/// One tool call from a Claude Code session, as the hub reports it.
struct ToolCall: Equatable {
    enum Kind: String { case read, edit, bash, test, agent, stop }
    let id: String
    let kind: Kind
    let text: String
}

/// The character rig: state machine, springs, blinks, saccades, voice, clips, idle variations,
/// touch, crown and gravity. `tick` advances it; `pose` reads it; `ChumsRenderer` draws it.
final class Rig {
    // appearance
    var character: CharacterKind = .fallback { didSet { if character != oldValue { worlds.calm(self); worlds.reset(self); staged?.onState(self, state, prev: nil, variant: variant) } } }
    /// Noodle and Pix draw their own screen.
    var staged: Staged? {
        switch character {
        case .stretch: StretchStage.shared
        case .peeps: PeepsStage.shared
        case .crunch: PixStaged.apple
        case .nana: PixStaged.banana
        case .squint: SquintStage.shared
        case .ray: RayStage.shared
        case .tangle: TangleStage.shared
        case .noodle: NoodleStage.shared
        case .biscuit: BiscuitStage.shared
        default: nil
        }
    }
    var mode: StageMode = .world { didSet { if mode != oldValue { modeChanged() } } }
    var cute = 0.55
    var ao = false
    /// The watch draws its own faces, status, caption, talk bar and approval card on the character's screen; the iPhone
    /// draws those natively around it, so it turns them off here.
    var chrome = true
    /// The approval card's own × and "hold to allow" (the widgets draw their own buttons).
    var cardControls = true
    /// Screen pixels per design point, set by the renderer each frame (Pix draws at the screen's own pixels).
    var pixelScale = 2.0

    // state
    private(set) var state: ChumsState = .idle
    private(set) var variant = false
    private(set) var stateT = 0.0
    var t = 0.0

    /// Where the rig goes when a one-shot (done, dizzy) finishes. The app keeps this current.
    var restState: ChumsState = .idle
    var restVariant = false
    func settle() { if state != restState || variant != restVariant { setState(restState, variant: restVariant) } }
    /// States the rig plays out by itself before settling.
    var inOneShot: Bool { state == .done || state == .dizzy }

    // words on screen, set by the app
    var label = ""
    /// The agents' faces at the top left, the one you talk to first: each one's character, and whether it needs you
    var faces: [(character: String, needs: Bool)] = [(CharacterKind.fallback.rawValue, false)]
    var caption = "" { didSet { if caption != oldValue { captionShown = (state == .speaking) ? 0 : 999 } } }
    var captionShown = 999.0
    /// The caption on screen is cut short (the renderer sets it): a tap there opens the reply in full.
    var captionLong = false
    var subCaption = ""
    var approvalTitle = ""
    var approvalDetail = ""
    var onAllow: (() -> Void)?
    var onDeny: (() -> Void)?
    var onHaptic: ((Haptic) -> Void)?
    enum Haptic { case click, strong, success, failure, notification, start, settle }   // settle: something set down in its place

    // springs
    var S = Springs()
    struct Springs {
        var lift = Spring(0, 12, 0.45), squash = Spring(0, 16, 0.5), tilt = Spring(0, 12, 0.6), scale = Spring(1, 12, 0.7)
        var rays = Spring(0.6, 9, 0.6), spin = Spring(0, 5, 0.9), amber = Spring(0, 10, 0.9)
        var wide = Spring(0, 38, 0.85), happy = Spring(0, 38, 0.85), sleepy = Spring(0, 38, 0.85)
        var worried = Spring(0, 38, 0.85), closed = Spring(0, 38, 0.85), intent = Spring(0, 38, 0.85)
        mutating func stepAll(_ dt: Double) {
            lift.step(dt); squash.step(dt); tilt.step(dt); scale.step(dt); rays.step(dt); spin.step(dt); amber.step(dt)
            wide.step(dt); happy.step(dt); sleepy.step(dt); worried.step(dt); closed.step(dt); intent.step(dt)
        }
    }
    var spinAngle = 0.0

    // eyes
    struct Gaze { var x = 0.0, y = 0.0, fx = 0.0, fy = 0.0, tx = 0.0, ty = 0.0, t0 = 0.0, dur = 0.0, fixLeft = 1.0 }
    var gaze = Gaze()
    struct Blink { var open = 1.0, phase = 0, t = 0.0, next = 2.5, double = false, doubleIn = -1.0 }  // phase 0 idle 1 close 2 hold 3 open
    var blink = Blink()

    // voice: 0…1. The app feeds the microphone level while listening and word pulses while speaking.
    var voice = 0.0
    var voiceInput: Double? = nil
    private var voiceRaw = 0.0, vt = 0.0

    // one-shots
    var clip: Clip?
    struct Particle { var kind: Kind; var x, y, vx, vy, life: Double; var age = 0.0; enum Kind { case spark, heart, sweat, ripple, z } }
    var particles: [Particle] = []
    private var idleBag: [String] = [], idleNext = 10.0
    var idleVar: String?
    private var idleT = 0.0

    // portrait working: three lines of code get written, the eyes read along
    struct Typing { var line = 0, prog = 0.0, pause = 0.3, widths: [Double] = [rnd(0.62, 0.92), rnd(0.4, 0.75), rnd(0.55, 0.95)] }
    var typing = Typing()
    let DOC = (x: 34.0, w: 130.0, y: 176.0, gap: 9.0)

    // touch
    var touch: CGPoint?          // gaze target while a finger is down (normalized −1…1)
    /// While you talk: how far the finger has slid, −1 (onto × to cancel) … 1 (onto ✓ to send); armed past ±0.75
    var talkSlide = 0.0
    static let armed = 0.75
    var holding = false          // finger held on the approval card
    var hold = 0.0

    // world, camera, physics
    let worlds = Worlds()
    var W = WorldState()

    init() { worlds.reset(self) }

    // MARK: state

    /// The state table.
    func spec(_ s: ChumsState) -> StateSpec {
        let st = STATE_SPECS[s]!
        return st
    }

    func setState(_ s: ChumsState, variant v: Bool = false) {
        let prev = state
        state = s; stateT = 0; variant = v; particles = []; hold = 0; clip = nil
        staged?.onState(self, s, prev: prev, variant: v)
        // shaken: thrown in the Physics stage, jolted in place anywhere else; any other state ends it
        if s == .dizzy && staged == nil {
            if mode == .physics { if W.loose == nil { W.loose = WorldState.Loose() } } else if W.jolt == nil { worlds.jolt(self, 2) }
        } else if s != .dizzy { worlds.calm(self) }
        let st = spec(s)
        S.lift.t = st.lift; S.scale.t = st.scale; S.tilt.t = st.tilt; S.rays.t = st.rays; S.spin.t = st.spin
        S.wide.t = st.wide; S.happy.t = st.happy; S.sleepy.t = st.sleepy; S.worried.t = st.worried; S.closed.t = st.closed; S.intent.t = st.intent
        S.amber.t = (s == .needs || s == .approval) ? 1 : 0
        if s == .speaking { captionShown = 0 }
        if s == .done { clip = .jump() }
        if s == .needs { clip = .knock() }
        if s == .working { typing = Typing() }
        if s == .blocked { particles.append(Particle(kind: .sweat, x: 24, y: -26, vx: 0, vy: 0, life: 99)) }
        saccade(st.home.0, st.home.1, ms: 120)
        startBlink()
        worlds.onState(self, s, v)
    }

    private func modeChanged() {
        worlds.calm(self)
        W.zoom = 0; W.zoomV = 0
        if mode == .physics { W.body = WorldState.Body() }
        if mode == .world { worlds.reset(self); startLaunch() }
    }

    // MARK: eyes

    func startBlink(_ double: Bool = false) { blink.phase = 1; blink.t = 0; blink.double = double }

    private func stepBlink(_ dt: Double) {
        blink.t += dt
        if blink.doubleIn > 0 { blink.doubleIn -= dt; if blink.doubleIn <= 0 { startBlink() } }
        switch blink.phase {
        case 1: blink.open = 1 - inC(clamp(blink.t / 0.07, 0, 1)); if blink.t >= 0.07 { blink.phase = 2; blink.t = 0 }
        case 2: blink.open = 0; if blink.t >= 0.03 { blink.phase = 3; blink.t = 0 }
        case 3:
            blink.open = outC(clamp(blink.t / 0.12, 0, 1))
            if blink.t >= 0.12 { blink.phase = 0; if blink.double { blink.doubleIn = 0.14; blink.double = false } }
        default:
            blink.next -= dt
            if blink.next <= 0 {
                // never blink over an expression that owns the eyes
                if S.closed.x < 0.5 && S.happy.x < 0.5 && state != .dizzy { startBlink(Double.random(in: 0..<1) < 0.15) }
                blink.next = rnd(2.5, 6)
            }
        }
    }

    func saccade(_ x: Double, _ y: Double, ms: Double? = nil) {
        gaze.fx = gaze.x; gaze.fy = gaze.y; gaze.tx = clamp(x, -1, 1); gaze.ty = clamp(y, -1, 1); gaze.t0 = 0
        let dist = hypot(gaze.tx - gaze.fx, gaze.ty - gaze.fy) * 6
        gaze.dur = (ms ?? (60 + 8 * dist)) / 1000
        if Double.random(in: 0..<1) < 0.35 && blink.phase == 0 && dist > 2 { startBlink() }
    }

    private func stepGaze(_ dt: Double) {
        gaze.t0 += dt
        let k = gaze.dur > 0 ? minJerk(clamp(gaze.t0 / gaze.dur, 0, 1)) : 1
        gaze.x = lerp(gaze.fx, gaze.tx, k); gaze.y = lerp(gaze.fy, gaze.ty, k)
        let st = STATE_SPECS[state]!
        if let f = touch {
            if hypot(f.x - gaze.tx, f.y - gaze.ty) > 0.05 { saccade(f.x, f.y, ms: 90) }
            gaze.fixLeft = 0.25; return
        }
        gaze.fixLeft -= dt
        if gaze.fixLeft > 0 { return }
        switch state {
        case .thinking:
            let pts: [(Double, Double)] = [(0.7, -0.55), (0, 0), (-0.7, -0.55), (0, 0)]
            let p = pts[Int(stateT / 0.55) % 4]; saccade(p.0, p.1); gaze.fixLeft = 0.55
        case .approval:
            saccade(0, Int(stateT / 0.7) % 2 == 0 ? 0.8 : 0); gaze.fixLeft = 0.7
        case .subagents:
            saccade([-0.85, 0, 0.85][Int(stateT / 0.85) % 3], 1); gaze.fixLeft = 0.85
        case .working:
            let x = clamp((caretX() - 99) / 62, -1, 1)
            saccade(x, 0.75 + Double(typing.line) * 0.1, ms: typing.prog < 0.05 ? 110 : 45)
            gaze.fixLeft = typing.pause > 0 ? 0.3 : 0.16
        case .listening, .dizzy, .sleep:
            saccade(st.home.0, st.home.1); gaze.fixLeft = 1
        default:
            let (lo, hi) = st.fix ?? (1, 2)
            gaze.fixLeft = rnd(lo, hi)
            let back = state == .speaking ? 0.7 : 0.55
            if Double.random(in: 0..<1) < back {
                saccade(st.home.0 + rnd(-0.05, 0.05), st.home.1 + rnd(-0.05, 0.05))
            } else {
                let a = rnd(0, TAU), r = clamp(0.3 + abs(gauss()) * 1.2, 0.3, 0.9)
                saccade(st.home.0 + cos(a) * r, st.home.1 + sin(a) * r * 0.7)
            }
        }
    }

    // MARK: voice, typing

    private func stepVoice(_ dt: Double, active: Bool) {
        vt += dt
        if let v = voiceInput, active { voiceRaw = clamp(v, 0, 1) }
        else {
            let syll = max(0, sin(vt * 13.5) * 0.6 + sin(vt * 5.1) * 0.4), phrase = (sin(vt * 1.3) * 0.5 + 0.5) > 0.2 ? 1.0 : 0.1
            voiceRaw = active ? clamp(syll * phrase * rnd(0.7, 1), 0, 1) : 0
        }
        let k = voiceRaw > voice ? 1 - exp(-dt / 0.06) : 1 - exp(-dt / 0.18)
        voice += (voiceRaw - voice) * k
    }

    private func stepTyping(_ dt: Double) {
        if typing.pause > 0 {
            typing.pause -= dt
            if typing.pause <= 0 && typing.prog >= 1 {
                typing.line += 1; typing.prog = 0
                if typing.line > 2 { typing = Typing(); typing.pause = 0.25 }
            }
            return
        }
        typing.prog = min(1, typing.prog + dt * rnd(0.7, 1.3) / (typing.widths[typing.line] * 1.6))
        if typing.prog >= 1 { typing.pause = 0.45 }
    }
    func caretX() -> Double { DOC.x + DOC.w * typing.widths[min(typing.line, 2)] * typing.prog }

    // MARK: frame

    func tick(_ dt0: Double) {
        let dt = min(dt0, 1.0 / 20)                   // a dropped frame never teleports the rig
        t += dt; stateT += dt
        trauma = max(0, trauma - dt * 0.75)
        staged?.step(self, dt)
        let st = spec(state)
        stepBlink(dt); stepGaze(dt)
        stepVoice(dt, active: state == .listening || state == .speaking)

        // breathing
        let breathP = state == .sleep ? 5.0 : 4.0, breath = sin(t * TAU / breathP) * (state == .sleep ? 0.04 : 0.03)
        S.scale.t = st.scale * (1 + breath * lerp(0.6, 1, cute))
        S.rays.t = st.rays * (1 + breath * 1.5)

        if state == .listening { S.rays.t = 0.85 + voice * 0.4; S.scale.t = 1.08 * (1 + voice * 0.04) }
        if state == .speaking {
            S.squash.t = voice * 0.04; S.lift.t = 2 + voice * 1.5; S.rays.t = 0.7 + voice * 0.15
            captionShown = min(Double(caption.count), captionShown + dt * 24)
            if voiceRaw < 0.05 && voice > 0.3 && Double.random(in: 0..<1) < 0.02 { startBlink() }
        } else if state != .blocked && state != .sleep { S.squash.t = 0 }
        if state == .working { stepTyping(dt); S.lift.t = st.lift + (typing.pause <= 0 && Int(stateT * 9) % 2 == 1 ? 1.4 : 0) }
        if state == .subagents { S.tilt.t = sin(stateT * 1.6) * 2 * DEG }
        if state == .blocked { S.squash.t = -0.08 }
        if state == .sleep { S.squash.t = -0.1 }
        if state == .dizzy && W.loose == nil && W.jolt == nil {
            // staged characters play their own dizzy; the rig's own characters are thrown loose (Worlds.loose)
            S.tilt.t = sin(stateT * TAU * 1.25) * 16 * DEG * max(0, 1 - stateT / 2)
            S.spin.t = 360 * max(0, 1 - stateT / 2)
            if stateT > 2 && clip == nil { clip = .shakeOff() }
            if stateT > (staged?.holds[.dizzy] ?? 2.6) { settle(); startBlink() }
        }
        if W.loose != nil || W.jolt != nil { S.tilt.t = 0; S.spin.t = 0 }
        if state == .done && stateT > (staged?.holds[.done] ?? 2.4) && !W.busy { settle() }
        if state == .needs && clip == nil && stateT > 3.2 { clip = .knock(); stateT = 0.01 }
        if state == .approval {
            hold = holding ? min(1, hold + dt / 1.0) : max(0, hold - dt * 1.6)
            if hold >= 1 { holding = false; hold = 0; onHaptic?(.success); onAllow?() }
        }
        if state == .done && stateT > 0.38 && stateT - dt <= 0.38 {
            for i in 0..<5 {
                let a = -Double.pi / 2 + Double(i - 2) * 0.55
                particles.append(Particle(kind: .spark, x: cos(a) * 50, y: sin(a) * 42, vx: cos(a) * 26, vy: sin(a) * 26, life: 0.8))
            }
            if variant { particles.append(Particle(kind: .heart, x: 0, y: -58, vx: 0, vy: -24, life: 1.4)) }
        }
        if state == .sleep && Double.random(in: 0..<1) < dt * 0.6 { particles.append(Particle(kind: .z, x: 22, y: -30, vx: 5, vy: -11, life: 2.6)) }

        // idle variations from a shuffle bag, never the same twice in a row
        if state == .idle {
            idleNext -= dt
            if idleVar == nil && idleNext <= 0 {
                if idleBag.isEmpty { idleBag = ["stretch", "look", "wiggle", "hop"].shuffled() }
                idleVar = idleBag.removeLast(); idleT = 0; idleNext = rnd(9, 18)
                if idleVar == "hop" { clip = .hop(6) }
                if idleVar == "look" { saccade(-0.8, -0.2); gaze.fixLeft = 0.7 }
            }
            if let v = idleVar {
                idleT += dt
                if v == "stretch" { S.rays.t = idleT < 1 ? 1.1 : 0.6; S.lift.t = idleT < 1 ? 3 : 0; S.squash.t = idleT < 1 ? 0.08 : 0 }
                if v == "look" && idleT > 0.7 && idleT - dt <= 0.7 { saccade(0.8, -0.2); gaze.fixLeft = 0.7 }
                if v == "wiggle" { S.spin.t = idleT < 1.2 ? 60 : 0 }
                if idleT > 1.8 { idleVar = nil }
            }
        } else { idleVar = nil }

        if var c = clip { c.t += dt; clip = c.t >= c.dur ? nil : c }

        worlds.step(self, dt)
        S.stepAll(dt)
        spinAngle += S.spin.x * DEG * dt
        for i in particles.indices {
            particles[i].age += dt; particles[i].x += particles[i].vx * dt; particles[i].y += particles[i].vy * dt
            if particles[i].kind == .spark { particles[i].vy += 40 * dt }
        }
        particles.removeAll { $0.age >= $0.life }
    }


    // MARK: pose

    func pose(raw: Bool = false) -> Pose {
        let c = clip?.frame() ?? ClipFrame()
        var p = Pose(t: t)
        p.cx = 99; p.cy = 112 - (S.lift.x + c.dy)
        p.sy = (1 + S.squash.x) * c.sy
        p.tilt = S.tilt.x + c.tilt
        p.scale = S.scale.x * c.sc
        p.c = cute
        p.eye.open = blink.open * (1 - S.closed.x); p.eye.closed = S.closed.x; p.eye.wide = S.wide.x; p.eye.happy = S.happy.x
        p.eye.sleepy = S.sleepy.x; p.eye.worried = S.worried.x; p.eye.intent = S.intent.x
        p.eye.spiral = state == .dizzy && W.jolt == nil && (W.loose != nil || stateT < 2.1)
        p.eye.gx = gaze.x; p.eye.gy = gaze.y
        p.eye.tremorX = sin(t * TAU * 0.3) * 0.5; p.eye.tremorY = cos(t * TAU * 0.23) * 0.5
        p.pulse = state == .listening ? voice : state == .speaking ? voice * 0.45 : 0
        p.amber = S.amber.x
        p.ao = ao
        return raw ? p : worlds.apply(self, p)
    }

    // MARK: input

    /// A finger went down at a point in design space (198 × 242).
    func touchDown(_ pt: CGPoint) -> TouchTarget {
        touch = CGPoint(x: clamp((pt.x - 99) / 70, -1, 1), y: clamp((pt.y - 112) / 90, -1, 1))
        if state == .approval {
            if let st = staged, st.ownsApproval {                  // Noodle balances the card on its hair: anywhere holds it but ×
                if hypot(pt.x - 36, pt.y - 210) < 18 { return .deny }
                holding = true; return .card
            }
            if worlds.worldOn(self) { holding = true; return .card }
            if pt.y > 196 && pt.x < 60 { return .deny }
            if pt.y > 112 && pt.y < 196 { holding = true; return .card }
            return .none
        }
        if worlds.onCharacter(self, pt) { return .character }
        return pt.y > 145 ? .talkZone : .none
    }
    enum TouchTarget { case none, character, talkZone, card, deny }

    func touchMoved(_ pt: CGPoint) {
        touch = CGPoint(x: clamp((pt.x - 99) / 70, -1, 1), y: clamp((pt.y - 112) / 90, -1, 1))
    }

    func touchUp() {
        touch = nil
        holding = false
    }

    /// A short tap on the character.
    func poke() {
        if let st = staged { st.poke(self); onHaptic?(.click); return }
        let now = t
        W.pokes = W.pokes.filter { now - $0 < 3 } + [now]
        if mode == .physics { W.body.vy -= 320; W.body.vx += rnd(-70, 70) } else { clip = .hop(6) }
        startBlink(); onHaptic?(.click)
        if W.pokes.count >= 5 { W.pokes = []; S.worried.t = 0.7; W.annoyedT = 0.9 }
    }

    /// Stroking across the character.
    func pet(_ distance: Double) {
        staged?.pet(self)
        W.petDist += distance
        if W.petDist > 40 { W.petDist = 0; onHaptic?(.click) }
        S.happy.t = 1; S.squash.t = -0.06
    }
    func petEnded() { S.happy.t = STATE_SPECS[state]!.happy; S.squash.t = 0 }

    /// The Digital Crown turned by `delta` (in the crown's own units).
    func crown(_ delta: Double) {
        if let st = staged { st.crown(self, delta); return }
        if worlds.crown(self, delta) { return }
        W.crownAcc += delta; spinAngle += delta * 0.4
        saccade(delta > 0 ? 0.8 : -0.8, 0, ms: 60); gaze.fixLeft = 0.6
        if abs(W.crownAcc) > 0.4 { W.crownAcc = 0; onHaptic?(.click) }
    }

    /// The pixel level is a game. Is it on screen and taking input; has it had input lately; a tap there (a jump).
    var levelLive: Bool { false }
    var levelPlaying: Bool { false }
    func levelPress() -> Bool { false }

    /// The watch's own acceleration, in g on the screen's axes (x right, y down, z toward you), about 50 times a second.
    /// A sharp move shakes the character: in the Physics stage it's thrown the other way (it stays put while the watch
    /// moves); in Portrait and the level it's jolted where it stands. Staged characters get every move (Noodle sloshes
    /// with them) and a hard shake makes them dizzy.
    private var lastKick = -9.0
    /// How hard it's being shaken, 0…1, the same measure as Noodle's: the square of each move over 0.9 g piles up, and
    /// drains in about a second. A hard shake jolts harder and leaves it dazed longer.
    private(set) var trauma = 0.0
    func motion(_ ax: Double, _ ay: Double, _ az: Double) {
        guard !ao else { return }
        staged?.motion(self, ax, ay, az)
        let g = sqrt(ax * ax + ay * ay + az * az)
        trauma = min(1, trauma + max(0, g - 0.9) * max(0, g - 0.9) * 0.03)
        guard state != .approval && state != .listening, t - lastKick > 0.16 else { return }
        if staged != nil {
            if g > 2.2 { lastKick = t; if state != .dizzy || stateT > 3 { setState(.dizzy) } }
            return
        }
        // a first shake has to be sharp; once it's going, smaller ones keep it going
        if mode == .physics {
            guard g > (W.loose?.phase == .fly ? 1.35 : 1.6) else { return }
            lastKick = t
            let h = hypot(ax, ay)
            let (dx, dy) = h > 0.3 ? (-ax / h, -ay / h) : (cos(rnd(0, TAU)), sin(rnd(0, TAU)))   // along z: any way
            if g < 2.6 && W.loose == nil { worlds.kickBody(self, dx, dy, g); return }       // a shove
            if state != .dizzy { setState(.dizzy) }
            worlds.kick(self, dx, dy, g)
        } else {
            // Portrait and the level: jolted where it stands (the game doesn't move)
            guard g > (W.jolt != nil ? 1.35 : 1.9) else { return }
            lastKick = t
            if state != .dizzy { setState(.dizzy) } else { worlds.jolt(self, g) }
        }
    }

    func startLaunch() { worlds.startLaunch(self) }

    /// Tool calls from the hub, in order. Each world turns them into its own landmark.
    func push(_ call: ToolCall) { worlds.queue.append(call) }
}
