#!/data/data/com.termux/files/usr/bin/bash

set -e

# ============================================================
# Android Project Build Script for Termux / ARM64
#
# 用途：
#   1. 檢查 Termux 必要套件
#   2. 設定 Android SDK
#   3. 設定 local.properties
#   4. 修正 gradle.properties
#   5. 強制 Android Gradle Plugin 使用 Termux ARM64 aapt2
#   6. 避開 Gradle daemon JVM / SystemInfo 問題
#   7. 執行 assembleDebug
#
# 使用：
#   chmod +x termux-build.sh
#   ./termux-build.sh
# ============================================================


# ------------------------------------------------------------
# 顏色
# ------------------------------------------------------------

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
CYAN='\033[0;36m'
NC='\033[0m'


info() {
    echo -e "${CYAN}[INFO]${NC} $1"
}

ok() {
    echo -e "${GREEN}[OK]${NC} $1"
}

warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

error() {
    echo -e "${RED}[ERROR]${NC} $1"
}


# ------------------------------------------------------------
# 基本設定
# ------------------------------------------------------------

PROJECT_DIR="$(pwd)"
ANDROID_HOME="$HOME/android-sdk"
ANDROID_SDK_ROOT="$ANDROID_HOME"

TERMUX_AAPT2="$PREFIX/bin/aapt2"

GRADLE_PROPERTIES="$PROJECT_DIR/gradle.properties"
LOCAL_PROPERTIES="$PROJECT_DIR/local.properties"

DAEMON_JVM_PROPERTIES="$PROJECT_DIR/gradle/gradle-daemon-jvm.properties"
DAEMON_JVM_BACKUP="$PROJECT_DIR/gradle/gradle-daemon-jvm.properties.bak"


echo
echo "============================================================"
echo " Android Termux ARM64 Build"
echo "============================================================"
echo

info "Project:"
echo "  $PROJECT_DIR"

info "Android SDK:"
echo "  $ANDROID_HOME"

echo


# ============================================================
# [1/10] 確認 Android 專案
# ============================================================

echo
echo "[1/10] Checking Android project..."

if [ ! -f "$PROJECT_DIR/settings.gradle.kts" ] && \
   [ ! -f "$PROJECT_DIR/settings.gradle" ]; then

    error "目前目錄看起來不是 Android Gradle 專案。"
    echo
    echo "請先進入專案，例如："
    echo
    echo "  cd ~/gesture-test"
    echo
    echo "再執行："
    echo
    echo "  ./termux-build.sh"
    exit 1
fi

ok "Android project detected."


# ============================================================
# [2/10] 安裝 / 檢查 Termux 套件
# ============================================================

echo
echo "[2/10] Checking Termux packages..."

REQUIRED_PACKAGES=(
    openjdk-21
    gradle
    android-tools
    aapt
    apksigner
    wget
    unzip
)

for pkg in "${REQUIRED_PACKAGES[@]}"; do

    if dpkg -s "$pkg" >/dev/null 2>&1; then
        ok "$pkg"
    else
        warn "$pkg is missing. Installing..."
        pkg install -y "$pkg"
    fi

done


# ============================================================
# [3/10] 確認 Java / Gradle
# ============================================================

echo
echo "[3/10] Checking Java / Gradle..."

if ! command -v java >/dev/null 2>&1; then
    error "java not found."
    exit 1
fi

if ! command -v gradle >/dev/null 2>&1; then
    error "gradle not found."
    exit 1
fi

echo
java -version
echo
gradle --version

ok "Java / Gradle available."


# ============================================================
# [4/10] 設定 Android SDK 環境
# ============================================================

echo
echo "[4/10] Configuring Android SDK environment..."

mkdir -p "$ANDROID_HOME"
mkdir -p "$ANDROID_HOME/platforms"
mkdir -p "$ANDROID_HOME/build-tools"

export ANDROID_HOME
export ANDROID_SDK_ROOT

export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
export PATH="$ANDROID_HOME/platform-tools:$PATH"

ok "ANDROID_HOME=$ANDROID_HOME"
ok "ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"


# ============================================================
# [5/10] 建立 local.properties
# ============================================================

echo
echo "[5/10] Writing local.properties..."

# 不使用 echo 直接串接，避免換行問題
cat > "$LOCAL_PROPERTIES" <<EOF
sdk.dir=$ANDROID_HOME
EOF

ok "local.properties:"

cat "$LOCAL_PROPERTIES"


# ============================================================
# [6/10] 檢查 ARM64 AAPT2
# ============================================================

echo
echo "[6/10] Checking Termux ARM64 aapt2..."

if [ ! -x "$TERMUX_AAPT2" ]; then
    error "找不到 Termux aapt2:"
    echo
    echo "  $TERMUX_AAPT2"
    echo
    echo "嘗試執行："
    echo
    echo "  pkg install aapt"
    exit 1
fi

ok "aapt2:"
echo "  $TERMUX_AAPT2"

echo
file "$TERMUX_AAPT2" || true

echo
aapt2 version || true


# ============================================================
# [7/10] 修正 gradle.properties
# ============================================================

echo
echo "[7/10] Configuring gradle.properties..."

touch "$GRADLE_PROPERTIES"


# ------------------------------------------------------------
# 先修復之前可能產生的錯誤：
#
# kotlin.code.style=officialandroid.aapt2FromMavenOverride=...
#
# 或其他黏在同一行的情況
# ------------------------------------------------------------

sed -i \
    's/kotlin\.code\.style=officialandroid\.aapt2FromMavenOverride=.*/kotlin.code.style=official/' \
    "$GRADLE_PROPERTIES"


