#!/bin/bash
# penink VPN — 一鍵建置＋部署腳本
# 用途：build release APK → 放入網站目錄 → 部署到 Cloudflare Pages → 推 GitHub
set -e

cd "$(dirname "$0")"
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"

echo "==> [1/4] 建置 release APK"
gradle -p android assembleRelease --no-daemon -q

echo "==> [2/4] 複製 APK 到網站目錄"
cp android/app/build/outputs/apk/release/app-release.apk web/penink-vpn.apk
cp android/app/build/outputs/apk/release/app-release.apk release/penink-vpn.apk
ls -lh web/penink-vpn.apk

echo "==> [3/4] 部署到 Cloudflare Pages"
wrangler pages deploy web --project-name penink-vpn --branch main --commit-dirty=true

echo "==> [4/4] 推送到 GitHub"
git add -A
git commit -m "build: 更新 release APK ($(date '+%Y-%m-%d %H:%M'))" || echo "(無變更，略過 commit)"
git push -u origin main

open https://penink-vpn.pages.dev
echo "✅ 完成：https://penink-vpn.pages.dev"