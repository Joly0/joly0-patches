/* Adapted from morphe-patches (GPLv3), app.morphe.extension.youtube.patches.playlist. */

package app.joly0.extension.playlist;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

import app.joly0.extension.Logger;
import app.joly0.extension.Utils;
import app.joly0.extension.requests.PlaylistItem;

import static app.joly0.extension.Strings.str;

/**
 * Draws a checkbox over each visible playlist row and takes the taps on them.
 * <p>
 * It has to draw rather than add views: the rows are LithoViews, and Litho throws
 * {@code UnsupportedOperationException: Adding Views manually within LithoViews is not supported}
 * when anything is added to one. Drawing over them from a sibling view is untouched by that.
 * <p>
 * The overlay only consumes a touch that lands on a checkbox. Everything else is declined so it
 * reaches the list underneath and scrolling, tapping a video and so on all behave normally.
 */
@SuppressLint("ViewConstructor")
final class PlaylistSelectionOverlay extends View {

    private static final int CHECKBOX_DP = 18;
    private static final int MARGIN_DP = 9;
    /** Touch target padding around the drawn box, so a small box is still comfortable to hit. */
    private static final int TOUCH_SLOP_DP = 12;

    private final ViewGroup recyclerView;
    private final PlaylistRowTracker tracker;
    private final PlaylistSelectionState state;
    private final Runnable onSelectionChanged;
    /** Called when a visible row cannot be identified from what has been fetched. */
    private final Runnable onNeedMoreItems;

    private final Paint boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path tickPath = new Path();
    private final RectF boxRect = new RectF();
    private final RectF hitRect = new RectF();
    private final int[] recyclerLocation = new int[2];
    private final int[] overlayLocation = new int[2];
    private final int[] offset = new int[2];
    private final int[] obstructionLocation = new int[2];

    /**
     * YouTube's bottom navigation bar, and our own action bar. Both are drawn over the list.
     * Either may be null when the bar is not present.
     */
    View bottomNavBar;
    View actionBar;

    private final float checkboxSize;
    private final float margin;
    private final float touchSlop;
    /** Distance a finger must travel before a press on a checkbox becomes a scroll. */
    private final float dragSlop;
    private final int gutterPx;

    /**
     * Entry being pressed, so a tap only counts if it goes down and up on the same checkbox.
     * Null when nothing is pressed.
     */
    private PlaylistItem pressedItem;

    /** Where the current gesture went down, in this view's coordinates. */
    private float downX;
    private float downY;

    /**
     * Set once a gesture has been handed to the list as a scroll. The rest of that gesture goes
     * there too, whatever it passes over.
     */
    private boolean forwardingToList;

    PlaylistSelectionOverlay(Context context, ViewGroup recyclerView, PlaylistRowTracker tracker,
                             PlaylistSelectionState state, Runnable onSelectionChanged,
                             Runnable onNeedMoreItems) {
        super(context);
        this.recyclerView = recyclerView;
        this.tracker = tracker;
        this.state = state;
        this.onSelectionChanged = onSelectionChanged;
        this.onNeedMoreItems = onNeedMoreItems;

        checkboxSize = dp(CHECKBOX_DP);
        margin = dp(MARGIN_DP);
        touchSlop = dp(TOUCH_SLOP_DP);
        // The system's own drag threshold, so letting go of a gesture happens at the same
        // distance the list itself would have started scrolling at.
        dragSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        gutterPx = (int) dp(PlaylistRowTracker.GUTTER_DP);

        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(dp(1.6f));
        boxPaint.setColor(Color.WHITE);

        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setColor(Color.WHITE);

        tickPaint.setStyle(Paint.Style.STROKE);
        tickPaint.setStrokeWidth(dp(2f));
        tickPaint.setColor(Color.BLACK);
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

    }

    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    /**
     * The overlay lives in the activity's content frame rather than beside the list, because the
     * list's parent is a SwipeRefreshLayout, which lays out only its single scrolling child and
     * leaves anything else at zero size. So row coordinates, which are relative to the list,
     * have to be shifted into this view's space.
     */
    private void computeOffset(int[] out) {
        recyclerView.getLocationInWindow(recyclerLocation);
        getLocationInWindow(overlayLocation);
        out[0] = recyclerLocation[0] - overlayLocation[0];
        out[1] = recyclerLocation[1] - overlayLocation[1];
    }

