package me.leon.tvlauncher;

import android.app.Activity;
import android.app.Instrumentation;
import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.ImageView;
import java.util.concurrent.atomic.AtomicReference;

/** On-device checks against real views and lifecycle, without third-party test dependencies. */
public final class LauncherSmokeInstrumentation extends Instrumentation {
    private Activity activity;
    private int checks;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            checkNativeBlur();
            checkClockShadowPixels();
            checkIconColors();
            checkCardBorders();
            Intent launch = new Intent().setClassName(getTargetContext(), MainActivity.class.getName())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = startActivitySync(launch);
            awaitApps();
            awaitWindow(true);
            SystemClock.sleep(300);
            require(focusTag().startsWith("dock:") || focusTag().startsWith("grid:"),
                    "Startup loading must restore an app card rather than leave focus on a temporary header action");
            // Existing installs legitimately preserve a previous surface and focused app.
            onUi(() -> {
                activity.onBackPressed();
                activity.onBackPressed();
                dockRow().getChildAt(0).requestFocus();
            });
            SystemClock.sleep(300);
            checkInitialFocusAndGeometry();
            checkTextAndClipGeometry(result);
            checkIconColorBindings();
            checkDockGutters();
            checkNavigation();
            checkPressAndRelease();
            checkReturnFromExternalActivity();
            checkHeaderActions();
            checkPictureDrawerGeometry();
            result.putString("stream", "PASS: " + checks + " checks\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) {
            result.putString("stream", "FAIL after " + checks + " checks: " + error + "\n");
            result.putString("stack", android.util.Log.getStackTraceString(error));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void checkIconColors() {
        Bitmap icon = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        icon.eraseColor(0xff2277cc);
        int blue = IconColors.from(icon);
        require(Color.blue(blue) > Color.red(blue) * 2 && Color.alpha(blue) == 255,
                "A blue icon must produce an opaque blue background");
        icon.eraseColor(Color.WHITE);
        for (int y = 12; y < 36; y++) for (int x = 12; x < 36; x++) icon.setPixel(x, y, 0xff2277cc);
        blue = IconColors.from(icon);
        require(Color.blue(blue) > Color.red(blue) * 2,
                "White icon padding must not hide a significant colored accent");
        icon.eraseColor(0xff888888);
        int gray = IconColors.from(icon);
        require(Math.abs(Color.red(gray) - Color.blue(gray)) <= 2,
                "A monochrome icon must retain a neutral background");
        require(IconColors.from(null) == IconColors.FALLBACK, "Missing icons must use the neutral fallback");
        icon.recycle();
    }

    private void checkIconColorBindings() {
        var apps = AppCatalog.load(getTargetContext());
        onUi(() -> {
            View root = activity.findViewById(android.R.id.content);
            for (AppCatalog.Entry app : apps) for (String surface : new String[]{"dock:", "grid:"}) {
                View card = root.findViewWithTag(surface + app.key());
                require(card != null, "App must exist in both surfaces: " + app.name());
                GradientDrawable background = (GradientDrawable) card.findViewWithTag("cover").getBackground();
                require(background.getColor().getDefaultColor() == app.backgroundColor(),
                        "Both surfaces must use the extracted background: " + app.name());
                require(card.findViewWithTag("cover").getForeground() instanceof GradientDrawable,
                        "Both app surfaces must retain their decorative default stroke: " + app.name());
            }
        });
    }

    private void checkCardBorders() {
        onUi(() -> {
            AppCardView app = new AppCardView(getTargetContext(), "App", null,
                    320, 180, false, false, 1);
            app.setSelected(false);
            var appBorder = app.findViewWithTag("cover").getForeground();
            appBorder.setBounds(0, 0, 320, 180);
            Bitmap appPixels = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
            appBorder.draw(new Canvas(appPixels));
            require(appPixels.getPixel(160, 0) == 0x1fffffff
                            && appPixels.getPixel(160, 1) == 0x1fffffff
                            && Color.alpha(appPixels.getPixel(160, 2)) == 0,
                    "Default app borders must be 2px white at 12 percent opacity");
            require(Color.alpha(appPixels.getPixel(0, 0)) == 0
                            && Color.alpha(appPixels.getPixel(160, 90)) == 0,
                    "Default app borders must preserve rounded corners and a transparent center");
            appPixels.recycle();
            AppCardView card = new AppCardView(getTargetContext(), "Picture", null,
                    320, 180, false, true, 1);
            card.setSelected(true);
            View cover = card.findViewWithTag("cover");
            var border = cover.getForeground();
            border.setBounds(0, 0, 320, 180);
            Bitmap pixels = Bitmap.createBitmap(320, 180, Bitmap.Config.ARGB_8888);
            border.draw(new Canvas(pixels));
            require(Color.alpha(pixels.getPixel(19, 3)) > 240
                            && Color.alpha(pixels.getPixel(300, 3)) > 240,
                    "The blue stroke must reach the image's 28px outer corner without a gap");
            require(Color.alpha(pixels.getPixel(0, 0)) == 0
                            && Color.alpha(pixels.getPixel(160, 90)) == 0,
                    "The border must preserve the rounded outer corner and clear image center");
            pixels.recycle();
        });
    }

    private void checkHeaderActions() {
        String dockTag = focusTag();
        key(KeyEvent.KEYCODE_DPAD_UP);
        onUi(() -> {
            View root = activity.findViewById(android.R.id.content).findViewWithTag("launcher");
            ViewGroup wallpaper = root.findViewWithTag("wallpaper-entry");
            ViewGroup settings = root.findViewWithTag("settings-entry");
            float unit = root.getWidth() / 1920f;
            require(Math.abs(settings.getWidth() - 80 * unit) <= 1
                            && Math.abs(settings.getHeight() - 80 * unit) <= 1,
                    "Header buttons must be 80x80 design pixels");
            require(Math.abs(wallpaper.getLeft() - settings.getRight() - 24 * unit) <= 1,
                    "Settings must be left of wallpaper with the 24px gap");
            require(buttonColor(settings) == ActionButton.NORMAL,
                    "The inactive action must use 10 percent black");
            require(buttonColor(wallpaper) == ActionButton.HOVERED,
                    "Focused actions must be opaque white");
            assertActionScale(wallpaper, 1.2f);
            assertActionScale(settings, 1f);
            require(buttonIconColor(wallpaper) == Color.BLACK && buttonIconColor(settings) == Color.WHITE,
                    "Focused icons must be black and default icons white");
            View shadow = wallpaper.findViewWithTag("action-shadow");
            require(shadow.getVisibility() == View.VISIBLE && shadow.getWidth() > wallpaper.getWidth()
                            && !wallpaper.getClipChildren() && !wallpaper.getClipToPadding(),
                    "The focused shadow must extend beyond the circle without clipping");
            require(settings.findViewWithTag("action-shadow").getVisibility() == View.INVISIBLE,
                    "Default actions must not show a drop shadow");
            require(!((ViewGroup) wallpaper.getParent()).getClipChildren(),
                    "The enlarged button must not be clipped by the action group");
            for (ViewGroup button : new ViewGroup[]{settings, wallpaper}) {
                ImageView image = button.findViewWithTag("action-icon");
                require(Math.abs(image.getWidth() - 48 * unit) <= 1
                                && Math.abs(image.getHeight() - 48 * unit) <= 1,
                        "Action icon slots must be 48x48 design pixels");
                require(image.getDrawable() instanceof VectorDrawable, "Header icons must use native vectors");
                Bitmap pixels = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888);
                var drawable = image.getDrawable().getConstantState().newDrawable().mutate();
                drawable.setBounds(0, 0, 32, 32);
                drawable.draw(new Canvas(pixels));
                int visible = 0;
                for (int y = 0; y < 32; y++) for (int x = 0; x < 32; x++) {
                    if (Color.alpha(pixels.getPixel(x, y)) > 128) visible++;
                }
                require(visible > 80, "The exported vector must contain visible icon paths");
                pixels.recycle();
            }
        });
        long down = SystemClock.uptimeMillis();
        sendKeySync(new KeyEvent(down, down, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0));
        waitForIdleSync();
        SystemClock.sleep(100);
        onUi(() -> require(buttonColor((ViewGroup) activity.getCurrentFocus()) == ActionButton.PRESSED,
                "Pressed actions must retain the opaque white hover fill"));
        onUi(() -> assertActionScale(activity.getCurrentFocus(), 1.1f));
        onUi(() -> require(buttonIconColor((ViewGroup) activity.getCurrentFocus()) == Color.BLACK,
                "Pressed icons must stay black"));
        sendKeySync(new KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_DPAD_CENTER, 0, 0, -1, 0, KeyEvent.FLAG_CANCELED));
        waitForIdleSync();
        SystemClock.sleep(160);
        onUi(() -> require(buttonColor((ViewGroup) activity.getCurrentFocus()) == ActionButton.HOVERED,
                "A canceled press must restore the white hover state without activating"));
        onUi(() -> assertActionScale(activity.getCurrentFocus(), 1.2f));
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        require("settings-entry".equals(focusTag()), "Left from wallpaper must focus settings");
        onUi(() -> {
            assertActionScale(activity.getCurrentFocus(), 1.2f);
            assertActionScale(activity.findViewById(android.R.id.content).findViewWithTag("wallpaper-entry"), 1f);
        });
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        require("settings-entry".equals(focusTag()), "Header left edge must not wrap");
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        awaitWindow(false);
        require(getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK),
                "System Back must return from the settings shortcut");
        awaitWindow(true);
        SystemClock.sleep(300);
        require("settings-entry".equals(focusTag()), "Returning from settings must restore its shortcut focus");
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        require("wallpaper-entry".equals(focusTag()), "Right from settings must focus wallpaper");
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        require(dockTag.equals(focusTag()), "Header Down must restore the original Dock card");
    }

    private int buttonColor(ViewGroup button) {
        return ((GradientDrawable) button.findViewWithTag("action-fill").getBackground().getCurrent())
                .getColor().getDefaultColor();
    }

    private int buttonIconColor(ViewGroup button) {
        ImageView image = button.findViewWithTag("action-icon");
        return image.getImageTintList().getColorForState(button.getDrawableState(), Color.MAGENTA);
    }

    private void assertActionScale(View button, float expected) {
        require(Math.abs(button.getScaleX() - expected) < .005f
                        && Math.abs(button.getScaleY() - expected) < .005f,
                "Unexpected action button scale " + expected);
    }

    private void checkPictureDrawerGeometry() {
        if (getTargetContext().checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        key(KeyEvent.KEYCODE_DPAD_UP);
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        long deadline = SystemClock.uptimeMillis() + 8000;
        AtomicReference<Boolean> ready = new AtomicReference<>(false);
        while (SystemClock.uptimeMillis() < deadline) {
            onUi(() -> {
                HorizontalScrollView scroll = activity.findViewById(android.R.id.content)
                        .findViewWithTag("picture-scroll");
                ViewGroup row = (ViewGroup) scroll.getChildAt(0);
                ready.set(row.getChildCount() > 0 && row.getChildAt(0).getWidth() > 0);
            });
            if (ready.get()) break;
            SystemClock.sleep(50);
        }
        require(ready.get(), "The authorized Wallpaper folder must populate the drawer");
        SystemClock.sleep(300);
        onUi(() -> {
            View root = activity.findViewById(android.R.id.content).findViewWithTag("launcher");
            ViewGroup drawer = root.findViewWithTag("wallpaper-drawer");
            HorizontalScrollView scroll = drawer.findViewWithTag("picture-scroll");
            View first = ((ViewGroup) scroll.getChildAt(0)).getChildAt(0);
            float unit = root.getWidth() / 1920f;
            require(Math.abs(drawer.getHeight() - 311 * unit) <= 1,
                    "Wallpaper panel must retain the 311px fixed height");
            require(scroll.getHeight() == drawer.getHeight()
                            && ((View) scroll.getParent()).getHeight() == drawer.getHeight()
                            && Math.abs(scroll.getPaddingTop() - 99 * unit) <= 1
                            && Math.abs(scroll.getPaddingBottom() - 40 * unit) <= 1
                            && !scroll.getClipToPadding() && !scroll.getClipChildren(),
                    "Wallpaper shadows must draw through padding up to the full panel boundary");
            require(Math.abs(first.getHeight() - 172 * unit) <= 1
                            && Math.abs(first.getWidth() / (float) first.getHeight() - 16f / 9f) < .02f,
                    "Wallpaper cards must match the latest 172px high 16:9 design");
            int[] panel = new int[2], card = new int[2], title = new int[2];
            drawer.getLocationInWindow(panel);
            first.getLocationInWindow(card);
            drawer.findViewWithTag("picture-title").getLocationInWindow(title);
            require(Math.abs(title[1] - panel[1] - 42 * unit) <= 1,
                    "The title must start after the 2px top border and 40px padding");
            require(Math.abs(card[1] - panel[1] - 99 * unit) <= 1,
                    "The cards must follow the 33px title and 24px gap");
            require(Math.abs(panel[1] + drawer.getHeight() - card[1] - first.getHeight() - 40 * unit) <= 1,
                    "Wallpaper cards must retain 40px bottom padding");
            for (ViewGroup surface : new ViewGroup[]{drawer, root.findViewWithTag("dock-surface")}) {
                View border = surface.findViewWithTag("panel-border");
                require(border != null && Math.abs(border.getHeight() - 2 * unit) <= 1
                                && border.getWidth() == root.getWidth(),
                        "Both panels need the same full-width 2px top border");
            }
            ViewGroup button = root.findViewWithTag("wallpaper-entry");
            require(button.isActivated() && buttonColor(button) == ActionButton.ACTIVE,
                    "Wallpaper action must stay blue while focus moves into the drawer");
            assertActionScale(button, 1.2f);
            require(buttonIconColor(button) == Color.WHITE
                            && button.findViewWithTag("action-shadow").getVisibility() == View.VISIBLE,
                    "Activated actions must keep white icons and the hover shadow after focus moves away");
        });
        key(KeyEvent.KEYCODE_BACK);
        require("wallpaper-entry".equals(focusTag()), "Closing the drawer must restore wallpaper action focus");
        onUi(() -> {
            require(!activity.getCurrentFocus().isActivated(), "Closing must clear drawer activation");
            require(buttonColor((ViewGroup) activity.getCurrentFocus()) == ActionButton.HOVERED,
                    "Closing must restore the white hover state while the action retains focus");
            assertActionScale(activity.getCurrentFocus(), 1.2f);
        });
        key(KeyEvent.KEYCODE_DPAD_DOWN);
    }

    private void checkTextAndClipGeometry(Bundle result) {
        onUi(() -> {
            View root = activity.findViewById(android.R.id.content).findViewWithTag("launcher");
            TextView time = root.findViewWithTag("time");
            TextView date = root.findViewWithTag("date");
            float unit = root.getWidth() / 1920f;
            require(time instanceof ClockTextView && date instanceof ClockTextView,
                    "Time and date must both retain native TextViews with a second shadow pass");
            require(Math.abs(time.getShadowRadius() - 24 * unit) < .01f
                            && Math.abs(date.getShadowRadius() - 12 * unit) < .01f
                            && time.getShadowColor() == 0x66000000 && date.getShadowColor() == 0x66000000
                            && Math.abs(time.getShadowDy() - 4 * unit) < .01f
                            && Math.abs(date.getShadowDy() - 4 * unit) < .01f,
                    "Clock primary shadows must match the requested 0/4/24 and 0/4/12 at 40 percent black");
            require(time.isHardwareAccelerated() && date.isHardwareAccelerated(),
                    "Clock text must use the hardware accelerated window");
            require(time.getPaint().isAntiAlias() && date.getPaint().isAntiAlias(),
                    "Clock text must retain native antialiasing");
            require(time.getLayerType() == View.LAYER_TYPE_NONE && time.getScaleX() == 1f
                            && time.getScaleY() == 1f,
                    "Clock text must not be scaled from an offscreen bitmap layer");
            ViewGroup clock = (ViewGroup) time.getParent();
            require(!clock.getClipChildren() && !clock.getClipToPadding(),
                    "Clock container must allow the text shadow to extend outside its bounds");
            HorizontalScrollView dock = root.findViewWithTag("dock");
            require(dock.getWidth() == root.getWidth(),
                    "Dock mask must span the full screen without a smaller inset viewport");
            result.putString("rendering", "Native TextView: hardware=true, antialias=true, layer=NONE, scale=1; window="
                    + root.getWidth() + "x" + root.getHeight());
        });
    }

    private void checkNativeBlur() {
        Bitmap source = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.BLACK);
        for (int y = 0; y < 90; y++) for (int x = 80; x < 160; x++) source.setPixel(x, y, Color.WHITE);
        Bitmap blurred = WallpaperBackdropView.blur(getTargetContext(), source, 1920, 1080);
        int center = blurred.getWidth() / 2, y = blurred.getHeight() / 2;
        int darkEdge = Color.red(blurred.getPixel(center - 2, y));
        int lightEdge = Color.red(blurred.getPixel(center + 2, y));
        require(darkEdge > 0 && lightEdge < 255 && darkEdge < lightEdge,
                "Native blur must soften both sides of a sharp wallpaper edge");
        require(blurred.getPixel(0, y) == Color.BLACK
                && blurred.getPixel(blurred.getWidth() - 1, y) == Color.WHITE,
                "Uniform wallpaper regions must keep their color");
        require(source.getPixel(79, 45) == Color.BLACK && source.getPixel(80, 45) == Color.WHITE,
                "Blur cache must not mutate the sharp wallpaper");
        blurred.recycle();
        source.recycle();
    }

    private void checkClockShadowPixels() {
        onUi(() -> {
            for (int detail : new int[]{2, 4}) {
                TextView single = new TextView(getTargetContext());
                TextView layered = new ClockTextView(getTargetContext(), detail, 2);
                Bitmap[] rendered = new Bitmap[2];
                TextView[] views = {single, layered};
                for (int i = 0; i < views.length; i++) {
                    TextView view = views[i];
                    view.setText("88");
                    view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 64);
                    view.setTextColor(Color.WHITE);
                    view.setIncludeFontPadding(false);
                    view.setGravity(android.view.Gravity.CENTER_VERTICAL);
                    view.setShadowLayer(detail == 4 ? 24 : 12, 0, 4, 0x66000000);
                    view.measure(View.MeasureSpec.makeMeasureSpec(140, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY));
                    view.layout(0, 0, 140, 80);
                    rendered[i] = Bitmap.createBitmap(220, 160, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(rendered[i]);
                    canvas.translate(40, 40);
                    view.draw(canvas);
                }
                long[] alpha = new long[2];
                int[] white = new int[2];
                for (int i = 0; i < 2; i++) {
                    for (int y = 0; y < 160; y++) for (int x = 0; x < 220; x++) {
                        int color = rendered[i].getPixel(x, y);
                        alpha[i] += Color.alpha(color);
                        if (color == Color.WHITE) white[i]++;
                    }
                    rendered[i].recycle();
                }
                require(alpha[1] > alpha[0], "The extra 12 percent shadow must add visible native shadow pixels");
                require(white[1] == white[0] && white[0] > 0,
                        "Adding a second shadow must preserve the native white glyph body");
            }
        });
    }

    private void checkDockGutters() {
        onUi(() -> dockRow().getChildAt(dockRow().getChildCount() - 1).requestFocus());
        SystemClock.sleep(300);
        onUi(() -> {
            View root = activity.findViewById(android.R.id.content).findViewWithTag("launcher");
            View card = activity.getCurrentFocus();
            int[] rootPosition = new int[2], position = new int[2];
            root.getLocationInWindow(rootPosition);
            card.getLocationInWindow(position);
            float gutter = root.getWidth() - (position[0] - rootPosition[0] + card.getWidth());
            require(Math.abs(gutter - root.getWidth() * 80f / 1920f) <= 2,
                    "Last Dock card must retain the design's right gutter after scrolling");
            dockRow().getChildAt(0).requestFocus();
        });
        SystemClock.sleep(300);
        onUi(() -> {
            View root = activity.findViewById(android.R.id.content).findViewWithTag("launcher");
            int[] rootPosition = new int[2], position = new int[2];
            root.getLocationInWindow(rootPosition);
            activity.getCurrentFocus().getLocationInWindow(position);
            require(Math.abs(position[0] - rootPosition[0] - root.getWidth() * 80f / 1920f) <= 2,
                    "Returning to the first Dock card must restore the left gutter");
        });
    }

    private void checkInitialFocusAndGeometry() {
        onUi(() -> {
            ViewGroup row = dockRow();
            View first = row.getChildAt(0);
            View current = activity.getCurrentFocus();
            require(first.hasFocus(), "First Dock card must have focus; current="
                    + (current == null ? "none" : current.getTag()));
            assertScale(first, 1.1f);
            require(!first.isSelected(), "App focus must not become persistent selection");
            View cover = first.findViewWithTag("cover");
            HorizontalScrollView dock = activity.findViewById(android.R.id.content)
                    .findViewWithTag("dock");
            require(dock.getScrollX() == 0, "Initial Dock must keep its left margin; scroll=" + dock.getScrollX());
            require(Math.abs(cover.getWidth() / (float) cover.getHeight() - 16f / 9f) < .02f,
                    "Dock cover must be 16:9");
            if (row.getChildCount() > 1) {
                View second = row.getChildAt(1);
                int firstLeft = first.getLeft(), secondLeft = second.getLeft();
                second.requestFocus();
                require(first.getLeft() == firstLeft && second.getLeft() == secondLeft,
                        "Focus scale must not change neighboring layout slots");
                first.requestFocus();
            }
        });
    }

    private void checkNavigation() {
        String dockTag = focusTag();
        key(KeyEvent.KEYCODE_DPAD_LEFT);
        require(dockTag.equals(focusTag()), "Dock start must not wrap");
        key(KeyEvent.KEYCODE_DPAD_UP);
        require("wallpaper-entry".equals(focusTag()), "Dock Up must focus wallpaper entry");
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        require(dockTag.equals(focusTag()), "Wallpaper Down must restore Dock card");
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        require(focusTag().equals(dockTag.replace("dock:", "grid:")),
                "Grid must open on the same app");
        onUi(() -> {
            View card = activity.getCurrentFocus();
            View label = card.findViewWithTag("name");
            require(label != null, "Grid names must be visible");
            require(label.getScaleX() == 1f && label.getScaleY() == 1f,
                    "Grid name must keep original scale");
            require(card.getScaleX() == 1f, "Only cover may scale, not whole card");
            assertScale(card, 1.1f);
            View root = activity.findViewById(android.R.id.content).findViewWithTag("launcher");
            android.widget.ScrollView scroll = root.findViewWithTag("grid-scroll");
            ViewGroup content = (ViewGroup) scroll.getParent();
            ViewGroup mask = root.findViewWithTag("app-list");
            int[] origin = new int[2], position = new int[2];
            root.getLocationInWindow(origin);
            scroll.getLocationInWindow(position);
            require(scroll.getWidth() == root.getWidth() && scroll.getHeight() == root.getHeight()
                            && position[0] == origin[0] && position[1] == origin[1],
                    "The grid viewport must cover the entire list page, including its gutters");
            require(!scroll.getClipChildren() && !scroll.getClipToPadding()
                            && !content.getClipChildren() && !content.getClipToPadding()
                            && content.getParent() == mask && mask.getClipChildren(),
                    "Text shadows must draw into padding and clip only at the list page boundary");
            card.getLocationInWindow(position);
            require(Math.abs(position[1] - origin[1] + scroll.getScrollY()
                            - root.getWidth() * 168f / 1920f) <= 2,
                    "Expanding the grid mask must preserve the first row's 168px content origin");
            View header = root.findViewWithTag("list-header");
            require(header.getParent() == scroll.getChildAt(0) && header.getWidth() == root.getWidth()
                            && Math.abs(header.getHeight() - root.getWidth() * 128f / 1920f) <= 1
                            && scroll.getPaddingTop() == 0 && scroll.getScrollY() == 0,
                    "The title must be part of scrollable content and fully visible with first-row focus");
            int[] headerPosition = new int[2];
            header.getLocationInWindow(headerPosition);
            require(Math.abs(position[1] - headerPosition[1] - header.getHeight()
                            - root.getWidth() * 40f / 1920f) <= 2,
                    "The first-row layout must retain its 40px gap below the scrolling header");
        });
        key(KeyEvent.KEYCODE_DPAD_UP);
        require(dockTag.equals(focusTag()), "First-row Up must close grid and restore Dock");
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        key(KeyEvent.KEYCODE_BACK);
        require(dockTag.equals(focusTag()), "Back must close grid and restore Dock");

        key(KeyEvent.KEYCODE_DPAD_DOWN);
        AtomicReference<Integer> count = new AtomicReference<>();
        onUi(() -> count.set(dockRow().getChildCount()));
        for (int i = 0; i < (count.get() + 3) / 4; i++) key(KeyEvent.KEYCODE_DPAD_DOWN);
        String bottom = focusTag();
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        require(bottom.equals(focusTag()), "Grid bottom must not wrap");
        onUi(() -> {
            View root = activity.findViewById(android.R.id.content).findViewWithTag("launcher");
            View card = activity.getCurrentFocus();
            int[] rootPosition = new int[2], position = new int[2];
            root.getLocationInWindow(rootPosition);
            card.getLocationInWindow(position);
            require(root.getHeight() - (position[1] - rootPosition[1] + card.getHeight())
                            >= root.getWidth() * 40f / 1920f - 2,
                    "Last grid row must remain above the fixed bottom gutter");
            android.widget.ScrollView scroll = root.findViewWithTag("grid-scroll");
            root.findViewWithTag("list-header").getLocationInWindow(position);
            require(Math.abs(position[1] - rootPosition[1] + scroll.getScrollY()) <= 2,
                    "The header must move with the list rather than remain pinned");
        });
        key(KeyEvent.KEYCODE_BACK);
        require(dockTag.equals(focusTag()), "Back from lower row must restore original Dock app");
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        onUi(() -> {
            View root = activity.findViewById(android.R.id.content).findViewWithTag("launcher");
            ((ViewGroup) root.findViewWithTag("grid-cells")).getChildAt(0).requestFocus();
        });
        SystemClock.sleep(300);
        onUi(() -> {
            View root = activity.findViewById(android.R.id.content).findViewWithTag("launcher");
            android.widget.ScrollView scroll = root.findViewWithTag("grid-scroll");
            int[] origin = new int[2], title = new int[2];
            root.getLocationInWindow(origin);
            root.findViewWithTag("grid-title").getLocationInWindow(title);
            require(scroll.getScrollY() == 0
                            && Math.abs(title[1] - origin[1] - root.getWidth() * 40f / 1920f) <= 2,
                    "Returning to the first row must restore scroll zero and the original title position");
        });
        key(KeyEvent.KEYCODE_BACK);
    }

    private void checkPressAndRelease() {
        long down = SystemClock.uptimeMillis();
        sendKeySync(new KeyEvent(down, down, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER, 0));
        SystemClock.sleep(100);
        onUi(() -> assertScale(activity.getCurrentFocus(), 1.04f));
        // A canceled key-up exercises release without launching a user's application.
        sendKeySync(new KeyEvent(down, SystemClock.uptimeMillis(), KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_DPAD_CENTER, 0, 0, -1, 0, KeyEvent.FLAG_CANCELED));
        SystemClock.sleep(160);
        onUi(() -> {
            assertScale(activity.getCurrentFocus(), 1.1f);
            require(!activity.getCurrentFocus().isSelected(), "Release must not add selection");
        });
    }

    private void checkReturnFromExternalActivity() {
        checkReturn(false);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        checkReturn(true);
        key(KeyEvent.KEYCODE_BACK);
    }

    private void checkReturn(boolean inGrid) {
        String expected = focusTag();
        onUi(() -> activity.startActivity(new Intent(Settings.ACTION_SETTINGS)));
        awaitWindow(false);
        require(getUiAutomation().performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK),
                "System Back should be delivered from Settings");
        awaitWindow(true);
        SystemClock.sleep(300);
        require(expected.equals(focusTag()), "Returning must restore " + (inGrid ? "grid" : "Dock") + " focus");
        onUi(() -> {
            assertScale(activity.getCurrentFocus(), 1.1f);
            require(!activity.getCurrentFocus().isSelected(), "Returning must not select app");
        });
    }

    private void awaitWindow(boolean focused) {
        long deadline = SystemClock.uptimeMillis() + 8000;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Boolean> ready = new AtomicReference<>(false);
            onUi(() -> ready.set(activity.hasWindowFocus() == focused));
            if (ready.get()) { waitForIdleSync(); return; }
            SystemClock.sleep(50);
        }
        throw new AssertionError("Timed out waiting for launcher window focus=" + focused);
    }

    private void awaitApps() {
        long deadline = SystemClock.uptimeMillis() + 10000;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicReference<Boolean> ready = new AtomicReference<>(false);
            onUi(() -> {
                HorizontalScrollView dock = activity.findViewById(android.R.id.content)
                        .findViewWithTag("dock");
                ready.set(dock != null && ((ViewGroup) dock.getChildAt(0)).getChildCount() > 0
                        && ((ViewGroup) dock.getChildAt(0)).getChildAt(0).getTag() != null);
            });
            if (ready.get()) { waitForIdleSync(); return; }
            SystemClock.sleep(50);
        }
        throw new AssertionError("No TV launchable apps appeared");
    }

    private ViewGroup dockRow() {
        HorizontalScrollView dock = activity.findViewById(android.R.id.content).findViewWithTag("dock");
        return (ViewGroup) dock.getChildAt(0);
    }

    private String focusTag() {
        AtomicReference<String> tag = new AtomicReference<>();
        onUi(() -> {
            View focus = activity.getCurrentFocus();
            tag.set(focus == null ? "" : String.valueOf(focus.getTag()));
        });
        return tag.get();
    }

    private void key(int key) {
        sendKeyDownUpSync(key);
        waitForIdleSync();
        SystemClock.sleep(300);
    }

    private void assertScale(View card, float expected) {
        View cover = card.findViewWithTag("cover");
        require(cover != null && Math.abs(cover.getScaleX() - expected) < .005f
                && Math.abs(cover.getScaleY() - expected) < .005f, "Unexpected cover scale " + expected);
    }

    private void onUi(Runnable work) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runOnMainSync(() -> { try { work.run(); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError(failure.get());
    }

    private void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
