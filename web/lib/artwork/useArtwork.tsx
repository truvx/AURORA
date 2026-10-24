"use client";

import {
  ReactNode,
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
} from "react";
import { StoredTrack } from "../library/db";
import { useLibrary } from "../player/PlayerProvider";
import { ArtworkLoader, TrackArtwork } from "./loader";

const ArtworkContext = createContext<ArtworkLoader | undefined>(undefined);

const NO_ARTWORK: TrackArtwork = {};

/**
 * Owns the one artwork loader for the app, so its cache is shared between the library rows,
 * the mini-player and the full player rather than each decoding the same cover.
 */
export function ArtworkProvider({ children }: { children: ReactNode }) {
  const { directory, repository } = useLibrary();

  // Built during render rather than in an effect: the constructor only stores two
  // references, so there is nothing here that needs a browser or a commit to have happened.
  const loader = useMemo(
    () => new ArtworkLoader(directory, repository),
    [directory, repository]
  );

  // Every cached entry holds an object URL, and a blob URL keeps its data alive until it is
  // revoked. Replacing the loader without releasing it leaks every decoded cover.
  useEffect(() => () => loader.release(), [loader]);

  return <ArtworkContext.Provider value={loader}>{children}</ArtworkContext.Provider>;
}

/**
 * Artwork for one track, loaded through the shared loader.
 *
 * The result is stored against the id it was loaded for, and a result for any other id is
 * treated as absent. That does two jobs at once: the previous track's cover never appears
 * under the current track's title, and there is no need to clear state synchronously when
 * the id changes - which would cause exactly the cascading render React warns about.
 */
function useArtwork(
  id: string | undefined,
  load: (loader: ArtworkLoader) => Promise<TrackArtwork>
): TrackArtwork {
  const loader = useContext(ArtworkContext);
  const [loaded, setLoaded] = useState<{ id: string; artwork: TrackArtwork }>();

  useEffect(() => {
    if (!loader || !id) return;

    let cancelled = false;
    load(loader)
      .then((artwork) => {
        if (!cancelled) setLoaded({ id, artwork });
      })
      .catch(() => {
        // Decoration. A failure shows the placeholder and is not worth surfacing.
      });

    return () => {
      cancelled = true;
    };
    // `load` closes over the track, and is recreated every render; the id is what actually
    // identifies the work to be done.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [loader, id]);

  // Not `loaded?.id === id`: with no track and nothing loaded that compares undefined to
  // undefined and reports a hit.
  return loaded !== undefined && loaded.id === id ? loaded.artwork : NO_ARTWORK;
}

/** Artwork for a library record, which already carries everything needed to open the file. */
export function useTrackArtwork(track: StoredTrack | undefined): TrackArtwork {
  return useArtwork(track?.id, (loader) =>
    track ? loader.forTrack(track) : Promise.resolve(NO_ARTWORK)
  );
}

/** Artwork for whatever the player is holding, which knows only the track's id. */
export function useArtworkById(id: string | undefined): TrackArtwork {
  return useArtwork(id, (loader) => loader.forTrackId(id));
}
