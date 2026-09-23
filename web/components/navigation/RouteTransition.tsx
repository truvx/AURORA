"use client";

import { usePathname } from "next/navigation";
import { ReactNode, ViewTransition } from "react";

/**
 * Crossfades the page when the route changes.
 *
 * A crossfade rather than a directional slide, because the six destinations are siblings.
 * A slide says "you went deeper" or "you came back", and there is no forward or back
 * between Listen and Library - claiming one would tell the user something untrue about
 * where they are.
 *
 * Keyed by pathname: the key change is what makes React treat the old and new page as an
 * exit/enter pair rather than an in-place update. Without it nothing animates, because
 * this sits in the shell and a layout persists across navigation.
 *
 * Browsers without the View Transitions API, and anyone who has asked for reduced motion,
 * get the navigation with no animation - which is the browser's own behaviour.
 */
export function RouteTransition({ children }: { children: ReactNode }) {
  const pathname = usePathname();

  return (
    <ViewTransition key={pathname} name="route-content" share="auto" enter="auto" default="none">
      {children}
    </ViewTransition>
  );
}
