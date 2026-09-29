package dev.aurora.player.ui.library

/**
 * What the library is showing.
 *
 * The same four the Web client offers, in the same order, because a listener who uses both
 * should not have to learn the library twice.
 *
 * Favourites are deliberately not a fifth view: they are Tracks with a filter applied,
 * which is how Web has it and how Home's "Liked songs" tile arrives here. A separate view
 * would imply a separate collection, and there is only one.
 */
enum class LibraryView(val label: String) {
    Tracks("Tracks"),
    Albums("Albums"),
    Artists("Artists"),
    Playlists("Playlists"),
    ;

    /** Singular, for the sentences an empty or opened group writes about itself. */
    val singular: String get() = label.dropLast(1).lowercase()
}
