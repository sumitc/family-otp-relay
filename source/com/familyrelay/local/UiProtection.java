/*
 * Decompiled with CFR 0.152.
 */
package com.familyrelay.local;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Toast;

final class UiProtection {
    private UiProtection() {
    }

    static void protect(Window window) {
        if (Build.VERSION.SDK_INT >= 31) {
            window.setHideOverlayWindows(true);
        }
        UiProtection.protect(window.getDecorView());
    }

    @SuppressLint(value={"ClickableViewAccessibility"})
    private static void protect(View view) {
        view.setFilterTouchesWhenObscured(true);
        if (view.isClickable() || view.isLongClickable() || view.isFocusable()) {
            view.setOnTouchListener((target, event) -> {
                boolean obscured;
                boolean bl = obscured = (event.getFlags() & 1) != 0 || Build.VERSION.SDK_INT >= 29 && (event.getFlags() & 2) != 0;
                if (obscured && event.getActionMasked() == 0) {
                    Toast.makeText((Context)target.getContext(), (CharSequence)"Close overlapping apps to use Improm.", (int)0).show();
                }
                return obscured;
            });
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup)view;
            for (int i = 0; i < group.getChildCount(); ++i) {
                UiProtection.protect(group.getChildAt(i));
            }
        }
    }

    static void show(AlertDialog dialog) {
        dialog.show();
        UiProtection.protect(dialog.getWindow());
    }
}

