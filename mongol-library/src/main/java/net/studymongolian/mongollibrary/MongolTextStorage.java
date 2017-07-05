package net.studymongolian.mongollibrary;

// The purpose of this class is to store the text for TextViews.
// It is a wrapper for the unicode to glyph rendering and indexing
// so that developers can use Unicode exclusively without worrying
// about the glyph rendering and indexing.
//
// glyph: this is what is displayed by the font and MongolTextView
// unicode: this is the encoding that the app user and library user
//          (app developer) work with
//
// TODO: render the unicode to glyphs using MongolCode
// TODO: maintain Unicode<->Glyph index maps with lazy instantiation
// TODO: handle spanned text
// TODO: update changes without needing to render everything again
//
// XXX can we keep this class package private?
// Is it actually needed by app developers?

import android.graphics.Color;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.CharacterStyle;
import android.view.inputmethod.BaseInputConnection;

import java.util.ArrayList;
import java.util.List;


public class MongolTextStorage implements Editable {

    private CharSequence mUnicodeText;
    private CharSequence mGlyphText;
    private MongolCode mRenderer;
    //private int[] mGlyphArrayWithUnicodeIndexes;
    //private int[] mUnicodeArrayWithGlyphIndexes;
    private ArrayList<Integer> mGlyphIndexes; // item number is unicode index, value is glyph index
    private OnChangeListener mChangelistener;

    MongolTextStorage() {
        this("");
    }

    MongolTextStorage(CharSequence unicodeText) {
        mRenderer = MongolCode.INSTANCE;
        this.mChangelistener = null;
        setText(unicodeText);
    }

    // callback methods to let EditText (or other view) know about changes
    // to the text here
    public interface OnChangeListener {
        void onTextChanged(CharSequence text, int start, int lengthBefore, int lengthAfter);
        //void onSpanChanged();
        void onSpanChanged(Spanned buf, Object what, int oldStart, int newStart, int oldEnd, int newEnd);
    }

    public void setOnChangeListener(OnChangeListener listener) {
        this.mChangelistener = listener;
    }


    CharSequence getUnicodeText() {
        return mUnicodeText;
    }

    CharSequence getGlyphText() {
        return mGlyphText;
    }

    public void setText(CharSequence unicodeText) {
        if (unicodeText == null) {
            unicodeText = "";
        }

        int oldLength = (mUnicodeText == null) ? 0 : mUnicodeText.length();

        mUnicodeText = unicodeText;
        mGlyphText = mRenderer.unicodeToMenksoft(unicodeText);
        if (mUnicodeText instanceof Spannable) {
            mGlyphText = new SpannableStringBuilder(mGlyphText);
        }
        mGlyphIndexes = new ArrayList<>();
        updateGlyphTextForUnicodeRange(0, mUnicodeText.length());


        if (mChangelistener != null)
            mChangelistener.onTextChanged(mUnicodeText, 0, oldLength, mUnicodeText.length());
    }

    // FIXME this is hugely inefficient because it recalculates everything rather than a range
//    private void updateGlyphInfoForSpannedText() {
//        mUnicodeArrayWithGlyphIndexes = new int[mUnicodeText.length()];
//        mGlyphText = mRenderer.unicodeToMenksoft(mUnicodeText.toString(), mUnicodeArrayWithGlyphIndexes);
//        //setSpanOnRenderedText();
//
//        if (!(mUnicodeText instanceof Spanned)) return;
//
//        SpannableString spannable = new SpannableString(mGlyphText);
//        CharacterStyle[] spans = ((Spanned) mUnicodeText).getSpans(0, mUnicodeText.length(), CharacterStyle.class);
//        for (CharacterStyle span : spans) {
//            int unicodeStart = ((Spanned) mUnicodeText).getSpanStart(span);
//            int unicodeEnd = ((Spanned) mUnicodeText).getSpanEnd(span);
//            int glyphStart = mUnicodeArrayWithGlyphIndexes[unicodeStart];
//            int glyphEnd = mUnicodeArrayWithGlyphIndexes[unicodeEnd - 1] + 1;
//            spannable.setSpan(span, glyphStart, glyphEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//        }
//        mGlyphText = spannable;
//    }

