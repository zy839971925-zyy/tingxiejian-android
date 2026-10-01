package com.example.tingxiejian;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The ONLY class in this app that touches the network.
 *
 * Rules, enforced by design-tools/check-cloud.py:
 *   - nothing runs unless the user saved an API key in Settings;
 *   - requests go to the endpoint the user configured (presets are starting points);
 *   - the key is never logged and never written to shared storage;
 *   - every failure is translated into Chinese the user can act on.
 *
 * All three providers speak the OpenAI /chat/completions dialect:
 *   - Xiaomi MiMo : https://api.xiaomimimo.com/v1   (mimo-v2.5 / mimo-v2.5-asr)
 *   - DeepSeek    : https://api.deepseek.com/v1     (deepseek-flash)
 *   - custom      : any OpenAI-compatible base URL
 * MiMo speech recognition rides the same endpoint with an "input_audio" message part,
 * which keeps this client small.
 */
final class Cloud {
    static final String PREFS = "cloud";
    private static final ScheduledThreadPoolExecutor REQUEST_WATCHDOG=new ScheduledThreadPoolExecutor(2,r->{
        Thread t=new Thread(r,"cloud-request-deadline");t.setDaemon(true);return t;
    });
    static {REQUEST_WATCHDOG.setRemoveOnCancelPolicy(true);}

    static final class Provider {
        final String id, name, baseUrl, chatModel, asrModel;

        Provider(String id, String name, String baseUrl, String chatModel, String asrModel) {
            this.id = id;
            this.name = name;
            this.baseUrl = baseUrl;
            this.chatModel = chatModel;
            this.asrModel = asrModel;
        }
    }

    static final Provider[] PRESETS = {
            new Provider("mimo", "小米 MiMo", "https://api.xiaomimimo.com/v1", "mimo-v2.6-flash", "mimo-v2.5-asr"),
            new Provider("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-flash", ""),
            new Provider("custom", "自定义（OpenAI 兼容）", "", "", ""),
    };

    /** A failure with a message the user can act on, plus the raw body for support. */
    static final class ApiException extends Exception {
        final String hint;

        ApiException(String hint, Throwable cause) {
            super(hint, cause);
            this.hint = hint;
        }

        ApiException(String hint) {
            super(hint);
            this.hint = hint;
        }
    }

    // ---------------------------------------------------------------- settings

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String provider(Context context) {
        return prefs(context).getString("provider", "mimo");
    }

    static String baseUrl(Context context) {
        return prefs(context).getString("baseUrl", preset(provider(context)).baseUrl);
    }

    static String apiKey(Context context) {
        return SecureSecretStore.read(context);
    }

    static String chatModel(Context context) {
        return prefs(context).getString("model", preset(provider(context)).chatModel);
    }

    static String asrModel(Context context) {
        return prefs(context).getString("asrModel", preset(provider(context)).asrModel);
    }

    /** Save a key explicitly; encryption failure leaves the previous credential intact. */
    static synchronized boolean save(Context context, String provider, String baseUrl, String apiKey,
                        String chatModel, String asrModel) {
        if (!SecureSecretStore.save(context, apiKey)) return false;
        saveMetadata(context, provider, baseUrl, chatModel, asrModel);
        return true;
    }

    static synchronized void saveMetadata(Context context, String provider, String baseUrl,
                             String chatModel, String asrModel) {
        prefs(context).edit()
                .putString("provider", provider)
                .putString("baseUrl", baseUrl)
                .putString("model", chatModel)
                .putString("asrModel", asrModel)
                .apply();
    }

    static synchronized boolean clear(Context context) {
        return SecureSecretStore.save(context, "");
    }

    static String secretStatus(Context context) {
        return SecureSecretStore.status();
    }

    static Provider preset(String id) {
        for (Provider preset : PRESETS) {
            if (preset.id.equals(id)) return preset;
        }
        return PRESETS[PRESETS.length - 1];   // custom：绝不返回 null
    }

