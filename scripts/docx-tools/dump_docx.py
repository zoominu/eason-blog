import sys
from docx import Document
from docx.table import Table
from docx.text.paragraph import Paragraph


def iter_block_items(parent):
    from docx.oxml.ns import qn
    parent_elm = parent.element.body
    for child in parent_elm.iterchildren():
        if child.tag == qn('w:p'):
            yield Paragraph(child, parent)
        elif child.tag == qn('w:tbl'):
            yield Table(child, parent)


path = sys.argv[1]
out = sys.argv[2]
doc = Document(path)
lines = []
for block in iter_block_items(doc):
    if isinstance(block, Paragraph):
        text = block.text.strip()
        if not text:
            continue
        style = block.style.name if block.style is not None else ''
        if style.lower().startswith('heading') or style in ('Title', 'Subtitle'):
            lines.append(f"\n[{style}] {text}")
        else:
            lines.append(text)
    else:
        lines.append("\n--- TABLE ---")
        for row in block.rows:
            cells = []
            for cell in row.cells:
                cells.append(cell.text.replace('\n', ' / ').strip())
            lines.append(" | ".join(cells))
        lines.append("--- END TABLE ---\n")

with open(out, 'w', encoding='utf-8') as f:
    f.write("\n".join(lines))
print(f"paragraphs+tables dumped, chars={sum(len(l) for l in lines)}")
