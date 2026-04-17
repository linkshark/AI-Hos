#!/usr/bin/env python3
"""One-shot importer for AiHos domestic Chinese disease standards.

This script is intentionally destructive for the disease-standard layer only:
it drops the previous ICD-11 prototype tables and the new medical_* tables,
recreates the domestic schema, imports the local Chinese seed dataset, and
backfills knowledge document mappings from existing document titles/keywords.
"""

from __future__ import annotations

import json
import os
import re
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any
from zipfile import ZipFile

import pymysql

ROOT = Path(__file__).resolve().parents[1]
LOCAL_CONFIG = ROOT / "config" / "application-local.yml"
SEED_PATH = ROOT / "src" / "main" / "resources" / "medical-standards" / "domestic-disease-seed.json"
DOWNLOAD_DIR = ROOT / "tmp" / "medical-standards"
HUBEI_XLSX = DOWNLOAD_DIR / "hubei-icd-2.0-mapping.xlsx"
HUBEI_XLSX_URL = "https://wjw.hubei.gov.cn/bmdt/ywdt/yzyg/202102/P020210220555931947548.xlsx"
HUBEI_SOURCE_PAGE = "https://wjw.hubei.gov.cn/bmdt/ywdt/yzyg/202102/t20210220_3352242.shtml"
VERSION_TAG = "NHC-CLINICAL-2.0-HUBEI-2021"
CATEGORY_NAMES = {
    "A": "某些传染病和寄生虫病", "B": "某些传染病和寄生虫病", "C": "肿瘤", "D": "肿瘤及血液免疫疾病",
    "E": "内分泌、营养和代谢疾病", "F": "精神和行为障碍", "G": "神经系统疾病", "H": "眼和耳疾病",
    "I": "循环系统疾病", "J": "呼吸系统疾病", "K": "消化系统疾病", "L": "皮肤和皮下组织疾病",
    "M": "肌肉骨骼系统和结缔组织疾病", "N": "泌尿生殖系统疾病", "O": "妊娠、分娩和产褥期",
    "P": "起源于围生期的某些情况", "Q": "先天性畸形、变形和染色体异常", "R": "症状、体征和临床异常所见",
    "S": "损伤、中毒和外因的某些其他后果", "T": "损伤、中毒和外因的某些其他后果",
    "U": "特殊目的编码", "V": "疾病和死亡的外因", "W": "疾病和死亡的外因", "X": "疾病和死亡的外因",
    "Y": "疾病和死亡的外因", "Z": "影响健康状态和与保健机构接触的因素",
}


def read_local_config() -> str:
    return LOCAL_CONFIG.read_text(encoding="utf-8") if LOCAL_CONFIG.exists() else ""


def config_value(text: str, key: str, default: str = "") -> str:
    pattern = re.compile(rf"^\s*{re.escape(key)}:\s*(.+?)\s*$", re.MULTILINE)
    match = pattern.search(text)
    if not match:
        return default
    value = match.group(1).strip().strip("'\"")
    if value.startswith("${") and ":" in value and value.endswith("}"):
        env_key, env_default = value[2:-1].split(":", 1)
        return os.environ.get(env_key, env_default)
    return os.environ.get(value[2:-1], default) if value.startswith("${") and value.endswith("}") else value


def parse_jdbc_url(url: str) -> dict[str, Any]:
    match = re.search(r"jdbc:mysql://([^:/?]+)(?::(\d+))?/([^?]+)", url)
    if not match:
        raise SystemExit(f"Unsupported JDBC url: {url}")
    return {
        "host": match.group(1),
        "port": int(match.group(2) or 3306),
        "database": match.group(3),
    }


def connect():
    text = read_local_config()
    jdbc_url = os.environ.get("AIHOS_DB_URL") or config_value(text, "url")
    username = os.environ.get("AIHOS_DB_USERNAME") or config_value(text, "username")
    password = os.environ.get("AIHOS_DB_PASSWORD") or config_value(text, "password")
    parsed = parse_jdbc_url(jdbc_url)
    return pymysql.connect(
        host=parsed["host"],
        port=parsed["port"],
        user=username,
        password=password,
        database=parsed["database"],
        charset="utf8mb4",
        autocommit=False,
    )


def ensure_source_file() -> None:
    DOWNLOAD_DIR.mkdir(parents=True, exist_ok=True)
    if HUBEI_XLSX.exists() and HUBEI_XLSX.stat().st_size > 1024:
        return
    urllib.request.urlretrieve(HUBEI_XLSX_URL, HUBEI_XLSX)


