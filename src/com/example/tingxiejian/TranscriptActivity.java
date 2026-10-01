package com.example.tingxiejian;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.os.Bundle;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads one saved transcript end to end and gets it out of the phone:
 * copy, share, export (TXT / SRT / JSON), or hand it to the AI chat.
 */
public class TranscriptActivity extends Activity {
    private static final String EXTRA_ID = "id";
    private static final int REQ_SAVE = 91;
    private static final String STATE_EXPORT_FORMAT = "pending_export_format";
    private static final String STATE_LAYER = "transcript_polished_layer";
    private static final String STATE_EXPORT_LAYER = "export_polished_layer";

    private String id = "";
    private JSONObject result;
    private JSONObject selectedResult;
    private boolean polishedLayer;
    private boolean pendingLayer;
    private final List<JSONObject> items = new ArrayList<>();
    // Persist the format and chosen layer. Rebuild output from History after the picker returns:
    // Android may destroy and recreate this Activity while the picker is in front.
    private int pendingFormat = -1;
    private View transcriptHeader;
    private SegmentAdapter adapter;
    private final List<JSONObject> allItems = new ArrayList<>();
    private android.widget.EditText search;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable searchUpdate = this::filterSegments;

    static void open(Context context, String id) {
        Intent intent = new Intent(context, TranscriptActivity.class);
        intent.putExtra(EXTRA_ID, id);
        context.startActivity(intent);
    }

