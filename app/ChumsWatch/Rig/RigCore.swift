import SwiftUI
@_exported import simd                          // vector maths for every rig (WatchKit brings it on the watch; the iPhone needs it said)

// Shared math, springs, palette and the state table. The rig is a port of the Character Lab, the HTML prototype the
// characters were designed in (not in the public repository): same parameters, same springs, same timings. Everything is drawn in a 198 × 242 pt design space
// (the 45 mm screen) and scaled to the real screen.

let TAU = Double.pi * 2
let DEG = Double.pi / 180
let DESIGN = CGSize(width: 198, height: 242)

@inline(__always) func clamp(_ v: Double, _ a: Double, _ b: Double) -> Double { max(a, min(b, v)) }
@inline(__always) func lerp(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
@inline(__always) func inQ(_ t: Double) -> Double { t * t }
@inline(__always) func outQ(_ t: Double) -> Double { 1 - (1 - t) * (1 - t) }
@inline(__always) func inC(_ t: Double) -> Double { t * t * t }
@inline(__always) func outC(_ t: Double) -> Double { 1 - pow(1 - t, 3) }
@inline(__always) func minJerk(_ t: Double) -> Double { t * t * t * (10 - 15 * t + 6 * t * t) }
@inline(__always) func ease3(_ k: Double) -> Double { k < 0.5 ? 4 * k * k * k : 1 - pow(-2 * k + 2, 3) / 2 }
@inline(__always) func rnd(_ a: Double, _ b: Double) -> Double { Double.random(in: a...b) }
func gauss() -> Double { sqrt(-2 * log(Double.random(in: 0..<1) + 1e-9)) * cos(TAU * Double.random(in: 0..<1)) * 0.35 }

/// A damped spring toward `t`: ω is stiffness in rad/s, ζ the damping ratio. Two half steps per frame.
struct Spring {
    var x: Double, v: Double = 0, t: Double
    let w: Double, z: Double
    init(_ x: Double, _ w: Double, _ z: Double) { self.x = x; self.t = x; self.w = w; self.z = z }
    mutating func step(_ dt: Double) {
        for _ in 0..<2 {
            let h = dt / 2
            v += (w * w * (t - x) - 2 * z * w * v) * h
            x += v * h
        }
    }
}

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255, opacity: alpha)
    }
    static func gray(_ v: Double) -> Color { Color(.sRGB, red: v, green: min(1, v + 0.008), blue: min(1, v + 0.02), opacity: 1) }
}

enum Pal {
    static let clay = Color(hex: 0xD97757), coral = Color(hex: 0xD77757), ink = Color(hex: 0x1E130F)
    static let white = Color(hex: 0xF4F5F6), gray = Color(hex: 0x8A8F95), dim = Color(hex: 0x4A4E53)
    static let amber = Color(hex: 0xFFB020), card = Color(hex: 0x131517), sweat = Color(hex: 0x7FB3E6)
    static let heart = Color(hex: 0xF2707A), green = Color(hex: 0x3DD68C), red = Color(hex: 0xE5484D)
}

enum ChumsState: String, CaseIterable {
    case idle, listening, thinking, working, subagents, speaking, done, needs, approval, blocked, dizzy, sleep
}

enum CharacterKind: String, CaseIterable, Identifiable {
    case peeps, stretch, crunch, nana, squint, ray, tangle, noodle, biscuit
    var name: String { ["peeps": "Peeps", "stretch": "Stretch", "crunch": "Crunch", "nana": "Nana", "squint": "Squint", "ray": "Ray", "tangle": "Tangle", "noodle": "Noodle", "biscuit": "Biscuit"][rawValue]! }
    var gridded: Bool { false }
    static let fallback = CharacterKind.noodle
    var id: String { rawValue }
    /// A character by its id, or by the id it had before the October 2026 rename where that id isn't taken again
    /// ("noodle" was Stretch's and is now the tube's: what was stored under it was moved once, `migrate`).
    init?(id: String) { self.init(rawValue: CharacterKind.legacy[id] ?? id) }
    static let legacy = ["eyes": "peeps", "apple": "crunch", "banana": "nana", "kao": "squint", "sol": "ray", "scribble": "tangle", "coil": "noodle"]
    /// The character stored on the watch, under its new id: once.
    static func migrate() {
        let d = UserDefaults.standard
        guard !d.bool(forKey: "renamed2026") else { return }
        if let c = d.string(forKey: "character") { d.set(c == "noodle" ? "stretch" : legacy[c] ?? c, forKey: "character") }
        d.set(true, forKey: "renamed2026")
    }
    /// Draws its own whole screen (rendered frames, its own pixels) instead of the shared rig and a world.
    var staged: Bool { !gridded }
    /// Drawn from rendered frames or its own pixels, at most 24 times a second: no need to draw it 60 times.
    var framed: Bool { self == .stretch || self == .crunch || self == .nana }
}

