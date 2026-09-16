package app.joly0.patches.playlist

import app.joly0.patches.auth.authHookPatch
import app.joly0.patches.recyclerview.addRecyclerViewTreeHook
import app.joly0.patches.recyclerview.recyclerViewTreeHookPatch
import app.joly0.patches.shared.Constants.COMPATIBILITY_YOUTUBE
import app.morphe.patcher.patch.bytecodePatch

private const val EXTENSION_CLASS = "Lapp/joly0/extension/PlaylistBulkRemove;"

/**
 * Select several videos in a playlist you own and remove them in one request, instead of
 * swiping and confirming one video at a time.
 */
@Suppress("unused")
val playlistBulkRemovePatch = bytecodePatch(
    name = "Playlist bulk remove",
    description = "Adds checkboxes to playlists you own so several videos can be removed at once.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_YOUTUBE)

    dependsOn(authHookPatch, recyclerViewTreeHookPatch)

    extendWith("extensions/extension.mpe")

    execute {
        addRecyclerViewTreeHook(EXTENSION_CLASS)
    }
}
