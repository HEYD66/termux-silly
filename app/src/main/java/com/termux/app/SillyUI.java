package com.termux.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shared visual styling for the SillyTavern control panel surfaces
 * (home overlay, standalone launcher, browser, log viewer). Keeps a single
 * dark "SillyTavern" palette and a handful of reusable drawable builders so
 * the various activities stay visually consistent.
 */
public final class SillyUI {

    private SillyUI() {}

    // Palette - light anime control panel over the real black Termux terminal.
    public static final int BG_ROOT       = 0x00000000;
    public static final int BG_CARD       = 0xF7FFFFFF;
    public static final int BG_CARD_ALT   = 0xFFF7FAFF;
    public static final int BG_INPUT      = 0xFFFFFFFF;
    public static final int ACCENT        = 0xFF4D7CFF;
    public static final int ACCENT_DARK   = 0xFF2E5ADB;
    public static final int ACCENT_TEXT   = 0xFF3151B7;
    public static final int DANGER        = 0xFFE24B64;
    public static final int SUCCESS       = 0xFF13A15B;
    public static final int WARNING       = 0xFFD49216;
    public static final int TEXT_PRIMARY  = 0xFF1F2430;
    public static final int TEXT_SECONDARY= 0xFF747B8C;
    public static final int TEXT_HINT     = 0xFF9CA3AF;
    public static final int DIVIDER       = 0xFFDDE5F2;

    public static final int BTN_PRIMARY   = 1;
    public static final int BTN_SECONDARY = 2;
    public static final int BTN_DANGER    = 3;
    public static final int BTN_GHOST     = 4;

    public static int dp(Context c, int v) {
        return (int) (v * c.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable roundRect(int fill, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radiusDp);
        if (strokeColor != 0 && strokeDp > 0) {
            d.setStroke((int) (strokeDp * 4), strokeColor);
        }
        return d;
    }

    public static GradientDrawable card(Context c) {
        return roundRect(BG_CARD, dp(c, 16), 0, 0);
    }

    public static GradientDrawable inputBackground(Context c, boolean focused) {
        return roundRect(BG_INPUT, dp(c, 10), focused ? ACCENT : DIVIDER, focused ? 1.5f : 1f);
    }

    public static void styleButton(Context c, Button b, int type) {
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setMinHeight(dp(c, 46));
        b.setPadding(dp(c, 16), dp(c, 12), dp(c, 16), dp(c, 12));
        b.setForeground(null);

        int fill, stroke, textColor;
        float strokeDp;
        switch (type) {
            case BTN_PRIMARY:
                fill = 0xFFF4F7FF; stroke = ACCENT; textColor = ACCENT_DARK; strokeDp = 1.5f;
                break;
            case BTN_DANGER:
                fill = 0xFFFFF2F5; stroke = 0xFFFF9AA8; textColor = DANGER; strokeDp = 1.5f;
                break;
            case BTN_GHOST:
                fill = 0xFFFFFBF3; stroke = 0xFFF3D6A6; textColor = 0xFF9A6817; strokeDp = 1f;
                break;
            case BTN_SECONDARY:
            default:
                fill = 0xFFF8FCFF; stroke = 0xFFB8DFFF; textColor = 0xFF3268B7; strokeDp = 1.5f;
                break;
        }
        GradientDrawable bg = roundRect(fill, dp(c, 12), stroke, strokeDp);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int rippleColor = (type == BTN_PRIMARY) ? 0x224D7CFF : 0x225DA7E8;
            int mask = (type == BTN_PRIMARY) ? Color.WHITE : ACCENT;
            b.setBackground(new RippleDrawable(ColorStateList.valueOf(rippleColor), bg,
                roundRect(mask, dp(c, 12), 0, 0)));
        } else {
            b.setBackground(bg);
        }
        b.setTextColor(textColor);
    }

    public static TextView sectionHeader(Context c, String text) {
        TextView tv = new TextView(c);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setTextColor(TEXT_PRIMARY);
        tv.setLetterSpacing(0f);
        return tv;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(DIVIDER);
        v.setLayoutParams(new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 1)));
        return v;
    }

    public static void styleInput(Context c, EditText e) {
        e.setBackground(inputBackground(c, false));
        e.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
        e.setTextColor(TEXT_PRIMARY);
        e.setHintTextColor(TEXT_HINT);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setOnFocusChangeListener((v, hasFocus) ->
            v.setBackground(inputBackground(c, hasFocus)));
    }

    public static void styleSpinner(Context c, android.widget.Spinner s) {
        GradientDrawable bg = roundRect(BG_INPUT, dp(c, 10), DIVIDER, 1f);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setBackground(new RippleDrawable(ColorStateList.valueOf(0x225DA7E8), bg,
                roundRect(BG_INPUT, dp(c, 10), 0, 0)));
        } else {
            s.setBackground(bg);
        }
        s.setPadding(dp(c, 14), dp(c, 12), dp(c, 14), dp(c, 12));
    }

    public static void styleCheckBox(Context c, CheckBox cb) {
        cb.setTextColor(TEXT_PRIMARY);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        cb.setPadding(dp(c, 8), 0, 0, 0);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cb.setButtonTintList(ColorStateList.valueOf(ACCENT_DARK));
        }
    }

    public static LinearLayout card(Context c, String title) {
        LinearLayout card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(card(c));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(c, 6), 0, dp(c, 6));
        card.setLayoutParams(lp);
        int pad = dp(c, 16);
        card.setPadding(pad, pad, pad, pad);

        if (title != null) {
            TextView header = sectionHeader(c, title);
            LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hlp.setMargins(0, 0, 0, dp(c, 10));
            card.addView(header, hlp);
        }
        return card;
    }

    public static void darkRoot(LinearLayout root) {
        root.setBackgroundColor(0xFF0B0D14);
    }
}
