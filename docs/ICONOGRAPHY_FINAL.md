# AURORA Iconography

Status: final v1 specification, accepted by ADR-015.

## System

Use Material Symbols Outlined as the default action/status icon system. Use the same semantic name across Android and Web, with platform-appropriate asset delivery. Use Lucide only as an explicit fallback when a required Material Symbol is unavailable or a web-only utility needs an equivalent permissive icon.

## Rules

- Default optical size is 24 dp on Android and 24 px on Web; align icons to the text baseline or control center.
- The icon glyph is not the touch target. Interactive controls are at least 48 dp on Android and 44 CSS px on Web, with adequate spacing.
- Icon-only controls require an accessible semantic label. Unfamiliar icon-only controls expose a tooltip on hover/focus; visible text is preferred for high-risk actions.
- Use filled/selected state, supporting text, or shape in addition to color for favorites, downloads, playback state, and errors.
- Do not use emoji, Apple SF Symbols, copied Apple artwork, or hand-drawn SVG approximations.
- Do not use icons as decoration where they compete with artwork or metadata. A missing icon is a state to handle, not a reason to substitute an arbitrary glyph.
