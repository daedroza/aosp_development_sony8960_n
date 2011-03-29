/*
 * Copyright (C) 2009 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.inputmethod.latin;

import com.android.inputmethod.compat.InputConnectionCompatUtils;

import android.text.TextUtils;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;

import java.util.regex.Pattern;

/**
 * Utility methods to deal with editing text through an InputConnection.
 */
public class EditingUtils {
    /**
     * Number of characters we want to look back in order to identify the previous word
     */
    private static final int LOOKBACK_CHARACTER_NUM = 15;

    private EditingUtils() {
        // Unintentional empty constructor for singleton.
    }

    /**
     * Append newText to the text field represented by connection.
     * The new text becomes selected.
     */
    public static void appendText(InputConnection connection, String newText) {
        if (connection == null) {
            return;
        }

        // Commit the composing text
        connection.finishComposingText();

        // Add a space if the field already has text.
        String text = newText;
        CharSequence charBeforeCursor = connection.getTextBeforeCursor(1, 0);
        if (charBeforeCursor != null
                && !charBeforeCursor.equals(" ")
                && (charBeforeCursor.length() > 0)) {
            text = " " + text;
        }

        connection.setComposingText(text, 1);
    }

    private static int getCursorPosition(InputConnection connection) {
        ExtractedText extracted = connection.getExtractedText(
            new ExtractedTextRequest(), 0);
        if (extracted == null) {
          return -1;
        }
        return extracted.startOffset + extracted.selectionStart;
    }

    /**
     * @param connection connection to the current text field.
     * @param sep characters which may separate words
     * @return the word that surrounds the cursor, including up to one trailing
     *   separator. For example, if the field contains "he|llo world", where |
     *   represents the cursor, then "hello " will be returned.
     */
    public static String getWordAtCursor(InputConnection connection, String separators) {
        Range r = getWordRangeAtCursor(connection, separators);
        return (r == null) ? null : r.mWord;
    }

    /**
     * Removes the word surrounding the cursor. Parameters are identical to
     * getWordAtCursor.
     */
    public static void deleteWordAtCursor(InputConnection connection, String separators) {
        Range range = getWordRangeAtCursor(connection, separators);
        if (range == null) return;

        connection.finishComposingText();
        // Move cursor to beginning of word, to avoid crash when cursor is outside
        // of valid range after deleting text.
        int newCursor = getCursorPosition(connection) - range.mCharsBefore;
        connection.setSelection(newCursor, newCursor);
        connection.deleteSurroundingText(0, range.mCharsBefore + range.mCharsAfter);
    }

    /**
     * Represents a range of text, relative to the current cursor position.
     */
    public static class Range {
        /** Characters before selection start */
        public final int mCharsBefore;

        /**
         * Characters after selection start, including one trailing word
         * separator.
         */
        public final int mCharsAfter;

        /** The actual characters that make up a word */
        public final String mWord;

        public Range(int charsBefore, int charsAfter, String word) {
            if (charsBefore < 0 || charsAfter < 0) {
                throw new IndexOutOfBoundsException();
            }
            this.mCharsBefore = charsBefore;
            this.mCharsAfter = charsAfter;
            this.mWord = word;
        }
    }

    private static Range getWordRangeAtCursor(InputConnection connection, String sep) {
        if (connection == null || sep == null) {
            return null;
        }
        CharSequence before = connection.getTextBeforeCursor(1000, 0);
        CharSequence after = connection.getTextAfterCursor(1000, 0);
        if (before == null || after == null) {
            return null;
        }

        // Find first word separator before the cursor
        int start = before.length();
        while (start > 0 && !isWhitespace(before.charAt(start - 1), sep)) start--;

        // Find last word separator after the cursor
        int end = -1;
        while (++end < after.length() && !isWhitespace(after.charAt(end), sep)) {
            // Nothing to do here.
        }

        int cursor = getCursorPosition(connection);
        if (start >= 0 && cursor + end <= after.length() + before.length()) {
            String word = before.toString().substring(start, before.length())
                    + after.toString().substring(0, end);
            return new Range(before.length() - start, end, word);
        }

        return null;
    }

    private static boolean isWhitespace(int code, String whitespace) {
        return whitespace.contains(String.valueOf((char) code));
    }

    private static final Pattern spaceRegex = Pattern.compile("\\s+");

    public static CharSequence getPreviousWord(InputConnection connection,
            String sentenceSeperators) {
        //TODO: Should fix this. This could be slow!
        CharSequence prev = connection.getTextBeforeCursor(LOOKBACK_CHARACTER_NUM, 0);
        if (prev == null) {
            return null;
        }
        String[] w = spaceRegex.split(prev);
        if (w.length >= 2 && w[w.length-2].length() > 0) {
            char lastChar = w[w.length-2].charAt(w[w.length-2].length() -1);
            if (sentenceSeperators.contains(String.valueOf(lastChar))) {
                return null;
            }
            return w[w.length-2];
        } else {
            return null;
        }
    }

    public static class SelectedWord {
        public final int mStart;
        public final int mEnd;
        public final CharSequence mWord;

        public SelectedWord(int start, int end, CharSequence word) {
            mStart = start;
            mEnd = end;
            mWord = word;
        }
    }

    /**
     * Takes a character sequence with a single character and checks if the character occurs
     * in a list of word separators or is empty.
     * @param singleChar A CharSequence with null, zero or one character
     * @param wordSeparators A String containing the word separators
     * @return true if the character is at a word boundary, false otherwise
     */
    private static boolean isWordBoundary(CharSequence singleChar, String wordSeparators) {
        return TextUtils.isEmpty(singleChar) || wordSeparators.contains(singleChar);
    }

    /**
     * Checks if the cursor is inside a word or the current selection is a whole word.
     * @param ic the InputConnection for accessing the text field
     * @param selStart the start position of the selection within the text field
     * @param selEnd the end position of the selection within the text field. This could be
     *               the same as selStart, if there's no selection.
     * @param wordSeparators the word separator characters for the current language
     * @return an object containing the text and coordinates of the selected/touching word,
     *         null if the selection/cursor is not marking a whole word.
     */
    public static SelectedWord getWordAtCursorOrSelection(final InputConnection ic,
            int selStart, int selEnd, String wordSeparators) {
        if (selStart == selEnd) {
            // There is just a cursor, so get the word at the cursor
            EditingUtils.Range range = getWordRangeAtCursor(ic, wordSeparators);
            if (range != null && !TextUtils.isEmpty(range.mWord)) {
                return new SelectedWord(selStart - range.mCharsBefore, selEnd + range.mCharsAfter,
                        range.mWord);
            }
        } else {
            // Is the previous character empty or a word separator? If not, return null.
            CharSequence charsBefore = ic.getTextBeforeCursor(1, 0);
            if (!isWordBoundary(charsBefore, wordSeparators)) {
                return null;
            }

            // Is the next character empty or a word separator? If not, return null.
            CharSequence charsAfter = ic.getTextAfterCursor(1, 0);
            if (!isWordBoundary(charsAfter, wordSeparators)) {
                return null;
            }

            // Extract the selection alone
            CharSequence touching = InputConnectionCompatUtils.getSelectedText(
                    ic, selStart, selEnd);
            if (TextUtils.isEmpty(touching)) return null;
            // Is any part of the selection a separator? If so, return null.
            final int length = touching.length();
            for (int i = 0; i < length; i++) {
                if (wordSeparators.contains(touching.subSequence(i, i + 1))) {
                    return null;
                }
            }
            // Prepare the selected word
            return new SelectedWord(selStart, selEnd, touching);
        }
        return null;
    }
}
