#!/usr/bin/env python3
"""Invariants for the Xiaomi Super Island adapter layer.

The XMSF validation gate changes a system network rule for a ~220ms window, so the dangerous parts
must stay provably correct:

  1. the gate restores in a finally-adjacent way: `publishWithValidation` must contain a `finally`
     block that calls `restoreIfNeeded`;
  2. every hidden-API call is wrapped in a catch-Throwable (never crash the app);
  3. hidden API / HyperOS types stay inside the adapter classes (XiaomiXmsfValidationGate,
     XiaomiIslandCapability) — business code must not import them;
  4. no network APIs anywhere in the island layer (offline app; network lives in Cloud.java only);
  5. island updates use one stable notification id (publish/cancel take the id as a parameter —
     no `System.currentTimeMillis()` as an id);
  6. the payload builder stays Android-free so the shipped bytes are testable on a plain JDK.

Run it via design-tools/check-all.sh. Exits non-zero on the first violation.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "src" / "com" / "example" / "tingxiejian"

failures = []


def check(condition, message):
    if not condition:
        failures.append(message)


def read(name):
    return (SRC / name).read_text(encoding="utf-8")


gate = read("XiaomiXmsfValidationGate.java")
publisher = read("XiaomiIslandPublisher.java")
capability = read("XiaomiIslandCapability.java")
payload = read("XiaomiIslandPayloadBuilder.java")
facade = read("IslandNotification.java")
bridge_path = SRC / "ShizukuIslandBridge.java"
bridge = bridge_path.read_text(encoding="utf-8") if bridge_path.exists() else ""

# 0. Shizuku bridge must be opt-in, observable, and reversible even after a failed post.
check("rikka.shizuku.Shizuku" in bridge and "ShizukuBinderWrapper" in bridge,
      "需要真正的 Shizuku Binder 代理，不能仅反射隐藏方法")
check("getUidFirewallRule" in bridge and "getFirewallChainEnabled" in bridge,
      "修改网络规则前必须读取原值")
check("setUidFirewallRule" in bridge and "setFirewallChainEnabled" in bridge,
      "只有 Shizuku 代理可实际修改并恢复 XMSF 防火墙")
check("island_shizuku" in bridge and "checkSelfPermission" in bridge,
      "门禁须由用户显式开启且确认 Shizuku 授权")
check("restoreIfNeeded" in bridge and "finally" in publisher,
      "发布结束必须恢复原有 XMSF 规则")
check("recovery.edit().putInt(TARGET_UID" in bridge and "putBoolean(PENDING, true).commit()" in bridge
      and bridge.index("putBoolean(PENDING, true).commit()") < bridge.index('call(remote, "setUidFirewallRule", OEM_DENY_3, uid, RULE_DENY)'),
      "门禁写入前必须持久化原始规则，供进程死亡后恢复")
check("WATCHDOG.schedule" in bridge and "activeWindow" in bridge
      and "ShizukuIslandBridge.class" in bridge,
      "恢复需超时看门狗及跨实例互斥，不能中断仍在发布的窗口")
check("readRule(remote, uid) != RULE_DENY" in bridge and "readRule(remote, uid) != oldRule" in bridge,
      "写入/恢复网络规则都要读回，不得只检查调用是否抛异常")
manifest_xml = (ROOT / "AndroidManifest.xml").read_text(encoding="utf-8")
check("ShizukuProvider" in manifest_xml
      and "moe.shizuku.manager.permission.API_V23" in manifest_xml,
      "必须登记 Shizuku provider 及其授权权限以接收 Binder")
build_script = (ROOT / "build.sh").read_text(encoding="utf-8")
check(all('shizuku-%s.jar' % module in build_script for module in ('api', 'provider', 'aidl', 'shared')),
      "Shizuku API 的 aidl/shared 传递依赖必须一并打包，缺失会启动闪退")
settings_xml = (ROOT / "res/layout/activity_settings.xml").read_text(encoding="utf-8")
check('android:id="@+id/shizuku_island_switch"' in settings_xml
      and 'android:id="@+id/shizuku_authorize"' in settings_xml
      and 'android:layout_height="48dp"' in settings_xml,
      "Shizuku 授权与显式开关须在设置页，并保持可触达的点击区域")
settings_java = read("SettingsActivity.java")
check('setTitle("启用实验性 Shizuku 门禁？")' in settings_java
      and 'setOnCancelListener(dialog -> shizukuIslandSwitch.setChecked(false))' in settings_java,
      "网络副作用须经用户明确确认，取消不得留下已开启的视觉假象")

# 1. unconditional restoration around the validation window
check(re.search(r"finally\s*\{[^}]*restoreIfNeeded\(\)", publisher, re.S),
       "finally 块内必须调用 xmsfGate.restoreIfNeeded()（无条件恢复 XMSF 规则）")
check("setXmsfBlocked(true)" in publisher and "restoreIfNeeded()" in publisher,
       "门禁必须成对 arm/restore")
check("restoreIfNeeded" in gate and "blockedByThisInstance" in gate,
       "门禁需要 restoreIfNeeded + 本次实例是否 arm 的记录")
check("Thread.sleep(XMSF_VALIDATION_WINDOW_MS)" in publisher,
       "必须等待验证窗口后再返回（reference: 220ms）")
check("static synchronized boolean publishWithValidation" in publisher,
       "同进程多个发布线程必须串行开关 XMSF 门禁")
check("XMSF_VALIDATION_WINDOW_MS = 220L" in publisher, "验证窗口应与 upstream 一致（220ms）")

# 2. hidden-API calls never escape
for name, text in (("ShizukuIslandBridge", bridge), ("XiaomiIslandCapability", capability)):
    check("catch (Throwable" in text, "%s 的 hidden API 调用必须 catch Throwable" % name)
    check("HiddenApiBypass" in text, "%s 应通过 HiddenApiBypass 访问 hidden API" % name)
check("updateAurogonUidRule" not in bridge,
      "Shizuku 必须代理系统 Binder 而非重用未经授权的 ConnectivityManager 实例")
check("ShizukuIslandBridge" in gate, "门禁必须使用 Shizuku 特权 Binder，不能依赖普通 UID 调用成功")
check("catch (Throwable error)" in gate, "门禁调用必须捕获 Throwable 并回退")

# 3. adapter boundary: business code keeps its hands clean
adapter = {"ShizukuIslandBridge.java", "XiaomiXmsfValidationGate.java", "XiaomiIslandCapability.java",
           "XiaomiIslandPublisher.java", "XiaomiIslandPayloadBuilder.java", "IslandNotification.java"}
for path in SRC.glob("*.java"):
    if path.name in adapter:
        continue
    text = path.read_text(encoding="utf-8")
    check("HiddenApiBypass" not in text, "业务类 %s 不得直接使用 hidden API" % path.name)
    check("updateAurogonUidRule" not in text, "业务类 %s 不得直接操作 XMSF 规则" % path.name)
    check("miui.focus" not in text, "业务类 %s 不得直接拼 HyperOS 字段" % path.name)

# 4. no network in the island layer
for name, text in (("ShizukuIslandBridge", bridge), ("XiaomiXmsfValidationGate", gate),
                   ("XiaomiIslandCapability", capability),
                   ("XiaomiIslandPublisher", publisher), ("XiaomiIslandPayloadBuilder", payload),
                   ("IslandNotification", facade)):
    for bad in ("java.net.", "HttpURLConnection", "okhttp", "Socket("):
        check(bad not in text, "%s 不允许网络 API：%s" % (name, bad))

# 5. stable notification id: publish/cancel take the id, and nothing mints ids from the clock
check("publish(Context context, NotificationManager manager, int notificationId" in publisher,
       "publish 必须接收稳定 notificationId")
check("cancel(Context context, NotificationManager manager, int notificationId)" in publisher,
       "cancel 必须接收稳定 notificationId")
check("System.currentTimeMillis()" not in publisher,
       "不得用当前时间当 notificationId（会造成重复岛/无法取消）")
check("manager.notify(notificationId, notification)" in publisher,
       "必须用传入的 notificationId 发布")
check("manager.cancel(notificationId)" in publisher, "必须用传入的 notificationId 取消")

# 6. payload builder stays testable on a plain JDK
for bad in ("import android.", "import androidx."):
    check(bad not in payload, "XiaomiIslandPayloadBuilder 不得 import Android（桌面测试依赖）")
check("MAX_PAYLOAD_BYTES = 3072" in payload, "payload 必须保留 3072 字节上限")
check("FocusTemplateFactory.V3" in payload, "payload 顶层 type 必须是 FocusTemplateFactory.V3")
check('"custom_island"' in payload, "business 必须与 upstream 一致（custom_island）")
check("bigIslandArea" in payload and "smallIslandArea" in payload, "两个 island area 必填")
check('"actions", actions' not in payload, "upstream expanded accessory 是互斥枚举：进度不能与动作混搭")
manifest = (ROOT / "AndroidManifest.xml").read_text(encoding="utf-8")
service = read("LocalService.java")
check('<package android:name="com.xiaomi.xmsf"/>' in manifest,
      "Android 11+ 必须声明 XMSF package visibility")
check("NotificationManager.IMPORTANCE_DEFAULT : NotificationManager.IMPORTANCE_LOW" in service,
      "岛频道需与 upstream 同为 DEFAULT；普通通知保持 LOW")
check("return publishWithValidation(context, manager, notificationId, notification," in publisher
      and "firstFrame ? firstPoster : null" in publisher,
      "与 upstream 一样每次岛更新校验 XMSF；仅首帧可切换前台 ID")
check("BOOTSTRAP_ID = 12" in service and "startForeground(useIslandChannel ? BOOTSTRAP_ID" in service
      and "startForeground(NOTIFICATION_ID, focus)" in service,
      "首个岛通知必须是新 ID 的前台通知，不能只是普通通知的更新")
check("Notification.Builder.recoverBuilder(context, notification)" in publisher
      and ".addExtras(extras).build()" in publisher,
      "焦点载荷必须在 builder.build 前 attach，与 GitHub 示例保持一致")
check("islandWanted && XiaomiIslandPublisher.isSupported(this)" in service,
      "岛开关和能力检测必须同时生效")
check("protocolMissing || protocol < 3 || !gateSupported" in capability,
      "焦点协议 V3 与已授权的特权门禁缺一不可")
check("if (!notification.extras.containsKey(KEY_FOCUS_PARAM)" in publisher,
      "通知发布前验证焦点扩展字段真的进入成品 Notification")

if failures:
    for message in failures:
        print("FAIL:", message)
    sys.exit(1)
print("island gate invariants ok: 7 rules across %d files" % (len(list(SRC.glob('*.java')))))
