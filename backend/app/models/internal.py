"""Internal talent tables: t_hr_employee, t_hr_employee_tag_rel, t_hr_internal_match_log."""
from sqlalchemy import Column, BigInteger, String, Integer, Date, DateTime, DECIMAL
from app.models.base import BaseModel


class Employee(BaseModel):
    __tablename__ = 't_hr_employee'

    user_id = Column(BigInteger, nullable=False, unique=True, comment='绑定登录账号ID')
    dept_id = Column(BigInteger, nullable=True, index=True, comment='当前部门ID')
    position_id = Column(BigInteger, nullable=True, index=True, comment='当前岗位ID')
    work_years = Column(Integer, nullable=True, comment='总工作年限')
    perf_score = Column(DECIMAL(3, 1), nullable=True, comment='绩效分')
    last_promote_date = Column(Date, nullable=True, comment='上次晋升时间')
    can_transfer = Column(Integer, nullable=False, default=0, comment='0不可调 1可调')
    compositive_score = Column(DECIMAL(4, 1), nullable=True, comment='综合评估分')
    transfer_restrict_reason = Column(String(256), nullable=True, comment='不可调岗原因')


class EmployeeTagRel(BaseModel):
    __tablename__ = 't_hr_employee_tag_rel'

    employee_id = Column(BigInteger, nullable=False, index=True, comment='员工ID')
    tag_id = Column(BigInteger, nullable=False, index=True, comment='标签ID')
    tag_source = Column(Integer, nullable=False, comment='1档案同步 2HR手动 3内部匹配生成')
    tag_related_score = Column(DECIMAL(3, 1), nullable=True, comment='标签附带绩效分')
    valid_end = Column(Date, nullable=True, comment='荣誉/临时标签过期时间')


class InternalMatchLog(BaseModel):
    __tablename__ = 't_hr_internal_match_log'

    match_no = Column(String(32), unique=True, nullable=False, comment='匹配流水编号')
    demand_id = Column(BigInteger, nullable=False, index=True, comment='需求ID')
    employee_id = Column(BigInteger, nullable=False, index=True, comment='员工ID')
    match_score = Column(DECIMAL(4, 1), nullable=True, comment='内部匹配分')
    match_status = Column(Integer, nullable=False, comment='10待确认 20已调配 30忽略')
    operator_user_id = Column(BigInteger, nullable=True, comment='操作HR')
    matched_at = Column(DateTime, nullable=False, comment='匹配时间')
