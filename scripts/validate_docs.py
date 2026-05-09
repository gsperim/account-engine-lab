#!/usr/bin/env python3
"""
validate_docs.py — Validação mecânica de consistência da documentação.

Verifica:
  1. Cabeçalho **Papéis:** presente em todos os documentos obrigatórios
  2. Âncoras de links existem nos arquivos de destino
  3. IDs D-xx referenciados estão declarados em drivers.md
  4. IDs P-xx referenciados estão declarados em principios.md
  5. Seções obrigatórias presentes em cada RF
  6. Estrutura obrigatória presente em cada ADR

Uso:
  python3 scripts/validate_docs.py
"""

import re
import sys
from pathlib import Path

ROOT = Path(__file__).parent.parent
DOCS_DIR = ROOT / "docs"
NEGOCIO_DIR = DOCS_DIR / "negocio"

errors: list[str] = []
warnings: list[str] = []


def err(file: str, msg: str) -> None:
    errors.append(f"  ERRO   {file}: {msg}")


def warn(file: str, msg: str) -> None:
    warnings.append(f"  AVISO  {file}: {msg}")


def strip_code_blocks(text: str) -> str:
    text = re.sub(r"```[\s\S]*?```", "", text)
    text = re.sub(r"`[^`\n]+`", "", text)
    return text


# ── 1. Cabeçalho **Papéis:** obrigatório ─────────────────────────────────────

DOCS_COM_PAPEL = [
    NEGOCIO_DIR / "drivers.md",
    NEGOCIO_DIR / "requisitos.md",
    NEGOCIO_DIR / "principios.md",
    NEGOCIO_DIR / "dominios.md",
    DOCS_DIR / "glossario.md",
    DOCS_DIR / "visao-executiva.md",
    DOCS_DIR / "stack.md",
    DOCS_DIR / "planejamento.md",
]

for path in DOCS_COM_PAPEL:
    if not path.exists():
        warn(path.name, "arquivo não encontrado")
        continue
    content = path.read_text(encoding="utf-8")
    if "**Papéis:**" not in content and "**Papel:**" not in content:
        err(path.name, "cabeçalho **Papéis:** ausente")


# ── 2. Coletar IDs declarados por arquivo ────────────────────────────────────

def slugify(text: str) -> str:
    """Gera slug compatível com MkDocs Material (Python-Markdown)."""
    text = text.lower()
    text = re.sub(r"[^\w\s-]", "", text)
    text = re.sub(r"\s+", "-", text)
    text = re.sub(r"-{2,}", "-", text)
    return text.strip("-")


def collect_ids(path: Path) -> set[str]:
    """Coleta todos os IDs navegáveis de um arquivo markdown."""
    ids: set[str] = set()
    content = path.read_text(encoding="utf-8")

    # IDs explícitos em headings: ### Título { #meu-id }
    for m in re.finditer(r"\{[^}]*#([\w-]+)[^}]*\}", content):
        ids.add(m.group(1))

    # IDs em spans: <span id="meu-id">
    for m in re.finditer(r'<span\s+id=["\']([^"\']+)["\']', content):
        ids.add(m.group(1))

    # Headings sem id explícito → slug automático
    for m in re.finditer(r'^#{1,6}\s+(.+?)(?:\s*\{[^}]*\})?\s*$', content, re.MULTILINE):
        raw = m.group(1).strip()
        ids.add(slugify(raw))

    return ids


# Índice: caminho relativo a DOCS_DIR → conjunto de IDs
file_ids: dict[str, set[str]] = {}
for md_path in DOCS_DIR.rglob("*.md"):
    rel = str(md_path.relative_to(DOCS_DIR))
    file_ids[rel] = collect_ids(md_path)


# ── 3. Validar links com âncoras ─────────────────────────────────────────────

LINK_RE = re.compile(r"\[([^\]]+)\]\(([^)#\s][^)]*#[^)]+|#[^)]+)\)")

for source_path in DOCS_DIR.rglob("*.md"):
    if not source_path.exists():
        continue
    raw = source_path.read_text(encoding="utf-8")
    content = strip_code_blocks(raw)
    source_rel = str(source_path.relative_to(DOCS_DIR))

    for m in LINK_RE.finditer(content):
        href = m.group(2)
        if "#" not in href:
            continue

        file_ref, anchor = href.split("#", 1)
        if not anchor:
            continue

        if file_ref == "":
            # âncora interna
            target_ids = file_ids.get(source_rel, set())
        else:
            target_path = (source_path.parent / file_ref).resolve()
            if not target_path.exists():
                err(source_path.name, f"arquivo de destino não existe: {file_ref}")
                continue
            target_rel = str(target_path.relative_to(DOCS_DIR))
            target_ids = file_ids.get(target_rel, set())

        if anchor not in target_ids:
            err(source_path.name, f"âncora não encontrada: #{anchor} → {file_ref or 'mesmo arquivo'}")


