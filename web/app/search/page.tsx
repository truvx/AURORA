import { AppShell } from "@/components/navigation/AppShell";

export default function SearchPage() {
  return (
    <AppShell>
      <div>
        <h1 className="aurora-headline">Search</h1>
        <p className="aurora-body" style={{ color: "var(--aurora-text-secondary)", marginTop: "var(--aurora-space-2)" }}>
          Find music across your library and YouTube
        </p>
      </div>
    </AppShell>
  );
}
