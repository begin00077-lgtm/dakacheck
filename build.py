"""Build the 打卡提醒 APK offline: javac -> d8 -> aapt2 -> zipalign -> apksigner."""
import os
import subprocess
import sys
import zipfile

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")

HERE = os.path.dirname(os.path.abspath(__file__))

# 支持两种布局：
#   1) 开发布局：<root>/app + <root>/tools（build.py 在 app/ 里）
#   2) 发布尃局：本目录就是模块根（src/res/AndroidManifest.xml 同级）
if os.path.exists(os.path.join(HERE, "AndroidManifest.xml")):
    APP = HERE
    ROOT = os.path.dirname(HERE)
else:
    ROOT = os.path.dirname(HERE)
    MODULE = "app"
    if "--app" in sys.argv:
        MODULE = sys.argv[sys.argv.index("--app") + 1]
    APP = os.path.join(ROOT, MODULE)

# tools 目录：同级或上级都找一遍；也可用 ANDROID_TOOLS 环境变量指定
TOOLS = os.environ.get("ANDROID_TOOLS") or ""
if not TOOLS:
    for cand in (os.path.join(ROOT, "tools"), os.path.join(HERE, "tools")):
        if os.path.isdir(cand):
            TOOLS = cand
            break

BUILD = os.path.join(APP, "build")
OUT_NAME = "dakacheck.apk"


def find(root, name):
    for dp, dn, fn in os.walk(root):
        if name in fn:
            return os.path.join(dp, name)
    return None


JAVAC = find(os.path.join(TOOLS, "jdk"), "javac.exe")
JAVA = find(os.path.join(TOOLS, "jdk"), "java.exe")
KEYTOOL = find(os.path.join(TOOLS, "jdk"), "keytool.exe")
AAPT2 = find(os.path.join(TOOLS, "build-tools"), "aapt2.exe")
D8JAR = find(os.path.join(TOOLS, "build-tools"), "d8.jar")
ZIPALIGN = find(os.path.join(TOOLS, "build-tools"), "zipalign.exe")
APKSIGNER = find(os.path.join(TOOLS, "build-tools"), "apksigner.jar")
ANDROID_JAR = find(os.path.join(TOOLS, "platform"), "android.jar")
KEYSTORE = os.path.join(HERE, "debug.keystore")
if not os.path.exists(KEYSTORE):
    KEYSTORE = os.path.join(ROOT, "debug.keystore")


def run(cmd):
    print("$", " ".join('"%s"' % c if " " in c else c for c in cmd)[:400], flush=True)
    r = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if r.returncode != 0:
        print(r.stdout)
        print(r.stderr)
        raise SystemExit("command failed: %s" % cmd[0])
    if r.stdout.strip():
        print(r.stdout.strip()[-2000:])
    if r.stderr.strip():
        print(r.stderr.strip()[-2000:])
    return r


def compile_java(gen=None):
    sources = []
    if gen:
        for dp, dn, fn in os.walk(gen):
            for f in fn:
                if f.endswith(".java"):
                    sources.append(os.path.join(dp, f))
    for dp, dn, fn in os.walk(os.path.join(APP, "src")):
        for f in fn:
            if f.endswith(".java"):
                sources.append(os.path.join(dp, f))
    classes = os.path.join(BUILD, "classes")
    os.makedirs(classes, exist_ok=True)
    run([JAVAC, "-encoding", "UTF-8", "-source", "8", "-target", "8",
         "-Xlint:-options", "-classpath", ANDROID_JAR, "-d", classes] + sources)
    return classes


def dex(classes):
    out = os.path.join(BUILD, "dex")
    os.makedirs(out, exist_ok=True)
    classfiles = []
    for dp, dn, fn in os.walk(classes):
        for f in fn:
            if f.endswith(".class"):
                classfiles.append(os.path.join(dp, f))
    run([JAVA, "-cp", D8JAR, "com.android.tools.r8.D8", "--min-api", "26",
         "--lib", ANDROID_JAR, "--output", out] + classfiles)
    return os.path.join(out, "classes.dex")


def resources():
    flat = os.path.join(BUILD, "res.flat")
    os.makedirs(flat, exist_ok=True)
    run([AAPT2, "compile", "--dir", os.path.join(APP, "res"), "-o", flat])
    flats = []
    for dp, dn, fn in os.walk(flat):
        for f in fn:
            if f.endswith(".flat"):
                flats.append(os.path.join(dp, f))
    base = os.path.join(BUILD, "base.apk")
    gen = os.path.join(BUILD, "gen")
    os.makedirs(gen, exist_ok=True)
    run([AAPT2, "link", "-o", base, "-I", ANDROID_JAR,
         "--java", gen,
         "--manifest", os.path.join(APP, "AndroidManifest.xml"),
         "--min-sdk-version", "26", "--target-sdk-version", "34",
         "--version-code", "1", "--version-name", "1.0",
         "--auto-add-overlay"] + flats)
    return base, gen


def add_dex(base_apk, dex_path):
    out = os.path.join(BUILD, "raw.apk")
    with zipfile.ZipFile(base_apk) as z:
        with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as o:
            for zi in z.infolist():
                o.writestr(zi, z.read(zi.filename))
            o.write(dex_path, "classes.dex")
    return out


def ensure_keystore():
    if os.path.exists(KEYSTORE):
        return
    run([KEYTOOL, "-genkeypair", "-keystore", KEYSTORE, "-alias", "daka",
         "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
         "-storepass", "android", "-keypass", "android",
         "-dname", "CN=Daka Reminder, O=DSH, C=CN"])


def sign(raw_apk):
    ensure_keystore()
    aligned = os.path.join(BUILD, "aligned.apk")
    out = os.path.join(BUILD, OUT_NAME)
    run([ZIPALIGN, "-f", "-p", "4", raw_apk, aligned])
    run([JAVA, "-jar", APKSIGNER, "sign", "--ks", KEYSTORE, "--ks-key-alias", "daka",
         "--ks-pass", "pass:android", "--key-pass", "pass:android",
         "--v1-signing-enabled", "false", "--v2-signing-enabled", "true",
         "--out", out, aligned])
    run([JAVA, "-jar", APKSIGNER, "verify", "--print-certs", out])
    print("APK:", out, os.path.getsize(out), "bytes")
    return out


def main():
    for name, path in (("javac", JAVAC), ("java", JAVA), ("keytool", KEYTOOL), ("aapt2", AAPT2),
                       ("d8.jar", D8JAR), ("zipalign", ZIPALIGN), ("apksigner.jar", APKSIGNER),
                       ("android.jar", ANDROID_JAR)):
        if not path:
            raise SystemExit("missing tool: " + name)
    base, gen = resources()
    classes = compile_java(gen)
    dex_path = dex(classes)
    raw = add_dex(base, dex_path)
    sign(raw)


if __name__ == "__main__":
    main()
