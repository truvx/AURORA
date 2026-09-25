import type { Metadata } from "next";
import { Space_Mono } from "next/font/google";
import localFont from "next/font/local";
import { AppShell } from "@/components/navigation/AppShell";
import { ArtworkProvider } from "@/lib/artwork/useArtwork";
import { ThemeProvider } from "@/lib/theme";
import { PlayerProvider } from "@/lib/player/PlayerProvider";
import "@/styles/globals.css";

/*
 * Self-hosted at build time rather than fetched from Google at runtime.
 *
 * The stylesheet links these replace were render-blocking and had to resolve two extra
 * origins before any text could paint. They also meant every visitor's browser announced
 * itself to a third party purely to read the interface - which for a local-first music
 * player is a request that buys nothing.
 *
 * `display: swap` keeps text visible while the face loads, so a slow font delays the look
 * of the page rather than its content.
 */
const spaceMono = Space_Mono({
  subsets: ["latin"],
  display: "swap",
  variable: "--font-aurora-mono",
  // Both of the two the family ships, and no more: Space Mono has Regular and Bold and
  // nothing between them. A role that wants a 500 gets 400 rather than a browser-
  // synthesised weight, which is a smeared Regular and looks like one.
  weight: ["400", "700"],
});

/*
 * The icon set, from a file in the repository.
 *
 * next/font/google cannot serve this one - Material Symbols is not in the Google Fonts
 * catalogue next/font mirrors - so the alternative was leaving one render-blocking request
 * to a third party just for icons.
 *
 * The committed file is subsetted to the twenty-odd glyphs this app actually draws, which
 * is 3.5 KB against roughly 3.5 MB for the full variable face. To regenerate it after
 * adding an icon, see docs/WEB_ARCHITECTURE.md.
 *
 * `display: block` rather than swap: these are icons, and a fallback face has no glyph for
 * "skip_previous", so swapping would flash the literal words before the glyphs arrive.
 */
const materialSymbols = localFont({
  src: "./fonts/material-symbols-outlined-subset.woff2",
  display: "block",
  variable: "--font-aurora-icons",
});

export const metadata: Metadata = {
  title: "AURORA",
  description: "Your music, reimagined — high-quality playback with intelligent discovery",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html
      lang="en"
      suppressHydrationWarning
      className={`${spaceMono.variable} ${materialSymbols.variable}`}
    >
      <body>
        <ThemeProvider>
          <PlayerProvider>
            <ArtworkProvider>
              {/*
                The shell lives here rather than in each page. Wrapping per page meant a page
                could simply forget, and two of them had: /library and /now-playing rendered
                with no navigation and no transport controls at all.
              */}
              <AppShell>{children}</AppShell>
            </ArtworkProvider>
          </PlayerProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
