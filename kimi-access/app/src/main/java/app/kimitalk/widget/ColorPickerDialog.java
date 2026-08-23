package app.kimitalk.widget;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Framework-only color picker: a grid of curated swatches (the editorial
 * palette plus clear light/dark anchors) plus a hex field. "Auto" resets
 * the slot to {@link SettingsKeys#COLOR_UNSET} so the theme logic derives
 * a sensible value (Material You / contrast) again.
 */
final class ColorPickerDialog {

    interface OnColorPicked {
        /** {@code color} is ARGB, or {@link SettingsKeys#COLOR_UNSET} for Auto. */
        void onPicked(int color);
    }

    /** Curated swatches: house palette + neutral anchors. */
    private static final int[] SWATCHES = {
            0xFFD4E932, // acid lime (default fg)
            0xFF23201C, // warm ink (default bg)
            0xFFF4F0EA, // cream
            0xFFFFFBF5, // off-white
            0xFF4F483E, // warm brown
            0xFF8A7D6B, // muted
            0xFFF54001, // burnt orange
            0xFF0E0E0D, // near-black
            0xFFFFFFFF, // white
            0xFFEAFE32, // acid yellow
            0xFF2E5BFF, // signal blue
            0xFF1E8E5A, // matcha
    };

    private ColorPickerDialog() {
        // static entry only
    }

    static void show(final Context context, int titleRes, int current,
                     final OnColorPicked callback) {
        int dp = (int) (context.getResources().getDisplayMetrics().density + 0.5f);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20 * dp, 12 * dp, 20 * dp, 0);

        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(4);
        for (final int swatch : SWATCHES) {
            View dot = new View(context);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = 44 * dp;
            lp.height = 44 * dp;
            lp.setMargins(6 * dp, 6 * dp, 6 * dp, 6 * dp);
            dot.setLayoutParams(lp);
            dot.setBackground(swatchDrawable(swatch, swatch == current));
            dot.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    callback.onPicked(swatch);
                }
            });
            grid.addView(dot);
        }
        root.addView(grid);

        TextView hexLabel = new TextView(context);
        hexLabel.setText(R.string.color_hex_label);
        hexLabel.setPadding(6 * dp, 8 * dp, 6 * dp, 0);
        root.addView(hexLabel);

        final EditText hex = new EditText(context);
        hex.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        hex.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        hex.setHint("#D4E932");
        hex.setSingleLine(true);
        if (current != SettingsKeys.COLOR_UNSET) {
            hex.setText(String.format("#%06X", (0xFFFFFF & current)));
        }
        root.addView(hex);

        new AlertDialog.Builder(context)
                .setTitle(titleRes)
                .setView(root)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    Integer parsed = parseHex(hex.getText().toString());
                    if (parsed != null) {
                        callback.onPicked(parsed);
                    }
                    // Unparseable hex with no swatch tap: keep the old value.
                })
                .setNeutralButton(R.string.color_auto, (d, w) ->
                        callback.onPicked(SettingsKeys.COLOR_UNSET))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private static GradientDrawable swatchDrawable(int color, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setStroke(selected ? 6 : 2, selected ? 0xFFF54001 : 0x338A7D6B);
        return g;
    }

    /** Parses "#RRGGBB" or "RRGGBB"; returns null on failure. */
    static Integer parseHex(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (s.length() != 6) return null;
        try {
            return 0xFF000000 | (int) Long.parseLong(s, 16);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