    private void updateGlyphTextForUnicodeRange(int start, int end) {
        //if (start == end) return;
        //if (mUnicodeText == null || mUnicodeText.length() == 0) return;
        if (mGlyphIndexes == null) {
            mGlyphIndexes = new ArrayList<>();
        } else if (mGlyphIndexes.size() < mUnicodeText.length()) {
            final int size = mGlyphIndexes.size();
            final int length = mUnicodeText.length();
            for (int i = size; i < length; i++) {
                mGlyphIndexes.add(i, i);
            }
        }

        // update glyph indexes
        boolean indexingHasStarted = false;
        int glyphIndex = 0;
        if (start > 0) {
            glyphIndex = getGlyphIndexForUnicodeIndex(start - 1);
            if (glyphIndex > 0 || MongolCode.isRenderedGlyph(0, mUnicodeText)) {
                indexingHasStarted = true;
            }
        }
        for (int i = start; i < mUnicodeText.length(); i++) {
            if (MongolCode.isRenderedGlyph(i, mUnicodeText)) {
                if (indexingHasStarted) {
                    glyphIndex++;
                }
                indexingHasStarted = true;
            }
            mGlyphIndexes.set(i, glyphIndex);
        }

        if (!(mUnicodeText instanceof Spanned)) return;

        // add spans to glyph string
        CharacterStyle[] spans = ((Spanned) mUnicodeText).getSpans(start, end, CharacterStyle.class);
        for (CharacterStyle span : spans) {
            final int unicodeStart = ((Spanned) mUnicodeText).getSpanStart(span);
            final int unicodeEnd = ((Spanned) mUnicodeText).getSpanEnd(span);
            final int glyphStart = getGlyphIndexForUnicodeIndex(unicodeStart);
            final int glyphEnd = getGlyphIndexForUnicodeIndex(unicodeEnd);
            ((SpannableStringBuilder) mGlyphText).setSpan(span, glyphStart, glyphEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    int getGlyphIndexForUnicodeIndex(int unicodeIndex) {
        // allow an index one past the end to support cursor selection
        //if (unicodeIndex == mUnicodeText.length()) return mGlyphText.length();
        //return mUnicodeArrayWithGlyphIndexes[unicodeIndex];
        //return getGlyphIndex(unicodeIndex);
        if (unicodeIndex == 0) {
            return 0;
        } else if (unicodeIndex == mUnicodeText.length()) {
            return mGlyphText.length();
        }
        return mGlyphIndexes.get(unicodeIndex);
    }

    int getUnicodeIndexForGlyphIndex(int glyphIndex) {
        // calculating the glyph index when needed rather than maintaining a second index
        int unicodeIndex = glyphIndex;
        int length = mUnicodeText.length();
        boolean foundIt = false;
        for (int i = glyphIndex + 1; i < length; i++) {
            //if (i == length) return mUnicodeArrayWithGlyphIndexes[i - 1] + 1;
            if (mGlyphIndexes.get(i) > glyphIndex) {
                foundIt = true;
                break;
            }
            unicodeIndex = i;
        }
        if (foundIt) return unicodeIndex;
        else return length;
    }

    // go to the start of the Mongol word from the indicated position
    private int getMongolWordStart(int position, CharSequence source) {
        int wordStart = position;
        for (int i = position - 1; i >= 0; i--) {
            final char thisChar = source.charAt(i);
            if (MongolCode.isMongolian(thisChar) || thisChar == MongolCode.Uni.NNBS) {
                wordStart = i;
            } else {
                break;
            }
        }
        return wordStart;
    }

    // go to the end of the Mongol word from the indicated position
    private int getMongolWordEnd(int position, CharSequence source) {
        int wordEnd = position;
        final int length = source.length();
        for (int i = position; i < length; i++) {
            final char thisChar = source.charAt(i);
            if (MongolCode.isMongolian(thisChar) || thisChar == MongolCode.Uni.NNBS) {
                wordEnd = i + 1;
            } else {
                break;
            }
        }
        return wordEnd;
    }

//    // MVS, FVS, and sometimes YA are excluded in the glyph indexing
//    private boolean isVisibleGlyphChar(int index, CharSequence someString) {
//        final char someChar = someString.charAt(index);
//        if (someChar == MongolCode.Uni.MVS) return false;
//        if (MongolCode.isFVS(someChar)) return false;
//        // Y is hidden in Vowel + Y + I
//        if (someChar == MongolCode.Uni.YA) {
//            if (index > 0 && index < someString.length() - 1 &&
//                    MongolCode.isVowel(someString.charAt(index - 1)) &&
//                    someString.charAt(index + 1) == MongolCode.Uni.I) {
//                return false;
//            }
//        }
//        return true;
//    }

//    private int getGlyphIndex(int unicodeIndex) {
//        if (unicodeIndex == 0) {
//            return 0;
//        } else if (unicodeIndex == mUnicodeText.length()) {
//            return mGlyphText.length();
//        }
//        return mUnicodeArrayListWithGlyphIndexes.get(unicodeIndex);
//    }

    // Editable interface methods

    @Override
    public Editable replace(int st, int en, CharSequence source, int start, int end) {
        if (!(mUnicodeText instanceof SpannableStringBuilder)) {
            mUnicodeText = new SpannableStringBuilder(mUnicodeText);
            mGlyphText = new SpannableStringBuilder(mGlyphText);
        }

        int oldLength = mUnicodeText.length();

        // replace glyphs (expand to the whole word preceding and following)
        int wordStart = getMongolWordStart(st, mUnicodeText);
        int wordEnd = getMongolWordEnd(en, mUnicodeText);
        int glyphStart = getGlyphIndexForUnicodeIndex(wordStart);
        int glyphEnd = getGlyphIndexForUnicodeIndex(wordEnd);
        ((SpannableStringBuilder) mUnicodeText).replace(st, en, source, start, end);
        int adjustedEnd = wordEnd + (end - start) - (en - st);
        CharSequence unicodeReplacement = mUnicodeText.subSequence(wordStart, adjustedEnd);
        String glyphReplacement = mRenderer.unicodeToMenksoft(unicodeReplacement);
        ((SpannableStringBuilder) mGlyphText).replace(glyphStart, glyphEnd, glyphReplacement);
        updateGlyphTextForUnicodeRange(wordStart, adjustedEnd);

        if (mChangelistener != null)
            mChangelistener.onTextChanged(mUnicodeText, st, oldLength, mUnicodeText.length());

        return this;
    }

    @Override
    public Editable replace(int st, int en, CharSequence text) {
        return replace(st, en, text, 0, text.length());
    }

    @Override
    public Editable insert(int where, CharSequence text, int start, int end) {
        return replace(where, where, text, start, end);
    }

//    @Override
//    public Editable insert(int where, CharSequence text, int start, int end) {
//        if (!(mUnicodeText instanceof SpannableStringBuilder)) {
//            mUnicodeText = new SpannableStringBuilder(mUnicodeText);
//            mGlyphText = new SpannableStringBuilder(mGlyphText);
//        }
//
//        int oldLength = (mUnicodeText == null) ? 0 : mUnicodeText.length();
//
//        //int insertionLength = end - start;
//        int wordStart = getMongolWordStart(where, mUnicodeText);
//        int wordEnd = getMongolWordEnd(where, mUnicodeText);
//        int glyphStart = getGlyphIndexForUnicodeIndex(wordStart);
//        int glyphEnd = getGlyphIndexForUnicodeIndex(wordEnd);
//        ((SpannableStringBuilder) mUnicodeText).insert(where, text, start, end);
//        String replacementString = mRenderer.unicodeToMenksoft(mUnicodeText.subSequence(wordStart, wordEnd + end - start));
//        ((SpannableStringBuilder) mGlyphText).replace(glyphStart, glyphEnd, replacementString);
//        updateGlyphTextForUnicodeRange(wordStart, wordEnd);
//
//        if (mChangelistener != null)
//            mChangelistener.onTextChanged(mUnicodeText, where, oldLength, mUnicodeText.length());
//
//        return this;
//    }

    @Override
    public Editable insert(int where, CharSequence text) {
        return insert(where, text, 0, text.length());
    }

    @Override
    public Editable delete(int st, int en) {
        return replace(st, en, "", 0, 0);
    }

    @Override
    public Editable append(CharSequence text) {
        return replace(length(), length(), text, 0, text.length());
    }

    @Override
    public Editable append(CharSequence text, int start, int end) {
        return replace(length(), length(), text, start, end) ;
    }

    @Override
    public Editable append(char text) {
        return append(String.valueOf(text));
    }

    @Override
    public void clear() {
        replace(0, length(), "", 0, 0);
    }

    @Override
    public void clearSpans() {
        if (!(mUnicodeText instanceof SpannableStringBuilder)) {
            return;
        }
        ((SpannableStringBuilder) mUnicodeText).clearSpans();
        ((SpannableStringBuilder) mGlyphText).clearSpans();

        final int length = mUnicodeText.length();
        if (mChangelistener != null)
            mChangelistener.onSpanChanged((Spanned) mUnicodeText, null, 0, 0, length, length);
    }

    @Override
    public void setFilters(InputFilter[] filters) {
        if (!(mUnicodeText instanceof SpannableStringBuilder)) {
            return;
        }
        // TODO: this is untested!
        int oldLength = mUnicodeText.length();
        ((SpannableStringBuilder) mUnicodeText).setFilters(filters);
        ((SpannableStringBuilder) mGlyphText).setFilters(filters);
        if (mChangelistener != null)
            mChangelistener.onTextChanged(mUnicodeText, 0, oldLength, mUnicodeText.length());
    }

    @Override
    public InputFilter[] getFilters() {
        if (!(mUnicodeText instanceof SpannableStringBuilder)) {
            return new InputFilter[0];
        }
        return ((SpannableStringBuilder) mUnicodeText).getFilters();
    }

    @Override
    public void getChars(int start, int end, char[] dest, int destoff) {
        if (!(mUnicodeText instanceof SpannableStringBuilder)) {
            ((String) mUnicodeText).getChars(start, end, dest, destoff);
        } else {
            ((SpannableStringBuilder) mUnicodeText).getChars(start, end, dest, destoff);
        }
    }

    @Override
    public void setSpan(Object what, int start, int end, int flags) {
        if (!(mUnicodeText instanceof SpannableStringBuilder)) {
            mUnicodeText = new SpannableStringBuilder(mUnicodeText);
            mGlyphText = new SpannableStringBuilder(mGlyphText);
        }
        ((SpannableStringBuilder) mUnicodeText).setSpan(what, start, end, flags);
        int glyphStart = getGlyphIndexForUnicodeIndex(start);
        int glyphEnd = getGlyphIndexForUnicodeIndex(end);
        ((SpannableStringBuilder) mGlyphText).setSpan(what, glyphStart, glyphEnd, flags);

        if (mChangelistener != null)
            mChangelistener.onSpanChanged((Spanned) mUnicodeText, what, start, start, end, end);
    }

    @Override
    public void removeSpan(Object what) {
        if (!(mUnicodeText instanceof Spanned)) {
            return;
        }
        if (!(mUnicodeText instanceof SpannableStringBuilder)) {
            return;
        }
        ((SpannableStringBuilder) mUnicodeText).removeSpan(what);
        ((SpannableStringBuilder) mGlyphText).removeSpan(what);

        final int length = mUnicodeText.length();
        if (mChangelistener != null)
            mChangelistener.onSpanChanged((Spanned) mUnicodeText, what, 0, 0, length, length);
    }

    @Override
    public <T> T[] getSpans(int start, int end, Class<T> type) {
        if (mUnicodeText instanceof Spanned) {
            return ((Spanned) mUnicodeText).getSpans(start, end, type);
        }
        return null;
    }

    @Override
    public int getSpanStart(Object tag) {
        if (mUnicodeText instanceof Spanned) {
            return ((Spanned) mUnicodeText).getSpanStart(tag);
        }
        return 0;
    }

    @Override
    public int getSpanEnd(Object tag) {
        if (mUnicodeText instanceof Spanned) {
            return ((Spanned) mUnicodeText).getSpanEnd(tag);
        }
        return 0;
    }

    @Override
    public int getSpanFlags(Object tag) {
        if (mUnicodeText instanceof Spanned) {
            return ((Spanned) mUnicodeText).getSpanFlags(tag);
        }
        return 0;
    }

    @Override
    public int nextSpanTransition(int start, int limit, Class type) {
        if (mUnicodeText instanceof Spanned) {
            return ((Spanned) mUnicodeText).nextSpanTransition(start, limit, type);
        }
        return 0;
    }

    @Override
    public int length() {
        return mUnicodeText.length();
    }

    @Override
    public char charAt(int index) {
        return mUnicodeText.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return mUnicodeText.subSequence(start, end);
    }

    @Override
    @NonNull
    public String toString() {
        return (mUnicodeText != null) ? mUnicodeText.toString() : "";
    }
}
