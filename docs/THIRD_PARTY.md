# Third-party code in AURORA

AURORA is GPL-3.0. Code taken from other projects is listed here with its origin and
licence, as the licence requires and so that the next person can find the upstream when
it changes.

## NomaTune

- Source: https://github.com/Shahdullah/NomaTune
- Licence: GPL-3.0-only
- Compatible with AURORA's GPL-3.0: yes, which is why AURORA is GPL-3.0.

Ported from it:

| AURORA | NomaTune origin | Notes |
|---|---|---|
| _(pending)_ | | |

Every file derived from NomaTune carries a header naming the upstream file and its
licence. A port is not a fork: where AURORA's architecture differs - one
`PlayerCoordinator` rather than a service plus Hilt graph, Room schemas that are not
theirs - the adapted code says so rather than pretending to be a copy.
