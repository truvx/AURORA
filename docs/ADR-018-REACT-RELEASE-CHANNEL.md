# ADR-018: The web client stays on stable React, now 19.3.0

- Status: Accepted
- Date: 2026-09-23
- Amends: the React row of `docs/ADR-016-VERSION-MATRIX.md`

## Context

React's `<ViewTransition>` is what makes a shared element persist across a navigation -
the mini-player's cover growing into the player's cover, rather than one square vanishing
and another appearing. AURORA wanted it for the player and could not have it: the app was
pinned to React 19.2.7, which has no such export.

Next 16.3.4 bundles a React canary (19.3.0-canary) for its own server rendering, which is
why the component appears to exist when inspected through `next/dist/compiled/react`. The
browser gets the React the project installs. Measured in Chromium rather than assumed: the
component rendered nothing and no transition started.

The question was framed as whether to move the web client to `react@canary`. It turned out
not to be the question.

## Decision

The web client stays on the stable React channel and moves to **React 19.3.0**, which
exports `ViewTransition`. No canary, no experimental build.

The version matrix row for React changes from `19.2.7` to `19.3.0`. `react-dom` moves with
it, as it must.

## Why not canary

A canary is cut daily and carries no compatibility promise between cuts. Depending on one
means every install can differ from the last, and a bug is a bug in an unreleased React,
against which the only move is to pin an exact build and hope. That is a reasonable trade
for a team shipping a framework; it is not one for a personal music player whose whole
argument for its dependencies is that they are boring and pinned.

It was also unnecessary. The feature had already shipped in a stable release; the project
was simply two minor versions behind.

## Consequences

- The shared-element morph is implemented: the mini-player cover and the player's cover
  share a `<ViewTransition name>`, so the browser moves one into the other.
- Route changes crossfade through React's transition rather than a keyed CSS animation,
  and the CSS that styles them lives in `styles/globals.css` under the motion tokens.
- Browsers without the View Transitions API, and anyone who has asked for reduced motion,
  navigate with no animation. That is the browser's own behaviour and nothing is lost but
  the movement.
- An e2e test watches for `document.startViewTransition` during a navigation, because the
  failure mode is silent: React renders the children and says nothing when the feature is
  unavailable.
