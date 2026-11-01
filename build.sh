#!/usr/bin/env bash
# 一键构建：前端（有 Node 时）→ 同步静态资源 → 打包可执行 jar
#
# 用法：
#   bash build.sh
#   JAVA_HOME=/usr/lib/jvm/java-17 bash build.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

if ! java -version 2>&1 | grep -qE '"(1[7-9]|2[0-9])'; then
  echo "!! 检测到的 JDK 不是 17+，编译会失败。先切换 JAVA_HOME 到 JDK 17 再跑。"
  java -version 2>&1 | head -2
fi

if command -v node >/dev/null 2>&1; then
  echo "==> 1/3 构建前端"
  (cd frontend && npm install --no-audit --no-fund && npm run build)
  echo "==> 2/3 同步静态资源到后端"
  rm -rf backend/src/main/resources/static
  mkdir -p backend/src/main/resources/static
  cp -r frontend/dist/. backend/src/main/resources/static/
else
  echo "==> 未检测到 Node，跳过前端构建"
  echo "    使用仓库里已提交的 backend/src/main/resources/static 产物（如果改过前端，请装 Node 后重跑）"
fi

echo "==> 3/3 打包 jar"
(cd backend && mvn -B -DskipTests clean package)

echo
echo "产物：backend/target/word-match-0.1.0.jar"
echo "运行：java -jar backend/target/word-match-0.1.0.jar --server.port=8080"
