"""Process tables: t_hr_recruit_process, t_hr_resume_match, t_hr_search_log."""
from sqlalchemy import Column, BigInteger, String, Integer, DateTime, JSON, DECIMAL
from app.models.base import BaseModel


class RecruitProcess(BaseModel):
    __tablename__ = 't_hr_recruit_process'

    process_no = Column(String(32), unique=True, nullable=False, comment='流程编号')
    demand_id = Column(BigInteger, nullable=False, index=True, comment='所属需求ID')
    resume_id = Column(BigInteger, nullable=False, index=True, comment='外部简历ID')
    candidate_id = Column(BigInteger, nullable=False, index=True, comment='候选人ID')
    process_status = Column(Integer, nullable=False, default=0, comment='0待筛 1邀约 2一面 3二面 4淘汰 5待Offer 6接受 7放弃 8入职')


class ResumeMatch(BaseModel):
    __tablename__ = 't_hr_resume_match'

    resume_id = Column(BigInteger, nullable=False, index=True, comment='外部简历ID')
    demand_id = Column(BigInteger, nullable=False, index=True, comment='需求ID')
    match_score = Column(DECIMAL(4, 1), nullable=False, comment='岗位匹配核心分')
    score_detail = Column(JSON, nullable=True, comment='各维度打分明细')
    calculate_time = Column(DateTime, nullable=False, comment='打分时间')


class SearchLog(BaseModel):
    __tablename__ = 't_hr_search_log'

    demand_id = Column(BigInteger, nullable=False, comment='关联需求ID')
    search_type = Column(Integer, nullable=False, comment='1内部员工库 2外部简历库')
    search_at = Column(DateTime, nullable=False, comment='检索执行时间')
    match_total = Column(Integer, nullable=False, comment='合格匹配人数')
    remark = Column(String(512), nullable=True, comment='筛选条件备注')
