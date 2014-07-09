package com.android.ex.chips;

import android.content.Context;

/**
 * Simple adapter for {@link RecipientsEditor}.
 * Shows contacts email or phone values.
 */
public class SimpleRecipientAdapter extends BaseRecipientAdapter {

    private static final int DEFAULT_PREFERRED_MAX_RESULT_COUNT = 5;
    private int dropdownResource;

    /**
     * @param context
     *            Activity's context
     * @param QUERY_TYPE
     *            {@link BaseRecipientAdapter#QUERY_TYPE_EMAIL} or {@link BaseRecipientAdapter#QUERY_TYPE_PHONE}
     */
    public SimpleRecipientAdapter(Context context, int QUERY_TYPE) {
        this(context, QUERY_TYPE, R.layout.chips_recipient_dropdown_item);
    }

    /**
     * @param context
     *            Activity's context
     * @param QUERY_TYPE
     *            {@link BaseRecipientAdapter#QUERY_TYPE_EMAIL} or {@link BaseRecipientAdapter#QUERY_TYPE_PHONE}
     * @param dropdownResource
     *            resource to be used as a autocomplete's dropdown layout
     */
    public SimpleRecipientAdapter(Context context, int QUERY_TYPE, int dropdownResource) {
        // The Chips UI is email-centric by default. By setting QUERY_TYPE_PHONE, the chips UI
        // will operate with phone numbers instead of emails.
        super(context, DEFAULT_PREFERRED_MAX_RESULT_COUNT, QUERY_TYPE);
        this.dropdownResource = dropdownResource;
    }

    /**
     * Returns a layout id for each item inside auto-complete list.
     * Each View must contain two TextViews (for display name and destination) and one ImageView
     * (for photo). Ids for those should be available via {@link #getDisplayNameId()}, {@link #getDestinationId()},
     * and {@link #getPhotoId()}.
     */
    @Override
    protected int getItemLayout() {
        return dropdownResource;
    }

}
