/* Adapted from morphe-patches (GPLv3), app.morphe.extension.shared. */

package app.joly0.extension;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public final class Utils {

    private static volatile Context context;

    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    private static final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(4,
            new ThreadFactory() {
                private final AtomicInteger count = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable,
                            "Joly0Background-" + count.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            });

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

    /**
     * Remembers the application context, handed over by the injection point. Toasts need one
     * and the views this feature adds do not always carry the application context themselves.
     */
    public static void setContext(Context applicationContext) {
        context = applicationContext;
    }

    /** The application context, or null if the injection point has not run yet. */
    public static Context getContext() {
        return context;
    }

    /**
     * The activity a view is attached to, or null if there is none. The fork gets the activity
     * from a hook this bundle does not have, so it is derived from the view's context instead,
     * unwrapping it until an Activity is found.
     */
    public static Activity activityOf(View view) {
        if (view == null) {
            return null;
        }
        Context current = view.getContext();
        while (current instanceof ContextWrapper wrapper) {
            if (current instanceof Activity activity) {
                return activity;
            }
            current = wrapper.getBaseContext();
        }
        return null;
    }

    /**
     * The child view carrying the given resource name, or null when the name resolves to no id
     * or the view is not present.
     */
    @SuppressWarnings("unchecked")
    public static <R extends View> R getChildViewByResourceName(View view, String name) {
        final int id = view.getResources().getIdentifier(name, "id",
                view.getContext().getPackageName());
        if (id == 0) {
            return null;
        }
        return (R) view.findViewById(id);
    }

    /**
     * Shows a short toast on the main thread. Safe to call from any thread. Does nothing when
     * the context is not known yet.
     */
    public static void showToastShort(String message) {
        runOnMainThread(() -> {
            Context current = getContext();
            if (current == null) {
                Logger.printDebug(() -> "showToastShort dropped, no context: " + message);
                return;
            }
            Toast.makeText(current, message, Toast.LENGTH_SHORT).show();
        });
    }

    /** Runs on the main thread. */
    public static void runOnMainThread(Runnable runnable) {
        mainHandler.post(runnable);
    }

    /** Runs on a small shared pool, off the main thread. */
    public static void runOnBackgroundThread(Runnable runnable) {
        backgroundExecutor.execute(runnable);
    }

    /**
     * Shows the dialog on the main thread, unless the activity is already going away, in which
     * case the dialog is skipped.
     */
    public static void showDialog(Activity activity, Dialog dialog) {
        runOnMainThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            dialog.show();
        });
    }
}
