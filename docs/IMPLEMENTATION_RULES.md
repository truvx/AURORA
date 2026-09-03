# AURORA implementation rules

1. Inspect architecture, docs, relevant skills, existing contracts, and repository status before editing.
2. Plan meaningful multi-file changes and implement in small reversible increments.
3. Keep dependencies inward and abstractions narrow. Do not create a second owner for state, playback, settings, or provider policy.
4. Keep external data and AI output untrusted until validated. Use typed errors/results and capability checks.
5. Never fake playback, login, media URLs, Hi-Res/lossless support, normalization parity, or provider capabilities.
6. Keep YouTube integration policy-compliant: supported playback/discovery only; no extraction, unauthorized downloading, DRM bypass, or credential scraping.
7. Keep secrets out of source, logs, tests, screenshots, bundles, and Git. Use secure storage and supported auth.
8. Keep UI accessible and readable over glass. Respect reduced motion/transparency. Centralize haptics and avoid scroll buzz.
9. Keep I/O and heavy work off the UI thread; measure artwork, lists, blur, scans, queue, and audio paths.
10. Test behavior at the owning layer, then run build/lint/type checks available in the actual repository.
11. Run architecture, security/privacy, frontend, test-coverage, and code-quality reviews before merge when applicable.
12. Update relevant docs when contracts, capabilities, lifecycle, security, or performance behavior changes. Avoid unrelated refactors and do not commit unless requested.
