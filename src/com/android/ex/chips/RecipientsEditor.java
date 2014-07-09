/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ex.chips;

import android.content.Context;
import android.telephony.PhoneNumberUtils;
import android.text.*;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.AttributeSet;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;

import java.util.ArrayList;
import java.util.List;

/**
 * Provide UI for editing the recipients with phone numbers or emails.
 */
public class RecipientsEditor extends RecipientEditTextView {

    private int mLongPressedPosition = -1;
    private final RecipientsEditorTokenizer mTokenizer;
    private char mLastSeparator = ',';
    private Runnable mOnSelectChipRunnable;
    private final AddressValidator mInternalValidator;
    private int recipientsLimit;

    /** A noop validator that does not munge invalid texts and claims any address is valid */
    private class AddressValidator implements Validator {

        public CharSequence fixText(CharSequence invalidText) {
            return invalidText;
        }

        public boolean isValid(CharSequence text) {
            return true;
        }
    }

    public RecipientsEditor(Context context, AttributeSet attrs) {
        super(context, attrs);

        mTokenizer = new RecipientsEditorTokenizer();
        setTokenizer(mTokenizer);

        mInternalValidator = new AddressValidator();
        super.setValidator(mInternalValidator);

        // For the focus to move to the message body when soft Next is pressed
        setImeOptions(EditorInfo.IME_ACTION_NEXT);

        setThreshold(1); // pop-up the list after a single char is typed

        /*
         * The point of this TextWatcher is that when the user chooses
         * an address completion from the AutoCompleteTextView menu, it
         * is marked up with Annotation objects to tie it back to the
         * address book entry that it came from. If the user then goes
         * back and edits that part of the text, it no longer corresponds
         * to that address book entry and needs to have the Annotations
         * claiming that it does removed.
         */
        addTextChangedListener(new TextWatcher() {

            private Annotation[] mAffected;

            @Override
            public void beforeTextChanged(CharSequence s, int start,
                    int count, int after) {
                mAffected = ((Spanned) s).getSpans(start, start + count,
                        Annotation.class);
            }

            @Override
            public void onTextChanged(CharSequence s, int start,
                    int before, int after) {
                if (before == 0 && after == 1) { // inserting a character
                    char c = s.charAt(start);
                    if (c == ',' || c == ';') {
                        // Remember the delimiter the user typed to end this recipient. We'll
                        // need it shortly in terminateToken().
                        mLastSeparator = c;
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (mAffected != null) {
                    for (Annotation a : mAffected) {
                        s.removeSpan(a);
                    }
                }
                mAffected = null;
            }
        });
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        super.onItemClick(parent, view, position, id);

        if (mOnSelectChipRunnable != null) {
            mOnSelectChipRunnable.run();
        }

        if (getRecipientsCount() == recipientsLimit) {
            setEnabled(false);
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(this.getWindowToken(), 0);
        }
    }

    @Override
    protected void removeChip(RecipientChip chip) {
        super.removeChip(chip);
        setEnabled(true);
    }

    @Override
    void replaceChip(RecipientChip chip, RecipientEntry entry) {
        super.replaceChip(chip, entry);
        if (getRecipientsCount() == recipientsLimit) {
            setEnabled(false);
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(this.getWindowToken(), 0);
        }
    }

    public void setOnSelectChipRunnable(Runnable onSelectChipRunnable) {
        mOnSelectChipRunnable = onSelectChipRunnable;
    }

    @Override
    public boolean enoughToFilter() {
        if (!super.enoughToFilter()) {
            return false;
        }
        // If the user is in the middle of editing an existing recipient, don't offer the
        // auto-complete menu. Without this, when the user selects an auto-complete menu item,
        // it will get added to the list of recipients so we end up with the old before-editing
        // recipient and the new post-editing recipient. As a precedent, gmail does not show
        // the auto-complete menu when editing an existing recipient.
        int end = getSelectionEnd();
        int len = getText().length();

        return end == len;

    }

    public int getRecipientsCount() {
        return mTokenizer.getAddresses().size();
    }

    public List<String> getRecipientsAddresses() {
        return mTokenizer.getAddresses();
    }

    public int getRecipientsLimit() {
        return recipientsLimit;
    }

    public void setRecipientsLimit(int maxRecipients) {
        this.recipientsLimit = maxRecipients;
    }

    private boolean isValidAddress(String address) {
        return PhoneNumberUtils.isWellFormedSmsAddress(address)
                || Patterns.EMAIL_ADDRESS.matcher(address).matches();
    }

    public boolean hasValidRecipient() {
        for (String address : mTokenizer.getAddresses()) {
            if (isValidAddress(address))
                return true;
        }
        return false;
    }

    public boolean hasInvalidRecipient() {
        for (String address : mTokenizer.getAddresses()) {
            // TODO
        }
        return false;
    }

    public String formatInvalidNumbers() {
        StringBuilder sb = new StringBuilder();
        for (String address : mTokenizer.getAddresses()) {
            if (!isValidAddress(address)) {
                if (sb.length() != 0) {
                    sb.append(", ");
                }
                sb.append(address);
            }
        }
        return sb.toString();
    }

    private int pointToPosition(int x, int y) {
        x -= getCompoundPaddingLeft();
        y -= getExtendedPaddingTop();

        x += getScrollX();
        y += getScrollY();

        Layout layout = getLayout();
        if (layout == null) {
            return -1;
        }

        int line = layout.getLineForVertical(y);
        int off = layout.getOffsetForHorizontal(line, x);

        return off;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        final int action = ev.getAction();
        final int x = (int) ev.getX();
        final int y = (int) ev.getY();

        if (action == MotionEvent.ACTION_DOWN) {
            mLongPressedPosition = pointToPosition(x, y);
        }

        return super.onTouchEvent(ev);
    }

    @Override
    protected ContextMenuInfo getContextMenuInfo() {
        if ((mLongPressedPosition >= 0)) {
            Spanned text = getText();
            if (mLongPressedPosition <= text.length()) {
                int start = mTokenizer.findTokenStart(text, mLongPressedPosition);
                int end = mTokenizer.findTokenEnd(text, start);

                if (end != start) {
                    String value = getAddressAt(getText(), start, end, getContext());
                    return new RecipientContextMenuInfo(value);
                }
            }
        }
        return null;
    }

    static class RecipientContextMenuInfo implements ContextMenuInfo {

        private String value;

        RecipientContextMenuInfo(String value) {
            this.value = value;
        }
    }

    private static String getAddressAt(Spanned sp, int start, int end, Context context) {
        String number = getFieldAt("address", sp, start, end, context);
        number = replaceUnicodeDigits(number);
        if (!TextUtils.isEmpty(number)) {
            int pos = number.indexOf('<');
            if (pos >= 0 && pos < number.indexOf('>')) {
                // The number looks like an Rfc882 address, i.e. <fred flinstone> 891-7823
                Rfc822Token[] tokens = Rfc822Tokenizer.tokenize(number);
                if (tokens.length == 0) {
                    return number;
                }
                return tokens[0].getAddress();
            }
        }
        return number;
    }

    public static String replaceUnicodeDigits(String number) {
        StringBuilder normalizedDigits = new StringBuilder(number.length());
        for (char c : number.toCharArray()) {
            int digit = Character.digit(c, 10);
            if (digit != -1) {
                normalizedDigits.append(digit);
            } else {
                normalizedDigits.append(c);
            }
        }
        return normalizedDigits.toString();
    }

    private static int getSpanLength(Spanned sp, int start, int end, Context context) {
        // TODO: there's a situation where the span can lose its annotations:
        // - add an auto-complete contact
        // - add another auto-complete contact
        // - delete that second contact and keep deleting into the first
        // - we lose the annotation and can no longer get the span.
        // Need to fix this case because it breaks auto-complete contacts with commas in the name.
        Annotation[] a = sp.getSpans(start, end, Annotation.class);
        if (a.length > 0) {
            return sp.getSpanEnd(a[0]);
        }
        return 0;
    }

    private static String getFieldAt(String field, Spanned sp, int start, int end,
            Context context) {
        Annotation[] a = sp.getSpans(start, end, Annotation.class);
        String fieldValue = getAnnotation(a, field);
        if (TextUtils.isEmpty(fieldValue)) {
            fieldValue = TextUtils.substring(sp, start, end);
        }
        return fieldValue;

    }

    private static String getAnnotation(Annotation[] a, String key) {
        for (int i = 0; i < a.length; i++) {
            if (a[i].getKey().equals(key)) {
                return a[i].getValue();
            }
        }

        return "";
    }

    private class RecipientsEditorTokenizer
            implements Tokenizer {

        @Override
        public int findTokenStart(CharSequence text, int cursor) {
            int i = cursor;
            char c;

            // If we're sitting at a delimiter, back up so we find the previous token
            if (i > 0 && ((c = text.charAt(i - 1)) == ',' || c == ';')) {
                --i;
            }
            // Now back up until the start or until we find the separator of the previous token
            while (i > 0 && (c = text.charAt(i - 1)) != ',' && c != ';') {
                i--;
            }
            while (i < cursor && text.charAt(i) == ' ') {
                i++;
            }

            return i;
        }

        @Override
        public int findTokenEnd(CharSequence text, int cursor) {
            int i = cursor;
            int len = text.length();
            char c;

            while (i < len) {
                if ((c = text.charAt(i)) == ',' || c == ';') {
                    return i;
                } else {
                    i++;
                }
            }

            return len;
        }

        @Override
        public CharSequence terminateToken(CharSequence text) {
            int i = text.length();

            while (i > 0 && text.charAt(i - 1) == ' ') {
                i--;
            }

            char c;
            if (i > 0 && ((c = text.charAt(i - 1)) == ',' || c == ';')) {
                return text;
            } else {
                // Use the same delimiter the user just typed.
                // This lets them have a mixture of commas and semicolons in their list.
                String separator = mLastSeparator + " ";
                if (text instanceof Spanned) {
                    SpannableString sp = new SpannableString(text + separator);
                    TextUtils.copySpansFrom((Spanned) text, 0, text.length(),
                            Object.class, sp, 0);
                    return sp;
                } else {
                    return text + separator;
                }
            }
        }

        public List<String> getAddresses() {
            Spanned sp = RecipientsEditor.this.getText();
            int len = sp.length();
            List<String> list = new ArrayList<String>();

            int start = 0;
            int i = 0;
            while (i < len + 1) {
                char c;
                if ((i == len) || ((c = sp.charAt(i)) == ',') || (c == ';')) {
                    if (i > start) {
                        list.add(getAddressAt(sp, start, i, getContext()));

                        // calculate the recipients total length. This is so if the name contains
                        // commas or semis, we'll skip over the whole name to the next
                        // recipient, rather than parsing this single name into multiple
                        // recipients.
                        int spanLen = getSpanLength(sp, start, i, getContext());
                        if (spanLen > i) {
                            i = spanLen;
                        }
                    }

                    i++;

                    while ((i < len) && (sp.charAt(i) == ' ')) {
                        i++;
                    }

                    start = i;
                } else {
                    i++;
                }
            }

            return list;
        }
    }

}