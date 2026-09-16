/* Adapted from morphe-patches (GPLv3), app.morphe.extension.youtube.patches.playlist. */

package app.joly0.extension.playlist;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import app.joly0.extension.Utils;
import app.joly0.extension.requests.PlaylistItem;

/**
 * Works out which playlist entry each visible row shows.
 * <p>
 * An editable playlist, the only kind this feature can act on, renders its rows as ordinary
 * Android views rather than Litho components: {@code playlist_video_item} wrapping a
 * {@code title} TextView. That is a different rendering path from a read-only playlist such as a
 * channel's uploads, which is Litho and carries no readable text (docs/06, docs/09).
 * <p>
 * Because the title is readable, each row is matched to its entry by title, independently of
 * where it sits on screen. That is what lets the user reorder the playlist while the checkboxes
 * keep following their own videos.
 */
final class PlaylistRowTracker {

    /**
     * Left padding added to a row so the checkbox has space of its own.
     * <p>
     * Padding rather than a translation: padding makes the row's own content reflow narrower, so
     * nothing is pushed off the right edge. Translating the row would have shifted its overflow
     * menu past the screen edge. These rows are ordinary Android views, so this works; it would
     * not on the Litho rows a read-only playlist uses.
     */
    static final int GUTTER_DP = 34;

    /** Resource name of a row in an editable playlist. */
    private static final String ROW_RESOURCE_NAME = "playlist_video_item";
    /** Resource name of the title within a row. */
    private static final String TITLE_RESOURCE_NAME = "title";

    /** Whether any visible row could be matched to a playlist entry on the last pass. */
    private boolean matchedAnything;

    boolean isAnchored() {
        return matchedAnything;
    }

    void reset() {
        matchedAnything = false;
    }

