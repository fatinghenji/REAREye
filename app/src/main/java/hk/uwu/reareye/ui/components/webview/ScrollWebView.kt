package hk.uwu.reareye.ui.components.webview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ScrollWebView extends WebView {
    private final int touchSlop;
    private float lastTouchY;

    public ScrollWebView(@NonNull Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public ScrollWebView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    public ScrollWebView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchY = event.getY();
                requestParentDisallowIntercept(true);
                break;
            case MotionEvent.ACTION_MOVE:
                float deltaY = event.getY() - lastTouchY;
                lastTouchY = event.getY();
                if (Math.abs(deltaY) >= touchSlop) {
                    boolean fingerMovingDown = deltaY > 0;
                    boolean canConsumeMove = fingerMovingDown
                            ? canScrollVertically(-1)
                            : canScrollVertically(1);
                    requestParentDisallowIntercept(canConsumeMove);
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                requestParentDisallowIntercept(false);
                break;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
        if (clampedY) {
            requestParentDisallowIntercept(false);
        }
        super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
    }

    private void requestParentDisallowIntercept(boolean disallowIntercept) {
        ViewParent parent = getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallowIntercept);
            if (parent instanceof View) {
                parent = ((View) parent).getParent();
            } else {
                break;
            }
        }
    }
}
