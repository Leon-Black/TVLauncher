package me.leon.tvlauncher;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.LruCache;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

final class AppCatalog {
    private static final LruCache<String, Integer> colors = new LruCache<>(128);

    record Entry(ComponentName component, String name, Bitmap icon, int backgroundColor, long updatedAt,
                 String launchCategory) {
        String key() { return component.flattenToString(); }
    }

    static List<Entry> load(Context context) {
        PackageManager manager = context.getPackageManager();
        LinkedHashMap<String, Entry> packages = new LinkedHashMap<>();
        addActivities(context, manager, Intent.CATEGORY_LEANBACK_LAUNCHER, packages);
        addActivities(context, manager, Intent.CATEGORY_HOME, packages);
        List<Entry> result = new ArrayList<>(packages.values());
        Collator collator = Collator.getInstance(Locale.getDefault());
        result.sort((a, b) -> {
            int byName = collator.compare(a.name(), b.name());
            return byName == 0 ? a.key().compareTo(b.key()) : byName;
        });
        return result;
    }

    private static void addActivities(Context context, PackageManager manager, String category,
                                      LinkedHashMap<String, Entry> packages) {
        Intent query = new Intent(Intent.ACTION_MAIN)
                .addCategory(category);
        List<ResolveInfo> candidates = new ArrayList<>(manager.queryIntentActivities(query, 0));
        candidates.sort(Comparator.comparing(info -> info.activityInfo.name));
        for (ResolveInfo info : candidates) {
            String packageName = info.activityInfo.packageName;
            if (packageName.equals(context.getPackageName()) || packages.containsKey(packageName)
                    || !info.activityInfo.exported || !info.activityInfo.enabled
                    || !info.activityInfo.applicationInfo.enabled) continue;
            String permission = info.activityInfo.permission;
            if (permission != null && manager.checkPermission(permission, context.getPackageName())
                    != PackageManager.PERMISSION_GRANTED) continue;
            try {
                boolean home = Intent.CATEGORY_HOME.equals(category);
                PackageInfo packageInfo = manager.getPackageInfo(packageName,
                        home ? PackageManager.GET_CONFIGURATIONS : 0);
                if (home && !declaresTelevisionSupport(packageInfo)) continue;
                String name = info.loadLabel(manager).toString();
                Drawable drawable = info.loadIcon(manager);
                Bitmap icon = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
                drawable.setBounds(0, 0, 192, 192);
                drawable.draw(new Canvas(icon));
                String colorKey = packageName + ":" + info.activityInfo.name + ":" + packageInfo.lastUpdateTime;
                Integer background = colors.get(colorKey);
                if (background == null) {
                    background = IconColors.from(icon);
                    colors.put(colorKey, background);
                }
                packages.put(packageName, new Entry(
                        new ComponentName(packageName, info.activityInfo.name), name, icon, background,
                        packageInfo.lastUpdateTime, category));
            } catch (PackageManager.NameNotFoundException ignored) {
                // A package can be removed while this background scan is running.
            }
        }
    }

    private static boolean declaresTelevisionSupport(PackageInfo info) {
        if (info.reqFeatures == null) return false;
        for (FeatureInfo feature : info.reqFeatures) {
            if (PackageManager.FEATURE_LEANBACK.equals(feature.name)) return true;
        }
        return false;
    }

    static boolean sameContents(List<Entry> a, List<Entry> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Entry x = a.get(i), y = b.get(i);
            if (!x.key().equals(y.key()) || !x.name().equals(y.name())
                    || !x.launchCategory().equals(y.launchCategory())
                    || x.updatedAt() != y.updatedAt()) return false;
        }
        return true;
    }
}
