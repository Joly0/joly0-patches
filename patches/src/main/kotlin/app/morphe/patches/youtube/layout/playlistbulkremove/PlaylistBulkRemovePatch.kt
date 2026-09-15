/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to this code.
 */

package app.morphe.patches.youtube.layout.playlistbulkremove

import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patches.shared.misc.litho.filter.addLithoFilter
import app.morphe.patches.shared.misc.settings.preference.SwitchPreference
import app.morphe.patches.youtube.misc.extension.sharedExtensionPatch
import app.morphe.patches.youtube.misc.litho.filter.lithoFilterPatch
import app.morphe.patches.youtube.misc.recyclerviewtree.addRecyclerViewTreeHook
import app.morphe.patches.youtube.misc.recyclerviewtree.recyclerViewTreeHookPatch
import app.morphe.patches.youtube.misc.settings.PreferenceScreen
import app.morphe.patches.youtube.misc.settings.settingsPatch
import app.morphe.patches.youtube.shared.Constants.COMPATIBILITY_YOUTUBE

private const val EXTENSION_CLASS =
    "Lapp/morphe/extension/youtube/patches/playlist/PlaylistBulkRemovePatch;"

private const val EXTENSION_FILTER =
    "Lapp/morphe/extension/youtube/patches/components/PlaylistRowFilter;"

/**
 * Removing several videos from a playlist normally means swiping each row, choosing remove and
 * confirming, one video at a time. This draws a checkbox on each row and removes everything
 * selected in a single request.
 *
 * No new bytecode hook is needed. Two existing ones carry everything:
 *
 * - The RecyclerView tree hook hands over every Litho backed list, playlists included. Its
 *   extension method is named `onFlyoutMenuCreate` only because flyout menus were its first
 *   user; the fingerprint matches the generic `LithoRVSLCBinder` constructor.
 * - A Litho filter identifies which of those lists is a playlist, and reads the playlist id out
 *   of the page header component's buffer. YouTube's own browse id hook was tried first and only
 *   ever reports `FEwhat_to_watch`.
 */
val playlistBulkRemovePatch = bytecodePatch(
    name = "Playlist bulk remove",
    description = "Adds an option to show a checkbox on each video in a playlist, so several can be removed at once.",
) {
    dependsOn(
        sharedExtensionPatch,
        settingsPatch,
        lithoFilterPatch,
        recyclerViewTreeHookPatch,
    )

    compatibleWith(COMPATIBILITY_YOUTUBE)

    execute {
        PreferenceScreen.FEED.addPreferences(
            SwitchPreference("morphe_playlist_bulk_remove"),
        )

        addRecyclerViewTreeHook(EXTENSION_CLASS)
        addLithoFilter(EXTENSION_FILTER)
    }
}
