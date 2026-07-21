"""Interview tables: t_hr_interview_slot, t_hr_interview_book, t_hr_interview_record."""
from sqlalchemy import Column, BigInteger, String, Integer, DateTime, JSON, Text
from app.models.base import BaseModel


class InterviewSlot(BaseModel):
    __tablename__ = 't_hr_interview_slot'

    interviewer_id = Column(BigInteger, nullable=False, comment='面试官用户ID')
    demand_id = Column(BigInteger, nullable=True, comment='绑定需求ID NULL=通用')
    start_dt = Column(DateTime, nullable=False, comment='时段开始')
    end_dt = Column(DateTime, nullable=False, comment='时段结束')
    is_booked = Column(Integer, nullable=False, default=0, comment='0空闲 1占用')


class InterviewBook(BaseModel):
    __tablename__ = 't_hr_interview_book'

    demand_id = Column(BigInteger, nullable=False, comment='需求ID')
    resume_id = Column(BigInteger, nullable=False, comment='简历ID')
    process_id = Column(BigInteger, nullable=False, comment='流程ID')
    slot_id = Column(BigInteger, nullable=False, comment='时段ID')
    interview_round = Column(Integer, nullable=False, comment='1一面 2二面')
    interview_type = Column(Integer, nullable=False, comment='1飞书 2腾讯会议 3其他线上 4线下')
    meeting_code = Column(String(32), nullable=True, comment='会议号码')
    meeting_pwd = Column(String(16), nullable=True, comment='入会密码')
    meeting_url = Column(String(500), nullable=True, default='', comment='视频会议链接')
    address = Column(String(200), nullable=True, comment='线下地址')
    book_time = Column(DateTime, nullable=False, comment='预约操作时间')
    invite_json = Column(JSON, nullable=True, comment='邀约记录')


class InterviewRecord(BaseModel):
    __tablename__ = 't_hr_interview_record'

    book_id = Column(BigInteger, nullable=False, comment='关联预约单ID')
    process_id = Column(BigInteger, nullable=False, comment='流程ID')
    interviewer_ids = Column(JSON, nullable=False, comment='本场所有面试官ID')
    submit_interviewer_id = Column(BigInteger, nullable=False, comment='提交评价人ID')
    is_arrive = Column(Integer, nullable=False, default=0, comment='0未到 1已到')
    interview_result = Column(Integer, nullable=False, comment='0不通过 1通过')
    evaluate_text = Column(Text, nullable=True, comment='文字评价')
    score_json = Column(JSON, nullable=True, comment='多维度分项打分')
    audio_url = Column(String(255), nullable=True, comment='录音文件地址')
    end_time = Column(DateTime, nullable=True, comment='面试结束时间')
    feishu_memo_url = Column(String(512), nullable=True, comment='飞书妙记链接')
    highlight_json = Column(JSON, nullable=True, comment='AI关键问答')
    ai_draft_json = Column(JSON, nullable=True, comment='AI评价草稿')
