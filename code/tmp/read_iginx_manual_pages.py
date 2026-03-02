import sys
path = r"E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code/IGinX-Server-0.8.0/用户手册-v0.8.0.pdf"
page_numbers = [38,39,40,41,42,43,44,45]
try:
    import pypdf
    reader = pypdf.PdfReader(path)
except Exception:
    try:
        import PyPDF2 as pypdf
        reader = pypdf.PdfReader(path)
    except Exception as e:
        with open(r"E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code/tmp/manual_pages.txt", "w", encoding="utf-8") as f:
            f.write("ERR_NO_PDF_LIB " + str(e))
        sys.exit(0)

out = []
for p in page_numbers:
    idx = p - 1
    if idx < 0 or idx >= len(reader.pages):
        continue
    try:
        text = reader.pages[idx].extract_text() or ""
    except Exception:
        text = ""
    out.append(f"=== page {p} ===")
    out.append(text)
    out.append(f"=== end page {p} ===")

with open(r"E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code/tmp/manual_pages.txt", "w", encoding="utf-8") as f:
    f.write("\n".join(out))
