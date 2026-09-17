import {
  ListeningEvent,
  ListeningEventKind,
  ResumePosition,
  STORE_HISTORY,
  STORE_RESUME,
  openDatabase,
  resumeKey,
  runTransaction,
} from "./db";

/**
 * Listening history and resume positions.
 *
 * Private, local, and never required for playback: erasing all of it must leave the player
 * fully working, per docs/SECURITY.md. Favorites and playlists are the user's curation and
 * are deliberately not touched by the privacy delete.
 */
export class HistoryRepository {
  private db?: IDBDatabase;

  private async connection(): Promise<IDBDatabase> {
    if (!this.db) this.db = await openDatabase();
    return this.db;
  }

  async record(event: Omit<ListeningEvent, "eventId">): Promise<void> {
    const db = await this.connection();
    await runTransaction(db, STORE_HISTORY, "readwrite", ([store]) => {
      store.add(event);
    });
  }

  async getAll(): Promise<ListeningEvent[]> {
    const db = await this.connection();
    const events = await runTransaction<ListeningEvent[]>(
      db,
      STORE_HISTORY,
      "readonly",
      ([store]) => store.getAll() as IDBRequest<ListeningEvent[]>
    );
    return (events ?? []).sort((a, b) => a.timestamp - b.timestamp);
  }

  /** Most recently played first, one entry per track, skips excluded. */
  async getRecentlyPlayed(limit: number): Promise<string[]> {
    const events = await this.getAll();
    const decided = new Set<string>();
    const recent: string[] = [];

    // The most recent event for a track decides. Filtering out SKIP events alone was not
    // enough: a skipped track still has an earlier PLAY, so it would reappear here as
    // something the user had listened to.
    for (let i = events.length - 1; i >= 0 && recent.length < limit; i -= 1) {
      const event = events[i];
      if (decided.has(event.mediaId)) continue;
      decided.add(event.mediaId);
      if (event.kind === "SKIP") continue;
      recent.push(event.mediaId);
    }
    return recent;
  }

  async pruneOlderThan(cutoff: number): Promise<void> {
    const events = await this.getAll();
    const stale = events.filter((event) => event.timestamp < cutoff);
    if (stale.length === 0) return;

    const db = await this.connection();
    await runTransaction(db, STORE_HISTORY, "readwrite", ([store]) => {
      stale.forEach((event) => {
        if (event.eventId !== undefined) store.delete(event.eventId);
      });
    });
  }

  // --- resume -------------------------------------------------------------------------

  async saveResume(position: Omit<ResumePosition, "key">): Promise<void> {
    const db = await this.connection();
    await runTransaction(db, STORE_RESUME, "readwrite", ([store]) => {
      store.put({ ...position, key: resumeKey(position.provider, position.mediaId) });
    });
  }

  async getResume(provider: string, mediaId: string): Promise<ResumePosition | undefined> {
    const db = await this.connection();
    return runTransaction<ResumePosition>(
      db,
      STORE_RESUME,
      "readonly",
      ([store]) => store.get(resumeKey(provider, mediaId)) as IDBRequest<ResumePosition>
    );
  }

  /** Erases behavioural data. Favorites and playlists are curation and are left alone. */
  async deleteAllPrivateData(): Promise<void> {
    const db = await this.connection();
    await runTransaction(db, [STORE_HISTORY, STORE_RESUME], "readwrite", ([history, resume]) => {
      history.clear();
      resume.clear();
    });
  }

  close(): void {
    this.db?.close();
    this.db = undefined;
  }
}

/**
 * Turns player state changes into history and resume writes.
 *
 * Kept outside the coordinator on purpose, exactly as on Android: the coordinator owns
 * canonical playback and must stay correct whether or not anything is being persisted, so a
 * failure here can never stop the music.
 */
export class PlaybackHistoryRecorder {
  private lastTrackId?: string;
  private lastProvider = "LOCAL";
  private lastProgressMs = 0;
  private lastDurationMs?: number;
  /** undefined means nothing has been written for this track yet. */
  private lastResumeWriteAt?: number;
  private completedCurrent = false;

  constructor(
    private readonly repository: HistoryRepository,
    private readonly sessionId: string = crypto.randomUUID(),
    private readonly now: () => number = Date.now,
    /** Fraction of a track that counts as listened rather than skipped. */
    private readonly completionThreshold = 0.9,
    /** Resume positions are written at most this often while a track plays. */
    private readonly resumeDebounceMs = 5000,
    /** Positions below this are not worth resuming from. */
    private readonly minimumResumeMs = 10000
  ) {}

  async onState(state: {
    status: string;
    currentTrack?: { id: string; provider: string };
    position: { elapsedMs: number; durationMs?: number };
  }): Promise<void> {
    const track = state.currentTrack;

    if (track?.id !== this.lastTrackId) {
      await this.finishPrevious();
      if (track) {
        await this.repository.record({
          mediaId: track.id,
          provider: track.provider,
          timestamp: this.now(),
          sessionId: this.sessionId,
          kind: "PLAY",
          progressMs: 0,
        });
      }
      this.lastTrackId = track?.id;
      this.lastProvider = track?.provider ?? this.lastProvider;
      this.lastProgressMs = 0;
      this.lastDurationMs = undefined;
      this.lastResumeWriteAt = undefined;
      this.completedCurrent = false;
    }

    if (!track) return;

    this.lastProgressMs = state.position.elapsedMs;
    this.lastDurationMs = state.position.durationMs;

    if (state.status === "Completed" && !this.completedCurrent) {
      this.completedCurrent = true;
      await this.repository.record({
        mediaId: track.id,
        provider: track.provider,
        timestamp: this.now(),
        sessionId: this.sessionId,
        kind: "COMPLETE",
        progressMs: state.position.elapsedMs,
      });
      // A finished track should start from the beginning next time.
      await this.repository.saveResume({
        provider: track.provider,
        mediaId: track.id,
        positionMs: 0,
        durationMs: state.position.durationMs,
        updatedAt: this.now(),
      });
      return;
    }

    if (state.status === "Playing") await this.maybeSaveResume(track);
  }

  /**
   * Writes a resume point at most once per debounce window. Position updates arrive four
   * times a second; persisting each would hammer IndexedDB.
   */
  private async maybeSaveResume(track: { id: string; provider: string }): Promise<void> {
    if (this.lastProgressMs < this.minimumResumeMs) return;
    const timestamp = this.now();
    if (
      this.lastResumeWriteAt !== undefined &&
      timestamp - this.lastResumeWriteAt < this.resumeDebounceMs
    ) {
      return;
    }
    this.lastResumeWriteAt = timestamp;

    await this.repository.saveResume({
      provider: track.provider,
      mediaId: track.id,
      positionMs: this.lastProgressMs,
      durationMs: this.lastDurationMs,
      updatedAt: timestamp,
    });
  }

  /**
   * A track left before the threshold was skipped, not listened to. Unknown duration counts
   * as a skip rather than inventing a completion.
   */
  private async finishPrevious(): Promise<void> {
    const previousId = this.lastTrackId;
    if (!previousId || this.completedCurrent) return;

    const duration = this.lastDurationMs;
    const listenedEnough =
      duration !== undefined &&
      duration > 0 &&
      this.lastProgressMs >= duration * this.completionThreshold;

    await this.repository.record({
      mediaId: previousId,
      provider: this.lastProvider,
      timestamp: this.now(),
      sessionId: this.sessionId,
      kind: listenedEnough ? "COMPLETE" : ("SKIP" as ListeningEventKind),
      progressMs: this.lastProgressMs,
    });
  }
}
