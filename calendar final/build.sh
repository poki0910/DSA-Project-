#!/bin/bash
# ============================================================
# build.sh – Biên dịch và chạy Calendar App
# ============================================================

set -e

SRC="src"
OUT="out"
MAIN="CalendarFrontend"

echo "==> Tạo thư mục output..."
mkdir -p "$OUT"

echo "==> Biên dịch..."
find "$SRC" -name "*.java" | xargs javac -d "$OUT"

echo "==> Biên dịch thành công!"
echo "==> Chạy ứng dụng..."
java -cp "$OUT" "$MAIN"
