package app.joly0.extension;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

/**
 * Entry point for playlist bulk removal.
 * <p>
 * Only a playlist you can edit renders native {@code playlist_video_item} rows; a read-only
 * playlist such as a channel's uploads is drawn by Litho and carries no such views. So looking
 * for that row both identifies the screen and confirms the playlist is editable at all.
 */
public final class PlaylistBulkRemove {

    private static final String ROW_RESOURCE_NAME = "playlist_video_item";

    private static boolean reported;

    private PlaylistBulkRemove() {
    }

    /** Injection point. Called for every Litho-backed RecyclerView the app creates. */
    public static void onRecyclerViewCreated(Object recyclerView) {
        try {
            if (!(recyclerView instanceof ViewGroup list)) {
                return;
            }
            list.getViewTreeObserver().addOnDrawListener(() -> {
                try {
                    if (reported || !hasEditableRows(list)) {
                        return;
                    }
                    reported = true;
                    Log.i("Joly0Patches", "editable playlist detected, rows=" + list.getChildCount()
                            + ", authenticated=" + !AuthUtils.isNotLoggedIn());
                } catch (Exception ex) {
                    Log.e("Joly0Patches", "draw listener failed", ex);
                }
            });
        } catch (Exception ex) {
            Log.e("Joly0Patches", "onRecyclerViewCreated failed", ex);
        }
    }

    private static boolean hasEditableRows(ViewGroup list) {
        final int rowId = list.getResources().getIdentifier(
                ROW_RESOURCE_NAME, "id", list.getContext().getPackageName());
        if (rowId == 0) {
            return false;
        }
        for (int i = 0, count = list.getChildCount(); i < count; i++) {
            View child = list.getChildAt(i);
            if (child.findViewById(rowId) != null) {
                return true;
            }
        }
        return false;
    }
}