    /** True only when the user opted in by filling a key. */
    static boolean configured(Context context) {
        return !apiKey(context).trim().isEmpty() && !baseUrl(context).trim().isEmpty();
    }

    static boolean hasAsr(Context context) {
        return configured(context) && !asrModel(context).trim().isEmpty();
    }

    /** Current model ids per provider, shown as field hints so nobody types a retired name. */
    static String modelHint(String id) {
        if ("mimo".equals(id)) return "mimo-v2.6-flash / mimo-v2.6-pro";
        if ("deepseek".equals(id)) return "deepseek-flash / deepseek-v4-pro";
        return "服务商当前的模型 ID";
    }

    static String asrHint(String id) {
        return "mimo".equals(id)
                ? "mimo-v2.5-asr（留空 = 只用本地）"
                : "服务商的语音识别模型 ID";
    }

    // ---------------------------------------------------------------- calls

    /** Connectivity check with a tiny request; returns a human-readable success line. */
    static String test(Context context) throws ApiException {
        long started = System.currentTimeMillis();
        String reply = chat(context, "你是连通性测试助手。",
                "只回复两个字：正常", 8_000);
        long cost = System.currentTimeMillis() - started;
        return "连接成功 · " + chatModel(context) + " · " + cost + " ms · " + trim(reply, 24);
    }

    static String chat(Context context, String system, String user) throws ApiException {
        return chat(context, system, user, 120_000);
    }

    static String chat(Context context, String system, String user, int readTimeoutMs) throws ApiException {
        JSONArray messages = new JSONArray();
        if (system != null && !system.isEmpty()) {
            messages.put(message("system", system));
        }
        messages.put(message("user", user));
        return chat(context, messages, readTimeoutMs);
    }

    /** Multi-turn chat. messages: [{role, content}, ...] */
    static String chat(Context context, JSONArray messages) throws ApiException {
        return chat(context, messages, 120_000);
    }

    static String chat(Context context, JSONArray messages, int readTimeoutMs) throws ApiException {
        ConnectionConfig config = requireBaseUrl(context);
        String model = chatModel(context);
        if (model.trim().isEmpty()) {
            throw new ApiException("还没有填模型名（设置 → 云端 AI → 模型）");
        }
        try {
            JSONObject body = new JSONObject()
                    .put("model", model.trim())
                    .put("messages", messages)
                    .put("temperature", 0.3);
            JSONObject response = post(context, config, "", body, readTimeoutMs);
            JSONArray choices = response.optJSONArray("choices");
            JSONObject first = choices != null && choices.length() > 0 ? choices.optJSONObject(0) : null;
            JSONObject message = first != null ? first.optJSONObject("message") : null;
            String content = message != null ? message.optString("content", "") : "";
            if (content.isEmpty()) {
                throw new ApiException("服务返回了空内容，可能不是 OpenAI 兼容接口：" + trim(response.toString(), 120));
            }
            return content;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw translate(e);
        }
    }

    /**
     * Speech recognition through MiMo's input_audio message part.
     * The wav payload must stay under ~7 MB raw (10 MB base64), so callers chunk long audio.
     */
    static String asr(Context context, byte[] wav, String language) throws ApiException {
        ConnectionConfig config = requireBaseUrl(context);
        String model = asrModel(context);
        if (model.trim().isEmpty()) {
            throw new ApiException("还没有填语音识别模型名（设置 → 云端 AI → 识别模型）");
        }
        String dataUrl = "data:audio/wav;base64," + Base64.encodeToString(wav, Base64.NO_WRAP);
        try {
            JSONObject inputAudio = new JSONObject().put("data", dataUrl).put("format", "wav");
            JSONObject part = new JSONObject().put("type", "input_audio")
                    .put("input_audio", inputAudio);
            JSONObject user = new JSONObject().put("role", "user")
                    .put("content", new JSONArray().put(part));
            JSONObject body = new JSONObject()
                    .put("model", model.trim())
                    .put("messages", new JSONArray().put(user));
            if (language != null && !language.isEmpty()) {
                body.put("asr_options", new JSONObject().put("language", language));
            }
            JSONObject response = post(context, config, "", body, 300_000);
            JSONArray choices = response.optJSONArray("choices");
            JSONObject first = choices != null && choices.length() > 0 ? choices.optJSONObject(0) : null;
            JSONObject message = first != null ? first.optJSONObject("message") : null;
            String content = message != null ? message.optString("content", "") : "";
            if (content.isEmpty()) {
                throw new ApiException("识别结果为空，可能是音频格式不被接受（MiMo 只支持 wav / mp3）");
            }
            return content.trim();
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw translate(e);
        }
    }

