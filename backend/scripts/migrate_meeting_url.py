"""Idempotent migration: add meeting_url column to t_hr_interview_book.

Run from the backend/ directory:

    .venv/Scripts/python scripts/migrate_meeting_url.py
"""
import os
import sqlite3
import sys

DB_PATH = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), 'hr_recruit.db')
TABLE = 't_hr_interview_book'
COLUMN = 'meeting_url'


def main():
    if not os.path.exists(DB_PATH):
        print(f"[migrate] DB not found: {DB_PATH}", file=sys.stderr)
        sys.exit(1)

    conn = sqlite3.connect(DB_PATH)
    try:
        cur = conn.execute(f"PRAGMA table_info({TABLE})")
        columns = [row[1] for row in cur.fetchall()]
        if COLUMN in columns:
            print(f"[migrate] column '{COLUMN}' already exists on {TABLE} — nothing to do")
            return
        conn.execute(f"ALTER TABLE {TABLE} ADD COLUMN {COLUMN} VARCHAR(500) DEFAULT ''")
        conn.commit()
        print(f"[migrate] added column '{COLUMN}' to {TABLE}")
    finally:
        conn.close()


if __name__ == '__main__':
    main()
