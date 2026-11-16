// Written by design/characters/fetch/build.py from its FRONT numbers and motion-front.json. Change those, not this file.

/// Biscuit facing the screen: its shapes and the keys of its 12 states (design/characters/fetch/SPEC-front.md).
/// Lengths are points on the 396 × 484 design canvas, twice the rig's 198 × 242. Circles are [x, y, r].
enum BiscuitArt {
    static let near: UInt32 = 0xF28A2E, far: UInt32 = 0xB45F1B
    static let ground = 418.0, centre = 188.0
    static let haunch: [[Double]] = [[140, 362, 56], [236, 362, 56]]
    static let chest: [[Double]] = [[188, 290, 48], [188, 334, 46]]
    static let neck: [[Double]] = [[188, 262, 40], [188, 226, 36]]
    static let head: [[Double]] = [[160, 184, 52], [216, 184, 52]]
    /// The screen-left ear, base then tip; the right one is its mirror image about `centre`.
    static let ear: [[Double]] = [[136, 148, 20], [116, 92, 4]]
    static let chin: [Double] = [188, 236]
    /// The eyes: how far each is from `centre`, their y, their radius.
    static let eye: [Double] = [28, 184, 7]
    static let nose: [Double] = [188, 203, 5]
    static let legWidth = 42.0, legGap = 4.0, legTop = 304.0, legRadius = 20.0, shoulderY = 316.0
    /// The tail's centre line (a quadratic curve), and its width at the base and at the tip.
    static let tail: [[Double]] = [[254, 398], [346, 404], [326, 296]]
    static let tailWidth: [Double] = [20, 16]
    static let flock: [[Double]] = [[22, 404], [44, 404], [66, 404]]

    /// What the keys move, in the order of `names` and `rest`.
    enum Ch: Int, CaseIterable { case rootX, rootY, rootRot, rootSx, rootSy, headRot, headX, headY, earLRot, earRRot, eyeSy, eyeX, eyeY, tailRot, pawLRot, pawLY, pawRRot, pawRY, dim, flockOn, f1Y, f2Y, f3Y }
    static let names: [String] = ["root.x", "root.y", "root.rot", "root.sx", "root.sy", "head.rot", "head.x", "head.y", "earL.rot", "earR.rot", "eye.sy", "eye.x", "eye.y", "tail.rot", "pawL.rot", "pawL.y", "pawR.rot", "pawR.y", "dim", "flock.on", "f1.y", "f2.y", "f3.y"]
    static let rest: [Double] = [0, 0, 0, 1, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0]

