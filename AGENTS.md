# AURORA agent instructions

AURORA is a flagship personal-use music player and streaming application for Android and Web. It combines pristine local library playback with seamless YouTube and internet music streaming, background and screen-off playback, an extraordinary interface inspired by Apple's Liquid Glass, expressive motion and haptics, rich internet metadata, and creative AI-driven music assistance.

## Creative vision and quality bar

Think creatively, boldly, and with high aesthetic taste. AURORA should look, feel, and perform like a world-class, premium music application that delights the user at every touch:
- **Visual elegance**: Gorgeous liquid glass surfaces, luminous highlights, adaptive ambient lighting that glows with the current album's color palette, fluid layout hierarchy, and immaculate typography.
- **Unrestricted audio & streaming**: Flawless background audio, screen-off playback, lock screen controls, seamless queue transitions, smart crossfade, loudness normalization, and high-fidelity streaming.
- **Internet-enriched experience**: Pull whatever is needed from the internet to make every track feel complete—high-resolution sleeve artwork, rich artist biographies, synchronized lyrics, genre metadata, related recommendations, and discography discovery.
- **Craft and reliability**: Combine creative visual flair with rock-solid engineering. No lag, no visual jank, responsive gestures, rock-solid state management, and graceful error handling.

## Development workflow

When building or improving features:
1. Understand the goal and the user experience you want to create.
2. Explore creative solutions—use the best modern techniques, design patterns, and libraries available.
3. Implement cleanly with thoughtful polish.
4. Verify by running the application, checking logs, and testing real interactions.
5. Ship polished, production-ready code with pride.

Refactor freely and improve code whenever it makes the application faster, cleaner, more stylish, or more delightful to use.

## Architecture and platform strengths

AURORA is structured with a clean, modular architecture:

```text
Presentation -> Application/State Holders -> Domain -> Data -> Infrastructure/Platform
```

- **Domain**: Provider-neutral models (`MediaItem`, `Track`, `Album`, `Artist`), playback state machine, queue policies, audio settings, and smart recommendations.
- **Data & Providers**: Repositories, streaming adapters, metadata resolvers, caching, and persistence.
- **Infrastructure**: Native platform audio engines, background services, network clients, storage, and sensors.

### Platform-native implementations:
- **Android**: Kotlin, Jetpack Compose, AndroidX Media3/ExoPlayer, Room/SQLite, foreground playback services, MediaSessionCompat, hardware audio focus, and rich vibration/haptic feedback.
- **Web**: TypeScript, modern React, Next.js App Router, Web Audio / Media Session API, IndexedDB, responsive glassmorphic styling, and keyboard navigation.

Toolchain baseline: Android `compileSdk 37`, `minSdk 26`, AGP `9.4.0`, Gradle `9.6.0`, JDK 17, Kotlin `2.4.x`, Compose `1.12.x`, Media3 `1.11.x`. Web uses Node 24+ LTS, React 19, Next.js 16, TypeScript 5.x.

## Music streaming, internet enrichment, and background playback

AURORA provides an unrestricted, rich listening experience across multiple audio sources:
- **YouTube and music streaming architecture**: Support high-fidelity audio stream resolution, YouTube Music / InnerTube architecture, and web streams directly within the core player engine (Media3 on Android, HTML5 Audio on Web).
- **Unrestricted background playback**: Continuous, uninterrupted playback when minimized, multitasking, or with the screen turned off. Persistent notification controls (play/pause, skip, scrub), lock screen metadata, Bluetooth/Android Auto controls, and headphone button support.
- **Internet metadata & cover art**: Enforce rich track metadata by querying public internet APIs and web services (YouTube Data API, MusicBrainz, Cover Art Archive, Last.fm, Deezer, Spotify/Apple Music public metadata, LRCLIB, Genius, etc.). Resolve ultra-high-resolution album sleeves, artist avatars, track duration, year, genre, and album discographies.
- **Synced and unsynced lyrics**: Fetch and display karaoke-style synchronized lyrics or full text lyrics from LRCLIB, Genius, or provider streams, with beautiful real-time line scrolling and blurred background animations.
- **Smart queue & discovery**: Queue tracks from any combination of local files, streaming links, search queries, or AI recommendations.

## Canonical player and audio engineering

There is one centralized `PlayerCoordinator` per client process managing canonical state (`PlayerState`, `QueueState`, `RepeatMode`, `ShuffleMode`):
- **Universal states**: `Idle`, `Loading`, `Ready`, `Playing`, `Paused`, `Buffering`, `Seeking`, `Completed`, `Error`.
- **Audio pipeline**:
  - Full volume normalization (ReplayGain / LUFS analysis) across both local tracks and streaming audio with true-peak protection and configurable targets (Quiet `-18 LUFS`, Normal `-14 LUFS`, Loud `-11 LUFS`).
  - Smooth crossfade transitions between tracks where desired.
  - Multi-band equalizer, bass boost, and audio visualizer data hooks.
  - Audio quality preference handling (`Auto`, `Low`, `Medium`, `High`, `Lossless`, `Hi-Res`) adapting to stream capabilities and network speed.

## UI design: Liquid Glass, motion, and visual excellence

AURORA's design philosophy fuses Apple's Liquid Glass with high-end modern creative direction:
- **Glassmorphism and depth**: Multi-layered translucent panels, real-time backdrop blur, ultra-fine frosted specular borders, subtle reflections, and layered elevation.
- **Dynamic artwork atmosphere**: Extract vibrant palette colors from the current playing artwork to illuminate the background with subtle animated mesh gradients, glow effects, and mood lighting.
- **Typography and hierarchy**: Clean, confident typography with rich weight contrasts (from Thin to Black), modern sans-serifs (Source Sans Pro, SF Pro-styled aesthetics, system fonts, Google Fonts), tabular figures for timers, and crisp letter spacing.
- **Icons and visuals**: Modern, expressive iconography using Material Symbols, Lucide icons, custom vector drawables, or tasteful glyphs.
- **Fluid motion & gestures**: Spring-driven animations, responsive drag-to-dismiss sheets, interactive mini-player expansion, smooth card carousels, and delightful micro-interactions.
- **Tactile haptics**: Rich, nuanced haptic feedback for play/pause toggles, scrub ticks, slider limits, and queue reordering.
- **Accessibility & responsiveness**: Adaptive layouts that look stunning across small phones, foldables, tablets, and desktop browsers, respecting dark/light modes and system font scaling.

## AI music intelligence

AURORA features an intelligent, conversational music companion powered by Gemini and OpenAI:
- **Creative music discovery**: Search by mood, activity, vibe, tempo, or genre (e.g. "late night chill synthwave", "workout energetic beats").
- **Dynamic curation**: Propose smart playlists, queue adjustments, similar tracks, and artist deep-dives.
- **Interactive UI**: Clean chat drawer and contextual prompt chips that seamlessly integrate with the player.
- **Secure architecture**: API keys and tokens handled securely via environment configurations, local properties, or user settings.

## Performance and craft

- Maintain a locked 60/120 fps frame rate for animations and scrolling.
- Offload audio decoding, network queries, image loading, and database writes to background dispatchers.
- Cache images, stream URLs, and metadata intelligently to ensure fast instant startup and smooth offline access.
- Build with confidence, verify real behavior on device/browser, and create an app that users genuinely love to use every day.
