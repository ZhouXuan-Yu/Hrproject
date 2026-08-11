"""统一 ID 生成：岗位 / 部门，避免受脏数据（Snowflake 大数）影响。

当表中已有来自外部迁移的大数值 ID 时，max()+1 逻辑会追着大数走，
导致 JavaScript 精度溢出。此模块提供带上限保护的递增 ID 生成。
"""
from sqlalchemy import func

# 岗位/部门 ID 的正常上限：超过此值视为脏数据，回退到起点
MAX_SAFE_POSITION_ID = 99_999
MAX_SAFE_DEPT_ID = 99_999

# 起始值
POSITION_ID_START = 2000
DEPT_ID_START = 1000


def next_position_id(db) -> int:
    """生成下一个 position_id，从 2000 起步，+1 递增。

    如果当前 max(position_id) 是脏大数（> 99999），回退到 2000 重新开始。
    """
    from app.models.iam import IamPosition

    max_id = db.session.query(func.max(IamPosition.position_id)).filter(
        IamPosition.is_deleted == 0
    ).scalar()

    if max_id is None:
        return POSITION_ID_START + 1
    if max_id > MAX_SAFE_POSITION_ID:
        return POSITION_ID_START + 1
    return max_id + 1


def next_dept_id(db) -> int:
    """生成下一个 dept_id，从 1000 起步，+1 递增。

    如果当前 max(dept_id) 是脏大数（> 99999），回退到 1000 重新开始。
    """
    from app.models.iam import IamDept

    max_id = db.session.query(func.max(IamDept.dept_id)).filter(
        IamDept.is_deleted == 0
    ).scalar()

    if max_id is None:
        return DEPT_ID_START + 1
    if max_id > MAX_SAFE_DEPT_ID:
        return DEPT_ID_START + 1
    return max_id + 1


def next_position_no(db) -> str:
    """生成下一个岗位编号，格式 PO{YYYYMM}{seq:04d}，与 demand_no 逻辑一致。

    示例：PO2026080001, PO2026080002
    """
    from datetime import datetime, timezone
    from app.models.iam import IamPosition

    prefix = f"PO{datetime.now(timezone.utc).strftime('%Y%m')}"

    latest = db.session.query(IamPosition.position_no).filter(
        IamPosition.position_no.like(f'{prefix}%'),
        IamPosition.is_deleted == 0,
    ).order_by(IamPosition.position_no.desc()).first()

    seq = int(latest.position_no[-4:]) + 1 if latest and latest.position_no else 1
    return f"{prefix}{seq:04d}"
