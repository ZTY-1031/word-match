#!/usr/bin/env bash
# 一键部署：本地打包 → 上传 app.jar → 远端重启 systemd 服务
#
# 用法：
#   deploy/deploy.sh root@1.2.3.4
#   REMOTE_DIR=/srv/word-match deploy/deploy.sh root@1.2.3.4
#
# 前提：
#   1. 本地已装 Node 18+ / Maven / JDK 17
#   2. 服务器已按 docs/部署-云服务器.md 装好 JDK 17 和 systemd 服务
set -euo pipefail

TARGET="${1:-}"
if [ -z "$TARGET" ]; then
  echo "用法：$0 <user>@<服务器IP>   例如 $0 root@1.2.3.4"
  exit 1
fi

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
REMOTE_DIR="${REMOTE_DIR:-/opt/word-match}"
JAR="$ROOT/backend/target/word-match-0.1.0.jar"

# 构建要 JDK 17+，本机默认 java 可能是 1.8，这里兜一下
if [ -z "${JDK17_HOME:-}" ] && [ -x "E:/jdk17/bin/java" ]; then
  JDK17_HOME="E:/jdk17"
fi
if [ -n "${JDK17_HOME:-}" ]; then
  export JAVA_HOME="$JDK17_HOME"
fi

echo "==> 1/4 构建前端"
(cd "$ROOT/frontend" && npm run build)

echo "==> 2/4 同步静态资源到后端"
rm -rf "$ROOT/backend/src/main/resources/static"
mkdir -p "$ROOT/backend/src/main/resources/static"
cp -r "$ROOT/frontend/dist/." "$ROOT/backend/src/main/resources/static/"

echo "==> 3/4 打包 jar"
(cd "$ROOT/backend" && mvn -B -q -DskipTests clean package)
[ -f "$JAR" ] || { echo "打包失败：找不到 $JAR"; exit 1; }

echo "==> 4/4 上传并重启（$TARGET:$REMOTE_DIR）"
ssh "$TARGET" "mkdir -p $REMOTE_DIR"
# 先传成 .new 再替换，避免覆盖正在运行的文件
scp -q "$JAR" "$TARGET:$REMOTE_DIR/app.jar.new"
ssh "$TARGET" "cd $REMOTE_DIR && mv -f app.jar.new app.jar && \
  if systemctl list-unit-files 2>/dev/null | grep -q '^word-match.service'; then \
    systemctl restart word-match && sleep 3 && systemctl is-active word-match; \
  else \
    echo '⚠ 未检测到 word-match 服务，请先按文档安装 systemd 单元'; \
  fi"

echo "==> 健康检查"
ssh "$TARGET" "curl -s --max-time 5 http://127.0.0.1:8080/api/health || echo '（请求失败，用 journalctl -u word-match -n 50 看日志）'"
echo
echo "完成，入口：http://${TARGET#*@}:8080"
