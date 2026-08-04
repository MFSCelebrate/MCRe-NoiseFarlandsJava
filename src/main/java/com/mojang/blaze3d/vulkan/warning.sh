#!/bin/sh

OUTPUT="all_original.txt"
rm -f "$OUTPUT"

find . -type f -name "*.*" \( -not -name "warning.sh" \) | while read -r file; do
    # 用醒目的分隔标记代替原来的 @文件名
    printf "\n====== %s ======\n" "$file" >> "$OUTPUT"
    # 直接输出原始文件内容，不做任何压缩
    cat "$file" >> "$OUTPUT"
    # 每个文件内容结束后加一个换行，避免与下一个标记粘在一起
    echo "" >> "$OUTPUT"
done

echo "Original size: $(stat -c%s "$OUTPUT" 2>/dev/null || stat -f%z "$OUTPUT") bytes"

# 分割成 1.5MB 每块，并添加 .txt 后缀
split -b 1500000 "$OUTPUT" "source_part_"
for f in source_part_*; do
    mv "$f" "$f.txt"
done
echo "Split into:"
ls -lh source_part_*.txt

cat > merge_instructions.txt << 'EOF'
请按顺序上传以下 .txt 文件内容（每个文件单独发送）：
- source_part_aa.txt
- source_part_ab.txt
- source_part_ac.txt（如果有）

我会在收到后自动合并并分析。
EOF

echo "Done. Now upload the source_part_*.txt files."