package me.leon.tvlauncher;

import android.app.Activity;
import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.util.LruCache;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loader = Executors.newSingleThreadExecutor();
    private final ExecutorService media = Executors.newSingleThreadExecutor();
    private final List<AppCardView> dockCards = new ArrayList<>();
    private final List<AppCardView> gridCards = new ArrayList<>();
    private final List<AppCardView> pictureCards = new ArrayList<>();
    private final List<WallpaperBackdropView> backdrops = new ArrayList<>();
    private final List<WallpaperBackdropView> actionBackdrops = new ArrayList<>();
    private final Set<String> thumbnailRequests = new HashSet<>();
    private final LruCache<String, Bitmap> thumbnails = new LruCache<>(8 * 1024 * 1024) {
        @Override protected int sizeOf(String key, Bitmap bitmap) { return bitmap.getByteCount(); }
    };
    private List<WallpaperStore.Picture> pictures = Collections.emptyList();
    private List<AppCatalog.Entry> apps = Collections.emptyList();
    private FrameLayout root, gridLayer, gridContent, dockSurface, drawerLayer, drawerContent;
    private HorizontalScrollView dock;
    private ScrollView grid;
    private HorizontalScrollView pictureScroll;
    private LinearLayout pictureRow;
    private TextView pictureStatus;
    private ImageView homeWallpaper;
    private View wallpaperButton, settingsButton;
    private TextView time, date;
    private Bitmap wallpaper, blurredWallpaper, actionBlurredWallpaper;
    private String selectedPicture = "";
    private int pictureIndex, pictureLoadVersion, blurVersion;
    private boolean drawerOpen, blurPending, picturesLoading;
    private String pictureError = "", pictureFocusKey = "";
    private static final int IMAGE_PERMISSION = 40;
    private static final long PANEL_DURATION = 240;
    private float unit;
    private int windowWidth, windowHeight;
    private String dockKey = "", gridKey = "";
    private int dockIndex, gridIndex, dockScroll, gridScroll;
    private boolean gridOpen, wallpaperFocused, settingsFocused, receiverRegistered, building;
    private boolean catalogLoaded;

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { reloadApps(); }
    };
    private final Runnable clock = new Runnable() {
        @Override public void run() {
            updateClock();
            main.postDelayed(this, 60_000 - System.currentTimeMillis() % 60_000);
        }
    };

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        SharedPreferences saved = getPreferences(MODE_PRIVATE);
        dockKey = saved.getString("dockKey", "");
        gridKey = saved.getString("gridKey", "");
        gridOpen = saved.getBoolean("gridOpen", false);
        dockScroll = saved.getInt("dockScroll", 0);
        gridScroll = saved.getInt("gridScroll", 0);
        selectedPicture = saved.getString("selectedPicture", "");
        wallpaper = WallpaperStore.current(this);
        root = new FrameLayout(this);
        root.setTag("launcher");
        root.setBackgroundColor(Color.rgb(128, 128, 128));
        root.setClipChildren(false);
        setContentView(root);
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int width = r - l, height = b - t;
            if (width > 0 && height > 0 && (width != windowWidth || height != windowHeight)) {
                windowWidth = width;
                windowHeight = height;
                unit = width / 1920f;
                blurredWallpaper = null;
                actionBlurredWallpaper = null;
                blurPending = false;
                blurVersion++;
                buildContent();
                prepareBlur();
            }
        });
        hideSystemBars();
    }

    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(packages, filter, Context.RECEIVER_EXPORTED);
        else registerReceiver(packages, filter);
        receiverRegistered = true;
    }

    @Override protected void onResume() {
        super.onResume();
        hideSystemBars();
        main.removeCallbacks(clock);
        clock.run();
        reloadApps();
        if (drawerOpen) loadPictures();
        root.post(this::restoreFocus);
    }

    @Override protected void onPause() {
        super.onPause();
        captureScroll();
        getPreferences(MODE_PRIVATE).edit()
                .putString("dockKey", dockKey).putString("gridKey", gridKey)
                .putBoolean("gridOpen", gridOpen)
                .putInt("dockScroll", dockScroll).putInt("gridScroll", gridScroll).apply();
        main.removeCallbacks(clock);
        dockCards.forEach(AppCardView::resetPress);
        gridCards.forEach(AppCardView::resetPress);
        pictureCards.forEach(AppCardView::resetPress);
        if (wallpaperButton != null) ((ActionButton) wallpaperButton).resetPress();
        if (settingsButton != null) ((ActionButton) settingsButton).resetPress();
    }

    @Override protected void onStop() {
        if (receiverRegistered) unregisterReceiver(packages);
        receiverRegistered = false;
        super.onStop();
    }

    @Override protected void onDestroy() {
        cancelPanelAnimations();
        main.removeCallbacksAndMessages(null);
        loader.shutdownNow();
        media.shutdownNow();
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // A Home intent while returning from an app keeps its original surface and focus.
        root.post(this::restoreFocus);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
            restoreFocus();
        }
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
    }

    private void reloadApps() {
        if (loader.isShutdown()) return;
        loader.execute(() -> {
            List<AppCatalog.Entry> updated = AppCatalog.load(getApplicationContext());
            main.post(() -> {
                if (isDestroyed()) return;
                boolean changed = !catalogLoaded || !AppCatalog.sameContents(apps, updated);
                catalogLoaded = true;
                if (changed) {
                    captureScroll();
                    apps = updated;
                    dockIndex = resolveIndex(dockKey, dockIndex);
                    gridIndex = resolveIndex(gridKey, gridIndex);
                    if (windowWidth > 0) buildContent();
                }
            });
        });
    }

    private int resolveIndex(String key, int fallback) {
        for (int i = 0; i < apps.size(); i++) if (apps.get(i).key().equals(key)) return i;
        return Math.max(0, Math.min(fallback, apps.size() - 1));
    }

    private void buildContent() {
        building = true;
        cancelPanelAnimations();
        root.removeAllViews();
        dockCards.clear();
        gridCards.clear();
        pictureCards.clear();
        backdrops.clear();
        actionBackdrops.clear();
        homeWallpaper = new ImageView(this);
        homeWallpaper.setTag("wallpaper");
        homeWallpaper.setImageBitmap(wallpaper);
        homeWallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        homeWallpaper.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(homeWallpaper, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout clockGroup = new LinearLayout(this);
        clockGroup.setOrientation(LinearLayout.VERTICAL);
        clockGroup.setClipChildren(false);
        clockGroup.setClipToPadding(false);
        time = clockText(64, 24, 4);
        date = clockText(24, 12, 2);
        time.setTag("time");
        date.setTag("date");
        clockGroup.addView(time, new LinearLayout.LayoutParams(-2, px(76)));
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-2, px(30));
        dateParams.topMargin = px(8);
        clockGroup.addView(date, dateParams);
        FrameLayout.LayoutParams clockParams = new FrameLayout.LayoutParams(-2, -2);
        clockParams.leftMargin = px(80);
        clockParams.topMargin = px(40);
        root.addView(clockGroup, clockParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setClipChildren(false);
        actions.setClipToPadding(false);
        settingsButton = actionEntry(true);
        wallpaperButton = actionEntry(false);
        wallpaperButton.setActivated(drawerOpen);
        actions.addView(settingsButton, new LinearLayout.LayoutParams(px(80), px(80)));
        LinearLayout.LayoutParams wallpaperParams = new LinearLayout.LayoutParams(px(80), px(80));
        wallpaperParams.leftMargin = px(24);
        actions.addView(wallpaperButton, wallpaperParams);
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(-2, px(80),
                Gravity.TOP | Gravity.RIGHT);
        actionParams.topMargin = px(63.5f);
        actionParams.rightMargin = px(80);
        root.addView(actions, actionParams);

        buildDock();
        buildGrid();
        buildDrawer();
        updateClock();
        gridLayer.setVisibility(gridOpen ? View.VISIBLE : View.GONE);
        drawerLayer.setVisibility(drawerOpen ? View.VISIBLE : View.GONE);
        dockSurface.setVisibility(drawerOpen ? View.INVISIBLE : View.VISIBLE);
        root.post(() -> {
            dock.scrollTo(dockScroll, 0);
            grid.scrollTo(0, gridScroll);
            building = false;
            restoreFocus();
            refreshThumbnails();
        });
    }

    private void buildDock() {
        dockSurface = new FrameLayout(this);
        dockSurface.setTag("dock-surface");
        dockSurface.addView(backdrop(0x33000000), new FrameLayout.LayoutParams(-1, -1));
        int coverWidth = px(410);
        int coverHeight = Math.round(coverWidth * 9f / 16f);
        GutterScrollFrame frame = new GutterScrollFrame(this, px(80), px(40), px(40));
        dock = frame.scroll;
        dock.setTag("dock");
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setOrientation(LinearLayout.HORIZONTAL);
        dock.addView(row, new ViewGroup.LayoutParams(-2, -1));
        for (int i = 0; i < apps.size(); i++) {
            AppCardView card = card(i, coverWidth, coverHeight, false);
            dockCards.add(card);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(coverWidth, coverHeight);
            if (i < apps.size() - 1) params.rightMargin = px(40);
            row.addView(card, params);
        }
        if (apps.isEmpty()) row.addView(text(catalogLoaded ? "暂无电视应用" : "正在读取应用…", 28, false));
        dockSurface.addView(frame, new FrameLayout.LayoutParams(-1, -1));
        addPanelBorder(dockSurface);
        root.addView(dockSurface, new FrameLayout.LayoutParams(-1, px(311), Gravity.BOTTOM));
    }

    private void addPanelBorder(FrameLayout panel) {
        View border = new View(this);
        border.setTag("panel-border");
        border.setBackgroundColor(0x33ffffff);
        border.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        panel.addView(border, new FrameLayout.LayoutParams(-1, px(2), Gravity.TOP));
    }

    private void buildGrid() {
        gridLayer = new FrameLayout(this);
        gridLayer.setTag("app-list");
        gridLayer.setClipChildren(true);
        WallpaperBackdropView gridBackground = backdrop(0x66000000);
        gridLayer.addView(gridBackground, new FrameLayout.LayoutParams(-1, -1));
        gridContent = new FrameLayout(this);
        gridContent.setClipChildren(false);
        gridContent.setClipToPadding(false);
        gridLayer.addView(gridContent, new FrameLayout.LayoutParams(-1, -1));
        TextView title = text("全部应用", 40, true);
        title.setTag("grid-title");
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-2, px(48));
        titleParams.leftMargin = px(80);
        titleParams.topMargin = px(40);

        grid = new RevealScrollView(this);
        grid.setTag("grid-scroll");
        grid.setVerticalScrollBarEnabled(false);
        grid.setOverScrollMode(View.OVER_SCROLL_NEVER);
        grid.setSmoothScrollingEnabled(true);
        grid.setFocusable(false);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, 0, 0, px(40));
        grid.setVerticalFadingEdgeEnabled(false);
        grid.setFadingEdgeLength(0);
        GridLayout cells = new GridLayout(this);
        cells.setTag("grid-cells");
        cells.setColumnCount(4);
        cells.setClipChildren(false);
        cells.setClipToPadding(false);
        cells.setPadding(px(80), px(40), px(80), 0);
        int coverWidth = (windowWidth - px(160) - px(120)) / 4;
        int coverHeight = Math.round(coverWidth * 9f / 16f);
        int slotHeight = coverHeight + px(52);
        for (int i = 0; i < apps.size(); i++) {
            AppCardView card = card(i, coverWidth, coverHeight, true);
            gridCards.add(card);
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / 4), GridLayout.spec(i % 4));
            params.width = coverWidth;
            params.height = slotHeight;
            params.rightMargin = i % 4 < 3 ? px(40) : 0;
            params.bottomMargin = i / 4 < (apps.size() - 1) / 4 ? px(40) : 0;
            cells.addView(card, params);
        }
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        FrameLayout header = new FrameLayout(this);
        header.setTag("list-header");
        header.setClipChildren(false);
        header.setClipToPadding(false);
        header.addView(title, titleParams);
        page.addView(header, new LinearLayout.LayoutParams(-1, px(128)));
        page.addView(cells, new LinearLayout.LayoutParams(-1, -2));
        grid.addView(page, new ViewGroup.LayoutParams(-1, -2));
        gridContent.addView(grid, new FrameLayout.LayoutParams(-1, -1));
        root.addView(gridLayer, new FrameLayout.LayoutParams(-1, -1));
    }

    private WallpaperBackdropView backdrop(int tint) {
        WallpaperBackdropView view = new WallpaperBackdropView(this, root,
                blurredWallpaper != null ? blurredWallpaper : wallpaper, tint);
        backdrops.add(view);
        return view;
    }

    private void prepareBlur() {
        if (wallpaper == null || blurredWallpaper != null || blurPending || windowWidth == 0) return;
        blurPending = true;
        int version = ++blurVersion, width = windowWidth, height = windowHeight;
        Bitmap source = wallpaper;
        media.execute(() -> {
            Bitmap blurred = blurWallpaper(source, width, height);
            Bitmap actionBlurred = blurWallpaper(source, width, height, 12);
            main.post(() -> {
                if (isDestroyed() || version != blurVersion) return;
                blurPending = false;
                blurredWallpaper = blurred;
                actionBlurredWallpaper = actionBlurred;
                backdrops.forEach(view -> view.setBitmap(blurred != null ? blurred : source));
                actionBackdrops.forEach(view -> view.setBitmap(actionBlurred != null ? actionBlurred : source));
            });
        });
    }

    private Bitmap blurWallpaper(Bitmap source, int width, int height) {
        return blurWallpaper(source, width, height, 32);
    }

    private Bitmap blurWallpaper(Bitmap source, int width, int height, float radius) {
        try {
            return WallpaperBackdropView.blur(getApplicationContext(), source, width, height, radius);
        } catch (RuntimeException error) {
            Log.e("TVLauncher", "Native wallpaper blur failed", error);
            main.post(() -> {
                if (!isDestroyed()) Toast.makeText(this, "当前设备无法生成背景模糊", Toast.LENGTH_LONG).show();
            });
            return null;
        }
    }

    private void buildDrawer() {
        drawerLayer = new FrameLayout(this);
        drawerLayer.setTag("wallpaper-drawer");
        drawerLayer.addView(backdrop(0x33000000), new FrameLayout.LayoutParams(-1, -1));
        drawerContent = new FrameLayout(this);
        drawerContent.setClipChildren(false);
        drawerContent.setFocusableInTouchMode(true);
        drawerContent.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
        drawerContent.setDefaultFocusHighlightEnabled(false);
        drawerLayer.addView(drawerContent, new FrameLayout.LayoutParams(-1, -1));
        TextView title = text("Choose Picture", 28, false);
        title.setTag("picture-title");
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-2, px(33));
        titleParams.leftMargin = px(80);
        titleParams.topMargin = px(42);
        drawerContent.addView(title, titleParams);

        int height = px(172), width = Math.round(height * 16f / 9f);
        // Let elevation shadows draw through the panel padding; only the whole panel clips.
        GutterScrollFrame frame = new GutterScrollFrame(this, px(80), px(99), px(40));
        pictureScroll = frame.scroll;
        pictureScroll.setTag("picture-scroll");
        pictureScroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> refreshThumbnails());
        pictureRow = new LinearLayout(this);
        pictureRow.setOrientation(LinearLayout.HORIZONTAL);
        pictureRow.setClipChildren(false);
        pictureRow.setClipToPadding(false);
        pictureRow.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> refreshThumbnails());
        pictureScroll.addView(pictureRow, new ViewGroup.LayoutParams(-2, -1));
        drawerContent.addView(frame, new FrameLayout.LayoutParams(-1, -1));

        pictureStatus = text("", 28, false);
        pictureStatus.setTag("picture-status");
        pictureStatus.setGravity(Gravity.CENTER);
        pictureStatus.setFocusable(true);
        pictureStatus.setFocusableInTouchMode(true);
        pictureStatus.setDefaultFocusHighlightEnabled(false);
        pictureStatus.setBackground(ActionButton.background(px(100)));
        pictureStatus.setOnClickListener(view -> { if (!hasImageAccess()) requestImageAccess(); });
        pictureStatus.setOnKeyListener((view, key, event) -> {
            if (key == KeyEvent.KEYCODE_DPAD_UP && event.getAction() == KeyEvent.ACTION_DOWN) closeDrawer();
            return FocusRules.isDirection(key);
        });
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(-1, height, Gravity.BOTTOM);
        statusParams.leftMargin = statusParams.rightMargin = px(80);
        statusParams.bottomMargin = px(40);
        drawerContent.addView(pictureStatus, statusParams);
        addPanelBorder(drawerLayer);
        root.addView(drawerLayer, new FrameLayout.LayoutParams(-1, px(311), Gravity.BOTTOM));
        renderPictures();
    }

    private void openDrawer() {
        if (gridOpen) return;
        captureScroll();
        drawerOpen = true;
        wallpaperFocused = true;
        settingsFocused = false;
        dockSurface.setVisibility(View.INVISIBLE);
        wallpaperButton.setActivated(true);
        animatePanel(drawerLayer, drawerContent, true, px(311));
        loadPictures();
        focusPicture();
        if (!hasImageAccess()) requestImageAccess();
    }

    private void closeDrawer() {
        drawerOpen = false;
        wallpaperButton.setActivated(false);
        pictureLoadVersion++;
        dockSurface.setVisibility(View.VISIBLE);
        animatePanel(drawerLayer, drawerContent, false, px(311));
        wallpaperFocused = true;
        wallpaperButton.requestFocus();
    }

    private boolean hasImageAccess() {
        String permission = Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                || Build.VERSION.SDK_INT >= 34 && checkSelfPermission(
                        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestImageAccess() {
        String[] permissions = Build.VERSION.SDK_INT >= 34
                ? new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED}
                : new String[]{Build.VERSION.SDK_INT >= 33 ? Manifest.permission.READ_MEDIA_IMAGES
                        : Manifest.permission.READ_EXTERNAL_STORAGE};
        requestPermissions(permissions, IMAGE_PERMISSION);
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == IMAGE_PERMISSION && drawerOpen) {
            loadPictures();
            if (Build.VERSION.SDK_INT >= 34 && hasImageAccess() && checkSelfPermission(
                    Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "当前仅能访问已授权的图片", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void loadPictures() {
        if (!drawerOpen || media.isShutdown()) return;
        int version = ++pictureLoadVersion;
        pictureError = "";
        if (!hasImageAccess()) {
            pictures = Collections.emptyList();
            picturesLoading = false;
            renderPictures();
            focusPicture();
            return;
        }
        picturesLoading = true;
        renderPictures();
        media.execute(() -> {
            List<WallpaperStore.Picture> loaded;
            String error = "";
            try { loaded = WallpaperStore.list(getApplicationContext()); }
            catch (RuntimeException failure) {
                loaded = Collections.emptyList();
                error = "无法读取 Wallpaper 文件夹";
            }
            List<WallpaperStore.Picture> result = loaded;
            String message = error;
            main.post(() -> {
                if (isDestroyed() || !drawerOpen || version != pictureLoadVersion) return;
                pictures = result;
                picturesLoading = false;
                pictureError = message;
                for (int i = 0; i < pictures.size(); i++) {
                    if (pictures.get(i).key().equals(pictureFocusKey)
                            || pictureFocusKey.isEmpty() && pictures.get(i).key().equals(selectedPicture)) {
                        pictureIndex = i;
                        break;
                    }
                }
                renderPictures();
                pictureScroll.post(() -> { focusPicture(); refreshThumbnails(); });
            });
        });
    }

    private void renderPictures() {
        if (pictureRow == null) return;
        // Retain focus inside the drawer while replacing focused cards or hiding the status view.
        if (drawerOpen && (pictureRow.hasFocus() || pictureStatus.hasFocus())) {
            drawerContent.requestFocus();
        }
        pictureRow.removeAllViews();
        pictureCards.clear();
        int height = px(172), width = Math.round(height * 16f / 9f);
        for (int i = 0; i < pictures.size(); i++) {
            WallpaperStore.Picture picture = pictures.get(i);
            int index = i;
            AppCardView card = new AppCardView(this, picture.name(), thumbnails.get(picture.cacheKey()),
                    width, height, false, true, unit);
            card.setTag("picture:" + picture.key());
            card.setSelected(picture.key().equals(selectedPicture));
            card.setOnFocusChangeListener((view, focused) -> {
                if (focused) { pictureIndex = index; pictureFocusKey = picture.key(); }
            });
            card.setNavigation((view, key) -> {
                if (key == KeyEvent.KEYCODE_DPAD_UP) closeDrawer();
                else if (key == KeyEvent.KEYCODE_DPAD_LEFT && index > 0) pictureCards.get(index - 1).requestFocus();
                else if (key == KeyEvent.KEYCODE_DPAD_RIGHT && index + 1 < pictureCards.size()) {
                    pictureCards.get(index + 1).requestFocus();
                }
            });
            card.setOnClickListener(view -> applyPicture(picture));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(width, height);
            if (i + 1 < pictures.size()) params.rightMargin = px(40);
            pictureRow.addView(card, params);
            pictureCards.add(card);
        }
        boolean empty = pictures.isEmpty();
        pictureStatus.setVisibility(empty ? View.VISIBLE : View.GONE);
        pictureStatus.setClickable(!hasImageAccess());
        pictureStatus.setText(!hasImageAccess() ? "允许访问图片" : picturesLoading ? "正在读取图片…"
                : !pictureError.isEmpty() ? pictureError : "Wallpaper 文件夹中暂无图片");
    }

    private void focusPicture() {
        if (!drawerOpen || pictureStatus == null) return;
        if (pictureCards.isEmpty()) pictureStatus.requestFocus();
        else pictureCards.get(Math.max(0, Math.min(pictureIndex, pictureCards.size() - 1))).requestFocus();
    }

    private void refreshThumbnails() {
        if (!drawerOpen || pictureScroll == null || media.isShutdown()
                || pictureScroll.getWidth() == 0 || pictureCards.isEmpty()
                || pictureCards.get(0).getWidth() == 0) return;
        int left = pictureScroll.getScrollX() - px(400);
        int right = pictureScroll.getScrollX() + pictureScroll.getWidth() + px(400);
        for (int i = 0; i < pictureCards.size(); i++) {
            AppCardView card = pictureCards.get(i);
            WallpaperStore.Picture picture = pictures.get(i);
            if (card.getRight() < left || card.getLeft() > right) { card.setPicture(null); continue; }
            Bitmap cached = thumbnails.get(picture.cacheKey());
            if (cached != null) { card.setPicture(cached); continue; }
            if (!thumbnailRequests.add(picture.cacheKey())) continue;
            media.execute(() -> {
                Bitmap loaded = null;
                try { loaded = WallpaperStore.thumbnail(getApplicationContext(), picture); }
                catch (IOException | RuntimeException ignored) { }
                Bitmap bitmap = loaded;
                main.post(() -> {
                    if (isDestroyed()) return;
                    thumbnailRequests.remove(picture.cacheKey());
                    if (bitmap != null) {
                        thumbnails.put(picture.cacheKey(), bitmap);
                        if (drawerOpen && card.getParent() == pictureRow
                                && card.getRight() >= pictureScroll.getScrollX() - px(400)
                                && card.getLeft() <= pictureScroll.getScrollX() + pictureScroll.getWidth() + px(400)) {
                            card.setPicture(bitmap);
                        }
                        refreshThumbnails();
                    }
                });
            });
        }
    }

    private void applyPicture(WallpaperStore.Picture picture) {
        int width = windowWidth, height = windowHeight;
        media.execute(() -> {
            try {
                Bitmap decoded = WallpaperStore.apply(getApplicationContext(), picture);
                Bitmap blurred = blurWallpaper(decoded, width, height);
                Bitmap actionBlurred = blurWallpaper(decoded, width, height, 12);
                getPreferences(MODE_PRIVATE).edit().putString("selectedPicture", picture.key()).apply();
                main.post(() -> {
                    if (isDestroyed()) return;
                    wallpaper = decoded;
                    blurredWallpaper = width == windowWidth && height == windowHeight ? blurred : null;
                    actionBlurredWallpaper = width == windowWidth && height == windowHeight ? actionBlurred : null;
                    blurVersion++;
                    blurPending = false;
                    selectedPicture = picture.key();
                    homeWallpaper.setImageBitmap(decoded);
                    backdrops.forEach(view -> view.setBitmap(blurredWallpaper != null ? blurredWallpaper : decoded));
                    actionBackdrops.forEach(view -> view.setBitmap(
                            actionBlurredWallpaper != null ? actionBlurredWallpaper : decoded));
                    if (width != windowWidth || height != windowHeight) prepareBlur();
                    for (int i = 0; i < pictureCards.size(); i++) {
                        pictureCards.get(i).setSelected(pictures.get(i).key().equals(selectedPicture));
                    }
                });
            } catch (IOException | RuntimeException error) {
                main.post(() -> {
                    if (!isDestroyed()) Toast.makeText(this, "无法读取这张图片", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private AppCardView card(int index, int width, int height, boolean inGrid) {
        AppCatalog.Entry app = apps.get(index);
        AppCardView card = new AppCardView(this, app, width, height, inGrid, unit);
        card.setTag((inGrid ? "grid:" : "dock:") + app.key());
        card.setOnFocusChangeListener((view, focused) -> {
            if (focused && !building) {
                wallpaperFocused = false;
                settingsFocused = false;
                if (inGrid) { gridKey = app.key(); gridIndex = index; }
                else { dockKey = app.key(); dockIndex = index; }
            }
        });
        card.setNavigation((view, key) -> move(index, inGrid, key));
        card.setOnClickListener(view -> launch(app));
        return card;
    }

    private View actionEntry(boolean settings) {
        WallpaperBackdropView background = new WallpaperBackdropView(this, root,
                actionBlurredWallpaper != null ? actionBlurredWallpaper : wallpaper, 0);
        actionBackdrops.add(background);
        ActionButton button = new ActionButton(this, background,
                settings ? R.drawable.settings_icon : R.drawable.wallpaper_icon, unit);
        button.setTag(settings ? "settings-entry" : "wallpaper-entry");
        button.setContentDescription(settings ? "系统设置" : "更换壁纸");
        button.setOnFocusChangeListener((view, focused) -> {
            if (focused && !building && catalogLoaded) {
                wallpaperFocused = !settings;
                settingsFocused = settings;
            }
        });
        button.setAction(() -> {
            if (!settings) { openDrawer(); return; }
            settingsFocused = true;
            wallpaperFocused = false;
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (android.content.ActivityNotFoundException | SecurityException error) {
                Toast.makeText(this, "无法打开系统设置", Toast.LENGTH_SHORT).show();
            }
        });
        button.setOnKeyListener((view, key, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (key == KeyEvent.KEYCODE_DPAD_DOWN) focusApp(dockIndex, false);
                else if (key == KeyEvent.KEYCODE_DPAD_LEFT && !settings) settingsButton.requestFocus();
                else if (key == KeyEvent.KEYCODE_DPAD_RIGHT && settings) wallpaperButton.requestFocus();
            }
            return FocusRules.isDirection(key);
        });
        return button;
    }

    private void move(int index, boolean inGrid, int key) {
        if (inGrid) {
            int target = FocusRules.gridNeighbor(index, apps.size(), key);
            if (target < 0) closeGrid();
            else focusApp(target, true);
        } else if (key == KeyEvent.KEYCODE_DPAD_UP) {
            wallpaperFocused = true;
            settingsFocused = false;
            wallpaperButton.requestFocus();
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN) {
            captureScroll();
            gridKey = apps.get(index).key();
            gridIndex = index;
            setGridOpen(true);
            focusApp(index, true);
        } else {
            int target = index + (key == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            if (target >= 0 && target < apps.size()) focusApp(target, false);
        }
    }

    private void focusApp(int index, boolean inGrid) {
        List<AppCardView> cards = inGrid ? gridCards : dockCards;
        if (cards.isEmpty()) {
            if (wallpaperButton != null) wallpaperButton.requestFocus();
            return;
        }
        wallpaperFocused = false;
        settingsFocused = false;
        cards.get(Math.max(0, Math.min(index, cards.size() - 1))).requestFocus();
    }

    private void restoreFocus() {
        if (building || wallpaperButton == null || isDestroyed()) return;
        if (drawerOpen) focusPicture();
        else if (gridOpen && !apps.isEmpty()) focusApp(resolveIndex(gridKey, gridIndex), true);
        else if (settingsFocused) settingsButton.requestFocus();
        else if (wallpaperFocused || apps.isEmpty()) wallpaperButton.requestFocus();
        else focusApp(resolveIndex(dockKey, dockIndex), false);
    }

    private void closeGrid() {
        gridScroll = grid.getScrollY();
        setGridOpen(false);
        dock.scrollTo(dockScroll, 0);
        focusApp(resolveIndex(dockKey, dockIndex), false);
    }

    private void setGridOpen(boolean open) {
        gridOpen = open;
        animatePanel(gridLayer, gridContent, open, Math.max(0, windowHeight - px(311)));
    }

    private void animatePanel(FrameLayout layer, View content, boolean open, int distance) {
        layer.animate().cancel();
        content.animate().cancel();
        if (open && layer.getVisibility() != View.VISIBLE) {
            layer.setAlpha(0);
            content.setTranslationY(distance);
        }
        layer.setVisibility(View.VISIBLE);
        content.animate().translationY(open ? 0 : distance).setDuration(PANEL_DURATION)
                .setInterpolator(new DecelerateInterpolator()).start();
        layer.animate().alpha(open ? 1 : 0).setDuration(PANEL_DURATION)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(() -> {
                    if (!open) layer.setVisibility(View.GONE);
                }).start();
    }

    private void cancelPanelAnimations() {
        if (gridLayer != null) gridLayer.animate().cancel();
        if (gridContent != null) gridContent.animate().cancel();
        if (drawerLayer != null) drawerLayer.animate().cancel();
        if (drawerContent != null) drawerContent.animate().cancel();
    }

    @Override @SuppressWarnings("deprecation") public void onBackPressed() {
        if (drawerOpen) closeDrawer();
        else if (gridOpen) closeGrid();
        else if ((wallpaperFocused || settingsFocused) && !apps.isEmpty()) focusApp(dockIndex, false);
        // The root of a HOME activity stays on screen when Back is pressed.
    }

    private void launch(AppCatalog.Entry app) {
        Intent intent = new Intent(Intent.ACTION_MAIN)
                .addCategory(app.launchCategory()).setComponent(app.component())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, "无法打开此应用", Toast.LENGTH_SHORT).show();
            reloadApps();
        }
    }

    private void captureScroll() {
        if (dock != null) dockScroll = dock.getScrollX();
        if (grid != null) gridScroll = grid.getScrollY();
    }

    private void updateClock() {
        if (time == null) return;
        Date now = new Date();
        time.setText(DateFormat.getTimeFormat(this).format(now));
        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "yMMMMd");
        date.setText(new java.text.SimpleDateFormat(pattern, Locale.getDefault()).format(now));
    }

    private TextView text(String value, float size, boolean bold) {
        return styleText(new TextView(this), value, size, bold);
    }

    private TextView clockText(float size, float mainBlur, float detailBlur) {
        TextView result = styleText(new ClockTextView(this, detailBlur * unit, 2 * unit), "", size, false);
        result.setShadowLayer(mainBlur * unit, 0, 4 * unit, 0x66000000);
        return result;
    }

    private TextView styleText(TextView result, String value, float size, boolean bold) {
        result.setText(value);
        result.setTextColor(Color.WHITE);
        result.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * unit);
        result.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        result.setIncludeFontPadding(false);
        result.setGravity(Gravity.CENTER_VERTICAL);
        result.setShadowLayer(32 * unit, 0, 4 * unit, 0x66000000);
        return result;
    }

    private int px(float value) { return Math.round(value * unit); }
}
