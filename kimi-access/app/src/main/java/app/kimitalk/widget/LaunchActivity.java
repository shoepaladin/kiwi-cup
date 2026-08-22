package app.kimitalk.widget;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Invisible trampoline activity. It reads the requested mode (forced by a
 * widget zone, or the saved setting), asks {@link TargetResolver} for a plan,
 * fires the corresponding intent, and finishes — the user never sees this
 * activity.
 *
 * <p>All Android-specific glue lives here; all decisions live in
 * {@link TargetResolver} so they can be unit-tested off-device.
 */
public class LaunchActivity extends Activity {

    /**
     * Optional extra used by the split widget's zones: "app" forces the Kimi
     * app, "web" forces the browser. Absent = honor the saved mode.
     */
    public static final String EXTRA_FORCE_TARGET = "app.kimitalk.widget.extra.FORCE_TARGET";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PackageManager pm = getPackageManager();

        // Capture the launch intent ONCE and reuse it. Re-querying at launch
        // time invites a TOCTOU null (uninstall between check and launch) —
        // and since the resolver only returns KIMI_APP when kimiInstalled is
        // true, reusing this object makes the launch provably null-safe.
        Intent appLaunch = pm.getLaunchIntentForPackage(KimiTargets.KIMI_PACKAGE);
        boolean kimiInstalled = appLaunch != null;
        boolean browserAvailable = !pm.queryIntentActivities(
                new Intent(Intent.ACTION_VIEW, Uri.parse(KimiTargets.KIMI_WEB_URL)), 0)
                .isEmpty();

        // A widget zone may force a target; otherwise the user's saved mode
        // from the settings screen wins. Garbage values fall back to AUTO.
        Intent incoming = getIntent();
        String forced = incoming != null ? incoming.getStringExtra(EXTRA_FORCE_TARGET) : null;
        String stored = getSharedPreferences(SettingsKeys.PREFS_NAME, MODE_PRIVATE)
                .getString(SettingsKeys.KEY_MODE, SettingsKeys.DEFAULT_MODE);
        TargetResolver.Mode mode =
                TargetResolver.Mode.fromString(forced != null ? forced : stored);

        TargetResolver.Plan plan = TargetResolver.resolve(mode, kimiInstalled, browserAvailable);

        switch (plan.action) {
            case KIMI_APP: {
                appLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(appLaunch);
                break;
            }
            case WEB_CHAT: {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(KimiTargets.KIMI_WEB_URL)));
                break;
            }
            case ASK_USER: {
                Intent ask = new Intent(this, ChooseTargetActivity.class);
                ask.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(ask);
                break;
            }
            case UNAVAILABLE:
            default: {
                Toast.makeText(this, R.string.msg_no_target, Toast.LENGTH_LONG).show();
                break;
            }
        }

        finish();
    }
}
