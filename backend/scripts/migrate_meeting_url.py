"""Idempotent migration: add meeting_url column to t_hr_interview_book.

Run from the backend/ directory:

    .venv/Scripts/python scripts/migrate_meeting_url.py
"""
import os
import sys
from urllib.parse import urlparse

from dotenv import load_dotenv
from sqlalchemy import create_engine, text

TABLE = 't_hr_interview_book'
COLUMN = 'meeting_url'


def main():
    backend_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    load_dotenv(os.path.join(backend_dir, '.env'))
    database_url = os.getenv('DATABASE_URL', '').strip()
    if not database_url:
        print("[migrate] DATABASE_URL is required", file=sys.stderr)
        sys.exit(1)
    if database_url.startswith('sqlite'):
        print("[migrate] SQLite is disabled; set DATABASE_URL to MySQL/MariaDB", file=sys.stderr)
        sys.exit(1)

    parsed = urlparse(database_url)
    schema = (parsed.path or '').lstrip('/').split('/')[0]
    if not schema:
        print("[migrate] DATABASE_URL must include a database name", file=sys.stderr)
        sys.exit(1)

    engine = create_engine(database_url)
    with engine.begin() as conn:
        rows = conn.execute(text("""
            SELECT COLUMN_NAME
            FROM INFORMATION_SCHEMA.COLUMNS
            WHERE TABLE_SCHEMA = :schema AND TABLE_NAME = :table
        """), {'schema': schema, 'table': TABLE}).fetchall()
        columns = [row[0] for row in rows]
        if COLUMN in columns:
            print(f"[migrate] column '{COLUMN}' already exists on {TABLE} — nothing to do")
            return
        conn.execute(text(f"ALTER TABLE {TABLE} ADD COLUMN {COLUMN} VARCHAR(500) DEFAULT ''"))
        print(f"[migrate] added column '{COLUMN}' to {TABLE}")


if __name__ == '__main__':
    main()
