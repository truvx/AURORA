"use client";

import {
  ReactNode,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  useSyncExternalStore,
} from "react";
import {
  isFileSystemAccessSupported,
  resolveTrackUrl,
  restoreMusicDirectory,
} from "../library/fileSystem";
import { LibraryRepository } from "../library/repository";
import { PlayerCoordinator } from "./coordinator";
import { HtmlAudioAdapter } from "./htmlAudioAdapter";
import { bindMediaSession } from "./mediaSession";
import { MediaItem, PlayerCommand, PlayerState, initialPlayerState } from "./types";

interface PlayerContextValue {
  readonly coordinator: PlayerCoordinator;
  readonly repository: LibraryRepository;
  readonly directory?: FileSystemDirectoryHandle;
  setDirectory(handle: FileSystemDirectoryHandle | undefined): void;
}

const PlayerContext = createContext<PlayerContextValue | undefined>(undefined);

/**
 * Owns the one coordinator, audio element, and library connection for the app.
 *
 * Deliberately does not put player state in React state: position updates arrive four times
 * a second, and holding them here would re-render every consumer. Components subscribe to
 * the slice they need through the hooks below.
 */
export function PlayerProvider({ children }: { children: ReactNode }) {
  const [directory, setDirectory] = useState<FileSystemDirectoryHandle | undefined>();

  // A plain mutable holder rather than a ref: the source resolver runs long after render
  // and needs the current folder, but rebuilding the coordinator on every folder change
  // would discard the audio element and the user gesture attached to it.
  // A ref is the right holder for something read outside render. It is written only from
  // an effect - writing during render is what the rules forbid - and read later, when the
  // resolver actually runs.
  const currentDirectory = useRef<FileSystemDirectoryHandle | undefined>(undefined);
  useEffect(() => {
    currentDirectory.current = directory;
  }, [directory]);

  const [repository] = useState(() => new LibraryRepository());

  // Built in an effect rather than during render: it owns an audio element and reads a ref,
  // neither of which belongs in a render pass, and there is no player on the server.
  const [coordinator, setCoordinator] = useState<PlayerCoordinator | undefined>();

  useEffect(() => {
    const adapter = new HtmlAudioAdapter();
    const created = new PlayerCoordinator(adapter, async (track: MediaItem) => {
      const root = currentDirectory.current;
      if (!root) return undefined;
      const stored = await repository.get(track.id);
      if (!stored) return undefined;
      return resolveTrackUrl(root, stored);
    });

    created.setCapabilities({ directoryPicker: isFileSystemAccessSupported() });
    const unbind = bindMediaSession(created);
    setCoordinator(created);

    return () => {
      unbind();
      created.release();
    };
  }, [repository]);

  // Re-open the previously chosen folder so the library survives a reload.
  useEffect(() => {
    let cancelled = false;
    restoreMusicDirectory()
      .then((handle) => {
        if (!cancelled && handle) setDirectory(handle);
      })
      .catch(() => {
        // No saved folder, or permission lapsed. The library page offers to pick one.
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const value = useMemo<PlayerContextValue | undefined>(
    () => (coordinator ? { coordinator, repository, directory, setDirectory } : undefined),
    [coordinator, repository, directory]
  );

  if (!value) return <>{children}</>;
  return <PlayerContext.Provider value={value}>{children}</PlayerContext.Provider>;
}

function usePlayerContext(): PlayerContextValue | undefined {
  return useContext(PlayerContext);
}

/**
 * Subscribes to a slice of player state.
 *
 * Passing a narrow selector matters: a component selecting only the track title will not
 * re-render on position ticks.
 */
export function usePlayerState<T>(selector: (state: PlayerState) => T): T {
  const context = usePlayerContext();

  const subscribe = useCallback(
    (onChange: () => void) => context?.coordinator.subscribe(onChange) ?? (() => {}),
    [context]
  );
  const getSnapshot = useCallback(
    () => selector(context?.coordinator.getState() ?? initialPlayerState),
    [context, selector]
  );
  // The server has no player; render the idle projection to avoid a hydration mismatch.
  const getServerSnapshot = useCallback(() => selector(initialPlayerState), [selector]);

  return useSyncExternalStore(subscribe, getSnapshot, getServerSnapshot);
}

export function usePlayerCommands() {
  const context = usePlayerContext();
  return useCallback(
    (command: PlayerCommand) => {
      void context?.coordinator.dispatch(command);
    },
    [context]
  );
}

export function useLibrary() {
  const context = usePlayerContext();
  return {
    repository: context?.repository,
    directory: context?.directory,
    setDirectory: context?.setDirectory,
  };
}
