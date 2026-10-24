import { ReactNode } from "react";
import styles from "./Glass.module.css";

interface GlassSurfaceProps {
  level?: "primary" | "secondary" | "elevated";
  opaqueFallback?: boolean;
  className?: string;
  children: ReactNode;
}

export function GlassSurface({
  level = "primary",
  opaqueFallback = false,
  className = "",
  children,
}: GlassSurfaceProps) {
  const levelClass = styles[`glass_${level}`] ?? styles.glass_primary;
  const fallbackClass = opaqueFallback ? styles.opaque : "";

  return (
    <div className={`${styles.surface} ${levelClass} ${fallbackClass} ${className}`}>
      {children}
    </div>
  );
}

interface GlassCardProps extends GlassSurfaceProps {}

export function GlassCard({
  level = "primary",
  opaqueFallback = false,
  className = "",
  children,
}: GlassCardProps) {
  const levelClass = styles[`glass_${level}`] ?? styles.glass_primary;
  const fallbackClass = opaqueFallback ? styles.opaque : "";

  return (
    <div className={`${styles.card} ${levelClass} ${fallbackClass} ${className}`}>
      {children}
    </div>
  );
}

interface GlassButtonProps {
  variant?: "primary" | "secondary" | "quiet";
  disabled?: boolean;
  /**
   * For a button that toggles a view rather than performing an action.
   *
   * Renders `aria-pressed` and the matching fill, so the state is both announced and
   * visible. Leave undefined for an ordinary button - `aria-pressed="false"` on something
   * that is not a toggle tells a screen reader the wrong thing.
   */
  pressed?: boolean;
  /**
   * An accessible name where the visible label is the shorter of the two.
   *
   * "Back" is clear enough on screen, where the heading above it says what you are backing
   * out of; read on its own in a list of controls it is not.
   */
  ariaLabel?: string;
  onClick?: () => void;
  className?: string;
  children: ReactNode;
}

export function GlassButton({
  variant = "primary",
  disabled = false,
  pressed,
  ariaLabel,
  onClick,
  className = "",
  children,
}: GlassButtonProps) {
  return (
    <button
      className={`${styles.button} ${styles[`button_${variant}`]} ${className}`}
      disabled={disabled}
      aria-pressed={pressed}
      aria-label={ariaLabel}
      onClick={onClick}
      type="button"
    >
      {children}
    </button>
  );
}

interface GlassIconButtonProps {
  icon: string; // Material Symbols Outlined name
  label: string;
  onClick?: () => void;
  disabled?: boolean;
  selected?: boolean;
  className?: string;
}

export function GlassIconButton({
  icon,
  label,
  onClick,
  disabled = false,
  selected = false,
  className = "",
}: GlassIconButtonProps) {
  return (
    <button
      className={`${styles.iconButton} ${selected ? styles.iconButton_selected : ""} ${className}`}
      disabled={disabled}
      onClick={onClick}
      type="button"
      aria-label={label}
      title={label}
    >
      <span className="material-symbols-outlined" aria-hidden="true">
        {icon}
      </span>
    </button>
  );
}
