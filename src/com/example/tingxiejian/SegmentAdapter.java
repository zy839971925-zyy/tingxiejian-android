package com.example.tingxiejian;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.List;

/** Virtualised transcript rows, so a two-hour recording does not create thousands of views. */
final class SegmentAdapter extends BaseAdapter {
    interface OnPick {
        void onPick(int position, JSONObject segment);
    }

    private final LayoutInflater inflater;
    private final List<JSONObject> items;
    private final OnPick onPick;
    private int active = -1;
    private java.util.regex.Pattern search;
    void setSearch(java.util.regex.Pattern pattern) { search = pattern; notifyDataSetChanged(); }

    SegmentAdapter(Context context, List<JSONObject> items, OnPick onPick) {
        this.inflater = LayoutInflater.from(context);
        this.items = items;
        this.onPick = onPick;
    }

    void setActive(int position) {
        if (active == position) return;
        active = position;
        notifyDataSetChanged();
    }

    int activePosition() {
        return active;
    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int position) {
        return items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View recycled, ViewGroup parent) {
        View row = recycled;
        if (row == null) row = inflater.inflate(R.layout.row_segment, parent, false);
        JSONObject segment = items.get(position);
        TextView time = row.findViewById(R.id.segment_time);
        TextView speaker = row.findViewById(R.id.segment_speaker);
        TextView text = row.findViewById(R.id.segment_text);
        View card = row.findViewById(R.id.segment_row);

        time.setText(Job.clock(segment.optDouble("start", 0)));
        String content = segment.optString("text");
        android.text.SpannableString highlighted = new android.text.SpannableString(content);
        if (search != null) {
            java.util.regex.Matcher matches = search.matcher(content);
            while (matches.find()) {
                highlighted.setSpan(new android.text.style.BackgroundColorSpan(row.getContext().getColor(R.color.accent_soft)),
                        matches.start(), matches.end(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                highlighted.setSpan(new android.text.style.ForegroundColorSpan(row.getContext().getColor(R.color.accent)),
                        matches.start(), matches.end(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        text.setText(highlighted);
        text.setTextIsSelectable(true);
        if (segment.isNull("speaker")) {
            speaker.setVisibility(View.GONE);
        } else {
            speaker.setVisibility(View.VISIBLE);
            speaker.setText("发言人 " + (segment.optInt("speaker") + 1));
        }
        boolean isActive = position == active;
        card.setBackgroundResource(isActive ? R.drawable.bg_row_active : R.drawable.bg_row);
        text.setTextColor(row.getContext().getColor(R.color.ink));
        final int index = position;
        card.setOnClickListener(onPick == null ? null : v -> onPick.onPick(index, items.get(index)));
        card.setClickable(onPick != null);
        card.setFocusable(onPick != null);
        return row;
    }
}