# ── 4. Consistência de IDs D-xx ──────────────────────────────────────────────

drivers_path = NEGOCIO_DIR / "drivers.md"
requisitos_path = NEGOCIO_DIR / "requisitos.md"
principios_path = NEGOCIO_DIR / "principios.md"

if drivers_path.exists():
    drivers_content = drivers_path.read_text(encoding="utf-8")
    declared_drivers = set(re.findall(r'id=["\']d-(\d+)["\']', drivers_content))

    for source_path in [requisitos_path, principios_path, NEGOCIO_DIR / "dominios.md"]:
        if not source_path.exists():
            continue
        content = strip_code_blocks(source_path.read_text(encoding="utf-8"))
        for d in re.findall(r"drivers\.md#d-(\d+)", content):
            if d not in declared_drivers:
                err(source_path.name, f"referencia D-{d} que não está declarado em drivers.md")


# ── 5. Consistência de IDs P-xx ──────────────────────────────────────────────

if principios_path.exists():
    princ_content = principios_path.read_text(encoding="utf-8")
    declared_principles = set(re.findall(r'id=["\']p-(\d+)["\']', princ_content))

    for source_path in DOCS_DIR.rglob("*.md"):
        if not source_path.exists():
            continue
        content = strip_code_blocks(source_path.read_text(encoding="utf-8"))
        for p in re.findall(r"principios\.md#p-(\d+)", content):
            if p not in declared_principles:
                err(source_path.name, f"referencia P-{p} que não está declarado em principios.md")


# ── 6. Seções obrigatórias nos RFs ───────────────────────────────────────────
# RF orientados a evento (ex: RF-04) usam "Trigger" em vez de "Campos de entrada".
# RFs que delegam explicitamente para outro RF (ex: RF-05 → RF-01) são isentos
# das seções de entrada e regras, mas mantêm "Critérios de aceite".

RF_ENTRADA = ["Campos de entrada", "Trigger"]   # qualquer um satisfaz
RF_REGRAS = "Regras de negócio"
RF_CRITERIOS = "Critérios de aceite"
RF_DELEGADO = "Detalhado como parte"             # RF que delega para outro

if requisitos_path.exists():
    req_content = requisitos_path.read_text(encoding="utf-8")
    for block in re.split(r"(?=### RF-\d+)", req_content):
        m = re.match(r"### (RF-\d+)", block)
        if not m:
            continue
        rf_id = m.group(1)
        delegado = RF_DELEGADO in block

        if not delegado:
            if not any(s in block for s in RF_ENTRADA):
                err("requisitos.md", f"{rf_id} não contém 'Campos de entrada' nem 'Trigger'")
            if RF_REGRAS not in block:
                err("requisitos.md", f"{rf_id} não contém a seção 'Regras de negócio'")

        if RF_CRITERIOS not in block:
            err("requisitos.md", f"{rf_id} não contém a seção 'Critérios de aceite'")


# ── 7. Estrutura obrigatória nos ADRs ────────────────────────────────────────

ADR_REQUIRED = ["## Contexto", "## Decisão", "## Consequências"]
adr_dir = DOCS_DIR / "adr"

if adr_dir.exists():
    for adr_path in sorted(adr_dir.glob("ADR-*.md")):
        content = adr_path.read_text(encoding="utf-8")
        for section in ADR_REQUIRED:
            if section not in content:
                err(adr_path.name, f"seção obrigatória ausente: '{section}'")


# ── 8. Tags obrigatórias nas páginas de conteúdo ────────────────────────────

FRONTMATTER_RE = re.compile(r"^---\n.*?\n---", re.DOTALL)

DOCS_COM_TAGS = [
    NEGOCIO_DIR / "drivers.md",
    NEGOCIO_DIR / "requisitos.md",
    NEGOCIO_DIR / "principios.md",
    NEGOCIO_DIR / "dominios.md",
    DOCS_DIR / "visao-executiva.md",
    DOCS_DIR / "glossario.md",
    DOCS_DIR / "stack.md",
    DOCS_DIR / "planejamento.md",
]

for path in DOCS_COM_TAGS:
    if not path.exists():
        continue
    content = path.read_text(encoding="utf-8")
    fm_match = FRONTMATTER_RE.match(content)
    if not fm_match or "tags:" not in fm_match.group(0):
        warn(path.name, "frontmatter tags: ausente — adicione ao menos uma tag")


# ── Resultado ─────────────────────────────────────────────────────────────────

print("\n── Validação de Documentação " + "─" * 45)

if warnings:
    print("\nAvisos:")
    for w in warnings:
        print(w)

if errors:
    print("\nErros:")
    for e in errors:
        print(e)
    print(f"\n✗ {len(errors)} erro(s) encontrado(s). Corrija antes de commitar.\n")
    sys.exit(1)

print(f"\n✓ Documentação consistente.{' (' + str(len(warnings)) + ' avisos)' if warnings else ''}\n")
sys.exit(0)
