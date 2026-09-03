# ADR-002: Original Liquid Glass-inspired design system

## Status

Accepted as foundation; final tokens and accessibility rules are in the design-system documents.

## Context

AURORA needs a premium, artwork-aware visual language across Android and web. A direct imitation of Apple’s proprietary implementation or assets is not appropriate, and glass effects can harm contrast, performance, and accessibility.

## Decision

Use an original semantic design system built from neutral-first color primitives, artwork-derived ambient themes, layered translucent surfaces, adaptive blur, soft highlights, large typography, restrained depth, and opaque/reduced-effects fallbacks. Components consume semantic tokens and are state-driven.

## Alternatives considered

- Generic Material-only UI — rejected because it does not express the intended atmospheric identity without becoming platform-generic.
- Exact Liquid Glass recreation — rejected for proprietary-asset/source risk and poor portability.
- Unconstrained glassmorphism — rejected because it risks unreadability, excessive blur, and frame-time cost.

## Consequences

The design has strong identity and cross-platform consistency, but requires compositing-time contrast tests, artwork edge-case handling, and performance measurement. Token and component specifications precede runtime interface work.

## Future migration

Material recipes may be adjusted or replaced without changing semantic token names or accessibility outcomes. Dynamic palette algorithms carry versions so cached themes can be invalidated safely.
