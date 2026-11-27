/*

 This is the source code of exteraGram for Android.

 We do not and cannot prevent the use of our code,
 but be respectful and credit the original author.

 Copyright @exteraGram, 2022-2026

 Licensed under the GNU GPL v. 3 or later, with the additional terms
 in ADDITIONAL_TERMS.md at the root of the repository.

*/

package com.exteragram.messenger.messageexport.render;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;

import androidx.core.content.ContextCompat;

import com.exteragram.messenger.IriskaConfig;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

public final class MessageExportWatermark {

    private static final int TOP_GAP = 8;
    private static final int BOTTOM_GAP = 12;
    private static final int PILL_HEIGHT = 26;
    private static final int PILL_PADDING = 10;
    private static final int ICON_SIZE = 14;
    private static final int ICON_GAP = 5;
    private static final int CONTENT_PADDING = 8;
    private static final String WATERMARK_TEXT = "IriskaGram";

    private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF pill = new RectF();
    private final Drawable icon;
    private final float textWidth;

    public MessageExportWatermark(Context context) {
        textPaint.setTextSize(dp(12));
        textPaint.setTypeface(AndroidUtilities.bold());
        textWidth = textPaint.measureText(WATERMARK_TEXT);
        Drawable d = ContextCompat.getDrawable(context, R.drawable.extera_watermark);
        icon = d != null ? d.mutate() : null;
    }

    public static boolean isEnabled() {
        return IriskaConfig.getMessageExportWatermark();
    }

    public static int getBottomInset() {
        return dp(isEnabled() ? TOP_GAP + PILL_HEIGHT + BOTTOM_GAP : CONTENT_PADDING);
    }

    public void draw(Canvas canvas, int width, float contentBottom, Theme.ResourcesProvider provider) {
        if (!isEnabled()) {
            pill.setEmpty();
            return;
        }
        float left = (width - getPillWidth()) / 2f;
        float top = Math.round(contentBottom) + dp(TOP_GAP);
        if (provider != null && provider.hasGradientService()) {
            canvas.save();
            canvas.translate(left, top);
            provider.applyServiceShaderMatrix(width, 0, left, top);
            drawPill(canvas, 0, 0, provider);
            canvas.restore();
            pill.offset(left, top);
        } else {
            drawPill(canvas, left, top, provider);
        }
    }

    public void drawPill(Canvas canvas, float left, float top, Theme.ResourcesProvider provider) {
        int color = Theme.getColor(Theme.key_chat_serviceText, provider);
        textPaint.setColor(color);
        int pillHeight = getPillHeight();
        pill.set(left, top, left + getPillWidth(), top + pillHeight);
        canvas.drawRoundRect(pill, pillHeight / 2f, pillHeight / 2f, Theme.getThemePaint(Theme.key_paint_chatActionBackground, provider));
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float x = pill.left + dp(PILL_PADDING);
        if (icon != null) {
            int iconSize = dp(ICON_SIZE);
            int iconTop = Math.round(pill.centerY() - iconSize / 2f);
            int iconLeft = Math.round(x);
            icon.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize);
            icon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            icon.draw(canvas);
            x += iconSize + dp(ICON_GAP);
        }
        float textY = pill.centerY() - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(WATERMARK_TEXT, x, textY, textPaint);
    }

    public RectF getBounds() {
        return pill;
    }

    public int getPillWidth() {
        int iconWidth = icon != null ? dp(ICON_SIZE + ICON_GAP) : 0;
        return Math.round(textWidth) + iconWidth + dp(PILL_PADDING) * 2;
    }

    public int getPillHeight() {
        return dp(PILL_HEIGHT);
    }
}
