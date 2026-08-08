# -*- coding: utf-8 -*-
"""Drop ALL tables and recreate from SQLAlchemy metadata.

DANGER — this wipes the entire database. Set FORCE_REBUILD=true to run.
"""
import os
import sys
from pathlib import Path

BACKEND = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(BACKEND))

if os.environ.get('FORCE_REBUILD', '').lower() not in ('true', '1', 'yes'):
    print("DANGER: This script drops ALL tables and recreates them.")
    print("Set FORCE_REBUILD=true to confirm.")
    sys.exit(1)

from sqlalchemy import text

from app import create_app
from app.extensions import db


def main() -> None:
    import app.models  # noqa: F401
    flask_app = create_app()
    with flask_app.app_context():
        engine = db.engine
        with engine.connect() as conn:
            conn.execute(text("SET FOREIGN_KEY_CHECKS=0"))
            rows = conn.execute(text("SHOW TABLES")).fetchall()
            for (tname,) in rows:
                conn.execute(text("DROP TABLE IF EXISTS `%s`" % tname))
                print("dropped:", tname)
            conn.execute(text("SET FOREIGN_KEY_CHECKS=1"))
            conn.commit()
        db.create_all()
        remaining = sorted(db.metadata.tables.keys())
        print("\ncreated %d tables:" % len(remaining))
        for t in remaining:
            print("  ", t)


if __name__ == "__main__":
    main()
