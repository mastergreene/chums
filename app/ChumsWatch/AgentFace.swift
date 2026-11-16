import SwiftUI

/// An agent's face: its character, small, on a black disc with a hairline ring. Black, because several avatars are
/// pictures on black (the pixel ones are a sprite in a black square): on black, there's no square to see.
struct AgentFace: View {
    let character: String
    var size = 34.0
    var body: some View {
        ZStack {
            Circle().fill(.black)
            Image("avatar-\(Self.asset(character))").resizable().interpolation(Self.fullBleed.contains(Self.asset(character)) ? .high : .none).scaledToFit()
                .padding(Self.fullBleed.contains(Self.asset(character)) ? 0 : size * 0.08)
        }
        .frame(width: size, height: size).clipShape(Circle())
        .overlay(Circle().strokeBorder(.white.opacity(0.16), lineWidth: max(0.5, size / 80)))
    }
    /// Avatars that are pictures to the edge (rendered), not pixel art in the middle of the circle.
    static let fullBleed: Set<String> = ["stretch", "squint", "ray", "tangle", "noodle", "biscuit"]
    static func asset(_ c0: String) -> String { let c = CharacterKind.legacy[c0] ?? c0; return CharacterKind(rawValue: c) != nil ? c : CharacterKind.fallback.rawValue }
}