enum StageMode: String, CaseIterable, Identifiable {
    case portrait, world, physics
    var id: String { rawValue }
    var name: String { ["portrait": "Portrait", "world": "World", "physics": "Physics"][rawValue]! }
    /// Each character's world has its own name.
    func name(for k: CharacterKind) -> String { self == .world ? (k.gridded ? "Level" : k == .peeps ? "Page" : "World") : name }
    /// What the stage is, for the settings footer.
    func about(for k: CharacterKind) -> String {
        switch self {
        case .portrait: return "\(k.name) up close, with nothing around it."
        case .physics: return "Tilt the watch and \(k.name) slides and falls with it. Drag it and throw it; shake the watch to send it flying."
        case .world:
            return k.gridded ? "A pixel level: each tool call is a block. Turn the crown to walk, tap to jump."
                : "A page of code: Peeps reads it, and tool calls change it as they happen."
        }
    }
}

/// Body targets (k) and lid targets (e) per state, plus gaze fixation range and home.
struct StateSpec {
    var lift = 0.0, scale = 1.0, tilt = 0.0, rays = 0.6, spin = 0.0
    var wide = 0.0, happy = 0.0, sleepy = 0.0, worried = 0.0, closed = 0.0, intent = 0.0
    var fix: (Double, Double)? = nil
    var home: (Double, Double) = (0, 0)
}

let STATE_SPECS: [ChumsState: StateSpec] = [
    .idle: StateSpec(fix: (0.6, 2.4)),
    .listening: StateSpec(lift: -24, scale: 1.08, rays: 0.85, wide: 1, home: (0, 0.15)),      // lower: the talk bar is above
    .thinking: StateSpec(tilt: -6 * DEG, rays: 0.7, spin: 40),
    .working: StateSpec(lift: 10, scale: 0.9, intent: 1, home: (0, 0.7)),
    .subagents: StateSpec(lift: 18, scale: 0.78, intent: 0.4, home: (0, 0.8)),
    .speaking: StateSpec(lift: 2, scale: 0.92, rays: 0.7, fix: (0.8, 2)),
    .done: StateSpec(rays: 0.9, happy: 1, fix: (1, 2), home: (0, -0.1)),
    .needs: StateSpec(lift: 10, scale: 1.02, tilt: 8 * DEG, rays: 0.8, wide: 1, fix: (1.2, 2.4)),
    .approval: StateSpec(lift: 42, scale: 0.62, rays: 0.5, home: (0, 0.6)),
    .blocked: StateSpec(lift: -5, scale: 0.96, rays: 0.45, worried: 1, fix: (1.5, 3), home: (0, 0.3)),
    .dizzy: StateSpec(rays: 0.7, spin: 360),
    .sleep: StateSpec(lift: -8, scale: 0.94, tilt: 6 * DEG, rays: 0.3, closed: 1),
]

/// A one-shot, additive animation: dy in pt upward, sy vertical scale, optional tilt and whole-body scale.
struct ClipFrame { var dy = 0.0, sy = 1.0, tilt = 0.0, sc = 1.0 }
struct Clip {
    var t = 0.0
    let dur: Double
    let eval: (Double) -> ClipFrame

    static func jump() -> Clip {
        Clip(dur: 0.91) { t in
            if t < 0.12 { return ClipFrame(sy: lerp(1, 0.8, outQ(t / 0.12))) }
            if t < 0.38 { let k = (t - 0.12) / 0.26; return ClipFrame(dy: 22 * outQ(k), sy: lerp(1.18, 1, k)) }
            if t < 0.58 { let k = (t - 0.38) / 0.2; return ClipFrame(dy: 22 * (1 - inQ(k)), sy: lerp(1, 1.1, k)) }
            if t < 0.66 { return ClipFrame(sy: 0.78) }
            let k = (t - 0.66) / 0.25
            return ClipFrame(sy: 1 + 0.06 * sin(k * .pi) * (1 - k))
        }
    }
    static func hop(_ h: Double = 6) -> Clip {
        Clip(dur: 0.42) { t in
            if t < 0.08 { return ClipFrame(sy: lerp(1, 0.9, t / 0.08)) }
            let k = (t - 0.08) / 0.34
            return ClipFrame(dy: h * sin(k * .pi), sy: k > 0.9 ? 0.92 : 1.05 - 0.05 * k)
        }
    }
    static func shakeOff() -> Clip {
        Clip(dur: 0.5) { t in ClipFrame(tilt: 8 * DEG * sin(t * 40) * (1 - t / 0.5)) }
    }
    /// Shaking the daze off: a few quick head shakes, bigger and longer than the dizzy one's.
    static func shakeOffHard() -> Clip {
        Clip(dur: 0.6) { t in ClipFrame(tilt: 12 * DEG * sin(t * 42) * (1 - t / 0.6)) }
    }
    /// Two quick bumps toward you, like knocking on the glass.
    static func knock() -> Clip {
        Clip(dur: 1.1) { t in
            func bump(_ k: Double) -> Double { max(0, 1 - abs(t - k) / 0.09) }
            return ClipFrame(sc: 1 + 0.09 * max(bump(0.12), bump(0.34)))
        }
    }
    func frame() -> ClipFrame { eval(min(t, dur)) }
}