    /**
     * Whether this list looks like an editable playlist at all, used to decide whether the
     * selection UI belongs on screen. Cheap: it only looks for one row.
     */
    static boolean looksLikeEditablePlaylist(ViewGroup recyclerView) {
        for (int i = 0, count = recyclerView.getChildCount(); i < count; i++) {
            if (findRow(recyclerView.getChildAt(i)) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recomputes the mapping for the currently visible rows.
     *
     * @param items the playlist as the API returned it, empty if not loaded yet.
     * @return the visible rows in screen order, each carrying the entry it maps to, or a null
     *         entry when the row could not be trusted.
     */
    List<TrackedRow> update(ViewGroup recyclerView, List<PlaylistItem> items) {
        List<View> rows = new ArrayList<>();
        List<String> titles = new ArrayList<>();

        for (int i = 0, count = recyclerView.getChildCount(); i < count; i++) {
            View row = findRow(recyclerView.getChildAt(i));
            if (row == null) {
                continue;
            }
            rows.add(row);
            titles.add(titleOf(row));
        }
        if (rows.isEmpty() || items.isEmpty()) {
            matchedAnything = false;
            return List.of();
        }

        // getChildAt order follows the layout manager's internal order, not screen order.
        sortByTop(recyclerView, rows, titles);

        // Each row is matched to its entry by title alone. Position is deliberately not used:
        // the user can drag videos into a different order, and then screen order and the order
        // the API returned no longer agree. An earlier version derived every row's index from its
        // distance to one anchor row, which made every checkbox go dead the moment anything was
        // reordered.
        Map<String, Deque<PlaylistItem>> byTitle = new HashMap<>();
        for (PlaylistItem item : items) {
            byTitle.computeIfAbsent(item.title(), key -> new ArrayDeque<>()).add(item);
        }

        boolean matched = false;
        List<TrackedRow> tracked = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            final String title = titles.get(i);
            PlaylistItem item = null;

            // Taking from a queue means a playlist holding the same video twice gives its two
            // rows two different entries rather than both pointing at the first.
            Deque<PlaylistItem> candidates = title.isEmpty() ? null : byTitle.get(title);
            if (candidates != null && !candidates.isEmpty()) {
                item = candidates.poll();
                matched = true;
            }
            tracked.add(new TrackedRow(rows.get(i), item));
        }

        matchedAnything = matched;
        return tracked;
    }

    /**
     * The row view within a RecyclerView child. The child is usually a swipe wrapper, so the row
     * itself sits one or more levels down.
     *
     * @return the row view, or null when the row matched nothing in the playlist.
     */
    private static View findRow(View child) {
        try {
            return Utils.getChildViewByResourceName(child, ROW_RESOURCE_NAME);
        } catch (Exception ex) {
            // A child that is not a playlist row at all.
            return null;
        }
    }

    /**
     * Colour of the row's title text, used to draw the checkbox. Taking it from the row means the
     * checkbox follows the app's light or dark theme without having to detect the theme.
     */
    static int titleColorOf(View row, int fallback) {
        try {
            View title = Utils.getChildViewByResourceName(row, TITLE_RESOURCE_NAME);
            if (title instanceof TextView textView) {
                return textView.getCurrentTextColor();
            }
        } catch (Exception ex) {
            // Fall through.
        }
        return fallback;
    }

    private static String titleOf(View row) {
        try {
            View title = Utils.getChildViewByResourceName(row, TITLE_RESOURCE_NAME);
            if (title instanceof TextView textView && textView.getText() != null) {
                return textView.getText().toString();
            }
        } catch (Exception ex) {
            // No title on this row.
        }
        return "";
    }

    private static void sortByTop(ViewGroup recyclerView, List<View> rows, List<String> titles) {
        for (int i = 1; i < rows.size(); i++) {
            View row = rows.get(i);
            String title = titles.get(i);
            final float top = rowTop(row, recyclerView);
            int j = i - 1;
            while (j >= 0 && rowTop(rows.get(j), recyclerView) > top) {
                rows.set(j + 1, rows.get(j));
                titles.set(j + 1, titles.get(j));
                j--;
            }
            rows.set(j + 1, row);
            titles.set(j + 1, title);
        }
    }

    /**
     * Row top in list coordinates, following any in-progress gesture.
     * <p>
     * The row is nested inside the list's child, so the offsets accumulate. Translation is added
     * as well as layout position: while a row is being dragged to a new place, or swiped
     * sideways, the app moves it with a translation and leaves its layout position alone. Reading
     * only the layout position left the checkbox sitting in the row's old slot until the drag
     * finished, which is exactly the moment the user is checking that the tick still belongs to
     * the video they are moving.
     */
    static float rowTop(View row, ViewGroup recyclerView) {
        return accumulate(row, recyclerView, true);
    }

    /** Row left in list coordinates, following any in-progress gesture. */
    static float rowLeft(View row, ViewGroup recyclerView) {
        return accumulate(row, recyclerView, false);
    }

    /**
     * Walks up from the row adding each ancestor's offset, stopping at the list itself.
     * <p>
     * The fork stopped on `getParent() instanceof RecyclerView`. That type is the app's own
     * bundled copy and is not on this module's classpath, so the list is identified by identity
     * instead. That is also stricter than the original: it stops at this list rather than at
     * whatever RecyclerView happens to be nearest.
     */
    private static float accumulate(View row, ViewGroup recyclerView, boolean vertical) {
        float total = 0;
        View current = row;
        while (current != null && current != recyclerView) {
            total += vertical
                    ? current.getTop() + current.getTranslationY()
                    : current.getLeft() + current.getTranslationX();
            current = current.getParent() instanceof View parent ? parent : null;
        }
        return total;
    }

    /**
     * Inserts, or removes, the checkbox gutter on a row. Re-applied on every pass because the
     * app rebinds recycled rows and resets their padding.
     */
    static void setGutter(View row, int gutterPx) {
        if (row.getPaddingLeft() != gutterPx) {
            row.setPadding(gutterPx, row.getPaddingTop(), row.getPaddingRight(),
                    row.getPaddingBottom());
        }
    }

    /** Clears the gutter from every row currently attached, used when tearing the feature down. */
    static void clearGutters(ViewGroup recyclerView) {
        for (int i = 0, count = recyclerView.getChildCount(); i < count; i++) {
            View row = findRow(recyclerView.getChildAt(i));
            if (row != null) {
                setGutter(row, 0);
            }
        }
    }

    /**
     * A visible row and the playlist entry it shows.
     *
     * @param view the row, used for its bounds.
     * @param item the entry, or null when the row's title matched nothing in the playlist.
     */
    record TrackedRow(View view, PlaylistItem item) {
    }
}
