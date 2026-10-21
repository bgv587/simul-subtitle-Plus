#!/usr/bin/env bash
# Qwen3-ASR 0.6B (int8) for the desktop app's optional backend (~940 MiB). The app picks it up from
# ./models/asr_qwen3 when run from the repo; tools/win-app/build.sh copies it into the packaged app if it is there.
#   tools/fetch-qwen3.sh
# Set HTTPS_PROXY if Hugging Face needs a proxy.
set -euo pipefail
cd "$(dirname "$0")/.."

QW=https://huggingface.co/csukuangfj2/sherpa-onnx-qwen3-asr-0.6B-int8-2026-03-25/resolve/main
DIR=models/asr_qwen3

# path in models/asr_qwen3, size, SHA-256 (the model repository's files)
MODELS=(
  "conv_frontend.onnx 44148281 d22dc4423e0940e49884e903d2ea2f7e5567c14fc1aed97e4e26d6b8f208ef9e"
  "encoder.int8.onnx 182491662 60748d3e6744a57c9c91e1b17424a6c2990567e8adceb0783940c03ed98fa9d9"
  "decoder.int8.onnx 755914231 4f6885be5959ae26af3089d38ee7972c5fafbeeb1cf8d5e76eab6d8b61ca5771"
  "tokenizer/vocab.json 2776833 ca10d7e9fb3ed18575dd1e277a2579c16d108e32f27439684afa0e10b1440910"
  "tokenizer/merges.txt 1671853 8831e4f1a044471340f7c0a83d7bd71306a5b867e95fd870f74d0c5308a904d5"
  "tokenizer/tokenizer_config.json 12487 4942d005604266809309cabc9f4e9cb89ce855d59b14681fdc0e1cc62ea26c4c"
)

sha() { if command -v shasum >/dev/null; then shasum -a 256 "$1"; else sha256sum "$1"; fi; }
ok() { [ "$(wc -c < "$1" | tr -d ' ')" = "$2" ] && [ "$(sha "$1" | cut -d' ' -f1)" = "$3" ]; }

mkdir -p "$DIR/tokenizer"
for m in "${MODELS[@]}"; do
  read -r path size sum <<< "$m"
  f="$DIR/$path"
  if [ -s "$f" ] && ok "$f" "$size" "$sum"; then continue; fi
  echo "↓ $f"
  curl -fL --retry 3 -o "$f.part" "$QW/$path"
  ok "$f.part" "$size" "$sum" || { echo "✗ $f: wrong size or checksum" >&2; exit 1; }
  mv "$f.part" "$f"
done
echo "Qwen3-ASR ready: $DIR ($(du -sh "$DIR" | cut -f1)); pick it in 设置 › 语音识别."
