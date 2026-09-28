package com.example.tingxiejian;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.util.Locale;

/** Optional, four-page first-use guide. Permissions and Shizuku are never granted automatically. */
public class WelcomeActivity extends Activity {
    private static final String PREFS = "first_run";
    private static final String COMPLETED = "completed";
    private static final String FROM_SETTINGS = "from_settings";
    private static final String STATE_PAGE = "guide_page";
    private static final String STATE_PERMISSION = "guide_permission_requested";
    private static final String STATE_WELCOME_PLAYED = "guide_welcome_played";
    private static final String[] PAGE_TITLES = {"让声音，留在这里。", "需要的权限，由你决定。",
            "更多能力，不必现在开启。", "欢迎使用。"};
    private static final int NOTIFICATION_REQUEST = 72;
    private static final int PAGE_COUNT = 4;
    private static final PathInterpolator PAGE_CURVE = new PathInterpolator(.22f, 1f, .36f, 1f);
    private boolean permissionRequested;
    private boolean changing;
    private boolean leaving;
    private boolean welcomePlayed;
    private int page;
    private View[] pages;
    private TextView notificationAction;
    private OnBackInvokedCallback backCallback;

    /** Established installations do not get interrupted by onboarding after an update. */
    static boolean shouldShow(Context context) {
        if (context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(COMPLETED, false)) return false;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (info.lastUpdateTime - info.firstInstallTime > 2000L) {
                context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(COMPLETED, true).apply();
                return false;
            }
        } catch (Throwable error) {
            Report.problem("检查首次安装状态失败", error);
        }
        return true;
    }

    static void openGuide(Activity activity) {
        activity.startActivity(new Intent(activity, WelcomeActivity.class).putExtra(FROM_SETTINGS, true));
        activity.overridePendingTransition(Motion.animatorsEnabled() ? R.anim.guide_enter : 0,
                Motion.animatorsEnabled() ? R.anim.guide_stay : 0);
    }

    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(UiTheme.wrap(base));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            setContentView(R.layout.activity_welcome);
            UiTheme.padForSystemBars(this, need(R.id.guide_root));
            pages = new View[]{need(R.id.guide_page_0), need(R.id.guide_page_1),
                    need(R.id.guide_page_2), need(R.id.guide_page_3)};
            page = state == null ? 0 : Math.max(0, Math.min(PAGE_COUNT - 1, state.getInt(STATE_PAGE, 0)));
            permissionRequested = state != null && state.getBoolean(STATE_PERMISSION, false);
            welcomePlayed = state != null && state.getBoolean(STATE_WELCOME_PLAYED, false);
            for (int i = 0; i < PAGE_COUNT; i++) pages[i].setVisibility(i == page ? View.VISIBLE : View.GONE);
            updateControls();
            notificationAction = need(R.id.notification_permission);
            notificationAction.setOnClickListener(v -> requestNotification());
            View mark = need(R.id.guide_mark);
            mark.setOnClickListener(v -> Motion.pop(mark));
            Motion.press(mark);
            View island = need(R.id.shizuku_settings);
            island.setOnClickListener(v -> {
                try { PortalTransition.open(this, island, new Intent(this, SettingsActivity.class), "guide_island_settings"); }
                catch (Throwable error) { Report.problem("打开超级岛设置失败", error); }
            });
            need(R.id.terms_review).setOnClickListener(v -> Consent.show(this, null));
            View next = need(R.id.guide_start);
            next.setOnClickListener(v -> {
                if (page == PAGE_COUNT - 1) finishSetup(true, next);
                else showPage(page + 1);
            });
            Motion.press(next);
            if (state == null && Motion.animatorsEnabled()) {
                Motion.enter(need(R.id.guide_title_first), 35);
                Motion.enter(need(R.id.guide_title_second), 125);
                Motion.enter(((ViewGroup) pages[0]).getChildAt(2), 215);
            }
            need(R.id.guide_back).setOnClickListener(v -> navigateBack());
            View skip = need(R.id.guide_skip);
            skip.setOnClickListener(v -> finishSetup(true, skip));
            if (Build.VERSION.SDK_INT >= 33) {
                backCallback = this::navigateBack;
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
            }
            Report.mark("first-run paged guide ready");
            Report.flush(this);
        } catch (Throwable error) {
            Report.problem("首次使用指南初始化失败", error);
            // A broken optional guide must never block the offline app or recurse via need().
            if (fromSettings()) finish();
            else {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(COMPLETED, true).commit();
                startHome(false);
            }
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt(STATE_PAGE, page);
        state.putBoolean(STATE_PERMISSION, permissionRequested);
        state.putBoolean(STATE_WELCOME_PLAYED, welcomePlayed);
        super.onSaveInstanceState(state);
    }

    @Override protected void onStop() {
        // Activity changes (permission UI, Settings, rotation) can cancel a page animator.
        // Never resume with both pages visible or with navigation permanently locked.
        if (changing && pages != null) {
            for (int i = 0; i < pages.length; i++) {
                View view = pages[i];
                view.animate().cancel();
                view.setVisibility(i == page ? View.VISIBLE : View.GONE);
                view.setAlpha(1f);
                view.setTranslationX(0f);
                view.setImportantForAccessibility(i == page
                        ? View.IMPORTANT_FOR_ACCESSIBILITY_YES
                        : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
            changing = false;
        }
        if (pages != null) {
            // The one-shot text entrance must not leave a title transparent after interruption.
            for (int id : new int[]{R.id.guide_title_first, R.id.guide_title_second}) {
                View line = findViewById(id);
                if (line != null) {
                    line.animate().cancel();
                    line.setAlpha(1f);
                    line.setTranslationY(0f);
                }
            }
            View finalMark = ((ViewGroup) pages[PAGE_COUNT - 1]).getChildAt(0);
            finalMark.animate().cancel();
            finalMark.setScaleX(1f);
            finalMark.setScaleY(1f);
        }
        super.onStop();
    }

    @Override protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        }
        super.onDestroy();
    }

    @Override protected void onResume() {
        super.onResume();
        if (notificationAction != null) refreshPermission();
    }

    private <T extends View> T need(int id) {
        T view = findViewById(id);
        if (view == null) throw new IllegalStateException("缺少指南视图 " + id);
        return view;
    }

    private void updateControls() {
        ((TextView) need(R.id.guide_progress)).setText(
                String.format(Locale.ROOT, "%02d / %02d", page + 1, PAGE_COUNT));
        ((TextView) need(R.id.guide_start)).setText(page == PAGE_COUNT - 1
                ? (fromSettings() ? "返回设置  →" : "开始使用  →") : "继续  →");
        need(R.id.guide_back).setVisibility(page == 0 ? View.INVISIBLE : View.VISIBLE);
        need(R.id.guide_skip).setVisibility(page == PAGE_COUNT - 1 ? View.INVISIBLE : View.VISIBLE);
    }

    /** Directional, interrupt-safe page change; only the page surface moves, not its children. */
    private void showPage(int next) {
        if (leaving || changing || next < 0 || next >= PAGE_COUNT || next == page) return;
        final View old = pages[page];
        final View target = pages[next];
        final int direction = next > page ? 1 : -1;
        changing = true;
        page = next;
        updateControls();
        ScrollView scroll = need(R.id.guide_scroll);
        scroll.scrollTo(0, 0);
        old.animate().cancel();
        target.animate().cancel();
        old.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        target.setVisibility(View.VISIBLE);
        target.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        if (!Motion.animatorsEnabled()) {
            old.setVisibility(View.GONE);
            old.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            old.setAlpha(1f);
            old.setTranslationX(0f);
            target.setAlpha(1f);
            target.setTranslationX(0f);
            changing = false;
        } else {
            float distance = Motion.dp(this, 28);
            target.setAlpha(0f);
            target.setTranslationX(direction * distance);
            old.animate().alpha(0f).translationX(-direction * distance * .6f)
                    .setDuration(170).setInterpolator(PAGE_CURVE)
                    .withEndAction(() -> {
                        old.setVisibility(View.GONE);
                        old.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                        old.setAlpha(1f);
                        old.setTranslationX(0f);
                    }).start();
            target.animate().alpha(1f).translationX(0f)
                    .setStartDelay(25).setDuration(260).setInterpolator(PAGE_CURVE)
                    .withEndAction(() -> {
                        changing = false;
                        if (page == PAGE_COUNT - 1 && !welcomePlayed) {
                            welcomePlayed = true;
                            Motion.pop(((ViewGroup) target).getChildAt(0));
                        }
                    }).start();
        }
        // One spoken heading, even though the first title animates as two visual lines.
        target.announceForAccessibility(((TextView) need(R.id.guide_progress)).getText()
                + "，" + PAGE_TITLES[page]);
    }

    private void navigateBack() {
        if (leaving || changing) return;
        if (page > 0) showPage(page - 1);
        else finishSetup(false, null);
    }

    private boolean fromSettings() {
        return getIntent() != null && getIntent().getBooleanExtra(FROM_SETTINGS, false);
    }

    private void refreshPermission() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        boolean enabled = manager != null && manager.areNotificationsEnabled();
        notificationAction.setText(enabled ? "已允许 · 管理通知"
                : permissionRequested ? "前往系统通知设置" : "允许通知");
    }

    private void requestNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (manager != null && manager.areNotificationsEnabled()) {
            openNotificationSettings();
        } else if (Build.VERSION.SDK_INT >= 33 && !permissionRequested
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            permissionRequested = true;
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_REQUEST);
        } else {
            openNotificationSettings();
        }
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == NOTIFICATION_REQUEST && notificationAction != null) refreshPermission();
    }

    private void openNotificationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } catch (Throwable error) {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (Throwable second) {
                Report.problem("打开系统通知设置失败", second);
            }
        }
    }

    private void finishSetup(boolean expand, View trigger) {
        if (leaving) return;
        leaving = true;
        if (fromSettings()) {
            // Animate the *window* out so the Settings window is exposed continuously.
            // Fading only our opaque root would expose a blank decor background instead.
            finish();
            overridePendingTransition(Motion.animatorsEnabled() ? R.anim.guide_stay : 0,
                    Motion.animatorsEnabled() ? R.anim.guide_exit : 0);
            return;
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(COMPLETED, true).commit();
        FrameLayout root = need(R.id.guide_root);
        if (!expand || !Motion.animatorsEnabled() || trigger == null
                || root.getWidth() == 0 || root.getHeight() == 0) {
            startHome(false);
            return;
        }
        View cover = new View(this);
        cover.setBackgroundColor(getColor(R.color.primary_fill));
        cover.setClickable(true);
        root.addView(cover, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        int[] origin = new int[2];
        int[] window = new int[2];
        trigger.getLocationInWindow(origin);
        root.getLocationInWindow(window);
        int x = origin[0] - window[0] + trigger.getWidth() / 2;
        int y = origin[1] - window[1] + trigger.getHeight() / 2;
        int reachX = Math.max(x, root.getWidth() - x);
        int reachY = Math.max(y, root.getHeight() - y);
        float radius = (float) Math.hypot(reachX, reachY);
        Animator reveal = ViewAnimationUtils.createCircularReveal(cover, x, y, 0f, radius);
        reveal.setDuration(430);
        reveal.setInterpolator(PAGE_CURVE);
        reveal.addListener(new AnimatorListenerAdapter() {
            private boolean done;
            private void complete() {
                if (done) return;
                done = true;
                startHome(true);
            }
            @Override public void onAnimationEnd(Animator animation) { complete(); }
            @Override public void onAnimationCancel(Animator animation) { complete(); }
        });
        reveal.start();
    }

    private void startHome(boolean portal) {
        startActivity(new Intent(this, MainActivity.class)
                .putExtra("skip_welcome_once", true)
                .putExtra("welcome_portal", portal));
        overridePendingTransition(0, 0);
        finish();
        overridePendingTransition(0, 0);
    }

    @Override public void onBackPressed() { navigateBack(); }
}
