"use client";

import { usePathname } from "next/navigation";
import { ReactNode } from "react";
import styles from "./RouteTransition.module.css";

/**
 * Animates the page in when the route changes.
 *
 * An arrival, not a directional slide: the six destinations are siblings, and a slide
 * would say "you went deeper" or "you came back" when there is no forward or back between
 * Listen and Library.
 *
 * Keyed by pathname, which is what replays the animation - React reuses the element
 * otherwise, and a CSS animation only runs when its element is created.
 *
 * Not React's <ViewTransition>: that needs react@canary, and the version matrix pins
 * React 19.2.7. This does the enter half on stable React. The shared-element morph
 * between the mini-player cover and the player - the part that genuinely needs the View
 * Transitions API - waits on that decision.
 */
export function RouteTransition({ children }: { children: ReactNode }) {
  const pathname = usePathname();

  return (
    <div key={pathname} className={styles.route}>
      {children}
    </div>
  );
}
