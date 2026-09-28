package com.example.tingxiejian;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Conversation about the recording: the transcript travels as the system context, the whole
 * message history goes back on every turn, and replies are shown as they arrive.
 *
 * Nothing is sent unless the user configured a cloud key in Settings - and if there is no key
 * this screen says so instead of pretending.
 */
public class ChatActivity extends Activity {
    private static final String EXTRA_ID = "id";
    private static final int MAX_CONTEXT_CHARS = 12000;

    private final Handler ui = new Handler(Looper.getMainLooper());

    private LinearLayout chatList;
    private ScrollView chatScroll;
    private EditText input;
    private TextView status, contextLine;
    private String id = "";
    private JSONObject result;
    private final JSONArray messages = new JSONArray();
    private int renderedMessages;
    private boolean animateNextMessage;
    private boolean busy;
    // Invalidates a pending network reply after Clear or Activity recreation.
    private int conversationGeneration;

    static void open(Context context, String id) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(EXTRA_ID, id);
        context.startActivity(intent);
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(UiTheme.wrap(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_chat);
            PortalTransition.install(this, findViewById(R.id.portal_surface),
                    findViewById(R.id.portal_content), savedInstanceState);
            UiTheme.padForSystemBars(this, findViewById(R.id.portal_content));
            Report.mark("chat.onCreate");
            id = getIntent() == null ? "" : getIntent().getStringExtra(EXTRA_ID);
            result = History.load(this, id);
            chatList = need(R.id.chat_list);
            chatScroll = need(R.id.chat_scroll);
            input = need(R.id.chat_input);
            status = need(R.id.chat_status);
            contextLine = need(R.id.context_line);

            need(R.id.back).setOnClickListener(v -> PortalTransition.close(this));
            click(R.id.clear, "清空对话", this::confirmClearMessages);
            click(R.id.chat_send, "发送消息", this::sendFromField);
            buildQuickPrompts();
            loadMessages();
            renderMessages();

            String name = result.optString("name", "这段录音");
            int turns = countTurns();
            contextLine.setText("基于《" + name + "》的转写文本 · " + (turns > 0 ? turns + " 轮对话" : "开始提问"));
            status.setText(Cloud.configured(this) ? "" : "还没有配置云端 AI：设置 → 云端 AI → 填入 Key。未配置时本页不会联网。");
            Report.mark("chat.onCreate complete");
            Report.flush(this);
        } catch (Throwable error) {
            Report.problem("对话页初始化失败", error);
            TextView fallback = new TextView(this);
            fallback.setText(Report.crashText("对话页初始化失败", error));
            fallback.setTextIsSelectable(true);
            fallback.setPadding(32, 32, 32, 32);
            fallback.setTextSize(11);
            setContentView(fallback);
        }
    }

    @Override
    protected void onDestroy() {
        conversationGeneration++;
        super.onDestroy();
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
                status.setText(what + "失败：" + error.getMessage());
            }
        });
    }

    // ---------------------------------------------------------------- quick prompts

    private void buildQuickPrompts() {
        LinearLayout quick = need(R.id.quick);
        quick.removeAllViews();
        String[] prompts = {"总结提炼", "列出待办", "重点结论", "有争议的地方"};
        for (String prompt : prompts) {
            TextView chip = new TextView(this);
            chip.setText(prompt);
            chip.setTextSize(12);
            chip.setBackgroundResource(R.drawable.bg_pill);
            chip.setMinHeight((int) Motion.dp(this, 48));
            chip.setGravity(Gravity.CENTER);
            chip.setPadding((int) Motion.dp(this, 14), 0,
                    (int) Motion.dp(this, 14), 0);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.rightMargin = (int) Motion.dp(this, 10);
            quick.addView(chip, params);
            chip.setOnClickListener(v -> {
                input.setText(prompt);
                sendFromField();
            });
        }
    }

    // ---------------------------------------------------------------- messages

    private void sendFromField() {
        String text = input.getText().toString().trim();
        if (text.isEmpty() || busy) {
            return;
        }
        input.setText("");
        send(text);
    }

    private void send(String text) {
        if (!Cloud.configured(this)) {
            status.setText("还没有配置云端 AI：设置 → 云端 AI → 填入 Key。");
            input.setText(text);
            return;
        }
        busy = true;
        try {
            messages.put(new JSONObject().put("role", "user").put("content", text));
        } catch (Exception ignored) {
        }
        animateNextMessage = true;
        renderMessages();
        saveMessages();
        status.setText("正在思考…");
        final JSONArray wire = buildWireMessages();
        final int generation = conversationGeneration;
        new Thread(() -> {
            try {
                String reply = Cloud.chat(this, wire);
                ui.post(() -> {
                    if (generation != conversationGeneration || isFinishing() || isDestroyed()) return;
                    try {
                        messages.put(new JSONObject().put("role", "assistant").put("content", reply));
                        animateNextMessage = true;
                        renderMessages();
                        saveMessages();
                        status.setText("");
                    } catch (Exception e) {
                        status.setText("保存回复失败：" + e.getMessage());
                    }
                    busy = false;
                });
            } catch (Throwable error) {
                String hint = error instanceof Cloud.ApiException ? ((Cloud.ApiException) error).hint
                        : String.valueOf(error.getMessage());
                ui.post(() -> {
                    if (generation != conversationGeneration || isFinishing() || isDestroyed()) return;
                    status.setText("失败 · " + hint);
                    busy = false;
                });
            }
        }, "cloud-chat").start();
    }

    /** transcript as system context + full history, capped so the request stays affordable. */
    private JSONArray buildWireMessages() {
        JSONArray wire = new JSONArray();
        try {
            String transcript = Exporter.plain(result);
            boolean truncated = transcript.length() > MAX_CONTEXT_CHARS;
            if (truncated) {
                transcript = transcript.substring(0, MAX_CONTEXT_CHARS);
            }
            String system = "你是一个严谨的中文会议/录音助手。下面是一段录音的转写文本"
                    + "（自动识别，可能有错字，说话人是匿名标签）。\n---\n"
                    + transcript
                    + (truncated ? "\n---（文本过长，已截断）" : "\n---")
                    + "\n只根据上面的文本回答；如果文本里没有依据，请直接说不知道，不要编造。";
            wire.put(new JSONObject().put("role", "system").put("content", system));
            for (int i = 0; i < messages.length(); i++) {
                wire.put(messages.getJSONObject(i));
            }
        } catch (Exception e) {
            Report.problem("组装对话上下文失败", e);
        }
        return wire;
    }

    private void renderMessages() {
        boolean nearBottom = chatScroll.getScrollY() + chatScroll.getHeight()
                >= chatList.getHeight() - Motion.dp(this, 72);
        if (messages.length() < renderedMessages) {
            chatList.removeAllViews(); // Explicit clear only; never recreate old messages on reply.
            renderedMessages = 0;
        }
        boolean added = false;
        boolean sentByUser = false;
        for (int i = renderedMessages; i < messages.length(); i++) {
            try {
                JSONObject message = messages.getJSONObject(i);
                String role = message.optString("role", "user");
                TextView bubble = addBubble(role, message.optString("content", ""));
                if (animateNextMessage) Motion.messageIn(bubble);
                sentByUser |= "user".equals(role);
                added = true;
            } catch (Exception ignored) {
            }
        }
        renderedMessages = messages.length();
        animateNextMessage = false;
        // Don't yank the scroll position away when a reply arrives while reading older turns.
        if (added && (nearBottom || sentByUser))
            chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private TextView addBubble(String role, String text) {
        boolean user = "user".equals(role);
        TextView bubble = new TextView(this);
        bubble.setText(text);
        bubble.setTextSize(15);
        bubble.setLineSpacing(2f * getResources().getDisplayMetrics().density, 1f);
        bubble.setTextColor(user ? getColorCompat(R.color.on_accent) : getColorCompat(R.color.ink));
        bubble.setBackgroundResource(user ? R.drawable.bg_bubble_user : R.drawable.bg_bubble_ai);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        bubble.setPadding(pad + 4, pad, pad + 4, pad);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = pad / 2;
        params.bottomMargin = pad / 2;
        params.gravity = user ? Gravity.END : Gravity.START;
        params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.78);
        bubble.setLayoutParams(params);
        chatList.addView(bubble);
        return bubble;
    }

    private int countTurns() {
        int turns = 0;
        for (int i = 0; i < messages.length(); i++) {
            try {
                if ("user".equals(messages.getJSONObject(i).optString("role"))) {
                    turns++;
                }
            } catch (Exception ignored) {
            }
        }
        return turns;
    }

    // ---------------------------------------------------------------- storage

    private File chatFile() {
        File dir = new File(getFilesDir(), "chat");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, (id.isEmpty() ? "default" : id) + ".json");
    }

    private void loadMessages() {
        File file = chatFile();
        if (!file.isFile()) {
            return;
        }
        try {
            byte[] data = new byte[(int) file.length()];
            try (FileInputStream in = new FileInputStream(file)) {
                int off = 0;
                while (off < data.length) {
                    int n = in.read(data, off, data.length - off);
                    if (n == -1) {
                        break;
                    }
                    off += n;
                }
            }
            JSONArray stored = new JSONArray(new String(data, StandardCharsets.UTF_8));
            for (int i = 0; i < stored.length(); i++) {
                messages.put(stored.getJSONObject(i));
            }
        } catch (Exception e) {
            Report.problem("读取对话记录失败", e);
        }
    }

    private void saveMessages() {
        try (FileOutputStream out = new FileOutputStream(chatFile())) {
            out.write(messages.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            Report.problem("保存对话记录失败", e);
        }
    }

    private void confirmClearMessages() {
        if (messages.length() == 0) return;
        new AlertDialog.Builder(this)
                .setTitle("清空这段录音的对话？")
                .setMessage("这会删除当前录音的问答记录，无法恢复；转写文本不会被删除。")
                .setNegativeButton("保留", null)
                .setPositiveButton("清空对话", (dialog, which) -> clearMessages())
                .show();
    }

    private void clearMessages() {
        conversationGeneration++;
        busy = false;
        while (messages.length() > 0) {
            messages.remove(messages.length() - 1);
        }
        renderMessages();
        saveMessages();
        contextLine.setText("基于《" + result.optString("name", "这段录音") + "》的转写文本 · 开始提问");
        status.setText("对话已清空。");
    }

    private int getColorCompat(int color) {
        return getResources().getColor(color, getTheme());
    }
}
