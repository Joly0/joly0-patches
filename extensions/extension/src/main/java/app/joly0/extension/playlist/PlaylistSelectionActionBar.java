/* Adapted from morphe-patches (GPLv3), app.morphe.extension.youtube.patches.playlist. */

package app.joly0.extension.playlist;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import static app.joly0.extension.Strings.str;

/**
 * The bar along the bottom of a playlist while rows are selected: how many are picked, select
 * all, clear, and remove.
 * <p>
 * Built in code rather than inflated from XML, because it is added to YouTube's own view
 * hierarchy and carrying a layout resource through the patcher for four widgets is not worth it.
 */
@SuppressLint("ViewConstructor")
final class PlaylistSelectionActionBar extends LinearLayout {

    private final int basePadding;
    /** How many rows are picked, kept because visibility depends on it and on the page. */
    private int selectedCount;
    /** False while the playlist page is not the page on screen. */
    private boolean pageVisible = true;
    private int systemBottomInset;
    private int bottomOffset = -1;
    private final TextView countLabel;
    private final Button removeButton;

    PlaylistSelectionActionBar(Context context, Runnable onSelectAll, Runnable onClear,
                               Runnable onRemove) {
        super(context);

        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        basePadding = dp(6);
        setPadding(basePadding, basePadding, basePadding, basePadding);
        setBackgroundColor(Color.argb(235, 24, 24, 24));
        // Sit above the list and take its own touches.
        setClickable(true);
        applyBottomInset();

        countLabel = new TextView(context);
        countLabel.setTextColor(Color.WHITE);
        countLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        addView(countLabel, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        addView(textButton(context, str("playlist_bulk_remove_select_all"), onSelectAll));
        addView(textButton(context, str("playlist_bulk_remove_clear"), onClear));

        removeButton = textButton(context, str("playlist_bulk_remove_remove"), onRemove);
        removeButton.setTextColor(Color.rgb(255, 120, 120));
        addView(removeButton);

        setSelectedCount(0, false);
    }

    private Button textButton(Context context, CharSequence text, Runnable onClick) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setOnClickListener(v -> onClick.run());

        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.TRANSPARENT);
        background.setCornerRadius(dp(15));
        background.setStroke(dp(1), Color.argb(90, 255, 255, 255));
        button.setBackground(background);

        final int horizontal = dp(10);
        button.setPadding(horizontal, 0, horizontal, 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);

        LayoutParams params = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(30));
        params.leftMargin = dp(6);
        button.setLayoutParams(params);
        return button;
    }

    /**
     * Keeps the bar clear of the system navigation bar. Without this it renders underneath the
     * gesture bar or the nav buttons, which is where it first ended up.
     * <p>
     * Both routes are used because neither is dependable alone: the activity's content frame does
     * not reliably dispatch insets to a view added to it after the fact, so the listener may never
     * fire, and getRootWindowInsets returns null before the view is attached.
     */
    private void applyBottomInset() {
        setOnApplyWindowInsetsListener((view, insets) -> {
            setBottomInset(bottomOf(insets));
            return insets;
        });
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        WindowInsets insets = getRootWindowInsets();
        if (insets != null) {
            setBottomInset(bottomOf(insets));
        }
        requestApplyInsets();
    }

    private static int bottomOf(WindowInsets insets) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? insets.getInsets(WindowInsets.Type.systemBars()).bottom
                : insets.getSystemWindowInsetBottom();
    }

    private void setBottomInset(int bottom) {
        systemBottomInset = bottom;
        applyPadding();
    }

    /**
     * Lifts the bar above YouTube's own bottom navigation, instead of covering it.
     * <p>
     * Covering it made the Home button unreachable. When the bar is lifted, the system inset is
     * already absorbed by the navigation bar below, so it must not be added twice.
     *
     * @param px height of the navigation bar, or 0 when it is hidden.
     */
    void setBottomOffset(int px) {
        if (bottomOffset == px) {
            return;
        }
        bottomOffset = px;

        if (getLayoutParams() instanceof FrameLayout.LayoutParams params) {
            params.bottomMargin = px;
            setLayoutParams(params);
        }
        applyPadding();
    }

    private void applyPadding() {
        final int bottom = bottomOffset > 0 ? 0 : systemBottomInset;
        setPadding(basePadding, basePadding, basePadding, basePadding + bottom);
    }

    /**
     * The bar only exists while something is selected. With nothing picked it has nothing to say,
     * and it was covering part of the list the whole time the feature was switched on. The
     * checkboxes are the affordance that the feature is available; this is just the actions.
     */
    void setSelectedCount(int count, boolean enabled) {
        selectedCount = count;
        applyVisibility();
        if (count == 0) {
            return;
        }

        countLabel.setText(str("playlist_bulk_remove_selected_count", count));
        removeButton.setEnabled(enabled);
        removeButton.setAlpha(enabled ? 1f : 0.4f);
    }

    /**
     * Follows the playlist page on and off screen. Without this the bar would reappear over
     * whatever replaced the playlist the next time the selection changed.
     */
    void setPageVisible(boolean visible) {
        if (pageVisible != visible) {
            pageVisible = visible;
            applyVisibility();
        }
    }

    private void applyVisibility() {
        final int visibility = pageVisible && selectedCount > 0 ? VISIBLE : GONE;
        if (getVisibility() != visibility) {
            setVisibility(visibility);
        }
    }

    /**
     * Layout params that pin this bar to the bottom of the list's container.
     */
    FrameLayout.LayoutParams bottomLayoutParams() {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM;
        return params;
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }
}
