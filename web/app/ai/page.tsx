import { AppShell } from "@/components/navigation/AppShell";

export default function AiPage() {
  return (
    <AppShell>
      <div>
        <h1 className="aurora-headline">AI</h1>
        <p className="aurora-body" style={{ color: "var(--aurora-text-secondary)", marginTop: "var(--aurora-space-2)" }}>
          Music discovery powered by intelligence
        </p>
      </div>
    </AppShell>
  );
}
