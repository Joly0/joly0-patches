package app.joly0.extension;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * English UI strings for the playlist bulk remove feature, inlined into code.
 * <p>
 * The fork these strings come from resolves them from patched-in Android string resources, so
 * they are localized there. This bundle has no resource patching, so the English text is carried
 * here verbatim instead and is not localized.
 * <p>
 * Keys deliberately keep the fork's {@code StringRef.str(key, args)} call shape so the ported
 * call sites read the same, minus the {@code morphe_} prefix the resource names carried.
 */
public final class Strings {

    private static final Map<String, String> VALUES = new HashMap<>();

    static {
        VALUES.put("playlist_bulk_remove_select_all", "All");
        VALUES.put("playlist_bulk_remove_clear", "Clear");
        VALUES.put("playlist_bulk_remove_remove", "Remove");
        VALUES.put("playlist_bulk_remove_selected_count", "%d selected");
        VALUES.put("playlist_bulk_remove_not_logged_in", "Sign in to edit playlists");
        VALUES.put("playlist_bulk_remove_load_failed",
                "Nothing to select here. This playlist is either empty, or not one you can edit");
        VALUES.put("playlist_bulk_remove_loading", "Loading playlist\u2026");
        VALUES.put("playlist_bulk_remove_confirm_title_one", "Remove this video?");
        VALUES.put("playlist_bulk_remove_confirm_title", "Remove %d videos?");
        VALUES.put("playlist_bulk_remove_confirm_message",
                "These will be removed from the playlist. Check the list is what you picked:\n\n%s");
        VALUES.put("playlist_bulk_remove_confirm_more", "\n and %d more");
        VALUES.put("playlist_bulk_remove_confirm_button", "Remove");
        VALUES.put("playlist_bulk_remove_removing", "Removing\u2026");
        VALUES.put("playlist_bulk_remove_failed", "Could not remove the selected videos");
        VALUES.put("playlist_bulk_remove_success_one", "Removed 1 video");
        VALUES.put("playlist_bulk_remove_success", "Removed %d videos");
        VALUES.put("playlist_bulk_remove_partial", "Removed %1$d videos, %2$d failed");
    }

    private Strings() {
    }

    /**
     * The string for a key, formatted with the given arguments when there are any. Returns the
     * key itself if it is somehow unknown, so a typo shows up on screen rather than crashing.
     */
    public static String str(String key, Object... args) {
        String value = VALUES.get(key);
        if (value == null) {
            return key;
        }
        if (args == null || args.length == 0) {
            return value;
        }
        return String.format(Locale.getDefault(), value, args);
    }
}
