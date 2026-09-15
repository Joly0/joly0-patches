/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.playlist;

import android.app.Activity;
import android.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;
import app.morphe.extension.shared.innertube.utils.AuthUtils;
import app.morphe.extension.youtube.patches.components.PlaylistRowFilter;
import app.morphe.extension.youtube.patches.utils.requests.EditPlaylistRequest;
import app.morphe.extension.youtube.patches.utils.requests.GetPlaylistItemsRequest;
import app.morphe.extension.youtube.patches.utils.requests.PlaylistItem;
import app.morphe.extension.youtube.patches.utils.requests.RemovePlaylistVideosRequest;

import static app.morphe.extension.shared.StringRef.str;

/**
 * Drives bulk selection for one playlist RecyclerView: puts the overlay and the action bar in
 * place, loads the playlist contents, and performs the removal.
 * <p>
 * One instance per RecyclerView handed to the hook. Several Litho RecyclerViews are alive at
 * once and most of them are not playlists, so an instance stays dormant until the Litho filter
 * confirms playlist rows are rendering.
 */
final class PlaylistSelectionController {

    /** Most titles anyone will read in a confirmation dialog before their eyes glaze over. */
    private static final int MAX_TITLES_IN_CONFIRMATION = 12;

    private final RecyclerView recyclerView;
    private final PlaylistRowTracker tracker = new PlaylistRowTracker();
    private final PlaylistSelectionState state = new PlaylistSelectionState();

    @Nullable
    private PlaylistSelectionOverlay overlay;
    @Nullable
    private PlaylistSelectionActionBar actionBar;

    private final int[] navBarLocation = new int[2];
    private final int[] contentLocation = new int[2];

    /** The list's own bottom padding before we added room for the action bar. */
    private int originalListBottomPadding = -1;

    /** Action bar height, remembered so room stays reserved while it is hidden. */
    private int reservedBarHeight;

    private boolean installed;
    private boolean fetchRunning;
    /** Set once something needs more than the first page. */
    private volatile boolean wantAllPages;
    private boolean selectAllWhenLoaded;
    @Nullable
    private String fetchedPlaylistId;

