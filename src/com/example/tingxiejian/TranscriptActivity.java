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

import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads one saved transcript end to end and gets it out of the phone:
 * copy, share, export (TXT / SRT / JSON), or hand it to the AI chat.
 */
public class TranscriptActivity extends Activity {
    private static final String EXTRA_ID = "id";
    private static final int REQ_SAVE = 91;

    private String id = "";
    private JSONObject result;
    private final List<JSONObject> items = new ArrayList<>();
    private String pendingExport = "";
    private String pendingName = "transcript.txt";

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
            result = History.load(this, id);
            items.clear();
            JSONArray segments = History.segments(result);
            for (int i = 0; i < segments.length(); i++) {
                JSONObject segment = segments.optJSONObject(i);
                if (segment != null) {
                    items.add(segment);
                }
            }
            TextView title = need(R.id.title);
            title.setText(result.optString("name", "转写结果"));
            TextView meta = need(R.id.meta);
            meta.setText(items.size() + " 段 · 时长 " + Exporter.clock(result.optDouble("durationSeconds", 0))
                    + " · " + (result.optString("engine", "local").equals("cloud") ? "云端识别" : "本地识别"));
            TextView legend = need(R.id.legend);
            legend.setText(result.optInt("speakers", 0) > 1
                    ? result.optInt("speakers", 0) + " 位发言人（匿名标签，不代表真实身份）"
                    : "未区分发言人");

            ListView list = need(R.id.segments);
            list.setAdapter(new SegmentAdapter(this, items, (position, segment) -> {
            }));

            need(R.id.back).setOnClickListener(v -> PortalTransition.close(this));
            click(R.id.copy, "复制全文", () -> copy(Exporter.plain(result)));
            click(R.id.share, "分享", this::share);
            click(R.id.save, "导出", this::askExportFormat);
            click(R.id.ask, "问 AI", () -> {
                Intent intent = new Intent(this, ChatActivity.class);
                intent.putExtra(EXTRA_ID, id);
                PortalTransition.open(this, need(R.id.ask), intent, "chat_portal");
            });
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
    public void onBackPressed() {
        PortalTransition.close(this);
    }

    private <T extends View> T need(int id) {
        T view = findViewById(id);
        if (view == null) {
            throw new IllegalStateException("视图 " + getResources().getResourceEntryName(id) + " 找不到");
        }
        return view;
    }

    private void click(int id, String what, Runnable action) {
        need(id).setOnClickListener(v -> {
            try {
                action.run();
            } catch (Throwable error) {
                Report.problem(what, error);
            }
        });
    }

    private void copy(String body) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("转写", body));
        }
    }

    private void share() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, result.optString("name", "转写结果"));
        send.putExtra(Intent.EXTRA_TEXT, Exporter.txt(result));
        startActivity(Intent.createChooser(send, "分享转写"));
    }

    private void askExportFormat() {
        String[] formats = {"TXT（带时间）", "SRT（字幕）", "JSON（结构化）"};
        new AlertDialog.Builder(this)
                .setTitle("导出格式")
                .setItems(formats, (dialog, which) -> {
                    if (which == 0) {
                        pendingExport = Exporter.txt(result);
                        pendingName = safeName(".txt");
                    } else if (which == 1) {
                        pendingExport = Exporter.srt(result);
                        pendingName = safeName(".srt");
                    } else {
                        pendingExport = Exporter.json(result);
                        pendingName = safeName(".json");
                    }
                    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType(which == 1 ? "text/plain" : which == 2 ? "application/json" : "text/plain");
                    intent.putExtra(Intent.EXTRA_TITLE, pendingName);
                    startActivityForResult(intent, REQ_SAVE);
                })
                .show();
    }

    private String safeName(String suffix) {
        String name = result.optString("name", "transcript").replaceAll("[\\\\/:*?\"<>|]", "_");
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        return name + suffix;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            super.onActivityResult(requestCode, resultCode, data);
            if (requestCode != REQ_SAVE || resultCode != RESULT_OK || data == null || data.getData() == null) {
                return;
            }
            Uri uri = data.getData();
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out != null) {
                    out.write(pendingExport.getBytes("UTF-8"));
                }
            }
        } catch (Throwable error) {
            Report.problem("导出失败", error);
        }
    }
}
