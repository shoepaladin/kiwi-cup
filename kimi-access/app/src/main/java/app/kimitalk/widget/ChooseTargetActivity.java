package app.kimitalk.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Translucent host for the "ask every time" dialog. Only reachable when the
 * user picked ASK mode and both the Kimi app and a browser are installed —
 * {@link TargetResolver} short-circuits every other case.
 */
public class ChooseTargetActivity extends Activity {

    private final int[] selected = {0};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            // Recreated (rotation, process restore): don't stack a second
            // dialog on top of the one the framework is already restoring.
            finish();
            return;
        }

        final String[] items = {
                getString(R.string.dialog_choice_app),
                getString(R.string.dialog_choice_web)
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_title)
                .setSingleChoiceItems(items, 0, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        selected[0] = which;
                    }
                })
                .setPositiveButton(R.string.dialog_once, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        fire(selected[0]);
                        finish();
                    }
                })
                .setNegativeButton(R.string.dialog_always, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        remember(selected[0]);
                        fire(selected[0]);
                        finish();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        finish();
                    }
                })
                .show();
    }

    private void remember(int which) {
        getSharedPreferences(SettingsKeys.PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(SettingsKeys.KEY_MODE, which == 0 ? "app" : "web")
                .apply();
    }

    private void fire(int which) {
        if (which == 0) {
            // The availability check happened seconds ago in LaunchActivity —
            // the app can genuinely be gone by now, so re-check before firing.
            Intent launch = getPackageManager()
                    .getLaunchIntentForPackage(KimiTargets.KIMI_PACKAGE);
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(launch);
            } else {
                Toast.makeText(this, R.string.msg_no_target, Toast.LENGTH_LONG).show();
            }
        } else {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(KimiTargets.KIMI_WEB_URL)));
        }
    }
}