    PlaylistSelectionController(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    void attach() {
        recyclerView.getViewTreeObserver().addOnDrawListener(this::onDraw);
        recyclerView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(View v) {
            }

            @Override
            public void onViewDetachedFromWindow(View v) {
                remove();
            }
        });
    }

    private void onDraw() {
        try {
            // An editable playlist renders real playlist_video_item rows. That is both the
            // detection and the requirement: a list without them is either not a playlist or is
            // one that cannot be edited, and either way there is nothing to offer here.
            if (!installed && !PlaylistRowTracker.looksLikeEditablePlaylist(recyclerView)) {
                return;
            }

            if (!installed) {
                install();
                return;
            }

            // The rows move under the overlay as the list scrolls, so it has to redraw with them.
            if (overlay != null) {
                // The navigation bar comes and goes as the app hides it on scroll, so where the
                // action bar belongs is re-decided rather than fixed at install time.
                if (overlay.bottomNavBar == null) {
                    overlay.bottomNavBar = findBottomNavBar(recyclerView.getRootView());
                }
                if (actionBar != null) {
                    actionBar.setBottomOffset(actionBarBottomOffset());
                    reserveRoomForActionBar();
                }
                overlay.invalidate();
            }
            maybeFetch();
        } catch (Exception ex) {
            Logger.printException(() -> "Playlist selection draw failure", ex);
        }
    }

    private void install() {
        Activity activity = Utils.getActivity();
        if (activity == null) {
            return;
        }
        if (!(activity.findViewById(android.R.id.content) instanceof FrameLayout contentFrame)) {
            return;
        }

        // Both views go in the activity's content frame rather than beside the list. The list's
        // parent is a SwipeRefreshLayout, which lays out only its single scrolling child and
        // leaves any other child at zero size, so an overlay added there never draws.
        overlay = new PlaylistSelectionOverlay(activity, recyclerView, tracker,
                state, this::onSelectionChanged, this::ensureFullyLoaded);
        contentFrame.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        actionBar = new PlaylistSelectionActionBar(activity,
                this::onSelectAll, this::onClear, this::onRemove);
        contentFrame.addView(actionBar, actionBar.bottomLayoutParams());

        overlay.actionBar = actionBar;
        overlay.bottomNavBar = findBottomNavBar(contentFrame);

        installed = true;
        Logger.printDebug(() -> "Playlist selection overlay installed");
        maybeFetch();
    }

    /**
     * YouTube's own bottom navigation. The action bar has to sit above it rather than over it,
     * and the overlay must not draw or take touches down there, or a checkbox ends up on top of
     * the Home button.
     */
    @Nullable
    private static View findBottomNavBar(View root) {
        try {
            return Utils.getChildViewByResourceName(root, "pivot_bar");
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Grows the list's bottom padding by the height of the action bar, so the last video can be
     * scrolled clear of it instead of sitting behind it.
     */
    private void reserveRoomForActionBar() {
        if (actionBar == null) {
            return;
        }
        View list = recyclerView;
        if (originalListBottomPadding < 0) {
            originalListBottomPadding = list.getPaddingBottom();
        }

        // Room is kept even while the bar is hidden. The bar appears the moment something is
        // selected, and if the list had not already made room for it, it would appear on top of a
        // row. Reserved space costs an invisible gap at the very end of the list; not reserving it
        // costs a covered row exactly when the user is looking at their selection.
        if (actionBar.getVisibility() == View.VISIBLE && actionBar.getHeight() > 0) {
            reservedBarHeight = actionBar.getHeight();
        }
        if (reservedBarHeight == 0) {
            return;
        }

        final int wanted = originalListBottomPadding + reservedBarHeight;
        if (list.getPaddingBottom() != wanted) {
            list.setPadding(list.getPaddingLeft(), list.getPaddingTop(),
                    list.getPaddingRight(), wanted);
        }
    }

    private void remove() {
        PlaylistRowTracker.clearGutters(recyclerView);
        if (originalListBottomPadding >= 0) {
            View list = recyclerView;
            list.setPadding(list.getPaddingLeft(), list.getPaddingTop(),
                    list.getPaddingRight(), originalListBottomPadding);
            originalListBottomPadding = -1;
            reservedBarHeight = 0;
        }

        if (overlay != null && overlay.getParent() instanceof ViewGroup parent) {
            parent.removeView(overlay);
        }
        if (actionBar != null && actionBar.getParent() instanceof ViewGroup parent) {
            parent.removeView(actionBar);
        }
        overlay = null;
        actionBar = null;
        installed = false;
        tracker.reset();
    }

    /**
     * Loads the playlist contents once the header has told us which playlist this is. The entries
     * are what make a selected row index mean a specific video, so nothing can be selected until
     * this succeeds.
     */
    private void maybeFetch() {
        final String playlistId = PlaylistRowFilter.currentPlaylistId;
        if (playlistId == null || playlistId.equals(fetchedPlaylistId) || state.isLoading()) {
            return;
        }

        if (AuthUtils.isNotLoggedIn()) {
            fetchedPlaylistId = playlistId;
            Utils.showToastShort(str("morphe_playlist_bulk_remove_not_logged_in"));
            return;
        }

        fetchedPlaylistId = playlistId;
        state.setLoading(true);
        onSelectionChanged();

        startFetch(playlistId);
    }

    /**
     * Reads the playlist, one page at a time.
     * <p>
     * Only the first page is pulled up front. Watch Later here is 4,477 entries over 76 requests
     * and forty seconds, and firing all of that at the account every time the playlist is merely
     * opened is both wasteful and conspicuous. The rest is fetched once the user shows intent:
     * selecting something, asking for select all, or scrolling to rows we cannot yet identify.
     */
    private void startFetch(String playlistId) {
        if (fetchRunning) {
            return;
        }
        fetchRunning = true;
        state.setLoading(true);
        onSelectionChanged();

        final Map<String, String> requestHeader = AuthUtils.getRequestHeader();
        Utils.runOnBackgroundThread(() -> {
            List<PlaylistItem> items = GetPlaylistItemsRequest.fetchAll(playlistId, requestHeader,
                    (itemsSoFar, complete) -> {
                        Utils.runOnMainThread(() -> {
                            state.setPlaylist(playlistId, itemsSoFar);
                            state.setComplete(complete);
                            if (complete && selectAllWhenLoaded) {
                                selectAllWhenLoaded = false;
                                state.selectAll();
                            }
                            onSelectionChanged();
                            if (overlay != null) {
                                overlay.invalidate();
                            }
                        });
                        // Keep going only while something actually needs the rest of it.
                        return wantAllPages;
                    });

            Utils.runOnMainThread(() -> {
                fetchRunning = false;
                state.setLoading(false);
                if (items.isEmpty()) {
                    Utils.showToastShort(str("morphe_playlist_bulk_remove_load_failed"));
                }
                onSelectionChanged();
                if (overlay != null) {
                    overlay.invalidate();
                }
            });
        });
    }

    /**
     * Asks for the remainder of the playlist, if it is not already known or on its way.
     */
    void ensureFullyLoaded() {
        if (state.isComplete() || wantAllPages) {
            return;
        }
        wantAllPages = true;

        final String playlistId = state.playlistId();
        if (playlistId != null && !fetchRunning) {
            // The earlier run already stopped after its first page, so start another.
            startFetch(playlistId);
        }
    }

    /**
     * How far above the bottom of the content frame the action bar must sit so that it rests on
     * top of YouTube's bottom navigation rather than covering it.
     * <p>
     * Measured from actual positions rather than from the navigation bar's height: the content
     * frame extends behind the system navigation bar as well, so using the height alone left the
     * action bar sitting exactly over the navigation buttons.
     */
    private int actionBarBottomOffset() {
        View navBar = overlay == null ? null : overlay.bottomNavBar;
        if (navBar == null || navBar.getVisibility() != View.VISIBLE || navBar.getHeight() == 0) {
            return 0;
        }
        if (actionBar == null || !(actionBar.getParent() instanceof View contentFrame)) {
            return 0;
        }

        navBar.getLocationInWindow(navBarLocation);
        contentFrame.getLocationInWindow(contentLocation);
        final int contentBottom = contentLocation[1] + contentFrame.getHeight();
        return Math.max(0, contentBottom - navBarLocation[1]);
    }

    private void onSelectionChanged() {
        if (actionBar != null) {
            actionBar.setSelectedCount(state.selectedCount(), state.isReady());
        }
    }

    private void onSelectAll() {
        if (!state.isComplete()) {
            // Selecting "all" of a playlist we have only partly read would quietly select a
            // fraction of it, which is the bug this whole round started from.
            selectAllWhenLoaded = true;
            ensureFullyLoaded();
            Utils.showToastShort(str("morphe_playlist_bulk_remove_loading"));
            return;
        }

        state.selectAll();
        onSelectionChanged();
        if (overlay != null) {
            overlay.invalidate();
        }
    }

    private void onClear() {
        state.clearSelection();
        onSelectionChanged();
        if (overlay != null) {
            overlay.invalidate();
        }
    }

    /**
     * Confirms before removing.
     * <p>
     * This step is not politeness. Which video a row maps to is inferred from its position in the
     * list, because a row view carries no identity of its own (docs/06-spike-results.md). Showing
     * the titles the selection actually resolved to turns a wrong inference into something the
     * user can see and cancel, instead of a silent deletion of the wrong videos.
     */
    private void onRemove() {
        List<PlaylistItem> selected = state.selectedItems();
        final String playlistId = state.playlistId();
        if (selected.isEmpty() || playlistId == null) {
            return;
        }

        Activity activity = Utils.getActivity();
        if (activity == null) {
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(selected.size() == 1
                        ? str("morphe_playlist_bulk_remove_confirm_title_one")
                        : str("morphe_playlist_bulk_remove_confirm_title", selected.size()))
                .setMessage(str("morphe_playlist_bulk_remove_confirm_message",
                        describeSelection(selected)))
                .setPositiveButton(str("morphe_playlist_bulk_remove_confirm_button"),
                        (d, which) -> performRemoval(playlistId, selected))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        Utils.showDialog(activity, dialog);
    }

    private String describeSelection(List<PlaylistItem> selected) {
        StringBuilder builder = new StringBuilder();
        final int shown = Math.min(selected.size(), MAX_TITLES_IN_CONFIRMATION);
        for (int i = 0; i < shown; i++) {
            PlaylistItem item = selected.get(i);
            if (i > 0) {
                builder.append('\n');
            }
            builder.append("• ").append(item.title().isEmpty() ? item.videoId() : item.title());
        }
        if (selected.size() > shown) {
            builder.append(str("morphe_playlist_bulk_remove_confirm_more", selected.size() - shown));
        }
        return builder.toString();
    }

    private void performRemoval(String playlistId, List<PlaylistItem> selected) {
        Utils.showToastShort(str("morphe_playlist_bulk_remove_removing"));

        List<String> setVideoIds = new ArrayList<>(selected.size());
        for (PlaylistItem item : selected) {
            setVideoIds.add(item.setVideoId());
        }

        final Map<String, String> requestHeader = AuthUtils.getRequestHeader();
        Utils.runOnBackgroundThread(() -> {
            RemovePlaylistVideosRequest.Result result =
                    RemovePlaylistVideosRequest.remove(playlistId, setVideoIds, requestHeader);

            Utils.runOnMainThread(() -> onRemovalFinished(selected, result));
        });
    }

    private void onRemovalFinished(List<PlaylistItem> selected,
                                   RemovePlaylistVideosRequest.Result result) {
        if (result.isCompleteFailure()) {
            Utils.showToastShort(str("morphe_playlist_bulk_remove_failed"));
            return;
        }

        List<PlaylistItem> removed = new ArrayList<>(result.removed().size());
        for (PlaylistItem item : selected) {
            if (result.removed().contains(item.setVideoId())) {
                removed.add(item);
                // The queue manager caches a per video edit result. Leaving stale entries there
                // would have it act on a video this playlist no longer holds.
                EditPlaylistRequest.clearVideoId(item.videoId());
            }
        }

        state.removeItems(removed);
        onSelectionChanged();
        if (overlay != null) {
            overlay.invalidate();
        }

        Utils.showToastShort(result.isCompleteSuccess()
                ? (removed.size() == 1
                        ? str("morphe_playlist_bulk_remove_success_one")
                        : str("morphe_playlist_bulk_remove_success", removed.size()))
                : str("morphe_playlist_bulk_remove_partial", removed.size(), result.failed().size()));
    }
}