    /** 16 kHz mono float samples -> WAV bytes, for the cloud ASR path. */
    static byte[] toWav(float[] samples) {
        byte[] data = new byte[samples.length * 2];
        for (int i = 0; i < samples.length; i++) {
            float value = samples[i];
            if (value > 1f) value = 1f;
            if (value < -1f) value = -1f;
            short pcm = (short) (value * 32767);
            data[i * 2] = (byte) (pcm & 0xff);
            data[i * 2 + 1] = (byte) ((pcm >> 8) & 0xff);
        }
        int sampleRate = 16000;
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length + 44);
        writeAscii(out, "RIFF");
        writeInt(out, 36 + data.length);
        writeAscii(out, "WAVEfmt ");
        writeInt(out, 16);
        writeShort(out, (short) 1);
        writeShort(out, (short) 1);
        writeInt(out, sampleRate);
        writeInt(out, sampleRate * 2);
        writeShort(out, (short) 2);
        writeShort(out, (short) 16);
        writeAscii(out, "data");
        writeInt(out, data.length);
        out.write(data, 0, data.length);
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- http

    private static final class ConnectionConfig {
        final String baseUrl, apiKey;
        ConnectionConfig(String baseUrl, String apiKey) { this.baseUrl = baseUrl; this.apiKey = apiKey; }
    }

    /** Keep a provider's endpoint and bearer token in one immutable request snapshot. */
    private static synchronized ConnectionConfig requireBaseUrl(Context context) throws ApiException {
        String baseUrl = baseUrl(context).trim();
        String apiKey = apiKey(context).trim();
        if (baseUrl.isEmpty()) {
            throw new ApiException("还没有填接口地址（设置 → 云端 AI → 接口地址）");
        }
        if (apiKey.isEmpty()) {
            throw new ApiException(secretStatus(context).isEmpty()
                    ? "还没有填 API Key（设置 → 云端 AI）；自动文本校正已跳过。" : secretStatus(context));
        }
        for(int i=0;i<apiKey.length();i++){
            char value=apiKey.charAt(i);
            if(value<32||value==127)throw new ApiException("API Key 含换行或控制字符，请重新粘贴完整密钥。");
        }
        try {
            URL parsed = new URL(baseUrl);
            String protocol = parsed.getProtocol();
            String host = parsed.getHost();
            boolean isLoopback = "localhost".equalsIgnoreCase(host)
                    || "127.0.0.1".equals(host) || "[::1]".equals(host);
            if ((!"https".equalsIgnoreCase(protocol) && !("http".equalsIgnoreCase(protocol) && isLoopback))
                    || host.isEmpty() || parsed.getUserInfo() != null || parsed.getRef() != null) {
                throw new ApiException("接口地址必须是 https（明文 http 只允许本机调试）");
            }
        } catch (MalformedURLException error) {
            throw new ApiException("接口地址不是有效的 URL", error);
        }
        return new ConnectionConfig(baseUrl, apiKey);
    }

