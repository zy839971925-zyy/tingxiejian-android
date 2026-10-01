package com.example.tingxiejian;

import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.AbsListView;
import android.widget.AbsSeekBar;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;

/** Native semantics and touch feedback for the app's custom Views controls. */
final class UiControls {
    private UiControls() { }

    static void apply(View view) {
        if (view.hasOnClickListeners() && !(view instanceof CompoundButton)
                && !(view instanceof EditText) && !(view instanceof AbsSeekBar)
                && !(view instanceof AbsListView)
                && !(view instanceof TextView && ((TextView) view).isTextSelectable())) {
            button(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) apply(group.getChildAt(i));
        }
    }

    static void button(View view) {
        view.setFocusable(true);
        view.setMinimumHeight((int) Motion.dp(view.getContext(), 48));
        view.setMinimumWidth((int) Motion.dp(view.getContext(), 48));
        view.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName(android.widget.Button.class.getName());
                info.setSelected(host.isSelected());
            }
        });
        Motion.press(view);
    }
}
