/* Adapted from morphe-patches (GPLv3), app.morphe.extension.shared. */

package app.joly0.extension;

import android.util.Log;

import java.util.function.Supplier;

/**
 * Logging for the extension, funnelled through one tag.
 * <p>
 * Nothing passed to this class may be a credential: the authorization header this extension
 * holds is a live credential and must never reach the log.
 */
public final class Logger {

    private static final String TAG = "Joly0Patches";

    private Logger() {
    }

    public static void printDebug(Supplier<String> message) {
        Log.d(TAG, message.get());
    }

    public static void printInfo(Supplier<String> message) {
        Log.i(TAG, message.get());
    }

    public static void printInfo(Supplier<String> message, Throwable ex) {
        Log.i(TAG, message.get(), ex);
    }

    public static void printException(Supplier<String> message, Throwable ex) {
        Log.e(TAG, message.get(), ex);
    }
}
