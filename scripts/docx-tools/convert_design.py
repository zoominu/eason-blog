"""把导出的设计文档纯文本转换为 Markdown，保留标题层级与表格。

输入：dump_docx.py 生成的 doc_dump.txt
输出：eason-blog/docs/PROJECT_DESIGN.md
"""
import re
import sys
from pathlib import Path

src = Path(sys.argv[1])
dst = Path(sys.argv[2])
lines = src.read_text(encoding="utf-8").splitlines()

out: list[str] = []
table: list[list[str]] | None = None
in_table = False
code_buffer: list[str] = []
first_heading_done = False

TABLE_START = "--- TABLE ---"
TABLE_END = "--- END TABLE ---"

# 架构图这类以 │ ├ └ ▼ 等制表符开头的连续行，放进代码块
BOX_CHARS = re.compile(r"^[\s│├└▼─┌┐┘┬┴┼]")
ARCH_HINT = re.compile(r"(Browser|Nginx|blog-server|blog-frontend|blog-admin|MySQL|Redis|File storage)")


def flush_code() -> None:
    global code_buffer
    if code_buffer:
        out.append("```text")
        out.extend(code_buffer)
        out.append("```")
        out.append("")
        code_buffer = []


def flush_table() -> None:
    global table, in_table
    if not table:
        in_table = False
        return
    header = table[0]
    out.append("| " + " | ".join(header) + " |")
    out.append("|" + "|".join([" --- "] * len(header)) + "|")
    for row in table[1:]:
        cells = (row + [""] * len(header))[: len(header)]
        out.append("| " + " | ".join(c.replace("|", "\\|") for c in cells) + " |")
    out.append("")
    table = None
    in_table = False


for raw in lines:
    line = raw.rstrip()
    stripped = line.strip()

    if stripped == TABLE_START:
        flush_code()
        in_table = True
        table = []
        continue
    if stripped == TABLE_END:
        flush_table()
        continue
    if in_table:
        assert table is not None
        if stripped:
            table.append([c.strip() for c in stripped.split("|")])
        continue

    if not stripped:
        flush_code()
        out.append("")
        continue

    m = re.match(r"^\[Heading (\d+)\]\s*(.+)$", stripped)
    if m:
        flush_code()
        level = min(int(m.group(1)) + 0, 6)
        out.append("#" * max(1, level) + " " + m.group(2))
        out.append("")
        first_heading_done = True
        continue

    # 文档开头的封面信息（标题/副标题/版本行）
    if not first_heading_done and not out:
        out.append("# " + stripped)
        out.append("")
        continue

    if BOX_CHARS.match(line) and ARCH_HINT.search(line):
        code_buffer.append(line)
        continue

    flush_code()
    out.append(stripped)
    out.append("")

flush_code()

text = "\n".join(out)
text = re.sub(r"\n{3,}", "\n\n", text).strip() + "\n"
dst.parent.mkdir(parents=True, exist_ok=True)
dst.write_text(text, encoding="utf-8")
print(f"written {dst} ({len(text)} chars)")
