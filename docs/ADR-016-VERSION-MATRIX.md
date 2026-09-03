# ADR-016: Android and Web Version Matrix

- Status: Accepted
- Date: 2026-09-03
- Supersedes: the version rows and earlier compile-SDK assumption in preparation notes

## Decision

AURORA uses the following implementation baseline:

| Area | Selected version or value | Decision |
| --- | --- | --- |
| Android compile SDK | `37` | Required by Compose `1.12.0`; AGP `9.4.0` supports API 37. |
| Android target SDK | `36` | Retained for the initial Android 16 behavior and release baseline. It meets the current Google Play requirement of API 36 or higher for new apps and updates beginning 2026-08-31. Raising target SDK to 37 requires a separate behavior, compatibility, and release review. |
| Android minimum SDK | `26` | Initial runtime floor for AURORA. |
| Android Gradle Plugin | `9.4.0` | Official compatibility anchor for this baseline. |
| Gradle | Wrapper `9.6.0` | AGP 9.4.0 minimum/default; project-local and reproducible. |
| JDK | `17` | AGP 9.4.0 minimum/default for Gradle builds. |
| Kotlin | `2.4.10` | Selected stable Kotlin line; AGP 9 supplies built-in Kotlin. |
| Compose | `1.12.0` | Selected stable Compose UI/Foundation/Runtime line; compile SDK 37 is required. |
| Compose compiler | Kotlin-aligned Compose Compiler Gradle plugin | Required approach for Kotlin 2.0 and newer; do not use the legacy compiler extension. |
| Material 3 | `1.4.0` | Selected stable Material 3 line. |
| AndroidX Media3 | `1.11.0` | Selected stable Media3 line. |
| Android Studio | `2026.1.3` installed locally | Retained; the current stable patch observed in the official release page is `2026.1.4`. |
| Web runtime | Node `24.20.0` LTS | Project and CI baseline. The installed Node `26.3.1` remains unchanged. |
| Web package manager | npm 11 | Bundled with the selected Node 24 line; use lockfile-enforced `npm ci` in CI. |
| React | `19.2.7` | Selected React 19.2 release. |
| Next.js | `16.3.4` App Router | Selected current Next.js line; official installation documentation requires Node `>=20.9`. |
| TypeScript | `5.16.1` | Selected stable TypeScript line. |
| CI | GitHub Actions Ubuntu `24.04`, Temurin 17 | Matches the project JDK and documented Web/Android environment. |

## SDK semantics

`compileSdk`, `targetSdk`, and `minSdk` are separate contracts:

- `compileSdk 37` controls the Android APIs available to source compilation and is required by Compose `1.12.0`. It does not select runtime behavior.
- `targetSdk 36` controls the platform behavior changes AURORA opts into for the initial release. It does not need to equal `compileSdk`. Android 17 is stable, but the current release strategy intentionally does not opt into API 37 behavior without a dedicated review.
- `minSdk 26` controls the oldest supported runtime and compatibility surface.

The selected `targetSdk 36` is not an API 36 compile-SDK fallback. The project compiles against API 37 and targets API 36. The API 36 platform may remain installed for testing and target behavior, but it is not the compile platform.

## Compatibility verification

### Android build stack

The official AGP 9.4.0 release notes state that API 37 is the maximum supported API level and list Gradle `9.6.0` as the minimum/default, SDK Build Tools `36.0.0` as the minimum/default, and JDK `17` as the minimum/default. This validates the selected AGP, wrapper, JDK, compile SDK, and Build Tools combination.

The official Compose compiler documentation states that projects using Compose `1.12.0` require `compileSdk 37` and AGP 9. The official Compose/Kotlin guidance directs Kotlin 2.0+ projects to the Compose Compiler Gradle plugin. Kotlin `2.4.10` is the selected stable Kotlin release and is used through AGP 9 built-in Kotlin, with the Kotlin-aligned Compose compiler plugin.

