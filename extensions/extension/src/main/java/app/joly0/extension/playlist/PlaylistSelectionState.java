/* Adapted from morphe-patches (GPLv3), app.morphe.extension.youtube.patches.playlist. */

package app.joly0.extension.playlist;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import app.joly0.extension.requests.PlaylistItem;

/**
 * What is selected, and what the playlist actually contains.
 * <p>
 * Selection is held as playlist indices rather than as row views, because row views are recycled
 * and say nothing about their own identity. Indices are resolved to real entries through
 * {@link #itemAt(int)} only at the point of acting on them.
 * <p>
 * Main thread only.
 */
final class PlaylistSelectionState {

    /**
     * Set video ids of the picked entries.
     * <p>
     * Keyed by the entry's own id rather than by its position: the user can drag videos into a
     * different order while selecting, and an index would then point at a different video than
     * the one that was ticked.
     */
    private final Set<String> selected = new LinkedHashSet<>();

    private List<PlaylistItem> items = List.of();

    /** Null until the playlist id is known. */
    private String playlistId;

    private boolean loading;

    /** Whether every page of the playlist has been read. */
    private boolean complete;

    /**
     * Replaces the known entries. Selection is kept when this is another page of the same
     * playlist, so ticks made while a long playlist is still loading are not wiped by its own
     * next page arriving.
     */
    void setPlaylist(String playlistId, List<PlaylistItem> items) {
        if (!playlistId.equals(this.playlistId)) {
            selected.clear();
            complete = false;
        }
        this.playlistId = playlistId;
        this.items = items;
    }

    void setLoading(boolean loading) {
        this.loading = loading;
    }

    void setComplete(boolean complete) {
        this.complete = complete;
    }

    boolean isComplete() {
        return complete;
    }

    boolean isLoading() {
        return loading;
    }

    /** True once the playlist contents are known and selections can be resolved to videos. */
    boolean isReady() {
        return playlistId != null && !items.isEmpty();
    }

    /**
     * The current playlist id, or null when it is not known yet.
     */
    String playlistId() {
        return playlistId;
    }

    /** The playlist as fetched, in order. Empty until loaded. */
    List<PlaylistItem> items() {
        return items;
    }

    int itemCount() {
        return items.size();
    }

    /**
     * The entry at an index, or null when the index is out of range.
     */
    PlaylistItem itemAt(int index) {
        return index >= 0 && index < items.size() ? items.get(index) : null;
    }

    /** Whether an entry can be selected. Null means the row matched nothing in the playlist. */
    boolean isSelectable(PlaylistItem item) {
        return isReady() && item != null;
    }

    boolean isSelected(PlaylistItem item) {
        return item != null && selected.contains(item.setVideoId());
    }

    /**
     * @return the new selected state, or false if the entry could not be selected.
     */
    boolean toggle(PlaylistItem item) {
        if (!isSelectable(item)) {
            return false;
        }
        if (selected.remove(item.setVideoId())) {
            return false;
        }
        selected.add(item.setVideoId());
        return true;
    }

    void clearSelection() {
        selected.clear();
    }

    void selectAll() {
        if (!isReady()) {
            return;
        }
        for (PlaylistItem item : items) {
            selected.add(item.setVideoId());
        }
    }

    int selectedCount() {
        return selected.size();
    }

    /** Selected entries, in playlist order rather than the order they were picked. */
    List<PlaylistItem> selectedItems() {
        List<PlaylistItem> result = new ArrayList<>(selected.size());
        for (PlaylistItem item : items) {
            if (selected.contains(item.setVideoId())) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Drops the entries that were just removed and renumbers what is left, so the view can carry
     * on without a full refetch.
     */
    void removeItems(List<PlaylistItem> removed) {
        if (removed.isEmpty()) {
            return;
        }
        Set<String> removedSetVideoIds = new LinkedHashSet<>();
        for (PlaylistItem item : removed) {
            removedSetVideoIds.add(item.setVideoId());
        }

        List<PlaylistItem> remaining = new ArrayList<>(items.size() - removed.size());
        for (PlaylistItem item : items) {
            if (!removedSetVideoIds.contains(item.setVideoId())) {
                remaining.add(new PlaylistItem(item.videoId(), item.setVideoId(), item.title(),
                        item.author(), item.thumbnailUrl(), remaining.size()));
            }
        }

        items = remaining;
        selected.clear();
    }
}
