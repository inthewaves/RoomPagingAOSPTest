package com.google.android.material.color;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.core.graphics.ColorUtils;

public class MaterialColorsUpdate {
    /**
     * Calculates a new color by multiplying an additional alpha int value to the alpha channel of a
     * color in integer type.
     *
     * @param originalARGB The original color.
     * @param alpha The additional alpha [0-255].
     * @return The blended color.
     */
    @ColorInt
    public static int compositeARGBWithAlpha(
            @ColorInt int originalARGB, @IntRange(from = 0, to = 255) int alpha) {
        alpha = Color.alpha(originalARGB) * alpha / 255;
        return ColorUtils.setAlphaComponent(originalARGB, alpha);
    }
}
