/**
 * Copyright 2018 WHO<980008027@qq.com>
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p>
 * Modified by project: https://github.com/980008027/JsDroidEditor
 */
package com.stardust.scriptdroid.ui.edit.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.support.v7.widget.AppCompatEditText;
import android.text.Layout;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TimingLogger;
import android.view.Gravity;

import com.stardust.scriptdroid.BuildConfig;
import com.stardust.scriptdroid.ui.edit.theme.Theme;

/**
 * Created by Administrator on 2018/2/11.
 */

public class CodeEditText extends AppCompatEditText {


    static final String LOG_TAG = "CodeEditText";
    private static final boolean DEBUG = BuildConfig.DEBUG;
    
    // 文字范围
    protected HVScrollView mParentScrollView;

    private CodeEditor.CursorChangeCallback mCallback;
    private volatile JavaScriptHighlighter.HighlightTokens mHighlightTokens;
    private Theme mTheme;
    private TimingLogger mLogger = new TimingLogger(LOG_TAG, "draw");

    public CodeEditText(Context context) {
        super(context);
        init();
    }

    public CodeEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setGravity(Gravity.START);
        // 设值背景透明
        setBackgroundColor(Color.TRANSPARENT);
        // 设置字体颜色
        setTextColor(Color.TRANSPARENT);
        // 设置字体
        setTypeface(Typeface.MONOSPACE);
        setHorizontallyScrolling(true);
        mTheme = Theme.getDefault(getContext());
    }

    public void setTheme(Theme theme) {
        mTheme = theme;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mLogger.reset();
        if (mParentScrollView == null) {
            mParentScrollView = (HVScrollView) getParent();
        }
        // 根据行号计算左边距padding 留出绘制行号的空间
        String max = Integer.toString(getLineCount());
        float gutterWidth = getPaint().measureText(max) + 20;
        if (getPaddingLeft() != gutterWidth) {
            setPadding((int) gutterWidth, 0, 0, 0);
        }
        super.onDraw(canvas);
        mLogger.addSplit("super draw");
        // 画文字
        canvas.save();
        canvas.translate(0, getExtendedPaddingTop());
        drawText(canvas);
        mLogger.addSplit("draw text");
        canvas.restore();
        mLogger.dumpToLog();
    }

    // 绘制文本着色
    private void drawText(Canvas canvas) {
        JavaScriptHighlighter.HighlightTokens highlightTokens = mHighlightTokens;
        Layout layout = getLayout();
        long lineRange = getLineRangeForDraw(layout, canvas);
        int firstLineForDraw = LayoutHelper.unpackRangeStartFromLong(lineRange);
        int lastLineForDraw = LayoutHelper.unpackRangeEndFromLong(lineRange);
        if (firstLineForDraw < 0) {
            return;
        }
        int lineCount = getLineCount();
        int paddingLeft = getPaddingLeft();
        Paint paint = getPaint();
        if (DEBUG)
            Log.d(LOG_TAG, "draw line: " + (lastLineForDraw - firstLineForDraw + 1));
        for (int line = firstLineForDraw; line <= lastLineForDraw && line < lineCount; line++) {
            int lineBottom = layout.getLineTop(line + 1);
            int lineBaseline = lineBottom - layout.getLineDescent(line);

            drawLineNumber(canvas, paint, line, lineBaseline);
            if (highlightTokens == null)
                continue;

            drawCode(canvas, paint, paddingLeft, line, layout, lineBaseline, highlightTokens);
        }
    }

    private void drawCode(Canvas canvas, Paint paint, int paddingLeft, int line, Layout layout, int lineBaseline, JavaScriptHighlighter.HighlightTokens highlightTokens) {
        int lineStart = layout.getLineStart(line);
        if (lineStart >= mHighlightTokens.getText().length()) {
            return;
        }
        int lineEnd = layout.getLineVisibleEnd(line);
        int fontCount = 0;
        int scrollX = Math.max(getRealScrollX() - paddingLeft, 0);
        int visibleCharStart = getVisibleCharIndex(paint, scrollX, lineStart, lineEnd);
        int visibleCharEnd = getVisibleCharIndex(paint, scrollX + mParentScrollView.getWidth(), lineStart, lineEnd) + 1;
        int previousColorPos = visibleCharStart;
        int previousColor = mHighlightTokens.getCharColor(previousColorPos);
        if (DEBUG)
            Log.d(LOG_TAG, "draw line " + line + ": " + (visibleCharEnd - visibleCharStart));
        for (int i = visibleCharStart; i < visibleCharEnd && i < lineEnd; i++) {
            fontCount++;
            int color = mHighlightTokens.getCharColor(i);
            if (previousColor != color) {
                drawText(canvas, paint, paddingLeft, lineBaseline, lineStart, previousColorPos, previousColorPos + fontCount, previousColor);
                previousColor = color;
                previousColorPos = i;
                fontCount = 1;
            }
            if (i == visibleCharEnd - 1) {
                drawText(canvas, paint, paddingLeft, lineBaseline, lineStart, previousColorPos, previousColorPos + fontCount, previousColor);
            }
        }

    }


    private int getVisibleCharIndex(Paint paint, int x, int lineStart, int lineEnd) {
        if (x == 0)
            return lineStart;
        int low = lineStart;
        int high = lineEnd - 1;
        while (low < high) {
            int mid = (high + low) >>> 1;
            float midX = paint.measureText(getText(), lineStart, mid + 1);
            if (x < midX) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return low;
    }

    private void drawText(Canvas canvas, Paint paint, int paddingLeft, int lineBaseline, int lineStart, int start, int end, int color) {
        if (start >= end) {
            return;
        }
        paint.setColor(color);
        float offsetX = paint.measureText(getText(), lineStart, start);
        canvas.drawText(getText(), start, end, paddingLeft + offsetX, lineBaseline, paint);
    }

    private void drawLineNumber(Canvas canvas, Paint paint, int line, int lineBaseline) {
        String lineNumberText = Integer.toString(line + 1);
        paint.setColor(mTheme.getLineNumberColor());
        canvas.drawText(lineNumberText, 0, lineNumberText.length(), 10,
                lineBaseline, paint);
    }

    private long getLineRangeForDraw(Layout layout, Canvas canvas) {
        canvas.save();
        int scrollY = getRealScrollY();
        float clipTop = (scrollY == 0) ? 0
                : getExtendedPaddingTop() + scrollY
                - mParentScrollView.getPaddingTop();
        canvas.clipRect(0, clipTop, getWidth(), scrollY
                + mParentScrollView.getHeight());
        long lineRangeForDraw = LayoutHelper.getLineRangeForDraw(layout, canvas);
        canvas.restore();
        return lineRangeForDraw;
    }

    private int getRealScrollY() {
        return mParentScrollView.getScrollY() + getScrollY();
    }


    private int getRealScrollX() {
        return mParentScrollView.getScrollX() + getScrollX();
    }

    @Override
    protected void onSelectionChanged(int selStart, int selEnd) {
        //调用父类的onSelectionChanged时会发送一个AccessibilityEvent，当文本过大时造成异常
        //super.onSelectionChanged(selStart, selEnd);
        if (mCallback == null || selStart != selEnd) {
            return;
        }
        String text = getText().toString();
        if (text.isEmpty()) {
            return;
        }
        int lineStart = text.lastIndexOf("\n", selStart) + 1;
        if (lineStart < 0) {
            lineStart = 0;
        }
        if (lineStart > text.length() - 1) {
            lineStart = text.length() - 1;
        }
        int lineEnd = text.indexOf("\n", selStart);
        if (lineEnd < 0) {
            lineEnd = text.length();
        }
        if (lineEnd < lineStart || lineStart < 0 || lineEnd > text.length())
            return;
        String line = text.substring(lineStart, lineEnd);
        int cursor = selStart - lineStart;
        mCallback.onCursorChange(line, cursor);
    }

    public void setCursorChangeCallback(CodeEditor.CursorChangeCallback callback) {
        mCallback = callback;
    }

    public void updateHighlightTokens(JavaScriptHighlighter.HighlightTokens highlightTokens) {
        mHighlightTokens = highlightTokens;
        postInvalidate();
    }

    @Override
    public void setSelection(int index) {
        if (index < 0) {
            index = 0;
        }
        if (index > getText().length()) {
            index = getText().length();
        }
        super.setSelection(index);
    }
}