def xlsx_shared_strings(zip_file: ZipFile) -> list[str]:
    ns = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
    if "xl/sharedStrings.xml" not in zip_file.namelist():
        return []
    root = ET.fromstring(zip_file.read("xl/sharedStrings.xml"))
    values: list[str] = []
    for item in root.findall("m:si", ns):
        values.append("".join(text.text or "" for text in item.iter("{http://schemas.openxmlformats.org/spreadsheetml/2006/main}t")))
    return values


def cell_index(cell_ref: str) -> int:
    letters = re.sub(r"[^A-Z]", "", cell_ref.upper())
    value = 0
    for char in letters:
        value = value * 26 + ord(char) - ord("A") + 1
    return max(0, value - 1)


def parse_xlsx_rows(path: Path, sheet_path: str = "xl/worksheets/sheet1.xml") -> list[list[str]]:
    ns = {"m": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
    with ZipFile(path) as zip_file:
        shared_strings = xlsx_shared_strings(zip_file)
        root = ET.fromstring(zip_file.read(sheet_path))
        rows: list[list[str]] = []
        for row in root.findall(".//m:row", ns):
            values: list[str] = []
            for cell in row.findall("m:c", ns):
                index = cell_index(cell.attrib.get("r", "A1"))
                while len(values) <= index:
                    values.append("")
                cell_type = cell.attrib.get("t")
                value_node = cell.find("m:v", ns)
                inline_node = cell.find("m:is/m:t", ns)
                if inline_node is not None:
                    values[index] = inline_node.text or ""
                elif value_node is not None:
                    raw_value = value_node.text or ""
                    values[index] = shared_strings[int(raw_value)] if cell_type == "s" and raw_value else raw_value
            rows.append(values)
        return rows


def category_for_code(code: str | None) -> tuple[str | None, str | None]:
    if not code:
        return None, None
    letter = code.strip()[:1].upper()
    return letter or None, CATEGORY_NAMES.get(letter)


def stable_concept_code(standard_code: str) -> str:
    return "CN-DIAG-" + re.sub(r"[^A-Za-z0-9]+", "_", standard_code).strip("_").upper()


def alias_variants(name: str) -> list[str]:
    if not name:
        return []
    variants = {name}
    cleaned = re.sub(r"[\\[\\]【】（）()]", " ", name)
    cleaned = re.sub(r"\\s+", " ", cleaned).strip()
    if cleaned and cleaned != name:
        variants.add(cleaned)
    for separator in ["，", ",", "；", ";", "、"]:
        if separator in name:
            first = name.split(separator, 1)[0].strip()
            if len(first) >= 2:
                variants.add(first)
    for pattern in [r"\[([^\]]+)\]", r"【([^】]+)】", r"（([^）]+)）", r"\(([^)]+)\)"]:
        for term in re.findall(pattern, name):
            if len(term.strip()) >= 2:
                variants.add(term.strip())
    return list(variants)


def load_authoritative_rows() -> list[dict[str, Any]]:
    ensure_source_file()
    rows = parse_xlsx_rows(HUBEI_XLSX)
    disease_rows: list[dict[str, Any]] = []
    seen_standard_codes: set[str] = set()
    seen_concept_codes: set[str] = set()
    for row in rows[1:]:
        standard_code = (row[0] if len(row) > 0 else "").strip()
        disease_name = (row[2] if len(row) > 2 else "").strip()
        nhsa_code = (row[3] if len(row) > 3 else "").strip() or None
        nhsa_name = (row[4] if len(row) > 4 else "").strip() or None
        if not standard_code or not disease_name:
            continue
        if standard_code in seen_standard_codes:
            continue
        seen_standard_codes.add(standard_code)
        category_code, category_name = category_for_code(standard_code)
        aliases = alias_variants(disease_name)
        if nhsa_name and nhsa_name != disease_name:
            aliases.extend(alias_variants(nhsa_name))
        aliases.extend([standard_code])
        if nhsa_code:
            aliases.append(nhsa_code)
        concept_code = stable_concept_code(standard_code)
        if concept_code in seen_concept_codes:
            suffix = 2
            while f"{concept_code}_{suffix}" in seen_concept_codes:
                suffix += 1
            concept_code = f"{concept_code}_{suffix}"
        seen_concept_codes.add(concept_code)
        disease_rows.append({
            "conceptCode": concept_code,
            "standardSystem": "NHC_CLINICAL_2_0",
            "standardCode": standard_code,
            "icd10Code": standard_code,
            "nhsaCode": nhsa_code,
            "diseaseName": disease_name,
            "categoryCode": category_code,
            "categoryName": category_name,
            "deptCode": "GENERAL",
            "aliases": list(dict.fromkeys(item for item in aliases if item)),
            "symptoms": [],
            "chiefComplaints": [],
            "weight": 0.8,
            "versionTag": VERSION_TAG,
            "sourcePage": HUBEI_SOURCE_PAGE,
            "sourceFile": HUBEI_XLSX_URL,
            "nhsaName": nhsa_name,
        })
    return disease_rows


def merge_seed_mappings(authoritative_rows: list[dict[str, Any]], seed_rows: list[dict[str, Any]]) -> list[dict[str, Any]]:
    by_code = {row["standardCode"]: row for row in authoritative_rows if row.get("standardCode")}
    by_name: dict[str, dict[str, Any]] = {}
    for row in authoritative_rows:
        for alias in row.get("aliases") or []:
            by_name.setdefault(alias, row)
    for seed in seed_rows:
        target = None
        for key in [seed.get("icd10Code"), seed.get("standardCode"), seed.get("diseaseName"), *(seed.get("aliases") or [])]:
            if key and not target:
                target = by_code.get(key) or by_name.get(key)
        if target is None:
            target = seed.copy()
            target["standardSystem"] = target.get("standardSystem") or "SEEDED"
            target["versionTag"] = target.get("versionTag") or "CN-SEED-2026-04"
            authoritative_rows.append(target)
        for field in ["aliases", "symptoms", "chiefComplaints"]:
            target[field] = list(dict.fromkeys([*(target.get(field) or []), *(seed.get(field) or [])]))
        target["weight"] = max(float(target.get("weight") or 0.8), float(seed.get("weight") or 0.8))
    return authoritative_rows


def chunks(values: list[tuple[Any, ...]], size: int):
    for start in range(0, len(values), size):
        yield values[start:start + size]


def drop_old_tables(cur) -> None:
    # 清掉上一版 WHO ICD-11 原型表、机械替换产生的临时表和当前 domestic 表。
    for table in [
        "icd11_sync_state",
        "icd11_doc_mapping",
        "icd11_symptom_mapping",
        "icd11_alias",
        "icd11_entity",
        "medicalStandard_sync_state",
        "medicalStandard_doc_mapping",
        "medicalStandard_symptom_mapping",
        "medicalStandard_alias",
        "medicalStandard_entity",
        "medical_doc_mapping",
        "medical_symptom_mapping",
        "medical_concept_alias",
        "medical_concept",
    ]:
        cur.execute(f"DROP TABLE IF EXISTS {table}")


def create_tables(cur) -> None:
    cur.execute(
        """
        CREATE TABLE medical_concept (
            id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
            concept_code VARCHAR(128) NOT NULL COMMENT '项目内统一疾病概念编码，全局唯一',
            standard_system VARCHAR(32) NOT NULL DEFAULT 'SEEDED' COMMENT '标准来源：NHC_CLINICAL / NHSA / ICD10_GB / HOSPITAL / SEEDED',
            standard_code VARCHAR(64) NULL COMMENT '原始标准编码，例如国家临床版、医保版或 ICD-10 编码',
            icd10_code VARCHAR(64) NULL COMMENT 'ICD-10 对照编码',
            nhsa_code VARCHAR(64) NULL COMMENT '医保疾病诊断分类与代码',
            icd11_uri VARCHAR(255) NULL COMMENT 'ICD-11 对照 URI，当前仅作扩展映射',
            disease_name VARCHAR(255) NOT NULL COMMENT '中文疾病名称',
            english_name VARCHAR(255) NULL COMMENT '英文名称或国际标准名称',
            category_code VARCHAR(64) NULL COMMENT '疾病分类编码',
            category_name VARCHAR(128) NULL COMMENT '疾病分类名称',
            parent_concept_code VARCHAR(128) NULL COMMENT '父级疾病概念编码',
            dept_code VARCHAR(64) NULL COMMENT '默认归属科室 deptCode',
            is_leaf TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否叶子概念',
            source VARCHAR(32) NOT NULL DEFAULT 'SEEDED' COMMENT '数据来源：SEEDED / ADMIN / IMPORTED / DOC_BACKFILL',
            version_tag VARCHAR(64) NOT NULL DEFAULT 'CN-SEED-2026-04' COMMENT '数据版本标识',
            status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / INACTIVE',
            raw_metadata_json LONGTEXT NULL COMMENT '原始导入数据或维护备注 JSON',
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
            PRIMARY KEY (id),
            UNIQUE KEY uk_medical_concept_code (concept_code),
            KEY idx_medical_concept_standard_code (standard_system, standard_code),
            KEY idx_medical_concept_icd10_code (icd10_code),
            KEY idx_medical_concept_name (disease_name),
            KEY idx_medical_concept_category (category_code),
            KEY idx_medical_concept_parent (parent_concept_code),
            KEY idx_medical_concept_dept (dept_code)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='疾病标准概念表'
        """
    )
    cur.execute(
        """
        CREATE TABLE medical_concept_alias (
            id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
            concept_code VARCHAR(128) NOT NULL COMMENT '对应 medical_concept.concept_code',
            alias VARCHAR(255) NOT NULL COMMENT '疾病别名、俗称、缩写或检索扩展词',
            alias_type VARCHAR(32) NOT NULL DEFAULT 'SEARCH' COMMENT '别名类型：OFFICIAL / SYNONYM / LAYMAN / ABBR / SEARCH',
            lang VARCHAR(16) NOT NULL DEFAULT 'zh' COMMENT '语言标记，默认 zh',
            source VARCHAR(32) NOT NULL DEFAULT 'SEEDED' COMMENT '来源：SEEDED / ADMIN / IMPORTED / DOC_BACKFILL',
            status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / INACTIVE',
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
            PRIMARY KEY (id),
            UNIQUE KEY uk_medical_alias_concept_alias (concept_code, alias),
            KEY idx_medical_alias_alias (alias),
            KEY idx_medical_alias_concept (concept_code)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='疾病标准别名表'
        """
    )
    cur.execute(
        """
        CREATE TABLE medical_symptom_mapping (
            id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
            concept_code VARCHAR(128) NOT NULL COMMENT '对应 medical_concept.concept_code',
            symptom_term VARCHAR(255) NULL COMMENT '症状词',
            chief_complaint_term VARCHAR(255) NULL COMMENT '主诉词',
            mapping_type VARCHAR(32) NOT NULL DEFAULT 'BOTH' COMMENT '映射类型：SYMPTOM / CHIEF_COMPLAINT / BOTH',
            weight DECIMAL(6,2) NOT NULL DEFAULT 1.00 COMMENT '命中权重',
            source VARCHAR(32) NOT NULL DEFAULT 'SEEDED' COMMENT '来源：SEEDED / ADMIN / IMPORTED / DOC_BACKFILL',
            enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
            PRIMARY KEY (id),
            UNIQUE KEY uk_medical_symptom_mapping (concept_code, symptom_term, chief_complaint_term),
            KEY idx_medical_symptom_concept (concept_code),
            KEY idx_medical_symptom_term (symptom_term),
            KEY idx_medical_complaint_term (chief_complaint_term)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='疾病症状主诉映射表'
        """
    )
    cur.execute(
        """
        CREATE TABLE medical_doc_mapping (
            id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键 ID',
            knowledge_hash VARCHAR(128) NOT NULL COMMENT '知识文档 hash，对应 knowledge_file_status.hash',
            concept_code VARCHAR(128) NOT NULL COMMENT '对应 medical_concept.concept_code',
            match_source VARCHAR(32) NOT NULL DEFAULT 'BACKFILL' COMMENT '映射来源：TITLE / KEYWORDS / ADMIN / BACKFILL',
            confidence DECIMAL(6,4) NOT NULL DEFAULT 0.5000 COMMENT '映射置信度，范围 0~1',
            created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
            updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
            PRIMARY KEY (id),
            UNIQUE KEY uk_medical_doc_mapping (knowledge_hash, concept_code),
            KEY idx_medical_doc_hash (knowledge_hash),
            KEY idx_medical_doc_concept (concept_code)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文档与疾病概念映射表'
        """
    )


def seed_concepts(cur, rows: list[dict[str, Any]]) -> tuple[int, int, int]:
    concept_values = []
    alias_values = []
    symptom_values = []
    for row in rows:
        concept_values.append(
            (
                row["conceptCode"], row.get("standardSystem", "SEEDED"), row.get("standardCode"),
                row.get("icd10Code"), row.get("nhsaCode"), row.get("icd11Uri"), row["diseaseName"],
                row.get("englishName"), row.get("categoryCode"), row.get("categoryName"),
                row.get("parentConceptCode"), row.get("deptCode") or "GENERAL", 1,
                row.get("source", "IMPORTED"), row.get("versionTag", VERSION_TAG), "ACTIVE",
                json.dumps(row, ensure_ascii=False)
            )
        )
        aliases = [row["diseaseName"], *(row.get("aliases") or [])]
        if row.get("standardCode"):
            aliases.append(row["standardCode"])
        if row.get("icd10Code"):
            aliases.append(row["icd10Code"])
        for alias in dict.fromkeys(item for item in aliases if item):
            alias_values.append(
                (row["conceptCode"], alias, "OFFICIAL" if alias == row["diseaseName"] else "SYNONYM", "zh",
                 row.get("source", "IMPORTED"), "ACTIVE")
            )
        for symptom in dict.fromkeys(row.get("symptoms") or []):
            symptom_values.append(
                (row["conceptCode"], symptom, None, "SYMPTOM", row.get("weight", 1.0), row.get("source", "SEEDED"), 1)
            )
        for complaint in dict.fromkeys(row.get("chiefComplaints") or []):
            symptom_values.append(
                (row["conceptCode"], None, complaint, "CHIEF_COMPLAINT", row.get("weight", 1.0), row.get("source", "SEEDED"), 1)
            )
    cur.executemany(
        """
        INSERT INTO medical_concept(
            concept_code, standard_system, standard_code, icd10_code, nhsa_code, icd11_uri,
            disease_name, english_name, category_code, category_name, parent_concept_code, dept_code,
            is_leaf, source, version_tag, status, raw_metadata_json
        ) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)
        """,
        concept_values,
    )
    for chunk in chunks(alias_values, 5000):
        cur.executemany(
            """
            INSERT IGNORE INTO medical_concept_alias(concept_code, alias, alias_type, lang, source, status)
            VALUES (%s,%s,%s,%s,%s,%s)
            """,
            chunk,
        )
    if symptom_values:
        cur.executemany(
            """
            INSERT IGNORE INTO medical_symptom_mapping(
                concept_code, symptom_term, chief_complaint_term, mapping_type, weight, source, enabled
            ) VALUES (%s,%s,%s,%s,%s,%s,%s)
            """,
            symptom_values,
        )
    return len(concept_values), len(alias_values), len(symptom_values)


def backfill_doc_mapping(cur) -> int:
    cur.execute(
        """
        SELECT e.concept_code, a.alias
        FROM medical_concept e
        JOIN medical_concept_alias a ON a.concept_code = e.concept_code AND a.status = 'ACTIVE'
        """
    )
    alias_rows = cur.fetchall()
    cur.execute(
        """
        SELECT hash, title, original_filename, keywords
        FROM knowledge_file_status
        """
    )
    docs = cur.fetchall()
    updated = 0
    for doc in docs:
        haystack = " ".join(str(value or "") for value in doc)
        seen: set[str] = set()
        for concept_code, alias in alias_rows:
            if not alias or alias not in haystack or concept_code in seen:
                continue
            cur.execute(
                """
                INSERT INTO medical_doc_mapping(knowledge_hash, concept_code, match_source, confidence)
                VALUES (%s,%s,'BACKFILL',0.8500)
                ON DUPLICATE KEY UPDATE match_source=VALUES(match_source),
                    confidence=GREATEST(confidence, VALUES(confidence)),
                    updated_at=CURRENT_TIMESTAMP
                """,
                (doc[0], concept_code),
            )
            updated += cur.rowcount
            seen.add(concept_code)
    return updated


def main() -> None:
    seed_rows = json.loads(SEED_PATH.read_text(encoding="utf-8"))
    concept_rows = merge_seed_mappings(load_authoritative_rows(), seed_rows)
    conn = connect()
    try:
        with conn.cursor() as cur:
            drop_old_tables(cur)
            create_tables(cur)
            concept_count, alias_count, mapping_count = seed_concepts(cur, concept_rows)
            doc_mapping_count = backfill_doc_mapping(cur)
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()
    print(json.dumps({
        "versionTag": VERSION_TAG,
        "conceptCount": concept_count,
        "aliasCount": alias_count,
        "symptomMappingCount": mapping_count,
        "docMappingAffectedRows": doc_mapping_count,
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