    @Override
    protected void onDraw(Canvas canvas) {
        try {
            List<PlaylistRowTracker.TrackedRow> rows = tracker.update(recyclerView, state.items());
            if (rows.isEmpty()) {
                return;
            }

            computeOffset(offset);

            canvas.save();
            // Confine drawing to the part of the list that is actually visible: not over the
            // header, and not over the bottom navigation bar or our own action bar.
            canvas.clipRect(offset[0], offset[1],
                    offset[0] + recyclerView.getWidth(), contentBottom());
            canvas.translate(offset[0], offset[1]);

            final boolean anchored = tracker.isAnchored();
            boolean sawUnknownRow = false;
            for (PlaylistRowTracker.TrackedRow row : rows) {
                if (row.item() == null) {
                    sawUnknownRow = true;
                }
                // An index the playlist does not have, or a lost anchor, means the mapping is
                // not trustworthy. Draw the box faded and refuse the tap rather than pretend.
                // item == null means the row's title matched nothing in the fetched playlist.
                final boolean usable = anchored && state.isSelectable(row.item());
                PlaylistRowTracker.setGutter(row.view(), gutterPx);
                drawCheckbox(canvas, row, usable);
            }
            canvas.restore();

            // Scrolled past what has been fetched. Ask for the rest rather than leaving these
            // rows permanently dead, which is how this looked on a long playlist.
            if (sawUnknownRow && !state.isComplete()) {
                onNeedMoreItems.run();
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Playlist overlay draw failure", ex);
        }
    }

    /**
     * Fills the rect for a row's checkbox. Shared by drawing and hit testing so the two can never
     * disagree about where the box is.
     * <p>
     * The box goes in the row's top left corner, over the corner of the thumbnail. Centring it
     * vertically put it in the middle of the thumbnail, covering the picture. The rows cannot be
     * shifted aside to make room because Litho draws them, so overlapping a corner is the least
     * destructive place available.
     */
    private void fillBoxRect(View row, RectF out) {
        // Both coordinates come from the tracker, which folds in any drag or swipe translation so
        // the checkbox travels with its row instead of staying in the row's old slot.
        final float centerY = PlaylistRowTracker.rowTop(row, recyclerView) + row.getHeight() / 2f;
        final float left = PlaylistRowTracker.rowLeft(row, recyclerView) + margin;
        out.set(left, centerY - checkboxSize / 2f,
                left + checkboxSize, centerY + checkboxSize / 2f);
    }

    /**
     * Lowest y, in this view's coordinates, that the overlay may use. The list extends behind
     * YouTube's bottom navigation bar and behind our own action bar, and drawing or taking
     * touches down there put a checkbox on top of the Home button.
     */
    private float contentBottom() {
        float bottom = offset[1] + recyclerView.getHeight();
        for (View obstruction : new View[]{bottomNavBar, actionBar}) {
            if (obstruction != null && obstruction.getVisibility() == VISIBLE) {
                obstruction.getLocationInWindow(obstructionLocation);
                getLocationInWindow(overlayLocation);
                bottom = Math.min(bottom, obstructionLocation[1] - overlayLocation[1]);
            }
        }
        return bottom;
    }

    private void drawCheckbox(Canvas canvas, PlaylistRowTracker.TrackedRow row, boolean usable) {
        View view = row.view();
        fillBoxRect(view, boxRect);

        // The box sits in a gutter over the page background, whose colour depends on the app's
        // theme. Borrowing the row's own title colour tracks that without detecting the theme.
        final int color = PlaylistRowTracker.titleColorOf(view, Color.GRAY);
        boxPaint.setColor(color);
        fillPaint.setColor(color);
        tickPaint.setColor(contrastingWith(color));

        final int alpha = usable ? 255 : 80;
        boxPaint.setAlpha(alpha);
        fillPaint.setAlpha(alpha);
        tickPaint.setAlpha(alpha);

        final float radius = dp(3);
        final boolean selected = usable && state.isSelected(row.item());

        if (selected) {
            canvas.drawRoundRect(boxRect, radius, radius, fillPaint);
            final float centerY = boxRect.centerY();
            tickPath.reset();
            tickPath.moveTo(boxRect.left + checkboxSize * 0.24f, centerY);
            tickPath.lineTo(boxRect.left + checkboxSize * 0.43f, centerY + checkboxSize * 0.20f);
            tickPath.lineTo(boxRect.left + checkboxSize * 0.76f, centerY - checkboxSize * 0.22f);
            canvas.drawPath(tickPath, tickPaint);
        } else {
            canvas.drawRoundRect(boxRect, radius, radius, boxPaint);
        }
    }

    /** Black or white, whichever stands out against the given colour. */
    private static int contrastingWith(int color) {
        final double luminance = (0.299 * Color.red(color)
                + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255.0;
        return luminance > 0.5 ? Color.BLACK : Color.WHITE;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        try {
            computeOffset(offset);
            final float x = event.getX() - offset[0];
            final float y = event.getY() - offset[1];
            final int action = event.getActionMasked();

            // A gesture already handed over stays handed over, even if it wanders out of the
            // gutter or past the bottom of the list. Android delivers the whole of a gesture to
            // whoever took the DOWN, so bailing out here would leave the list mid-scroll with an
            // UP it never receives.
            if (forwardingToList) {
                forwardToList(event, x, y);
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    forwardingToList = false;
                }
                return true;
            }

            // Outside the usable area: let it through, so the navigation bar underneath keeps
            // working. Without this a checkbox drawn near the bottom swallowed Home button taps.
            if (event.getY() > contentBottom()
                    || x < 0 || y < 0 || x > recyclerView.getWidth() || y > recyclerView.getHeight()) {
                return false;
            }

            // A tap anywhere in the checkbox gutter belongs to the overlay, even when the row is
            // not selectable yet. Declining it let the tap reach the row underneath, so tapping a
            // checkbox while the playlist was still loading opened and played the video.
            if (!isInGutter(x)) {
                return false;
            }

            final PlaylistItem hit = itemAtPoint(x, y);

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    pressedItem = hit;
                    downX = x;
                    downY = y;
                    // Consumed either way: a gutter tap must never fall through to the row.
                    return true;

                case MotionEvent.ACTION_MOVE:
                    // A finger that travels is scrolling, not ticking. The gutter used to hold
                    // on to the whole gesture, so the list only scrolled if you happened to
                    // start on the thumbnail or the title.
                    if (Math.abs(x - downX) > dragSlop || Math.abs(y - downY) > dragSlop) {
                        pressedItem = null;
                        forwardingToList = true;
                        // The list never saw the DOWN, and a scroll that starts from nowhere is
                        // ignored, so it gets one at the point the finger actually started from.
                        sendSyntheticDown(event);
                        forwardToList(event, x, y);
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                    if (pressedItem != null && hit != null
                            && pressedItem.setVideoId().equals(hit.setVideoId())) {
                        toggle(hit);
                    } else if (hit == null && state.isLoading()) {
                        Utils.showToastShort(str("playlist_bulk_remove_loading"));
                    }
                    pressedItem = null;
                    return true;

                case MotionEvent.ACTION_CANCEL:
                    pressedItem = null;
                    return true;

                default:
                    return true;
            }
        } catch (Exception ex) {
            Logger.printException(() -> "Playlist overlay touch failure", ex);
            pressedItem = null;
            forwardingToList = false;
            return false;
        }
    }

    /**
     * Passes an event on to the list, moved into the list's own coordinates. The list is not this
     * view's parent, so the framework does no translation for us.
     */
    private void forwardToList(MotionEvent event, float x, float y) {
        MotionEvent copy = MotionEvent.obtain(event);
        try {
            copy.setLocation(x, y);
            recyclerView.dispatchTouchEvent(copy);
        } finally {
            copy.recycle();
        }
    }

    /** Opens the forwarded gesture with the DOWN the list missed while this view held it. */
    private void sendSyntheticDown(MotionEvent source) {
        MotionEvent down = MotionEvent.obtain(source.getDownTime(), source.getEventTime(),
                MotionEvent.ACTION_DOWN, downX, downY, source.getMetaState());
        try {
            recyclerView.dispatchTouchEvent(down);
        } finally {
            down.recycle();
        }
    }

    /** @param item the entry to toggle, null is tolerated and does nothing. */
    private void toggle(PlaylistItem item) {
        state.toggle(item);
        invalidate();
        onSelectionChanged.run();
    }

    /**
     * Whether an x coordinate falls in the checkbox gutter, regardless of any row's state.
     * Measured from the list's own left edge rather than a row's, because a row being swiped
     * sideways must not drag the whole touch region with it.
     */
    private boolean isInGutter(float x) {
        return x >= 0 && x <= margin + checkboxSize + touchSlop;
    }

    /**
     * @return the entry whose checkbox contains the point, or null.
     */
    private PlaylistItem itemAtPoint(float x, float y) {
        if (!tracker.isAnchored()) {
            return null;
        }

        for (PlaylistRowTracker.TrackedRow row : tracker.update(recyclerView, state.items())) {
            if (!state.isSelectable(row.item())) {
                continue;
            }
            fillBoxRect(row.view(), hitRect);
            if (x >= hitRect.left - touchSlop && x <= hitRect.right + touchSlop
                    && y >= hitRect.top - touchSlop && y <= hitRect.bottom + touchSlop) {
                return row.item();
            }
        }
        return null;
    }
}
