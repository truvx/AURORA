# AURORA Toolchain Matrix

Status: final preparation baseline, 2026-09-03. No application project or build file is created by this document.

## Project matrix

| Area | Baseline | Rationale |
| --- | --- | --- |
| Android Studio | `2026.1.3` installed locally; current stable patch observed in official release page: `2026.1.4` | The installed IDE is retained because the patch difference is not a project dependency. Update through Android Studio before release if the IDE reports a compatibility issue. |
| Android Gradle Plugin | `9.4.0` | Current official compatibility page; supports API 37, requires Gradle 9.6.0 minimum/default, Build Tools 36.0.0, and JDK 17. |
| Gradle | Wrapper `9.6.0` | Project-local wrapper is the reproducible build contract; no global Gradle is required. |
| JDK | `17.0.20.1` | AGP 9.4 compatibility baseline. Android Studio continues to use its bundled JBR 21 runtime. |
| Kotlin | `2.4.10` | Current stable Kotlin line consulted; use AGP 9 built-in Kotlin and do not apply the old Android Kotlin plugin. |
| Compose UI | `1.12.0` | Current stable Compose UI/Foundation/Runtime line consulted. |
| Compose Material 3 | `1.4.0` | Current stable Material 3 line consulted. |
| Compose compiler | Kotlin/Compose compiler Gradle plugin aligned to `2.4.10` | Kotlin 2.0+ uses the Compose compiler Gradle plugin; do not use the legacy compiler extension line. |
| AndroidX Media3 | `1.11.0` | Current stable Media3 line consulted. |
| compileSdk | `37` | Required by Compose `1.12.0`; AGP `9.4.0` officially supports API 37. |
| targetSdk | `36` | Deliberately remains Android 16 for the initial release behavior baseline. Current Google Play policy requires API 36 or higher for new apps and updates from 2026-08-31; API 37 adoption requires a separate behavior and release review. |
| minSdk | `26` | Conservative initial floor for modern media, storage, lifecycle, and testing support; revisit only with measured product demand. |
| Web runtime | Node `24.20.0` LTS | Current official LTS line; local Node 26 is newer/current but CI should use the LTS line for reproducibility. |
| Web framework | React `19.2.7`, Next.js `16.3.4` App Router | Latest React 19.2 release listed by the official versions page and current Next.js installation document; Next.js requires Node >=20.9. |
| TypeScript | `5.16.1` | Current stable release line consulted from the official download page. |
| Package manager | npm 11 bundled with Node 24; `npm ci` in CI | Uses the existing npm workflow without adding another global package manager. |
| CI runner | GitHub Actions `ubuntu-24.04` | Stable hosted Linux baseline for Android and Web jobs. |
| CI actions | `actions/checkout@v5`, `actions/setup-java@v5`, `actions/setup-node@v5` | Pin action major lines and use lockfiles; exact action SHAs are a CI hardening follow-up. |
| CI Java | Temurin 17 | Matches the Android build JDK. |
| CI Android SDK | Compile platform `android-37.0`, Build Tools `36.0.0`, platform-tools | Matches the Compose/AGP compile contract. The app target remains API 36. |

## Android SDK semantics

These values are intentionally different:

- `compileSdk = 37`: selects the Android API surface used to compile AURORA. Compose `1.12.0` requires this value, and AGP `9.4.0` supports API 37.
- `targetSdk = 36`: selects the platform behavior baseline that the initial release opts into. It is not required to equal `compileSdk`; keeping 36 gives the release a reviewed Android 16 behavior target while satisfying the current Google Play API 36 minimum.
- `minSdk = 26`: selects the oldest Android API level that AURORA supports at runtime.

Android SDK Platform 37 is stable and installed locally as package `platforms;android-37.0`, revision `2` (`AndroidVersion.ApiLevel=37`). AGP `9.4.0` requires Build Tools `36.0.0` as its default, and that package is installed locally. Platform API 36 remains installed as well, but it is not the compile SDK.

## Compatibility notes

AGP `9.4.0` is the authoritative Android build compatibility anchor: its official table lists Gradle `9.6.0` as the minimum/default, Build Tools `36.0.0` as the minimum/default, JDK `17` as the minimum/default, and API 37 as the maximum supported API level. AGP 9 provides built-in Kotlin, so the future project must not apply the legacy Android Kotlin plugin. Kotlin `2.4.10` is the selected stable Kotlin line and Compose uses the Kotlin-aligned Compose Compiler Gradle plugin. Compose UI `1.12.0` is therefore paired with `compileSdk 37`.

