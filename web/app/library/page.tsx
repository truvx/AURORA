import { AppShell } from "@/components/navigation/AppShell";

export default function LibraryPage() {
  return (
    <AppShell>
      <div>
        <h1 className="aurora-headline">Library</h1>
        <p className="aurora-body" style={{ color: "var(--aurora-text-secondary)", marginTop: "var(--aurora-space-2)" }}>
          Favorites, playlists, and your collection
        </p>
      </div>
    </AppShell>
  );
}
