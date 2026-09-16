package app.joly0.extension;

import android.util.Log;

/**
 * Supplies the id of the playlist currently on screen.
 * <p>
 * It reads the {@code page_header} component's buffer, where the browse id appears as
 * {@code VL<playlistId>}. YouTube's own browse id hook was tried first and only ever reports
 * {@code FEwhat_to_watch}, the home feed, and the request URL carries no playlist id either
 * because it travels in the POST body.
 * <p>
 * Whether a playlist can be edited at all is a separate question, answered in
 * {@link PlaylistBulkRemove}: an editable playlist renders native {@code playlist_video_item}
 * rows rather than Litho components, so that check needs views, which a component hook never sees.
 */
public final class PlaylistHeader {

    private static final String LOG_TAG = "Joly0Patches";

    private static final String HEADER_IDENTIFIER = "page_header";

    /** Playlist whose header was most recently rendered, or null if none has been. */
    private static volatile String currentPlaylistId;

    private PlaylistHeader() {
    }

    /**
     * Injection point. Called off the main thread for every Litho component the app builds.
     *
     * @param context the component's conversion context.
     * @param buffer  the component's protobuf wrapper, left undecoded until it is wanted.
     */
    public static void onComponent(Object context, Object buffer) {
        try {
            if (!(context instanceof ConversionContextInterface conversionContext)) {
                return;
            }
            String identifier = conversionContext.patch_joly0_getIdentifier();
            if (identifier == null || !identifier.startsWith(HEADER_IDENTIFIER)) {
                return;
            }
            if (!(buffer instanceof ProtoBufferInterface protoBuffer)) {
                return;
            }

            byte[] bytes = protoBuffer.patch_joly0_encode();
            if (bytes == null || bytes.length == 0) {
                return;
            }

            String playlistId = extractPlaylistId(findAsciiStrings(bytes));
            if (playlistId != null && !playlistId.equals(currentPlaylistId)) {
                currentPlaylistId = playlistId;
                Log.i(LOG_TAG, "playlist page header: " + playlistId);
            }
        } catch (Exception ex) {
            Log.e(LOG_TAG, "onComponent failed", ex);
        }
    }

    /** Playlist whose header was most recently rendered, or null if none has been. */
    public static String getCurrentPlaylistId() {
        return currentPlaylistId;
    }

    /**
     * Pulls the playlist id out of a browse id of the form {@code VL<playlistId>}.
     * <p>
     * Ids can be very short: Watch Later is {@code VLWL} and Liked videos is {@code VLLL}, so a
     * minimum length cannot be used to rule out a false match. The longest candidate is taken
     * instead, since a stray "VL" in prose is followed by little or nothing id-shaped.
     */
    static String extractPlaylistId(String ascii) {
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

    /**
     * Every run of printable ASCII in the buffer, separated by newlines.
     * <p>
     * Runs shorter than four characters are dropped, which is the same floor the official bundle
     * uses. {@code VLWL} is exactly four, so the shortest real browse id still survives. The
     * separator matters: without it the tail of one run and the head of the next could form a
     * "VL" that is in neither.
     */
    private static String findAsciiStrings(byte[] buffer) {
        final int minimumAscii = 32;  // Space.
        final int maximumAscii = 126; // Tilde; 127 is delete.
        final int minimumRunLength = 4;

        StringBuilder builder = new StringBuilder(buffer.length);
        final int length = buffer.length;
        int start = 0;

        // Runs to length inclusive so a run ending at the last byte is still flushed.
        for (int end = 0; end <= length; end++) {
            final int value = end < length ? buffer[end] : -1;
            if (value >= minimumAscii && value <= maximumAscii) {
                continue;
            }
            if (end - start >= minimumRunLength) {
                for (int i = start; i < end; i++) {
                    builder.append((char) buffer[i]);
                }
                builder.append('\n');
            }
            start = end + 1;
        }

        return builder.toString();
    }
}
