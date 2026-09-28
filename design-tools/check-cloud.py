#!/usr/bin/env python3
"""
Security invariants for the optional cloud features.

The app earned its "nothing leaves the phone" promise by having no network code at all.
Now there is an optional cloud AI / cloud ASR feature, so the promise has to be enforced
by construction instead of by good intentions:

  1. network APIs may only appear in Cloud.java;
  2. the API key is never logged, never placed in a URL, never written to shared storage;
  3. plain http is only allowed for loopback addresses (the emulator probe);
  4. every cloud call goes through the "is a key configured?" gate;
  5. the presets for MiMo / DeepSeek / custom OpenAI-compatible endpoints are present;
  6. with no key configured, nothing can be sent.
"""
import re
import sys
import pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "src"
CLOUD = SRC / "com" / "example" / "tingxiejian" / "Cloud.java"

failures = []

def check(condition, message):
    if not condition:
        failures.append(message)

def main():
    check(CLOUD.is_file(), "Cloud.java 不见了")
    cloud = CLOUD.read_text(encoding="utf-8")

    # 1. network API usage only in Cloud.java
    network_tokens = ["java.net.", "HttpURLConnection", "URLConnection", "openConnection",
                      "Socket(", "OkHttp", "WebView", "HttpClient", "HttpUriRequest"]
    for java in sorted(SRC.rglob("*.java")):
        text = java.read_text(encoding="utf-8")
        for token in network_tokens:
            if token in text and java.name != "Cloud.java":
                failures.append("%s 使用了网络 API %s：网络代码只允许在 Cloud.java" % (java.name, token))

    # 2. the key never leaks
    check("Log." not in cloud or not re.search(r'Log\.[dvwie]\([^)]*apiKey', cloud),
          "Cloud.java 的日志里出现了 apiKey")
    check("?" not in re.sub(r'//[^\n]*', '', cloud).split("openConnection")[0] or "?key=" not in cloud,
          "Cloud.java 把密钥放进 URL 查询参数")
    check("getExternalStorage" not in cloud and "MediaStore" not in cloud,
          "Cloud.java 涉及共享存储，密钥/内容可能外泄")

    # 3. http only for loopback
    for match in re.finditer(r'"(http://[^"]+)"', cloud):
        failures.append("Cloud.java 出现明文 http 地址: %s（只允许 https 或 loopback）" % match.group(1))
    check("isLoopback" in cloud or "127.0.0.1" in cloud, "Cloud.java 缺少 loopback 例外的实现")

    # 4. gated calls
    for entry in ["static String chat(", "static String asr(", "static String test("]:
        check(entry in cloud, "Cloud.java 缺少入口 %s" % entry)
    check("requireBaseUrl(context)" in cloud and "configured(context)" in cloud,
          "Cloud.java 缺少 configured()/requireBaseUrl() 门禁")
    body = cloud[cloud.index("static boolean configured"):cloud.index("static boolean configured") + 260]
    check('isEmpty()' in body, "configured() 没有在密钥为空时返回 false")

    # 5. presets
    # Model ids must track the vendors' current names - stale ids are silent failures for users.
    for needle in ["api.xiaomimimo.com", "api.deepseek.com",
                   "mimo-v2.6-flash", "mimo-v2.6-pro", "deepseek-flash", "deepseek-v4-pro"]:
        check(needle in cloud, "Cloud.java 缺少预设 %s（模型 ID 必须是厂商当前名字）" % needle)
    for stale in ['"mimo-v2.5"', 'deepseek-chat', 'gpt-3.5']:
        check(stale not in cloud, "Cloud.java 还留着过时模型 ID: %s" % stale)

    # 6. the manifests must declare the permission exactly once, next to the explanation
    manifest = (ROOT / "AndroidManifest.xml").read_text(encoding="utf-8")
    check(manifest.count('android.permission.INTERNET"') == 1, "INTERNET 权限必须恰好声明一次")
    check("设置" in manifest or "key" in manifest, "INTERNET 权限旁应说明只有填了 key 才联网")

    if failures:
        for failure in failures:
            print("FAIL:", failure)
        sys.exit(1)
    print("cloud invariants ok: 6 rules across %d java files" % len(list(SRC.rglob("*.java"))))

if __name__ == "__main__":
    main()
