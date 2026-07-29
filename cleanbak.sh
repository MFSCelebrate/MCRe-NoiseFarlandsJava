#!/bin/sh

# 目标目录（请根据实际路径调整，末尾斜杠可有可无）
TARGET_DIR="/storage/emulated/0/MinecraftDev/32bit-backup/26.2-Unobfuscated"

# 检查目录是否存在
if [ ! -d "$TARGET_DIR" ]; then
    echo "错误：目录不存在 -> $TARGET_DIR"
    exit 1
fi

# 可选：预览要删除的文件（取消下一行的注释即可启用）
# echo "以下文件将被删除："
# find "$TARGET_DIR" -type f -name "*.bak" -print

# 执行删除（请确保路径正确，建议先预览）
echo "正在删除 $TARGET_DIR 及其子目录下的所有 .bak 文件..."
find "$TARGET_DIR" -type f -name "*.bak" -delete

if [ $? -eq 0 ]; then
    echo "删除完成。"
else
    echo "删除过程中出现错误。"
    exit 1
fi