#!/bin/bash
# 啟用 GitHub Actions CI 自動建置
# 步驟1：授權 workflow scope（會開瀏覽器，點 Authorize）
gh auth refresh -h github.com -s workflow
# 步驟2：推送 CI 設定（force add，因為 .gitignore 已排除）
git add -f .github/workflows/build.yml
git commit -m "ci: 加入 APK 自動建置 workflow"
git push
echo "✅ CI 已啟用：https://github.com/penink-WH/penink-vpn/actions"