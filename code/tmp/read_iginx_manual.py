import sys
path = r"E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code/IGinX-Server-0.8.0/用户手册-v0.8.0.pdf"
keywords = ["分片", "fragment", "存储引擎", "storage", "元数据", "zookeeper", "data_prefix", "schema_prefix", "has_data"]
try:
    import pypdf
    reader = pypdf.PdfReader(path)
except Exception:
    try:
        import PyPDF2 as pypdf
        reader = pypdf.PdfReader(path)
    except Exception as e:
        with open(r"E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code/tmp/manual_extract.txt", "w", encoding="utf-8") as f:
            f.write("ERR_NO_PDF_LIB " + str(e))
        sys.exit(0)

out = []
for i, page in enumerate(reader.pages):
    try:
        text = page.extract_text() or ""
    except Exception:
        continue
    if not any(k.lower() in text.lower() for k in keywords):
        continue
    lines = [line.strip() for line in text.splitlines() if line.strip()]
    matched = []
    for line in lines:
        lower = line.lower()
        if any(k.lower() in lower for k in keywords):
            matched.append(line)
    if matched:
        out.append(f"--- page {i+1} ---")
        out.extend(matched[:80])
        out.append("--- end ---")

with open(r"E:/毕设/基于IGinX的数据与模型智能关联管理系统设计与实现/code/tmp/manual_extract.txt", "w", encoding="utf-8") as f:
    f.write("\n".join(out))