Media3 `1.11.0` is the selected current stable AndroidX Media3 release. Its official release notes call out a Kotlin source upgrade to `2.2.0` and do not impose a conflicting AGP or Gradle requirement. The selected project Kotlin `2.4.10`, AGP `9.4.0`, and Gradle `9.6.0` form the project toolchain; the first real project dependency-resolution/compile check remains a Phase 1 acceptance test because this documentation-only task must not create a project.

## Web baseline decision

The selected Web baseline is Node `24.20.0` LTS, React `19.2.7`, Next.js `16.3.4`, TypeScript `5.16.1`, and npm 11. The current official Next.js `16.3.4` installation documentation requires Node `>=20.9`; the selected Node 24 LTS line satisfies that requirement. The official React versions page lists the React `19.2` line and `19.2.7` release. Node `26` is a valid newer/current local runtime, but the project and CI use Node 24 LTS for the more conservative reproducible baseline. The installed Node `26.3.1` and npm `12.0.2` are not changed or downgraded by this task.

## Observed local environment

| Tool | Observed value |
| --- | --- |
| OS / architecture | macOS `26.6`, Apple Silicon `arm64` |
| Node / npm | `v26.3.1` / `12.0.2` |
| Python | `3.14.2` |
| Git | `2.50.1` |
| Java / javac | OpenJDK `17.0.20.1`, installed by Homebrew `openjdk@17` |
| Android SDK | `/Users/t6ux/Library/Android/sdk`; platform `android-37.0` revision 2 and `android-36` revision 2; Build Tools `36.0.0`; platform-tools `37.0.1` |
| sdkmanager | `22.0`, from Homebrew `android-commandlinetools` |
| Android CLI | `1.0.16251017`, initialized with SDK root `/Users/t6ux/Library/Android/sdk` |
| adb | `1.0.41`, platform-tools version `37.0.1-15733141`, `/Users/t6ux/Library/Android/sdk/platform-tools/adb` when the documented SDK-first PATH is used |
| Android Studio | `2026.1.3` installed; IDE requires Java 21 and uses its bundled runtime |
| Gradle / Kotlin CLI | Not installed globally by design; future project uses Gradle Wrapper and Kotlin Gradle plugin |
| Codex | `0.149.0` |

## Environment contract

For terminal Gradle work, use:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_SDK_ROOT=/Users/t6ux/Library/Android/sdk
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:/opt/homebrew/bin:$PATH"
```

Android Studio should continue using its bundled JBR. The `JAVA_HOME` and `PATH` exports were written into `~/.zshrc` during the Antigravity toolchain repair (2026-09-03) so that `java`, `javac`, and Gradle Wrapper resolve JDK 17 in all new terminal sessions.

## Official references

- [Android Studio releases](https://developer.android.com/studio/releases)
- [Android Gradle Plugin compatibility](https://developer.android.com/build/releases/gradle-plugin)
- [Android Gradle JDK guidance](https://developer.android.com/build/jdks)
- [Android built-in Kotlin migration](https://developer.android.com/build/migrate-to-built-in-kotlin)
- [Compose releases](https://developer.android.com/jetpack/androidx/releases/compose)
- [Compose compiler Gradle plugin](https://developer.android.com/develop/ui/compose/compiler)
- [Compose and Kotlin compatibility](https://developer.android.com/jetpack/androidx/releases/compose-kotlin)
- [Media3 releases](https://developer.android.com/jetpack/androidx/releases/media3)
- [Android 16](https://developer.android.com/about/versions/16)
- [Android 17](https://developer.android.com/about/versions/17)
- [Google Play target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878)
- [Node.js downloads](https://nodejs.org/en/download)
- [React versions](https://react.dev/versions)
- [Next.js installation](https://nextjs.org/docs/app/getting-started/installation)
- [Next.js 16 release](https://nextjs.org/blog/next-16)
- [TypeScript downloads](https://www.typescriptlang.org/download/)
- [GitHub Actions Java with Gradle](https://docs.github.com/en/actions/automating-builds-and-tests/building-and-testing/java-with-gradle)
