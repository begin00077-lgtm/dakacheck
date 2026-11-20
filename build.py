"""Build the 打卡提醒 APK offline: javac -> d8 -> aapt2 -> zipalign -> apksigner."""
import io
import os
import subprocess
import sys
import zipfile

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
sys.stderr.reconfigure(encoding="utf-8", errors="replace")

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.path.join(ROOT, "tools")
MODULE = "app"
if "--app" in sys.argv:
    MODULE = sys.argv[sys.argv.index("--app") + 1]
APP = os.path.join(ROOT, MODULE)
BUILD = os.path.join(APP, "build")
OUT_NAME = "dakawake.apk" if MODULE != "app" else "dakacheck.apk"


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
    # 类文件多时命令行会超长，改用 @argfile 传入
    argfile = os.path.join(BUILD, "d8_args.txt")
    with io.open(argfile, "w", encoding="utf-8", newline="\n") as fh:
        for c in classfiles:
            fh.write(c.replace("\\", "/") + "\n")
    run([JAVA, "-cp", D8JAR, "com.android.tools.r8.D8", "--min-api", "26",
         "--lib", ANDROID_JAR, "--output", out, "@" + argfile])
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


# 正式签名密钥：优先用发布密钥，找不到再退回 debug
RELEASE_KS = os.environ.get("DAKA_KS") or os.path.join(
    os.path.dirname(APP), "dakacheck-release", "signing", "dakacheck.p12")
RELEASE_ALIAS = os.environ.get("DAKA_ALIAS", "dakacheck")
RELEASE_STOREPASS = os.environ.get("DAKA_STOREPASS", "dkpass")
RELEASE_KEYPASS = os.environ.get("DAKA_KEYPASS", RELEASE_STOREPASS)


def ensure_keystore():
    if os.path.exists(KEYSTORE):
        return
    run([KEYTOOL, "-genkeypair", "-keystore", KEYSTORE, "-alias", "daka",
         "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
         "-storepass", "android", "-keypass", "android",
         "-dname", "CN=Daka Reminder, O=DSH, C=CN"])


def sign(raw_apk):
    aligned = os.path.join(BUILD, "aligned.apk")
    out = os.path.join(BUILD, OUT_NAME)
    run([ZIPALIGN, "-f", "-p", "4", raw_apk, aligned])

    if os.path.exists(RELEASE_KS):
        ks, alias = RELEASE_KS, RELEASE_ALIAS
        ksp, keyp = RELEASE_STOREPASS, RELEASE_KEYPASS
        print("signing with release key:", ks)
    else:
        ensure_keystore()
        ks, alias, ksp, keyp = KEYSTORE, "daka", "android", "android"
        print("WARNING: release keystore not found, falling back to debug key")

    run([JAVA, "-jar", APKSIGNER, "sign", "--ks", ks, "--ks-key-alias", alias,
         "--ks-pass", "pass:" + ksp, "--key-pass", "pass:" + keyp,
         "--v1-signing-enabled", "false", "--v2-signing-enabled", "true",
         "--v3-signing-enabled", "true",
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
