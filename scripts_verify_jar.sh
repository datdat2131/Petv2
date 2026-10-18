#!/usr/bin/env bash
# Kiểm tra nội dung JAR: plugin.yml ở root + class main tồn tại + khai báo đúng.  Dùng: ./scripts_verify_jar.sh target/AstraxPetRPG-2.0.0.jar
set -euo pipefail
JAR="${1:-target/AstraxPetRPG-2.0.0.jar}"
[ -f "$JAR" ] || { echo "KHÔNG THẤY JAR: $JAR (chạy: mvn clean package)"; exit 2; }
LIST=$(unzip -Z1 "$JAR")
echo "$LIST" | grep -qx "plugin.yml" || { echo "LỖI: plugin.yml không nằm ở root JAR"; exit 1; }
MAIN=$(unzip -p "$JAR" plugin.yml | sed -n "s/^main:[[:space:]]*//p" | tr -d "\r")
echo "$LIST" | grep -qx "$(echo "$MAIN" | tr . /).class" || { echo "LỖI: không có class main $MAIN"; exit 1; }
for k in version api-version commands permissions; do unzip -p "$JAR" plugin.yml | grep -q "^$k:" || { echo "LỖI: plugin.yml thiếu $k"; exit 1; }; done
for f in config.yml pets.yml skills.yml eggs.yml equipment.yml; do echo "$LIST" | grep -qx "$f" || { echo "LỖI: thiếu $f"; exit 1; }; done
echo "OK: plugin.yml ở root, main=$MAIN tồn tại, version=$(unzip -p "$JAR" plugin.yml | sed -n "s/^version:[[:space:]]*//p")"
