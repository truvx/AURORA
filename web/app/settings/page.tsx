import { AppShell } from "@/components/navigation/AppShell";

export default function SettingsPage() {
  return (
    <AppShell>
      <div>
        <h1 className="aurora-headline">Settings</h1>
        <p className="aurora-body" style={{ color: "var(--aurora-text-secondary)", marginTop: "var(--aurora-space-2)" }}>
          Audio quality, normalization, and preferences
        </p>
      </div>
    </AppShell>
  );
}
