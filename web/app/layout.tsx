import type { Metadata } from "next";
import { ThemeProvider } from "@/lib/theme";
import { PlayerProvider } from "@/lib/player/PlayerProvider";
import "@/styles/globals.css";

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
    <html lang="en" suppressHydrationWarning>
      <head>
        <link
          rel="preconnect"
          href="https://fonts.googleapis.com"
        />
        <link
          rel="preconnect"
          href="https://fonts.gstatic.com"
          crossOrigin="anonymous"
        />
        <link
          href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&display=swap"
          rel="stylesheet"
        />
        <link
          rel="stylesheet"
          href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL,GRAD@24,400,0,0&display=swap"
        />
      </head>
      <body>
        <ThemeProvider>
          <PlayerProvider>
          {children}
        </PlayerProvider>
        </ThemeProvider>
      </body>
    </html>
  );
}
