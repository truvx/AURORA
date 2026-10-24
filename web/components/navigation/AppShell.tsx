"use client";

import { usePathname } from "next/navigation";
import Link from "next/link";
import { ReactNode, useEffect, useRef } from "react";
import styles from "./AppShell.module.css";
import { AmbientArtworkLayer } from "@/components/artwork/AmbientArtworkLayer";
import { MiniPlayer } from "@/components/player/MiniPlayer";
import { RouteTransition } from "@/components/navigation/RouteTransition";
import { YouTubeSurface } from "@/components/player/YouTubeSurface";

interface NavItem {
  href: string;
  label: string;
  icon: string; // Material Symbols Outlined name
}

/*
 * Named for what is actually on them. "Home" is an umbrella that tells you nothing about
 * what you will find, where "Listen" says it: resume, and what to play next. Specific
 * labels are what make a destination predictable before you tap it.
 *
 * Four, as on Android. Search sits outside the pill as its own circle - it is reached far
 * more often than the rest and does not belong in a list you scan - and the player is
 * reached from the mini-player above, which is where the thing it opens already is.
 */
const navItems: NavItem[] = [
  { href: "/", label: "Listen", icon: "play_circle" },
  { href: "/library", label: "Library", icon: "library_music" },
  { href: "/ai", label: "AI", icon: "auto_awesome" },
  { href: "/settings", label: "Settings", icon: "settings" },
];

export function AppShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  const chrome = useRef<HTMLDivElement>(null);
  const shell = useRef<HTMLDivElement>(null);

  // The floating chrome overlays the content rather than taking a strip out of the page,
  // so the page has to reserve exactly as much room as the chrome currently occupies -
  // and that changes when the mini-player appears. Measured rather than guessed: a fixed
  // reserve leaves dead space with nothing playing, or clips the last row with something
  // playing.
  useEffect(() => {
    const element = chrome.current;
    const container = shell.current;
    if (!element || !container || typeof ResizeObserver === "undefined") return;

    const observer = new ResizeObserver(([entry]) => {
      container.style.setProperty(
        "--aurora-chrome-height",
        `${entry.contentRect.height}px`
      );
    });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  return (
    <div className={styles.shell} ref={shell}>
      {/* Behind everything, so the glass above it has something to refract. */}
      <AmbientArtworkLayer />

      <main className={styles.main}>
        <RouteTransition>{children}</RouteTransition>
      </main>

      <div className={styles.chrome} ref={chrome}>
        <YouTubeSurface />
        {/*
        Hidden on the player's own route: that screen has its own transport, and two of them
        put every control in the accessibility tree twice.
      */}
      {pathname !== "/now-playing" && <MiniPlayer />}

        {/*
          A pill and a circle floating over the page, not a bar across it. The row is what
          keeps them beside each other; each is its own piece of glass.
        */}
        <nav className={styles.chromeRow} aria-label="Primary navigation">
          {/* The pill is a piece of glass inside the landmark, not the landmark itself:
              search is navigation too, and a link outside it belongs to nothing. */}
          <div className={styles.nav}>
            {navItems.map((item) => {
              const isActive = pathname === item.href;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  className={`${styles.navLink} ${isActive ? styles.active : ""}`}
                  aria-current={isActive ? "page" : undefined}
                >
                  <span className="material-symbols-outlined" aria-hidden="true">
                    {item.icon}
                  </span>
                  <span className={styles.navLabel}>{item.label}</span>
                </Link>
              );
            })}
          </div>

          <Link
            href="/search"
            className={`${styles.searchButton} ${pathname === "/search" ? styles.active : ""}`}
            aria-label="Search"
            aria-current={pathname === "/search" ? "page" : undefined}
          >
            <span className="material-symbols-outlined" aria-hidden="true">
              search
            </span>
          </Link>
        </nav>
      </div>
    </div>
  );
}
