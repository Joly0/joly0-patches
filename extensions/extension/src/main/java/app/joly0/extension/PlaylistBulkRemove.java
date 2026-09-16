package app.joly0.extension;

import android.view.ViewGroup;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import app.joly0.extension.playlist.PlaylistSelectionController;

/**
 * Lets several videos be removed from a playlist at once, instead of swiping and confirming one
 * at a time.
 * <p>
 * A checkbox is drawn over each playlist row and a bar along the bottom removes everything
 * picked in a single request. The checkboxes are drawn rather than added as views because the
 * rows are LithoViews, which refuse added children outright.
 */
public final class PlaylistBulkRemove {

    /**
     * Lists already given a controller.
     * <p>
     * The hooked Litho binder constructor runs more than once for the same RecyclerView, so
     * without this each list collected several controllers: several overlays stacked in the
     * content frame, several action bars, and the playlist fetched once per controller. Weakly
     * held so a list going away takes its entry with it.
     */
    private static final Set<ViewGroup> attached =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private PlaylistBulkRemove() {
    }

    /**
     * Injection point. Called for every Litho-backed RecyclerView the app creates, most of which
     * are not playlists. The controller stays dormant until the page header says otherwise.
     * <p>
     * The feature is always on: this bundle has no preference screen yet.
     */
    public static void onRecyclerViewCreated(Object recyclerView) {
        try {
            if (!(recyclerView instanceof ViewGroup list)) {
                return;
            }
            if (!attached.add(list)) {
                return;
            }
            // Toasts need a context, and the views this feature adds do not always carry one.
            Utils.setContext(list.getContext().getApplicationContext());
            new PlaylistSelectionController(list).attach();
        } catch (Exception ex) {
            Logger.printException(() -> "onRecyclerViewCreated failed", ex);
        }
    }
}