# ------------------------------------------------------------
# 移除舊的 AAPT2 override
# ------------------------------------------------------------

sed -i \
    '/^[[:space:]]*android\.aapt2FromMavenOverride=/d' \
    "$GRADLE_PROPERTIES"


# ------------------------------------------------------------
# 確保 kotlin.code.style 正確
# ------------------------------------------------------------

if grep -q '^kotlin\.code\.style=' "$GRADLE_PROPERTIES"; then

    sed -i \
        's/^kotlin\.code\.style=.*/kotlin.code.style=official/' \
        "$GRADLE_PROPERTIES"

else

    printf '\nkotlin.code.style=official\n' \
        >> "$GRADLE_PROPERTIES"

fi


# ------------------------------------------------------------
# 最重要：
#
# 強制 AGP 使用 Termux 的 ARM64 aapt2
#
# 不然 Gradle Maven 下載的是：
#
# aapt2-xxx-linux/aapt2
#
# 那個通常是 x86_64 Linux binary，
# Android ARM64 / Termux 無法直接執行。
# ------------------------------------------------------------

printf '\nandroid.aapt2FromMavenOverride=%s\n' \
    "$TERMUX_AAPT2" \
    >> "$GRADLE_PROPERTIES"


ok "gradle.properties configured."

echo
echo "Relevant properties:"
echo

grep -E \
    '^(kotlin\.code\.style|android\.aapt2FromMavenOverride)=' \
    "$GRADLE_PROPERTIES" || true


# ============================================================
# [8/10] Disable Gradle Daemon JVM Criteria
# ============================================================

echo
echo "[8/10] Checking Gradle daemon JVM configuration..."

# Termux / Android 上 Gradle 9 的 native integration 不完整。
#
# 如果存在：
#
# gradle/gradle-daemon-jvm.properties
#
# Gradle 可能在啟動階段碰到：
#
# Service 'SystemInfo' is not available
#
# 所以 Termux build 時先停用這個設定。

if [ -f "$DAEMON_JVM_PROPERTIES" ]; then

    warn "Found:"
    echo "  $DAEMON_JVM_PROPERTIES"

    if [ -f "$DAEMON_JVM_BACKUP" ]; then
        rm -f "$DAEMON_JVM_BACKUP"
    fi

    mv \
        "$DAEMON_JVM_PROPERTIES" \
        "$DAEMON_JVM_BACKUP"

    ok "Disabled daemon JVM configuration:"
    echo "  $DAEMON_JVM_BACKUP"

else

    ok "No active gradle-daemon-jvm.properties."

fi


# ============================================================
# [9/10] SDK / Project 診斷
# ============================================================

echo
echo "[9/10] Checking Android SDK configuration..."

if [ -f "$PROJECT_DIR/app/build.gradle.kts" ]; then

    echo
    grep -A 8 \
        "compileSdk" \
        "$PROJECT_DIR/app/build.gradle.kts" \
        | head -20 \
        || true

fi


echo
info "Installed platforms:"

if [ -d "$ANDROID_HOME/platforms" ]; then
    ls "$ANDROID_HOME/platforms" || true
fi


echo
info "Installed android.jar:"

find "$ANDROID_HOME/platforms" \
    -maxdepth 2 \
    -name android.jar \
    -print \
    2>/dev/null \
    || true


# ============================================================
# [10/10] Build APK
# ============================================================

echo
echo "[10/10] Building Debug APK..."

echo

# 防止之前的 daemon 干擾
gradle --stop || true

echo
echo "============================================================"
echo " Starting Gradle Build"
echo "============================================================"
echo


# ------------------------------------------------------------
# 使用 --no-daemon
#
# 這裡不要直接：
#
# gradle assembleDebug
#
# Termux / Android 上 Gradle daemon/native integration
# 容易出現 SystemInfo 問題。
# ------------------------------------------------------------

if gradle assembleDebug --no-daemon; then

    echo
    echo "============================================================"
    echo -e "${GREEN} BUILD SUCCESSFUL${NC}"
    echo "============================================================"

else

    echo
    echo "============================================================"
    echo -e "${RED} BUILD FAILED${NC}"
    echo "============================================================"

    echo
    echo "如果錯誤包含："
    echo
    echo "  Service 'SystemInfo' is not available"
    echo
    echo "請確認："
    echo
    echo "  gradle/gradle-daemon-jvm.properties"
    echo
    echo "已經不存在。"
    echo

    exit 1

fi


# ============================================================
# 找 APK
# ============================================================

echo
info "Searching generated APK..."

APK_FILE="$PROJECT_DIR/app/build/outputs/apk/debug/app-debug.apk"

if [ -f "$APK_FILE" ]; then

    echo
    ok "APK generated:"
    echo
    echo "  $APK_FILE"
    echo

    ls -lh "$APK_FILE"

else

    warn "Default app-debug.apk not found."

    echo
    echo "Available APK files:"
    echo

    find "$PROJECT_DIR" \
        -type f \
        -name "*.apk" \
        -print \
        2>/dev/null \
        || true

fi


echo
echo "============================================================"
echo " Done"
echo "============================================================"

APK="$HOME/gesture-test/app/build/outputs/apk/debug/app-debug.apk"

if [ -f "$APK" ]; then
    echo "[OK] APK generated:"
    echo "$APK"

    if [ -d "$HOME/storage/downloads" ]; then
        cp "$APK" "$HOME/storage/downloads/gesture-test-debug.apk"

        echo "[OK] APK copied to:"
        echo "$HOME/storage/downloads/gesture-test-debug.apk"
    fi
fi
