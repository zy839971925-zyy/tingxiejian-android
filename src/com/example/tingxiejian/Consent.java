package com.example.tingxiejian;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

/**
 * Read-and-confirm gate for the bundled third-party libraries and model weights.
 *
 * Every model the offline pipeline needs ships inside the APK, so nothing is downloaded and no
 * network is required. What the project MIT license does not cover (runtime libraries, model
 * weights) is therefore bundled as-is: the owner must read and accept those terms once before the
 * first transcription. Declining leaves the app usable for reading/exporting, but transcription
 * stays blocked until the terms are accepted. The panel follows the app's card language: paper
 * backdrop, numbered editorial eyebrow, sunken reading area, gated primary action.
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
     * positive action; dismissing or declining runs nothing. A {@code null} callback makes this a
     * plain "read the terms" preview from the guide.
     */
    static void show(final Activity activity, final Runnable onAccepted) {
        View root = activity.getLayoutInflater().inflate(R.layout.dialog_consent, null);
        TextView body = root.findViewById(R.id.consent_body);
        body.setText(R.string.consent_body);
        final CheckBox agree = root.findViewById(R.id.consent_check);
        final TextView accept = root.findViewById(R.id.consent_accept);
        final TextView decline = root.findViewById(R.id.consent_decline);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(root)
                .setCancelable(true)
                .create();
        accept.setEnabled(false); // accepting requires reading: the checkbox gates the button
        agree.setOnCheckedChangeListener((button, checked) -> accept.setEnabled(checked));
        accept.setOnClickListener(v -> {
            accept(activity);
            dialog.dismiss();
            if (onAccepted != null) onAccepted.run();
        });
        decline.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        Motion.enter(root);
        Motion.press(accept);
        Motion.press(decline);
    }
}
