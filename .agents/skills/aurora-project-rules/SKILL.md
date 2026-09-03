---
name: aurora-project-rules
description: Route AURORA work to its project rules, design/provider/player/AI skills, documentation, verification gates, and scope restrictions.
---

# AURORA project rules

Use at the start of any AURORA task, especially when a request is broad or crosses domains. Read `AGENTS.md` and the relevant `docs/*.md` before acting.

## Routing

- Architecture/contracts: `aurora-architecture`
- YouTube/local providers: `aurora-music-provider`
- Playback/queue/media session: `aurora-player`
- Quality/normalization/metadata: `aurora-audio`
- Gemini/OpenAI agent: `aurora-ai-music-agent`
- Ranking/discovery: `aurora-recommendations`
- UI/design/accessibility: `aurora-liquid-glass-ui` plus `ui-ux-pro-max`
- Motion: `aurora-motion` plus the relevant GSAP or Compose skill
- Haptics: `aurora-haptics`
- Security/privacy: `aurora-security` plus security review skills
- Tests/verification: `aurora-testing` plus focused TDD
- Web client: `aurora-web`

## Delivery gates

Before a major feature: inspect the existing architecture, read applicable skills/docs, make a plan, implement small increments, run focused tests/builds, review regressions, and update docs when behavior or contracts change. Do not build placeholders or claim unsupported media, quality, login, or download functionality.

## Current phase

AURORA is in foundation/specification phase. Do not generate app source, placeholder screens, fake providers, invented endpoints, or fake playback until the user explicitly begins implementation.