struct EyePose {
    var open = 1.0, closed = 0.0, wide = 0.0, happy = 0.0, sleepy = 0.0, worried = 0.0, intent = 0.0
    var spiral = false
    var wince = false                     // shut tight (a flinch): the closed line arches up instead of resting down
    var gx = 0.0, gy = 0.0, tremorX = 0.0, tremorY = 0.0

    /// Eyes for a small copy of a character: open, neutral, looking where it's told.
    static func neutral(gx: Double, gy: Double, intent: Double = 0.5) -> EyePose {
        var e = EyePose(); e.gx = gx; e.gy = gy; e.intent = intent; return e
    }
}

enum GridArms { case rest, raised }
enum GridLegs { case stand, stepA, stepB, crouch, tap, tucked }

/// Everything a character needs to draw one frame.
struct Pose {
    var t: Double
    var cx = 99.0, cy = 112.0, sy = 1.0, tilt = 0.0, scale = 1.0, c = 0.55
    var eye = EyePose()
    var pulse = 0.0, amber = 0.0
    var ao = false
    var unitScale = 1.0
    var world = 0.0
    var legs: GridLegs? = nil
    var arms: GridArms? = nil
    var mini = false
    var phase = 0.0
    var tint: Color? = nil
    var faceRot = 0.0
    var walking = false
    var awake = false                     // in a pixel level: asleep, but up to play
    var fade = 0.0
    // shaken loose (Worlds.loose): a gridded character's spin, its squash on a hit (+ against a floor or ceiling, − against a side),
    // its eyes (1 squeezed shut >_<, 2 spinning) and the stars circling its head (0…1)
    var roll = 0.0, impact = 0.0
    var daze = 0
    var stars = 0.0
}

/// Changes a world may apply to the pose once the camera is mostly wide.
struct PoseOverride {
    var legs: GridLegs? = nil
    var arms: GridArms? = nil
    var tilt: Double? = nil
    var open: Double? = nil
    var gx: Double? = nil, gy: Double? = nil
    var happy: Double? = nil, intent: Double? = nil, sleepy: Double? = nil, wide: Double? = nil
    var closed: Double? = nil, awake: Bool? = nil
}

struct WorldPose {
    var cx: Double, cy: Double, scale: Double, unitScale: Double = 1
    var o = PoseOverride()
}

/// Pixel art on a grid of `s` pt cells: rows of "X" and ".".
func pixArtPath(_ art: [String], x: Double, y: Double, s: Double) -> Path {
    var p = Path()
    for (j, row) in art.enumerated() {
        for (i, ch) in row.enumerated() where ch == "X" {
            p.addRect(CGRect(x: (x + Double(i) * s).rounded(), y: (y + Double(j) * s).rounded(), width: ceil(s), height: ceil(s)))
        }
    }
    return p
}

extension GraphicsContext {
    func fillRect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ c: Color) {
        fill(Path(CGRect(x: x, y: y, width: w, height: h)), with: .color(c))
    }
    func fillCircle(_ x: Double, _ y: Double, _ r: Double, _ c: Color) {
        fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: .color(c))
    }
    func strokeCircle(_ x: Double, _ y: Double, _ r: Double, _ c: Color, _ w: Double) {
        stroke(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: .color(c), lineWidth: w)
    }
    func strokeLine(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ c: Color, _ w: Double, dash: [CGFloat] = [], phase: Double = 0) {
        var p = Path(); p.move(to: CGPoint(x: x0, y: y0)); p.addLine(to: CGPoint(x: x1, y: y1))
        stroke(p, with: .color(c), style: StrokeStyle(lineWidth: w, lineCap: .round, dash: dash, dashPhase: phase))
    }
    func fillRoundRect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double, _ c: Color) {
        fill(Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r), with: .color(c))
    }
    /// A copy of the context with its opacity multiplied, for scoped alpha.
    func faded(_ a: Double) -> GraphicsContext { var c = self; c.opacity *= a; return c }
}
