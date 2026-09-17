"use client";

import { usePathname } from "next/navigation";
import Link from "next/link";
import { ReactNode, useEffect, useRef } from "react";
import styles from "./AppShell.module.css";
import { MiniPlayer } from "@/components/player/MiniPlayer";
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
 */
const navItems: NavItem[] = [
  { href: "/", label: "Listen", icon: "play_circle" },
  { href: "/search", label: "Search", icon: "search" },
  { href: "/library", label: "Library", icon: "library_music" },
  { href: "/now-playing", label: "Playing", icon: "graphic_eq" },
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
      <main className={styles.main}>{children}</main>

      <div className={styles.chrome} ref={chrome}>
        <YouTubeSurface />
        <MiniPlayer />

        <nav className={styles.nav} aria-label="Primary navigation">
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
        </nav>
      </div>
    </div>
  );
}
