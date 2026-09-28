package com.example.tingxiejian;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Builds the HyperOS FocusTemplateV3 payload (`miui.focus.param`) for Xiaomi Super Island.
 *
 * <p>Every field name and nesting rule here matches the verified reference implementation
 * (Xiaomi-SuperIsland-Playground, XiaomiSuperIslandPublisher.kt, Apache-2.0) — the payload is a
 * private protocol, so exact field parity is the whole game. Pure Java on purpose: the desktop
 * test {@code design-tools/IslandPayloadDump.java} runs this class on a plain JDK and the shell
 * test asserts the exact production bytes (layout, mandatory keys, escaping, size cap).
 *
 * <p>Template choices for the transcription scene (all from the reference's enum set):
 * expanded primary = TEXT_TWO (baseInfo type 2), expanded accessory = PROGRESS_TWO,
 * big island = LEFT_PROGRESS_TEXT, small island = PROGRESS_ICON. The upstream accessory is an
 * exclusive enum: PROGRESS_TWO and ACTIONS cannot be emitted together. Stopping remains an
 * ordinary Android Notification.Action, which works even when the OEM rejects an island.
 */
public final class XiaomiIslandPayloadBuilder {
    private XiaomiIslandPayloadBuilder() {}

    /** The exact template serial name read by HyperOS SystemUI. */
    public static final String FOCUS_V3_SERIAL_NAME =
        "com.xzakota.hyper.notification.focus.FocusNotification.FocusTemplateFactory.V3";
    public static final String BUSINESS_NAME = "custom_island";
    /** Picture keys: payload references these; the Notification must carry them in miui.focus.pics. */
    public static final String LOGO_KEY = "miui.focus.pic_island_logo";
    public static final String PRIMARY_IMAGE_KEY = "miui.focus.pic_island_primary";
    public static final String SECONDARY_IMAGE_KEY = "miui.focus.pic_island_secondary";

    /** App palette (values/colors.xml accent / a darker step of it). */
    public static final String ACCENT_HTML = "#16785C";
    public static final String SECONDARY_ACCENT_HTML = "#0F5942";

    public static final int MAX_PAYLOAD_BYTES = 3072;

    /**
     * @param title           big-island title and baseInfo title (e.g. "正在听写")
     * @param content         baseInfo content (stage line, e.g. "识别说话人 · 25%")
     * @param subTitle        baseInfo subTitle (detail line) — blank to omit
     * @param digitText       progress digit text (e.g. "25%")
     * @param progress        0..100
     * @param timeoutMinutes  island timeout, clamped to 720 like the reference
     * @param firstFrame      true on create (island floats up), false on updates (no re-jump)
     * @param actionTitle     label of the single round action (e.g. "停止")
     * @param actionIntentUri activity intent URI (intent# scheme), like the reference's launcher
     */
    public static String build(String title, String content, String subTitle, String digitText,
                               int progress, long timeoutMinutes, boolean firstFrame,
                               String actionTitle, String actionIntentUri) throws JSONException {
        int pct = progress < 0 ? 0 : Math.min(progress, 100);
        boolean shouldFloat = firstFrame;
        String digits = (digitText == null || digitText.isEmpty()) ? (pct + "%") : digitText;

        JSONObject focus = new JSONObject();
        focus.put("protocol", 1);
        focus.put("business", BUSINESS_NAME);
        focus.put("ticker", title);
        focus.put("tickerPic", LOGO_KEY);
        focus.put("aodTitle", title);
        focus.put("aodPic", LOGO_KEY);
        // Keep the notification active after its content intent opens (reference comment: HyperOS
        // treats a non-updatable Focus notification as one-shot and removes it after the click).
        focus.put("updatable", true);
        focus.put("reopen", "close");
        focus.put("enableFloat", shouldFloat);
        focus.put("islandFirstFloat", shouldFloat);
        long timeout = timeoutMinutes <= 0 ? 0 : Math.min(timeoutMinutes, 720L);
        if (timeout > 0) {
            focus.put("timeout", timeout);
        }

        // Expanded: baseInfo TEXT_TWO (type 2).
        JSONObject base = new JSONObject();
        base.put("type", 2);
        base.put("title", escapeHtml(title));
        base.put("content", escapeHtml(content == null || content.isEmpty() ? " " : content));
        String extra = subTitle == null ? "" : subTitle.trim();
        if (!extra.isEmpty()) {
            base.put("subTitle", escapeHtml(extra));
            base.put("showDivider", true);
        } else {
            base.put("showDivider", false);
        }
        base.put("colorTitle", ACCENT_HTML);
        base.put("colorTitleDark", ACCENT_HTML);
        focus.put("baseInfo", base);

        // Expanded accessory: PROGRESS_TWO.
        focus.put("progressInfo", new JSONObject()
                .put("progress", pct)
                .put("colorProgress", ACCENT_HTML)
                .put("colorProgressEnd", SECONDARY_ACCENT_HTML));

        // param_island: big = LEFT_PROGRESS_TEXT, small = PROGRESS_ICON.
        JSONObject island = new JSONObject();
        island.put("islandProperty", 1);
        island.put("islandOrder", false);
        island.put("highlightColor", ACCENT_HTML);

        JSONObject big = new JSONObject();
        big.put("imageTextInfoLeft", new JSONObject()
                .put("type", 1)
                .put("picInfo", new JSONObject().put("type", 1).put("pic", PRIMARY_IMAGE_KEY))
                .put("textInfo", summaryText(null, title, null)));
        big.put("progressTextInfo", new JSONObject()
                .put("progressInfo", new JSONObject()
                        .put("progress", pct)
                        .put("colorReach", ACCENT_HTML)
                        .put("colorUnReach", SECONDARY_ACCENT_HTML)
                        .put("isCCW", true))
                .put("textInfo", summaryText(null, digits, null)));
        island.put("bigIslandArea", big);

        JSONObject small = new JSONObject();
        small.put("combinePicInfo", new JSONObject()
                .put("picInfo", new JSONObject().put("type", 1).put("pic", PRIMARY_IMAGE_KEY))
                .put("progressInfo", new JSONObject()
                        .put("progress", pct)
                        .put("colorReach", ACCENT_HTML)
                        .put("colorUnReach", SECONDARY_ACCENT_HTML)
                        .put("isCCW", true)));
        island.put("smallIslandArea", small);

        long timeoutSeconds = Math.min(43_200L, timeout * 60L);
        if (timeoutSeconds > 0) {
            island.put("islandTimeout", timeoutSeconds);
        }
        focus.put("param_island", island);

        String payload = new JSONObject()
                .put("type", FOCUS_V3_SERIAL_NAME)
                .put("param_v2", focus)
                .toString();
        if (payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalStateException("focus payload exceeds 3072 bytes");
        }
        return payload;
    }

    /** Reference buildActionInfo with ActionStyle.ROUND (type 0) and actionIntentType 1. */
    public static JSONObject buildActionInfo(String label, String intentUri, boolean includeType) throws JSONException {
        String pressed = darkenColor(ACCENT_HTML, 0.14f);
        String titleColor = luminance(ACCENT_HTML) >= 0.62f ? "#161616" : "#FFFFFF";
        JSONObject o = new JSONObject();
        if (includeType) {
            o.put("type", 0);
        }
        o.put("actionIcon", SECONDARY_IMAGE_KEY);
        o.put("actionIconDark", SECONDARY_IMAGE_KEY);
        o.put("actionTitle", escapeHtml(label));
        o.put("actionTitleColor", titleColor);
        o.put("actionTitleColorDark", titleColor);
        o.put("actionBgColor", ACCENT_HTML);
        o.put("actionBgColorDark", ACCENT_HTML);
        o.put("actionBgPressColor", pressed);
        o.put("actionBgPressColorDark", pressed);
        o.put("actionIntentType", 1);
        o.put("actionIntent", intentUri);
        o.put("clickWithCollapse", true);
        return o;
    }

    /** Reference summaryText block used inside the island areas. */
    public static JSONObject summaryText(String frontTitle, String title, String content) throws JSONException {
        JSONObject o = new JSONObject();
        String front = frontTitle == null ? "" : frontTitle.trim();
        if (!front.isEmpty()) {
            o.put("frontTitle", front);
        }
        o.put("title", (title == null || title.isEmpty()) ? " " : title);
        String tail = content == null ? "" : content.trim();
        if (!tail.isEmpty()) {
            o.put("content", tail);
        }
        o.put("narrowFont", false);
        o.put("showHighlightColor", true);
        return o;
    }

    /** android.text.TextUtils.htmlEncode semantics (what the reference's htmlEncode does). */
    public static String escapeHtml(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&': b.append("&amp;"); break;
                case '<': b.append("&lt;"); break;
                case '>': b.append("&gt;"); break;
                case '"': b.append("&quot;"); break;
                case '\'': b.append("&#39;"); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }

    static String darkenColor(String html, float amount) {
        int rgb = parseHex(html);
        float f = 1f - Math.max(0f, Math.min(1f, amount));
        int r = (int) (((rgb >> 16) & 0xFF) * f);
        int g = (int) (((rgb >> 8) & 0xFF) * f);
        int b = (int) ((rgb & 0xFF) * f);
        return String.format(java.util.Locale.US, "#%02X%02X%02X", r, g, b);
    }

    /** android.graphics.Color.luminance semantics (relative luminance, 0..1). */
    static float luminance(String html) {
        int rgb = parseHex(html);
        double r = srgb(((rgb >> 16) & 0xFF) / 255.0);
        double g = srgb(((rgb >> 8) & 0xFF) / 255.0);
        double b = srgb((rgb & 0xFF) / 255.0);
        return (float) (0.2126 * r + 0.7152 * g + 0.0722 * b);
    }

    private static double srgb(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int parseHex(String html) {
        String h = html.startsWith("#") ? html.substring(1) : html;
        return Integer.parseInt(h, 16);
    }
}
