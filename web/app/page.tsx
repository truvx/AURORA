import { AppShell } from "@/components/navigation/AppShell";

export default function HomePage() {
  return (
    <AppShell>
      <div>
        <h1 className="aurora-headline">Home</h1>
        <p className="aurora-body" style={{ color: "var(--aurora-text-secondary)", marginTop: "var(--aurora-space-2)" }}>
          Your music, reimagined
        </p>
      </div>
    </AppShell>
  );
}