Media3 `1.11.0` is the current stable AndroidX release. Its official release notes document a Kotlin source upgrade from `2.0.20` to `2.2.0`, but do not specify an incompatible AGP or Gradle version. The selected Kotlin `2.4.10` is a newer stable Kotlin toolchain line and is not contradicted by the Media3 release notes. A real Gradle dependency-resolution and compile check is a Phase 1 acceptance criterion once the project exists; this foundation task intentionally does not create that project or a wrapper.

### Web stack

Next.js `16.3.4` officially requires Node `>=20.9`, and the Next.js 16 release documentation identifies React `19.2` and TypeScript `5.1` as supported floors, so React `19.2.7`, TypeScript `5.16.1`, and Node 24 satisfy the documented framework requirements. Node's official download page distinguishes Node 24 as LTS and Node 26 as Current. Node 24 is selected for project/CI reproducibility and support conservatism; the user's installed Node 26.3.1 and npm 12.0.2 are not modified.

## Local verification

The required Android packages are installed at `/Users/t6ux/Library/Android/sdk`:

| Package | Installed value | Role |
| --- | --- | --- |
| `platforms;android-37.0` | Revision `2`, `AndroidVersion.ApiLevel=37` | Compose-required compile SDK platform. |
| `build-tools;36.0.0` | `36.0.0` | AGP 9.4.0 default Build Tools. |
| `platform-tools` | `37.0.1` | `adb` and device tooling. |
| `platforms;android-36` | Revision `2` | API 36 target/runtime testing platform; not the compile SDK. |

Observed verification also confirms OpenJDK `17.0.20.1`, `javac 17.0.20.1`, `adb 1.0.41` with platform-tools `37.0.1-15733141`, and `sdkmanager 22.0`. Gradle and the Kotlin CLI are intentionally not installed globally; future Android builds use the project Gradle Wrapper.

## Alternatives considered

| Alternative | Decision | Reason |
| --- | --- | --- |
| Compile SDK below 37 | Rejected | Contradicts the official Compose 1.12.0 requirement for compile SDK 37. |
| `targetSdk 37` immediately | Deferred | Android 17 is stable, but the initial release behavior baseline and current Play minimum are API 36; target adoption needs its own compatibility review. |
| Node 26 for project/CI | Rejected for baseline | It is newer/current and remains valid locally, but Node 24 LTS is the more conservative reproducible project baseline. |
| Global Gradle or Kotlin CLI | Rejected | The Android project will use a pinned Gradle Wrapper and Kotlin Gradle configuration. |

## Assumptions, confidence, and follow-up

Assumptions: AURORA's first Android release follows the documented API 36 target behavior baseline, while compiling against API 37; the future Phase 1 project will use AGP built-in Kotlin and the Compose Compiler Gradle plugin. Confidence is high for the SDK/AGP/JDK/Compose and Web requirement decisions because they are directly supported by current official documentation. Confidence is medium-high for the Media3/Kotlin combination because the official Media3 release notes do not publish an AGP/Gradle conflict, while the final dependency graph can only be compiled after the prohibited project creation step is authorized.

## Official documentation consulted

- [AGP 9.4.0 release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes)
- [Android Gradle plugin and Android Studio compatibility](https://developer.android.com/build/releases/gradle-plugin)
- [Android Gradle JDK guidance](https://developer.android.com/build/jdks)
- [Android built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Compose compiler Gradle plugin](https://developer.android.com/develop/ui/compose/compiler)
- [Compose releases](https://developer.android.com/jetpack/androidx/releases/compose)
- [Compose and Kotlin compatibility](https://developer.android.com/jetpack/androidx/releases/compose-kotlin)
- [Media3 releases](https://developer.android.com/jetpack/androidx/releases/media3)
- [Android 17](https://developer.android.com/about/versions/17)
- [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Node.js downloads](https://nodejs.org/en/download)
- [React versions](https://react.dev/versions)
- [Next.js installation and system requirements](https://nextjs.org/docs/app/getting-started/installation)
- [Next.js 16 release](https://nextjs.org/blog/next-16)
- [TypeScript downloads](https://www.typescriptlang.org/download/)
- [GitHub Actions Java with Gradle](https://docs.github.com/en/actions/automating-builds-and-tests/building-and-testing/java-with-gradle)
