"""Core demand tables: t_hr_dept_hc, t_hr_recruit_demand, t_hr_demand_approval."""
from sqlalchemy import Column, BigInteger, String, Integer, Date, Text, JSON, DateTime, DECIMAL
from app.models.base import BaseModel


class DeptHC(BaseModel):
    __tablename__ = 't_hr_dept_hc'

    dept_id = Column(BigInteger, nullable=False, comment='部门ID')
    plan_year = Column(Integer, nullable=False, comment='编制年份')
    total_headcount = Column(Integer, nullable=False, comment='年度总编制')
    used_headcount = Column(Integer, nullable=False, default=0, comment='已占用')
    available_headcount = Column(Integer, nullable=False, default=0, comment='剩余可用')
    operation_json = Column(JSON, nullable=True, comment='编制变动流水快照')


class RecruitDemand(BaseModel):
    __tablename__ = 't_hr_recruit_demand'

    demand_no = Column(String(32), unique=True, nullable=False, comment='业务单号 DM2026070001')
    dept_id = Column(BigInteger, nullable=False, index=True, comment='发起部门ID')
    position_id = Column(BigInteger, nullable=False, index=True, comment='招聘岗位ID')
    recruit_type = Column(Integer, nullable=False, comment='1社招 2校招 3实习 4内推')
    plan_headcount = Column(Integer, nullable=False, comment='计划招聘人数')
    filled_count = Column(Integer, nullable=False, default=0, comment='已入职人数')
    expect_entry_date = Column(Date, nullable=True, comment='期望到岗日期')
    jd_content = Column(Text, nullable=True, comment='JD全文')
    edu_min = Column(String(64), nullable=True, comment='最低学历要求')
    exp_min = Column(Integer, nullable=True, comment='最低工作年限')
    work_city = Column(String(64), nullable=True, comment='工作城市')
    publishing_channels = Column(JSON, nullable=True, comment='发布渠道ID数组')
    demand_status = Column(Integer, nullable=False, default=0, comment='0草稿 1审批中 2通过 3驳回 4完结 5取消')
    cancel_at = Column(DateTime, nullable=True, comment='取消时间')
    cancel_reason = Column(String(512), nullable=True, comment='取消原因')
    audit_flow = Column(JSON, nullable=True, comment='审批节点快照')
    headcount_reserve_json = Column(JSON, nullable=True, comment='HC占用快照')
    creator_id = Column(BigInteger, nullable=False, comment='发起人用户ID')
    hr_owner_id = Column(BigInteger, nullable=True, comment='跟进HR用户ID')
    internal_searched = Column(Integer, nullable=False, default=0, comment='是否已检索内部员工')
    resume_searched = Column(Integer, nullable=False, default=0, comment='是否已检索外部简历库')
    approved_at = Column(DateTime, nullable=True, comment='审批通过时间')
    closed_at = Column(DateTime, nullable=True, comment='需求关闭时间')
    is_internal_given_up = Column(Integer, nullable=False, default=0, comment='是否放弃内部人才')
    recommend_limit = Column(Integer, nullable=True, comment='推荐人数上限 NULL=用全局')
    salary_range = Column(String(64), nullable=True, comment='薪资范围')
    urgency = Column(String(16), nullable=False, default='normal', comment='紧急度 very/high/normal')
    required_skills = Column(JSON, nullable=True, comment='必备技能列表')
    plus_skills = Column(JSON, nullable=True, comment='加分技能列表')
    position_name = Column(String(128), nullable=True, comment='岗位名称（前端文本直存）')
    dept_name = Column(String(64), nullable=True, comment='部门名称（前端文本直存）')


class DemandApproval(BaseModel):
    __tablename__ = 't_hr_demand_approval'

    demand_id = Column(BigInteger, nullable=False, index=True, comment='关联需求ID')
    approve_user_id = Column(BigInteger, nullable=True, comment='审批人用户ID')
    approve_level = Column(Integer, nullable=True, comment='审批层级 1/2/3')
    approve_result = Column(Integer, nullable=False, comment='1待审批 2通过 3驳回')
    approve_opinion = Column(String(512), nullable=True, comment='审批意见')
    approve_time = Column(DateTime, nullable=True, comment='审批操作时间')
