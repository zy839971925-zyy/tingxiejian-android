package com.example.tingxiejian;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Read-and-confirm gate for the bundled third-party libraries and model weights.
 *
 * Every model the offline pipeline needs ships inside the APK, so nothing is downloaded and no
 * network is required. What the project MIT license does not cover (runtime libraries, model
 * weights) is therefore bundled as-is: the owner must read and accept those terms once before the
 * first transcription. Declining leaves the app usable for reading/exporting, but transcription
 * stays blocked until the terms are accepted.
 */
final class Consent {
    private static final String PREFS = "third_party_consent";
    private static final String ACCEPTED_AT = "accepted_at";

    static boolean accepted(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(ACCEPTED_AT, 0) > 0;
    }

    private static void accept(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(ACCEPTED_AT, System.currentTimeMillis()).apply();
    }

    /**
     * Blocking modal. {@code onAccepted} runs only after the user ticks the checkbox and taps the
     * positive button; dismissing or declining runs nothing.
     */
    static void show(final Activity activity, final Runnable onAccepted) {
        float density = activity.getResources().getDisplayMetrics().density;
        int pad = (int) (16 * density);
        TextView body = new TextView(activity);
        body.setText(R.string.consent_body);
        body.setTextSize(14);
        body.setMovementMethod(new ScrollingMovementMethod());
        body.setPadding(pad, pad / 2, pad, pad / 2);
        CheckBox agree = new CheckBox(activity);
        agree.setText(R.string.consent_checkbox);
        agree.setPadding(pad, pad / 2, pad, 0);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(body);
        content.addView(agree);
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.consent_title)
                .setView(content)
                .setCancelable(true)
                .setPositiveButton(R.string.consent_accept, null)
                .setNegativeButton(R.string.consent_decline, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            Button proceed = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            proceed.setEnabled(false); // accepting requires reading: the checkbox gates the button
            agree.setOnCheckedChangeListener((button, checked) -> proceed.setEnabled(checked));
            proceed.setOnClickListener(v -> {
                accept(activity);
                dialog.dismiss();
                if (onAccepted != null) onAccepted.run();
            });
        });
        dialog.show();
    }
}