    private static JSONObject post(Context context, ConnectionConfig config, String path, JSONObject body,
                                   int readTimeout) throws ApiException, IOException {
        URL url = new URL(join(config.baseUrl, path));
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        Thread requester=Thread.currentThread();
        long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(Math.max(1,readTimeout));
        AtomicBoolean disconnected=new AtomicBoolean();
        ScheduledFuture<?> cancellation=REQUEST_WATCHDOG.scheduleAtFixedRate(()->{
            if((requester.isInterrupted()||System.nanoTime()>=deadline)&&disconnected.compareAndSet(false,true))connection.disconnect();
        },100,100,TimeUnit.MILLISECONDS);
        try {
            checkRequest(deadline);
            connection.setConnectTimeout(Math.min(10_000,Math.max(1,readTimeout)));
            connection.setReadTimeout(Math.max(1,readTimeout));
            // Do not forward a bearer token to an untrusted redirect destination.
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + config.apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = connection.getResponseCode();
            String response = readAll(code >= 400 ? connection.getErrorStream() : connection.getInputStream(),deadline);
            checkRequest(deadline);
            // Untrusted providers can echo Authorization in an error body. It must never
            // reach diagnostics/UI or downstream transcript fields as an exposed secret.
            response = response.replace(config.apiKey, "[已隐藏密钥]");
            if (code < 200 || code >= 300) {
                throw new ApiException(friendlyHttp(code, response));
            }
            try {
                return new JSONObject(response);
            } catch (Exception e) {
                throw new ApiException("服务返回的不是 JSON，可能不是 OpenAI 兼容接口：" + trim(response, 120));
            }
        } finally {
            cancellation.cancel(false);
            connection.disconnect();
        }
    }

    private static String friendlyHttp(int code, String body) {
        String detail = errorText(body);
        switch (code) {
            case 401:
                return "API Key 无效或已过期（401）" + detail;
            case 402:
            case 403:
                return "没有权限或额度不足（" + code + "）" + detail;
            case 404:
                return "接口地址或模型名不对（404）" + detail;
            case 429:
                return "请求太频繁或额度用尽（429）" + detail;
            default:
                return code >= 500
                        ? "服务端出错（" + code + "），稍后再试" + detail
                        : "请求失败（" + code + "）" + detail;
        }
    }

    private static String errorText(String body) {
        try {
            JSONObject json = new JSONObject(body);
            JSONObject error = json.optJSONObject("error");
            String message = error != null ? error.optString("message", "") : json.optString("message", "");
            return message.isEmpty() ? "" : "：" + trim(message, 120);
        } catch (Exception e) {
            return body == null || body.trim().isEmpty() ? "" : "：" + trim(body, 120);
        }
    }

    private static ApiException translate(Exception e) {
        if (e instanceof InterruptedIOException && Thread.currentThread().isInterrupted())
            return new ApiException("已取消请求", e);
        if (e instanceof SocketTimeoutException) {
            return new ApiException("网络超时，检查网络后重试", e);
        }
        if (e instanceof IOException) {
            return new ApiException("网络不通（" + e.getMessage() + "）", e);
        }
        return new ApiException("请求失败（" + e.getMessage() + "）", e);
    }

    private static JSONObject message(String role, String content) {
        try {
            return new JSONObject().put("role", role).put("content", content);
        } catch (Exception impossible) {
            return new JSONObject();
        }
    }

    private static String join(String baseUrl, String path) {
        String url = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return path == null || path.isEmpty() ? url : url + "/" + path;
    }

    private static void checkRequest(long deadline)throws IOException {
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("request cancelled");
        if(System.nanoTime()>=deadline)throw new SocketTimeoutException("request total deadline reached");
    }

    private static String readAll(InputStream in,long deadline) throws IOException {
        if (in == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            checkRequest(deadline);
            if (out.size() + n > 2 * 1024 * 1024)
                throw new IOException("云端返回内容超过 2 MB，已停止读取");
            out.write(buffer, 0, n);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String trim(String value, int max) {
        String text = value == null ? "" : value.trim().replace('\n', ' ');
        return text.length() > max ? text.substring(0, max) + "…" : text;
    }

    private static void writeAscii(ByteArrayOutputStream out, String text) {
        for (int i = 0; i < text.length(); i++) {
            out.write(text.charAt(i));
        }
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
        out.write((value >> 16) & 0xff);
        out.write((value >> 24) & 0xff);
    }

    private static void writeShort(ByteArrayOutputStream out, short value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }
}
