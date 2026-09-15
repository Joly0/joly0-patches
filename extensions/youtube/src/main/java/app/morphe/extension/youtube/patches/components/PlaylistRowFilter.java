/*
 * Copyright 2026 Morphe.
 * https://github.com/MorpheApp/morphe-patches
 *
 * See the included NOTICE file for GPLv3 Section 7 terms that apply to Morphe contributions.
 */

package app.morphe.extension.youtube.patches.components;

import androidx.annotation.Nullable;

import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.patches.components.BufferAsciiStrings;
import app.morphe.extension.shared.patches.components.ContextInterface;
import app.morphe.extension.shared.patches.components.Filter;
import app.morphe.extension.shared.patches.components.StringFilterGroup;
import app.morphe.extension.youtube.settings.Settings;

/**
 * Supplies the id of the playlist currently on screen.
 * <p>
 * It reads the {@code page_header} component's buffer, where the browse id appears as
 * {@code VL<playlistId>}. YouTube's own browse id hook was tried first and only ever reports
 * {@code FEwhat_to_watch}, the home feed (docs/04, docs/07).
 * <p>
 * Detecting that a list <em>is</em> an editable playlist is not done here. An editable playlist
 * renders native {@code playlist_video_item} rows rather than Litho components, so that check
 * lives in PlaylistRowTracker where the views actually are. A filter never sees an
 * android.view.View.
 */
public final class PlaylistRowFilter extends Filter {

    private static final String HEADER_IDENTIFIER = "page_header";

    /** Playlist whose header was most recently rendered, or null if none has been. */
    @Nullable
    public static volatile String currentPlaylistId;

    public PlaylistRowFilter() {
        addPathCallbacks(new StringFilterGroup(
                Settings.PLAYLIST_BULK_REMOVE,
                HEADER_IDENTIFIER
        ));
    }

    @Override
    public boolean isFiltered(ContextInterface contextInterface,
                              String identifier,
                              String accessibility,
                              String path,
                              byte[] buffer,
                              BufferAsciiStrings asciiStrings,
                              StringFilterGroup matchedGroup,
                              FilterContentType contentType,
                              int contentIndex) {
        try {
            if (identifier != null && identifier.startsWith(HEADER_IDENTIFIER)) {
                String playlistId = extractPlaylistId(asciiStrings.getStrings());
                if (playlistId != null && !playlistId.equals(currentPlaylistId)) {
                    currentPlaylistId = playlistId;
                    Logger.printDebug(() -> "Playlist page header: " + playlistId);
                }
            }
        } catch (Exception ex) {
            Logger.printException(() -> "PlaylistRowFilter failure", ex);
        }

        // Observe only. Nothing is ever hidden.
        return false;
    }

    /**
     * Pulls the playlist id out of a browse id of the form {@code VL<playlistId>}.
     * <p>
     * Ids can be very short: Watch Later is {@code VLWL} and Liked videos is {@code VLLL}, so a
     * minimum length cannot be used to rule out a false match. The longest candidate is taken
     * instead, since a stray "VL" in prose is followed by little or nothing id-shaped.
     */
    @Nullable
    static String extractPlaylistId(@Nullable String ascii) {
        if (ascii == null) {
            return null;
        }

        String best = null;
        int index = ascii.indexOf("VL");
        while (index >= 0) {
            int end = index + 2;
            while (end < ascii.length() && isIdChar(ascii.charAt(end))) {
                end++;
            }
            final int length = end - index - 2;
            if (length >= 2 && (best == null || length > best.length())) {
                best = ascii.substring(index + 2, end);
            }
            index = ascii.indexOf("VL", index + 1);
        }
        return best;
    }

    private static boolean isIdChar(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                || c == '_' || c == '-';
    }
}
