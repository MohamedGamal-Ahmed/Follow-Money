package com.mohamedgamal.masareef;

import android.content.Context;

/** Runtime palette backed by the same resources as the framework widget themes. */
final class UiTheme {
    static final int TOUCH = 48, FIELD_RADIUS = 12, BUTTON_RADIUS = 14, SHEET_RADIUS = 26;
    static final int BODY = 15, CAPTION = 12, TITLE = 20, AMOUNT = 36;
    static final int SPACE = 8, PAGE_PADDING = 20;
    final boolean dark;
    final int ink, muted, background, surface, accent, pale, border, fieldOutline, danger, income;
    final int brand, brandSoft, emerald, lime, indigo, gold, orange, onPrimary, onDanger;

    UiTheme(Context context, boolean dark) {
        this.dark = dark;
        ink = context.getColor(dark ? R.color.ui_ink_dark : R.color.ui_ink_light);
        muted = context.getColor(dark ? R.color.ui_muted_dark : R.color.ui_muted_light);
        background = context.getColor(dark ? R.color.ui_background_dark : R.color.ui_background_light);
        surface = context.getColor(dark ? R.color.ui_surface_dark : R.color.ui_surface_light);
        accent = context.getColor(dark ? R.color.ui_accent_dark : R.color.brand_forest);
        pale = context.getColor(dark ? R.color.ui_pale_dark : R.color.ui_pale_light);
        border = context.getColor(dark ? R.color.ui_border_dark : R.color.ui_border_light);
        fieldOutline = context.getColor(dark ? R.color.ui_field_outline_dark : R.color.ui_field_outline_light);
        danger = context.getColor(dark ? R.color.ui_danger_dark : R.color.ui_danger_light);
        income = context.getColor(dark ? R.color.ui_income_dark : R.color.ui_income_light);
        brand = context.getColor(R.color.brand_forest);
        brandSoft = context.getColor(R.color.ui_brand_soft);
        emerald = context.getColor(R.color.brand_emerald);
        lime = context.getColor(R.color.brand_lime);
        indigo = context.getColor(R.color.ui_indigo);
        gold = context.getColor(R.color.ui_gold);
        orange = context.getColor(R.color.ui_orange);
        onPrimary = dark ? background : surface;
        onDanger = dark ? background : surface;
    }
}
