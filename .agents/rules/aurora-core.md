# AURORA Core Rules

- **Consult Architecture First**: Read `docs/FINAL_ARCHITECTURE_BASELINE.md` before major implementation.
- **Read Skills**: Consult relevant skills in `.agents/skills/` before implementing a feature.
- **Player State**: Use centralized player state (`PlayerCoordinator`). Never create a player per screen.
- **Provider Constraints**: Do not fabricate provider capabilities (e.g., YouTube playback limits, music quality).
- **Quality Metrics**: Do not fabricate music/audio quality metrics (lossless, Hi-Res) if unknown.
- **Refactoring**: Test meaningful changes and avoid unrelated refactors.
