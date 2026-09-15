/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.playlist;

import android.support.v7.widget.RecyclerView;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.youtube.settings.Settings;

/**
 * Lets several videos be removed from a playlist at once, instead of swiping and confirming one
 * at a time.
 * <p>
 * A checkbox is drawn over each playlist row and a bar along the bottom removes everything
 * picked in a single request. The checkboxes are drawn rather than added as views because the
 * rows are LithoViews, which refuse added children outright.
 */
public final class PlaylistBulkRemovePatch {

    private PlaylistBulkRemovePatch() {
    }

    /**
     * Injection point. Called for every Litho-backed RecyclerView the app creates, most of which
     * are not playlists. The controller stays dormant until the Litho filter says otherwise.
     */
    public static void onFlyoutMenuCreate(RecyclerView recyclerView) {
        try {
            if (!Settings.PLAYLIST_BULK_REMOVE.get()) {
                return;
            }
            new PlaylistSelectionController(recyclerView).attach();
        } catch (Exception ex) {
            Logger.printException(() -> "onFlyoutMenuCreate failure", ex);
        }
    }
}