    static void open(Activity activity, String id, View source) {
        Intent intent = new Intent(activity, TranscriptActivity.class).putExtra(EXTRA_ID, id);
        PortalTransition.open(activity, source, intent, "transcript_portal");
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(UiTheme.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_transcript);
            PortalTransition.install(this, findViewById(R.id.portal_surface),
                    findViewById(R.id.portal_content), savedInstanceState);
            UiTheme.padForSystemBars(this, findViewById(R.id.portal_content));
            Report.mark("transcript.onCreate");
            id = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_ID);
            if (savedInstanceState != null) {
                pendingFormat = savedInstanceState.getInt(STATE_EXPORT_FORMAT, -1);
                polishedLayer = savedInstanceState.getBoolean(STATE_LAYER, false);
                pendingLayer = savedInstanceState.getBoolean(STATE_EXPORT_LAYER, false);
            }
            result = History.load(this, id);
            search = need(R.id.transcript_search);
            search.setText(savedInstanceState == null ? "" : savedInstanceState.getString("search_query", ""));
            search.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                    ui.removeCallbacks(searchUpdate); ui.postDelayed(searchUpdate, 150);
                }
                @Override public void afterTextChanged(android.text.Editable s) { }
            });
            search.setOnEditorActionListener((view, actionId, event) -> {
                if (actionId != android.view.inputmethod.EditorInfo.IME_ACTION_DONE) return false;
                android.view.inputmethod.InputMethodManager ime = (android.view.inputmethod.InputMethodManager)
                        getSystemService(INPUT_METHOD_SERVICE);
                if (ime != null) ime.hideSoftInputFromWindow(search.getWindowToken(), 0);
                search.clearFocus(); return true;
            });
            // Keep tools reachable on short screens / large fonts without sacrificing row
            // virtualization. Retain the exact header View for reverse portal mapping.
            transcriptHeader = need(R.id.transcript_header);
            ((android.view.ViewGroup) transcriptHeader.getParent()).removeView(transcriptHeader);
            ((ListView) need(R.id.segments)).addHeaderView(transcriptHeader, null, false);
            TextView title = need(R.id.title);
            title.setText(result.optString("name", "转写结果"));
            TextView meta = need(R.id.meta);
            meta.setText(History.segments(result).length() + " 段 · 时长 " + Exporter.clock(result.optDouble("durationSeconds", 0))
                    + " · " + (result.optString("engine", "local").equals("cloud") ? "云端识别" : "本地识别"));
            TextView legend = need(R.id.legend);
            legend.setText(result.optInt("speakers", 0) > 1
                    ? result.optInt("speakers", 0) + " 位发言人（匿名标签，不代表真实身份）"
                    : "未区分发言人");

            need(R.id.back).setOnClickListener(v -> PortalTransition.close(this));
            click(R.id.layer_faithful, "查看忠实稿", () -> selectLayer(false));
            click(R.id.layer_polished, "查看整理稿", () -> selectLayer(true));
            selectLayer(polishedLayer);
            click(R.id.copy, "复制全文", () -> copy(Exporter.plain(selectedResult)));
            click(R.id.share, "分享", this::share);
            click(R.id.save, "导出", this::askExportFormat);
            click(R.id.ask, "问 AI", () -> {
                Intent intent = new Intent(this, ChatActivity.class);
                intent.putExtra(EXTRA_ID, id);
                PortalTransition.open(this, need(R.id.ask), intent, "chat_portal");
            });
            UiControls.apply(findViewById(R.id.portal_content));
            UiControls.apply(transcriptHeader);
            Report.mark("transcript.onCreate complete");
            Report.flush(this);
        } catch (Throwable error) {
            Report.problem("转写页初始化失败", error);
            TextView fallback = new TextView(this);
            fallback.setText(Report.crashText("转写页初始化失败", error));
            fallback.setTextIsSelectable(true);
            fallback.setPadding(32, 32, 32, 32);
            fallback.setTextSize(11);
            setContentView(fallback);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        state.putInt(STATE_EXPORT_FORMAT, pendingFormat);
        state.putBoolean(STATE_LAYER, polishedLayer);
        state.putBoolean(STATE_EXPORT_LAYER, pendingLayer);
        if (search != null) state.putString("search_query", search.getText().toString());
        super.onSaveInstanceState(state);
    }

    @Override protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        PortalTransition.close(this);
    }

    private <T extends View> T need(int id) {
        T view = findViewById(id);
        if (view == null && transcriptHeader != null) view = transcriptHeader.findViewById(id);
        if (view == null) {
            throw new IllegalStateException("视图 " + getResources().getResourceEntryName(id) + " 找不到");
        }
        return view;
    }

    private interface Action { void run() throws Exception; }

    private void click(int id, String what, Action action) {
        need(id).setOnClickListener(v -> {
            try {
                action.run();
            } catch (Throwable error) {
                Report.problem(what, error);
                feedback(what + "失败，请重试或查看本机诊断。");
            }
        });
    }

    private void copy(String body) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(layerName(), body));
            feedback("已复制" + layerName());
        }
    }

    private void feedback(String message) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show();
        View status = need(R.id.layer_status);
        status.announceForAccessibility(message);
    }

    private String layerName() { return polishedLayer ? "整理稿" : "忠实稿"; }

    private void selectLayer(boolean polished) throws Exception {
        polishedLayer = polished;
        selectedResult = History.view(result, polished);
        allItems.clear();
        JSONArray segments = History.segments(selectedResult);
        for (int i = 0; i < segments.length(); i++) {
            JSONObject segment = segments.optJSONObject(i);
            if (segment != null) allItems.add(segment);
        }
        ListView list = need(R.id.segments);
        int firstVisible = list.getFirstVisiblePosition();
        View firstRow = list.getChildAt(0);
        int top = firstRow == null ? 0 : firstRow.getTop();
        items.clear(); items.addAll(allItems);
        if (adapter == null) {
            adapter = new SegmentAdapter(this, items, null);
            list.setAdapter(adapter);
        } else {
            adapter.notifyDataSetChanged();
            if (firstVisible >= 0 && firstVisible < list.getCount()) list.setSelectionFromTop(firstVisible, top);
        }
        need(R.id.transcript_empty).setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        for (int button : new int[]{R.id.layer_faithful, R.id.layer_polished}) {
            TextView label = need(button);
            boolean active = (button == R.id.layer_polished) == polished;
            label.setSelected(active);
            label.setBackgroundResource(active ? R.drawable.bg_primary : R.drawable.bg_row);
            label.setTextColor(getColor(active ? R.color.on_accent : R.color.ink));
        }
        TextView copyLabel = need(R.id.copy_label), shareLabel = need(R.id.share_label), saveLabel = need(R.id.save_label);
        copyLabel.setText("复制" + layerName());
        shareLabel.setText("分享" + layerName());
        saveLabel.setText("导出" + layerName());
        need(R.id.copy).setContentDescription("复制" + layerName());
        need(R.id.share).setContentDescription("分享" + layerName());
        need(R.id.save).setContentDescription("导出" + layerName());
        TextView status = need(R.id.layer_status);
        int corrected = 0;
        JSONArray originalSegments = History.segments(result);
        for (int i = 0; i < originalSegments.length(); i++) {
            JSONObject segment = originalSegments.optJSONObject(i);
            if (segment != null && !segment.optString("polished_text", "").trim().isEmpty()) corrected++;
        }
        if (originalSegments.length() == 0 && !result.optString("polished_text", "").trim().isEmpty()) corrected = 1;
        String detail = "忠实稿永久保留；复制、分享、导出使用当前" + layerName() + "。";
        if (polished) {
            if (corrected > 0) detail += "\n" + corrected + " 段已校正，其余段保留忠实原文。";
            else detail += "\n尚无校正结果，当前显示忠实原文。";
        }
        String recordedStatus = result.optString("polish_status", "").toLowerCase(Locale.US);
        if ("skipped_no_key".equals(recordedStatus)) detail += "\n" + TranscriptPolisher.NO_KEY_MESSAGE;
        else if ("failed".equals(recordedStatus) || "fallback".equals(recordedStatus))
            detail += "\nAI 校正未完成或未通过保护校验，已保留忠实原文。";
        else if (polished && corrected == 0 && !TranscriptPolisher.hasConfiguredKey(this))
            detail += "\n" + TranscriptPolisher.NO_KEY_MESSAGE;
        status.setText(detail);
        filterSegments();
    }

    private void filterSegments() {
        if (adapter == null || search == null || isDestroyed()) return;
        java.util.regex.Pattern pattern = TranscriptSearch.pattern(search.getText().toString());
        items.clear();
        for (JSONObject segment : allItems) {
            if (TranscriptSearch.matches(segment.optString("text"), pattern)) items.add(segment);
        }
        adapter.setSearch(pattern);
        TextView empty = need(R.id.transcript_empty);
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        empty.setText(pattern == null ? "这段录音还没有可显示的转写文字。" : "没有找到匹配的语句，试试其他关键词。");
        TextView count = need(R.id.search_status);
        count.setVisibility(pattern == null ? View.GONE : View.VISIBLE);
        count.setText("找到 " + items.size() + " / " + allItems.size()
                + " 段；复制与导出仍包含完整" + layerName() + "。");
    }

    private void share() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, result.optString("name", "转写结果") + " · " + layerName());
        send.putExtra(Intent.EXTRA_TEXT, layerName() + "\n" + Exporter.txt(selectedResult));
        startActivity(Intent.createChooser(send, "分享" + layerName()));
    }

    private void askExportFormat() {
        String[] formats = Exporter.menuLabels();
        new AlertDialog.Builder(this)
                .setTitle("导出" + layerName())
                .setItems(formats, (dialog, which) -> {
                    pendingFormat = which;
                    pendingLayer = polishedLayer;
                    Exporter.Format format = Exporter.menuFormat(which);
                    String name = safeName(format.suffix);
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(format.mime);
                    intent.putExtra(Intent.EXTRA_TITLE, name);
                    startActivityForResult(intent, REQ_SAVE);
                })
                .show();
    }

    private String safeName(String suffix) {
        String name = result.optString("name", "transcript").replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        if (name.length() > 100) name = name.substring(0, 100);
        if (name.trim().isEmpty()) name = "transcript";
        return name + "-" + layerName() + suffix;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode != REQ_SAVE) return;
            if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
            if (pendingFormat < 0 || pendingFormat > 2) {
                throw new IllegalStateException("导出格式已丢失，请重新选择导出格式");
            }
            Uri uri = data.getData();
            // Keep the layer chosen when the picker opened, including after Activity recreation.
            JSONObject output = History.view(result, pendingLayer);
            Exporter.Format format = Exporter.menuFormat(pendingFormat);
            String body = (format == Exporter.Format.TXT ? (pendingLayer ? "整理稿\n" : "忠实稿\n") : "") + format.render(output);
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out == null) throw new IOException("文档提供方未返回输出流");
                out.write(body.getBytes(StandardCharsets.UTF_8));
            }
            feedback("已导出" + (pendingLayer ? "整理稿" : "忠实稿"));
        } catch (Throwable error) {
            Report.problem("导出失败", error);
            feedback("导出失败，请重新选择保存位置。");
        } finally {
            if (requestCode == REQ_SAVE) { pendingFormat = -1; pendingLayer = false; }
        }
    }
}
