/* Adapted from morphe-patches (GPLv3), app.morphe.extension.shared. */

package app.joly0.extension;

import android.os.Looper;

public final class Utils {

    private Utils() {
    }

    /**
     * @throws IllegalStateException if called on the main looper.
     */
    public static void verifyOffMainThread() {
        if (Looper.getMainLooper().isCurrentThread()) {
            throw new IllegalStateException("Must not be called on the main thread");
        }
    }
}