    /// The keys, as JSON: for each state its length in frames (24 a second), how it's entered, and per channel
    /// [frame, value, ease] (no ease: "inout").
    static let motion = #"""
{"states":{
"idle":{"frames":60,"enter":{"frames":10,"ease":"inout"},"tracks":{"root.sy":[[0,1],[30,1.018],[60,1]],"head.rot":[[0,-4],[30,4],[60,-4]],"tail.rot":[[0,0],[6,0,"hold"],[10,-14,"out"],[20,6],[30,0],[34,-8,"out"],[44,3],[60,0]],"earR.rot":[[0,0],[36,0,"hold"],[38,16,"out"],[46,0,"spring"],[60,0,"hold"]],"eye.sy":[[0,1],[50,1,"hold"],[52,0.12,"in"],[55,1,"out"],[60,1]]}},
"listening":{"frames":36,"enter":{"frames":5,"ease":"over"},"tracks":{"root.sy":[[0,1.03],[36,1.03]],"head.y":[[0,-10],[36,-10]],"head.rot":[[0,11],[14,11,"hold"],[18,-11,"over"],[32,-11,"hold"],[36,11,"over"]],"earL.rot":[[0,8],[16,8,"hold"],[24,14,"spring"],[34,14,"hold"],[36,8]],"earR.rot":[[0,-14],[16,-14,"hold"],[24,-8,"spring"],[34,-8,"hold"],[36,-14]],"tail.rot":[[0,-12],[36,-12]]}},
"thinking":{"frames":48,"enter":{"frames":10,"ease":"inout"},"tracks":{"head.rot":[[0,9],[20,9,"hold"],[26,-9],[42,-9,"hold"],[48,9]],"head.y":[[0,-3],[48,-3]],"eye.x":[[0,-3],[20,-3,"hold"],[26,3],[42,3,"hold"],[48,-3]],"eye.y":[[0,-3],[48,-3]],"earL.rot":[[0,-34],[22,-34,"hold"],[30,0,"spring"],[44,0,"hold"],[48,-34,"out"]],"earR.rot":[[0,0],[22,0,"hold"],[30,34,"spring"],[44,34,"hold"],[48,0,"out"]],"tail.rot":[[0,0],[3,10,"out"],[6,0],[9,10,"out"],[12,0],[15,10,"out"],[18,0],[28,0,"hold"],[31,10,"out"],[34,0],[37,10,"out"],[40,0],[48,0,"hold"]]}},
"working":{"frames":24,"enter":{"frames":8,"ease":"out"},"tracks":{"head.y":[[0,8],[24,8]],"eye.y":[[0,3],[24,3]],"pawL.y":[[0,0],[3,-10,"out"],[6,0,"in"],[12,0,"hold"],[15,-10,"out"],[18,0,"in"],[24,0,"hold"]],"pawR.y":[[0,0],[6,0,"hold"],[9,-10,"out"],[12,0,"in"],[18,0,"hold"],[21,-10,"out"],[24,0,"in"]],"root.rot":[[0,2],[6,-2],[12,2],[18,-2],[24,2]],"head.rot":[[0,-3],[6,3],[12,-3],[18,3],[24,-3]],"earL.rot":[[0,-6],[24,-6]],"earR.rot":[[0,6],[24,6]],"tail.rot":[[0,-12],[6,12],[12,-12],[18,12],[24,-12]],"eye.sy":[[0,1],[18,1,"hold"],[20,0.12,"in"],[22,1,"out"],[24,1]]}},
"subagents":{"frames":48,"enter":{"frames":8,"ease":"over"},"tracks":{"flock.on":[[0,1],[48,1]],"f1.y":[[0,0],[4,-14,"out"],[8,0,"in"],[48,0,"hold"]],"f2.y":[[0,0],[12,0,"hold"],[16,-14,"out"],[20,0,"in"],[48,0,"hold"]],"f3.y":[[0,0],[24,0,"hold"],[28,-14,"out"],[32,0,"in"],[48,0,"hold"]],"head.rot":[[0,-10],[24,-14],[48,-10]],"head.y":[[0,4],[48,4]],"eye.x":[[0,-4],[48,-4]],"eye.y":[[0,3],[48,3]],"pawL.rot":[[0,0],[8,0,"hold"],[12,34,"over"],[18,0,"in"],[32,0,"hold"],[36,22,"over"],[42,0,"in"],[48,0,"hold"]],"earL.rot":[[0,6],[48,6]],"earR.rot":[[0,-4],[48,-4]],"tail.rot":[[0,0],[6,-14],[18,14],[30,-14],[42,14],[48,0]]}},
"speaking":{"frames":24,"enter":{"frames":6,"ease":"out"},"tracks":{"head.y":[[0,0],[3,5,"out"],[6,0],[9,3,"out"],[12,0],[15,6,"out"],[19,0],[21,3,"out"],[24,0]],"head.rot":[[0,-4],[3,4,"out"],[9,-4,"out"],[15,4,"out"],[21,-4,"out"],[24,-4]],"earL.rot":[[0,0],[5,-8],[8,0],[11,-5],[14,0],[17,-9],[21,0],[24,0]],"earR.rot":[[0,0],[5,8],[8,0],[11,5],[14,0],[17,9],[21,0],[24,0]],"root.sy":[[0,1],[3,1.012,"out"],[8,1],[12,1,"hold"],[15,1.012,"out"],[20,1],[24,1]],"tail.rot":[[0,-4],[12,6],[24,-4]]}},
"needs":{"frames":48,"enter":{"frames":8,"ease":"over"},"tracks":{"pawR.rot":[[0,0],[2,4],[8,-138,"over"],[11,-118],[14,-138,"out"],[17,-118],[20,-138,"out"],[23,-118],[26,-138,"out"],[36,-138,"hold"],[42,0,"in"],[48,0,"hold"]],"head.rot":[[0,0],[8,10,"over"],[36,10,"hold"],[44,0],[48,0]],"root.y":[[0,0],[28,0,"hold"],[31,-8,"out"],[34,0,"in"],[48,0,"hold"]],"root.sy":[[0,1],[26,1,"hold"],[28,0.96],[31,1.03,"out"],[34,0.96,"in"],[39,1,"over"],[48,1]],"earL.rot":[[0,0],[8,8,"over"],[36,8,"hold"],[44,0],[48,0]],"earR.rot":[[0,0],[8,-8,"over"],[36,-8,"hold"],[44,0],[48,0]],"tail.rot":[[0,0],[6,-10],[18,10],[30,-10],[42,10],[48,0]]}},
"approval":{"frames":48,"enter":{"frames":4,"ease":"out"},"tracks":{"root.sy":[[0,0.95],[48,0.95]],"head.y":[[0,6],[48,6]],"eye.sy":[[0,1.2],[48,1.2]],"earL.rot":[[0,10],[48,10]],"earR.rot":[[0,-10],[48,-10]],"root.x":[[0,0],[26,0,"hold"],[28,-4],[30,4],[32,-4],[34,4],[36,-4],[38,4],[40,0],[48,0,"hold"]],"root.rot":[[0,0],[26,0,"hold"],[28,2],[30,-2],[32,2],[34,-2],[36,2],[38,-2],[40,0],[48,0,"hold"]],"tail.rot":[[0,14],[26,14,"hold"],[28,6,"lin"],[30,20,"lin"],[32,6,"lin"],[34,20,"lin"],[36,6,"lin"],[38,20,"lin"],[40,14,"lin"],[48,14,"hold"]]}},
"blocked":{"frames":48,"enter":{"frames":12,"ease":"inout"},"tracks":{"root.x":[[0,0],[2,-3,"lin"],[4,3,"lin"],[6,-2,"lin"],[8,1,"lin"],[10,0,"lin"],[48,0,"hold"]],"root.sy":[[0,0.95],[10,0.95,"hold"],[30,0.975],[40,0.94],[48,0.95]],"head.y":[[0,10],[10,10,"hold"],[30,8],[40,12],[48,10]],"head.rot":[[0,-5],[48,-5]],"earL.rot":[[0,-62],[36,-62,"hold"],[38,-48,"out"],[44,-62,"spring"],[48,-62,"hold"]],"earR.rot":[[0,62],[48,62]],"tail.rot":[[0,26],[48,26]],"eye.sy":[[0,0.45],[48,0.45]],"eye.y":[[0,3],[48,3]]}},
"done":{"frames":36,"enter":{"frames":4,"ease":"out"},"tracks":{"root.y":[[0,0],[4,0,"hold"],[10,-26,"out"],[16,0,"in"],[36,0,"hold"]],"root.sy":[[0,1],[4,0.9],[8,1.05,"out"],[13,1],[17,0.92,"in"],[22,1.03,"over"],[27,1],[36,1]],"root.sx":[[0,1],[4,1.06],[8,0.96,"out"],[13,1],[17,1.05,"in"],[22,0.985,"over"],[27,1],[36,1]],"pawL.rot":[[0,0],[4,-4],[10,128,"out"],[17,0,"in"],[36,0,"hold"]],"pawR.rot":[[0,0],[4,4],[10,-128,"out"],[17,0,"in"],[36,0,"hold"]],"head.y":[[0,0],[4,5],[10,-6,"out"],[17,6,"in"],[23,0,"over"],[36,0]],"head.rot":[[0,0],[17,0,"hold"],[23,12,"over"],[30,12,"hold"],[36,0]],"earL.rot":[[0,0],[5,6],[10,-20,"out"],[17,-34,"in"],[26,0,"spring"],[36,0,"hold"]],"earR.rot":[[0,0],[5,-6],[10,20,"out"],[17,34,"in"],[26,0,"spring"],[36,0,"hold"]],"tail.rot":[[0,-8],[3,14],[6,-8],[9,14],[12,-8],[15,14],[18,-8],[21,14],[24,-8],[27,14],[30,-8],[33,14],[36,-8]]}},
"dizzy":{"frames":36,"enter":{"frames":6,"ease":"inout"},"tracks":{"head.x":[[0,8],[9,0],[18,-8],[27,0],[36,8]],"head.y":[[0,0],[9,5],[18,0],[27,-5],[36,0]],"head.rot":[[0,8],[9,0],[18,-8],[27,0],[36,8]],"root.rot":[[0,0],[9,4],[18,0],[27,-4],[36,0]],"earL.rot":[[0,-18],[9,0],[18,18],[27,0],[36,-18]],"earR.rot":[[0,0],[4,-18],[13,0],[22,18],[31,0],[36,0]],"eye.x":[[0,2.5],[4.5,0],[9,-2.5],[13.5,0],[18,2.5],[22.5,0],[27,-2.5],[31.5,0],[36,2.5]],"eye.y":[[0,0],[4.5,-2.5],[9,0],[13.5,2.5],[18,0],[22.5,-2.5],[27,0],[31.5,2.5],[36,0]],"tail.rot":[[0,20],[18,28],[36,20]],"pawL.rot":[[0,8],[36,8]],"pawR.rot":[[0,-8],[36,-8]]}},
"sleep":{"frames":60,"enter":{"frames":24,"ease":"inout"},"tracks":{"dim":[[0,0.5],[60,0.5]],"root.sy":[[0,0.93],[26,0.955],[60,0.93]],"head.y":[[0,14],[28,12],[60,14]],"head.rot":[[0,9],[60,9]],"earL.rot":[[0,-24],[60,-24]],"earR.rot":[[0,24],[44,24,"hold"],[46,12,"out"],[52,24,"spring"],[60,24,"hold"]],"tail.rot":[[0,-20],[20,-20,"hold"],[30,-26],[40,-20],[60,-20,"hold"]],"eye.sy":[[0,0.12],[60,0.12]]}}
},
"poke":{"frames":14,"enter":{"frames":1,"ease":"lin"},"tracks":{"root.sy":[[0,0],[2,-0.06,"out"],[7,0.03,"over"],[14,0]],"root.sx":[[0,0],[2,0.04,"out"],[7,-0.02,"over"],[14,0]],"head.y":[[0,0],[2,6,"out"],[8,-2,"over"],[14,0]],"earL.rot":[[0,0],[2,-20,"out"],[14,0,"spring"]],"earR.rot":[[0,0],[3,20,"out"],[14,0,"spring"]],"eye.sy":[[0,0],[2,0.3,"out"],[8,0],[14,0,"hold"]],"tail.rot":[[0,0],[3,-18,"out"],[14,0,"spring"]]}}}
"""#
}
