package app.kimitalk.widget;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

/**
 * The app's single visible screen — reached from the app-drawer icon or the
 * widget's three-dot affordance. Holds the tap-mode picker, the two project
 * shortcut slots, the three widget-color rows, and the About popup.
 */
public class SettingsActivity extends Activity {

    private SharedPreferences prefs;
    private EditText p1Name;
    private EditText p1Url;
    private EditText p2Name;
    private EditText p2Url;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences(SettingsKeys.PREFS_NAME, MODE_PRIVATE);

        // ---- tap mode ----
        RadioGroup group = (RadioGroup) findViewById(R.id.mode_group);
        group.check(idFor(prefs.getString(SettingsKeys.KEY_MODE, SettingsKeys.DEFAULT_MODE)));
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int checkedId) {
                prefs.edit().putString(SettingsKeys.KEY_MODE, modeFor(checkedId)).apply();
            }
        });

        // ---- project slots ----
        p1Name = (EditText) findViewById(R.id.p1_name);
        p1Url = (EditText) findViewById(R.id.p1_url);
        p2Name = (EditText) findViewById(R.id.p2_name);
        p2Url = (EditText) findViewById(R.id.p2_url);
        p1Name.setText(prefs.getString(SettingsKeys.KEY_P1_NAME, ""));
        p1Url.setText(prefs.getString(SettingsKeys.KEY_P1_URL, ""));
        p2Name.setText(prefs.getString(SettingsKeys.KEY_P2_NAME, ""));
        p2Url.setText(prefs.getString(SettingsKeys.KEY_P2_URL, ""));

        // ---- widget colors ----
        bindColorRow(R.id.row_color_fg, R.id.swatch_fg,
                SettingsKeys.KEY_FG, R.string.color_picker_fg_title);
        bindColorRow(R.id.row_color_bg, R.id.swatch_bg,
                SettingsKeys.KEY_BG, R.string.color_picker_bg_title);
        bindColorRow(R.id.row_color_hl, R.id.swatch_hl,
                SettingsKeys.KEY_HL, R.string.color_picker_hl_title);
        refreshSwatches();

        Button save = (Button) findViewById(R.id.btn_save_projects);
        save.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.edit()
                        .putString(SettingsKeys.KEY_P1_NAME, p1Name.getText().toString().trim())
                        .putString(SettingsKeys.KEY_P1_URL, p1Url.getText().toString().trim())
                        .putString(SettingsKeys.KEY_P2_NAME, p2Name.getText().toString().trim())
                        .putString(SettingsKeys.KEY_P2_URL, p2Url.getText().toString().trim())
                        .apply();
                KimiProjectsWidgetProvider.updateAllAppWidgets(SettingsActivity.this);
                Toast.makeText(SettingsActivity.this, R.string.msg_saved, Toast.LENGTH_SHORT)
                        .show();
            }
        });

        // ---- about ----
        Button about = (Button) findViewById(R.id.btn_about);
        about.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAbout();
            }
        });
    }

    /**
     * One tappable color row: opens the picker, persists the choice, then
     * repaints both widget variants immediately. Colors apply on pick —
     * they don't wait for the projects Save button.
     */
    private void bindColorRow(int rowId, final int swatchId, final String key,
                              final int titleRes) {
        findViewById(rowId).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int current = prefs.getInt(key, SettingsKeys.COLOR_UNSET);
                ColorPickerDialog.show(SettingsActivity.this, titleRes, current,
                        new ColorPickerDialog.OnColorPicked() {
                            @Override
                            public void onPicked(int color) {
                                prefs.edit().putInt(key, color).apply();
                                refreshSwatches();
                                KimiProjectsWidgetProvider.updateAllAppWidgets(
                                        SettingsActivity.this);
                                KimiMicWidgetProvider.updateAllAppWidgets(
                                        SettingsActivity.this);
                            }
                        });
            }
        });
    }

    /** Paint the three preview dots; unset slots show the resolved default. */
    private void refreshSwatches() {
        paintSwatch(R.id.swatch_fg, SettingsKeys.KEY_FG, SettingsKeys.DEFAULT_FG);
        paintSwatch(R.id.swatch_bg, SettingsKeys.KEY_BG, SettingsKeys.DEFAULT_BG);
        paintSwatch(R.id.swatch_hl, SettingsKeys.KEY_HL,
                WidgetStyling.contrastOn(
                        prefs.getInt(SettingsKeys.KEY_FG, SettingsKeys.DEFAULT_FG)));
    }

    private void paintSwatch(int swatchId, String key, int fallback) {
        int stored = prefs.getInt(key, SettingsKeys.COLOR_UNSET);
        int color = stored != SettingsKeys.COLOR_UNSET ? stored : fallback;
        android.graphics.drawable.GradientDrawable dot =
                new android.graphics.drawable.GradientDrawable();
        dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dot.setColor(color);
        dot.setStroke(2, 0x338A7D6B);
        findViewById(swatchId).setBackground(dot);
    }

    private void showAbout() {
        String message = getString(R.string.app_name)
                + "\nVersion " + VersionInfo.VERSION_NAME
                + " (" + VersionInfo.VERSION_DATE + ")"
                + "\n\nCode: " + VersionInfo.REPO_URL
                + "\n\nUnofficial widget — not affiliated with Moonshot AI.";
        new AlertDialog.Builder(this)
                .setTitle(R.string.about_title)
                .setMessage(message)
                .setPositiveButton(R.string.about_open_repo, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse(VersionInfo.REPO_URL)));
                    }
                })
                .setNegativeButton(R.string.about_close, null)
                .show();
    }

    private static int idFor(String mode) {
        if ("app".equals(mode)) return R.id.mode_app;
        if ("web".equals(mode)) return R.id.mode_web;
        if ("ask".equals(mode)) return R.id.mode_ask;
        return R.id.mode_auto;
    }

    private static String modeFor(int id) {
        if (id == R.id.mode_app) return "app";
        if (id == R.id.mode_web) return "web";
        if (id == R.id.mode_ask) return "ask";
        return "auto";
    }
}
