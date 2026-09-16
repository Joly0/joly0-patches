package app.joly0.extension;

import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import app.joly0.extension.requests.GetPlaylistItemsRequest;
import app.joly0.extension.requests.PlaylistItem;

/**
 * Entry point for playlist bulk removal.
 * <p>
 * Only a playlist you can edit renders native {@code playlist_video_item} rows; a read-only
 * playlist such as a channel's uploads is drawn by Litho and carries no such views. So looking
 * for that row both identifies the screen and confirms the playlist is editable at all.
 */
public final class PlaylistBulkRemove {

    private static final String ROW_RESOURCE_NAME = "playlist_video_item";

    /** Playlist the detection was last logged for, so switching playlists logs again. */
    private static String reportedPlaylistId;

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
                    String playlistId = PlaylistHeader.getCurrentPlaylistId();
                    if (playlistId == null || playlistId.equals(reportedPlaylistId)
                            || !hasEditableRows(list)) {
                        return;
                    }
                    reportedPlaylistId = playlistId;
                    final int rowCount = list.getChildCount();
                    final boolean authenticated = !AuthUtils.isNotLoggedIn();
                    Logger.printInfo(() -> "editable playlist detected, id=" + playlistId
                            + ", rows=" + rowCount
                            + ", authenticated=" + authenticated);
                    probeFirstPage(playlistId);
                } catch (Exception ex) {
                    Logger.printException(() -> "draw listener failed", ex);
                }
            });
        } catch (Exception ex) {
            Logger.printException(() -> "onRecyclerViewCreated failed", ex);
        }
    }

    /**
     * Temporary. Reads the first page of the playlist and logs what came back.
     *
     * The request plumbing has no caller yet, so without this nothing exercises it and a clean
     * build would prove only that it compiles. The selection UI replaces this.
     */
    private static void probeFirstPage(String playlistId) {
        if (AuthUtils.isNotLoggedIn()) {
            Logger.printInfo(() -> "probe skipped, no auth headers captured yet");
            return;
        }

        new Thread(() -> {
            try {
                // Stop after the first page: the point is to prove the request works, not to
                // pull a four thousand entry playlist.
                List<PlaylistItem> items = GetPlaylistItemsRequest.fetchAll(
                        playlistId, AuthUtils.getRequestHeaders(), (itemsSoFar, complete) -> false);

                if (items.isEmpty()) {
                    Logger.printInfo(() -> "probe got no entries for " + playlistId);
                    return;
                }
                PlaylistItem first = items.get(0);
                Logger.printInfo(() -> "probe read " + items.size() + " entries, first: "
                        + first.title() + " by " + first.author()
                        + ", setVideoId present=" + !first.setVideoId().isEmpty());
            } catch (Exception ex) {
                Logger.printException(() -> "probe failed", ex);
            }
        }, "Joly0PlaylistProbe").start();
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
