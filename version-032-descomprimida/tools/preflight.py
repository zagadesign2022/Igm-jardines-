#!/usr/bin/env python3
"""Lightweight source/schema preflight for IGM JARDINES (not an Android build)."""
from pathlib import Path
import re
import sqlite3

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

app_gradle = read("app/build.gradle.kts")
db_source = read("app/src/main/java/com/igmjardines/app/data/BusinessDatabase.kt")
backup_source = read("app/src/main/java/com/igmjardines/app/BackupManager.kt")
expense_source = read("app/src/main/java/com/igmjardines/app/ExpenseScreens.kt")
workflow = read(".github/workflows/android-apk.yml")

assert re.search(r"versionCode\s*=\s*32", app_gradle), "versionCode must be 32"
assert 'versionName = "0.32.0"' in app_gradle, "versionName must be 0.32.0"
assert "version = 13" in db_source, "Room schema must be version 13"
assert "fallbackToDestructiveMigration" not in db_source, "Destructive Room migration is forbidden"
assert "MIGRATION_12_13" in db_source, "Missing migration 12 -> 13"
assert "SHA-256" in backup_source and "IGM_BACKUP_V1" in backup_source, "Backup integrity manifest missing"
assert "version in 9..13" in backup_source, "Backup version compatibility check missing"
assert "copyResult.isFailure" in expense_source, "Receipt copy failure must block expense save"
assert "File(item.receiptPath).delete()" in expense_source, "Receipt cleanup on expense deletion missing"
assert 'gradle-version: \'8.9\'' in workflow, "CI Gradle version missing"
assert "assembleDebug" in workflow, "CI APK build step missing"

# Execute the exact SQL statements in the Room 12 -> 13 migration against a
# representative version-12 schema and verify both columns and indexes.
migration = db_source.split("private val MIGRATION_12_13", 1)[1].split("private val INSTANCE", 1)[0]
sql = re.findall(r'db\.execSQL\("([^"]+)"\)', migration)
assert len(sql) == 4, f"Expected 4 migration SQL statements, found {len(sql)}"
conn = sqlite3.connect(":memory:")
conn.execute("CREATE TABLE business_expenses (id INTEGER PRIMARY KEY, concept TEXT NOT NULL)")
conn.execute("CREATE TABLE travel_logs (id INTEGER PRIMARY KEY, clientName TEXT NOT NULL)")
for statement in sql:
    conn.execute(statement)
for table in ("business_expenses", "travel_logs"):
    columns = {row[1] for row in conn.execute(f"PRAGMA table_info({table})")}
    assert "workOrderId" in columns, f"{table}.workOrderId was not added"
    indexes = {row[1] for row in conn.execute(f"PRAGMA index_list({table})")}
    assert f"index_{table}_workOrderId" in indexes, f"Index missing for {table}.workOrderId"
conn.close()
print("Preflight OK: source invariants and Room 12 -> 13 migration SQL verified.")
print("Note: this does not replace Gradle compilation, Room KSP validation, or device tests.")
