"""Candidate tables: t_hr_candidate, t_hr_resume, t_hr_candidate_tag_rel."""
from sqlalchemy import Column, BigInteger, String, Integer, DateTime, Date, DECIMAL, JSON, Text
from app.models.base import BaseModel


class Candidate(BaseModel):
    __tablename__ = 't_hr_candidate'

    candidate_no = Column(String(32), unique=True, nullable=False, comment='候选人编号')
    candidate_name = Column(String(30), nullable=False, comment='候选人姓名')
    mobile = Column(String(20), nullable=True, comment='手机号(加密存储)')
    mobile_hash = Column(String(64), unique=True, nullable=True, comment='SHA256用于去重')
    email = Column(String(100), nullable=True, comment='邮箱')

    # Profile & scoring
    static_ability_score = Column(DECIMAL(4, 1), nullable=True, comment='静态画像分(0-100)')
    edu_level = Column(Integer, nullable=True, comment='学历: 1大专 2本科 3硕士 4博士')
    school_level = Column(Integer, nullable=True, comment='院校: 1普通 2-211 3-985 4-C9')
    work_years = Column(Integer, nullable=True, comment='工作年限')
    big_company_flag = Column(Integer, nullable=False, default=0, comment='大厂经历: 0否 1是')
    cert_count = Column(Integer, nullable=False, default=0, comment='证书数量')

    # Source & status
    source_channel = Column(String(32), nullable=True, comment='来源渠道 邮箱/Boss/猎聘/内推')
    status = Column(String(16), nullable=False, default='available', comment='available/locked/reserve/archived')

    # Blacklist
    black_flag = Column(Integer, nullable=False, default=0, comment='0正常 1黑名单')
    black_type = Column(Integer, nullable=True, comment='1简历造假 2多次爽约 3薪资虚高')
    black_add_at = Column(DateTime, nullable=True, comment='拉黑时间')

    # Note
    note = Column(String(512), nullable=True, comment='HR备注')


class Resume(BaseModel):
    __tablename__ = 't_hr_resume'

    candidate_id = Column(BigInteger, nullable=False, index=True, comment='关联候选人ID')
    resume_file_id = Column(BigInteger, nullable=True, index=True, comment='简历附件ID files.id')
    storage_time = Column(DateTime, nullable=False, comment='简历入库时间')
    base_score = Column(DECIMAL(4, 1), nullable=False, default=0, comment='时效分')
    work_exp_text = Column(Text, nullable=True, comment='工作经历摘要')
    extract_json = Column(JSON, nullable=True, comment='AI解析完整结构化数据')
    touch_json = Column(JSON, nullable=True, comment='触达/邀约/储备记录')
    source_channel_id = Column(BigInteger, nullable=True, index=True, comment='来源渠道ID')
    mail_account_id = Column(BigInteger, nullable=True, index=True, comment='采集邮箱ID')


class CandidateTagRel(BaseModel):
    __tablename__ = 't_hr_candidate_tag_rel'

    candidate_id = Column(BigInteger, nullable=False, index=True, comment='候选人ID')
    tag_id = Column(BigInteger, nullable=False, index=True, comment='标签ID')
    tag_source = Column(Integer, nullable=False, comment='1系统自动 2HR手动 3JD自动匹配')
    valid_end = Column(Date, nullable=True, comment='证书/技能过期时间')
