"""
一次性脚本：清理 t_core_position / t_core_dept 中的脏大数 ID，
重新编号为 2001/1001 起步的递增序列，同时更新所有外键引用。

运行方式：
  cd backend
  python scripts/cleanup_position_ids.py

安全措施：只改 position_id > 99999 或 dept_id > 99999 的记录。
"""
import sys
import os

# Add backend/ to path
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app import create_app
from app.extensions import db
from sqlalchemy import text

app = create_app('development')

# 外键引用映射（从 SHOW COLUMNS 实际查出）
FK_REFS = {
    'position_id': [
        ('t_core_user', 'position_id'),
        ('t_hr_recruit_demand', 'position_id'),
        ('t_hr_employee', 'position_id'),
        ('t_hr_entry', 'position_id'),
    ],
    'dept_id': [
        ('t_core_user', 'dept_id'),
        ('t_core_position', 'dept_id'),
        ('t_hr_recruit_demand', 'dept_id'),
        ('t_hr_dept_hc', 'dept_id'),
        ('t_hr_employee', 'dept_id'),
        ('t_hr_entry', 'dept_id'),
    ],
}

THRESHOLD = 99_999
POS_START = 2000
DEPT_START = 1000


def cleanup_table(table_name, id_col, start_value):
    """将 table_name 中 id_col > THRESHOLD 的记录重新编号。"""
    with app.app_context():
        # 找出所有脏 ID
        rows = db.session.execute(
            text(f"SELECT {id_col} FROM {table_name} WHERE {id_col} > :threshold AND is_deleted = 0 ORDER BY {id_col}"),
            {'threshold': THRESHOLD}
        ).fetchall()

        if not rows:
            print(f"[{table_name}] {id_col}: 没有脏数据，跳过")
            return

        old_ids = [row[0] for row in rows]
        print(f"[{table_name}] {id_col}: 找到 {len(old_ids)} 条脏数据")
        print(f"  旧 ID 范围: {old_ids[0]} ~ {old_ids[-1]}")

        # 找一个起始值：正常记录中最大的 ID
        max_normal = db.session.execute(
            text(f"SELECT COALESCE(MAX({id_col}), {start_value}) FROM {table_name} WHERE {id_col} <= :threshold"),
            {'threshold': THRESHOLD}
        ).scalar()
        next_id = max(max_normal, start_value) + 1
        print(f"  新 ID 从 {next_id} 开始")

        # 构建旧→新映射
        id_map = {}
        for old_id in old_ids:
            id_map[old_id] = next_id
            next_id += 1
        new_ids_end = next_id - 1
        print(f"  新 ID 范围: {list(id_map.values())[0]} ~ {new_ids_end}")

        # 1. 更新外键引用
        for ref_table, ref_col in FK_REFS.get(id_col, []):
            count = 0
            for old_id, new_id in id_map.items():
                result = db.session.execute(
                    text(f"UPDATE {ref_table} SET {ref_col} = :new_id WHERE {ref_col} = :old_id"),
                    {'new_id': new_id, 'old_id': old_id}
                )
                count += result.rowcount
            if count > 0:
                print(f"  [{ref_table}.{ref_col}] 更新了 {count} 行")

        # 2. 更新主表
        for old_id, new_id in id_map.items():
            db.session.execute(
                text(f"UPDATE {table_name} SET {id_col} = :new_id WHERE {id_col} = :old_id"),
                {'new_id': new_id, 'old_id': old_id}
            )

        db.session.commit()
        print(f"  ✓ 完成，共重编号 {len(id_map)} 条记录")


if __name__ == '__main__':
    print("=" * 60)
    print("清理脏 position_id / dept_id（> 99999 → 递增编号）")
    print("=" * 60)
    cleanup_table('t_core_position', 'position_id', POS_START)
    print()
    cleanup_table('t_core_dept', 'dept_id', DEPT_START)
    print()
    print("全部完成。建议重启 Flask 后验证。")
