"""Auxiliary tables: t_hr_recruit_mail_account, t_hr_chat_log, t_hr_notify_template, t_hr_audit_log."""
from sqlalchemy import Column, BigInteger, String, Integer, Text, DateTime
from app.models.base import BaseModel


class AiKnowledgeBase(BaseModel):
    """Company and recruiting context injected into AI workflows."""
    __tablename__ = 't_hr_ai_knowledge_base'

    company_name = Column(String(128), nullable=False, default='XX公司')
    industry = Column(String(128), nullable=True)
    website = Column(String(256), nullable=True)
    company_profile = Column(Text, nullable=True)
    hiring_principles = Column(Text, nullable=True)
    ai_context = Column(Text, nullable=True)
    status = Column(Integer, nullable=False, default=1)


class RecruitMailAccount(BaseModel):
    __tablename__ = 't_hr_recruit_mail_account'

    account_name = Column(String(64), nullable=False, comment='账号别名')
    email_address = Column(String(128), unique=True, nullable=False, comment='收简历邮箱地址')
    imap_host = Column(String(128), nullable=True, comment='IMAP服务器')
    imap_port = Column(Integer, nullable=True, comment='端口')
    owner_user_id = Column(BigInteger, nullable=True, comment='负责人HR')
    status = Column(Integer, nullable=False, default=1, comment='1启用 0停用')
    monitor_folder = Column(String(128), nullable=True, comment='监控文件夹 NULL=默认INBOX')
    # Extended fields for full email config support
    mail_type = Column(String(32), nullable=True, comment='邮箱类型: qq/163/gmail/corp/custom')
    sync_freq = Column(Integer, nullable=False, default=30, comment='同步周期(分钟)')
    password_encrypted = Column(String(256), nullable=True, comment='加密存储的密码/授权码')
    last_sync_time = Column(DateTime, nullable=True, comment='最近同步时间')


class MailLog(BaseModel):
    """系统外发邮件日志：哪个邮箱 → 发给谁 → 什么内容 → 成功与否。"""
    __tablename__ = 't_hr_mail_log'

    sender_account_id = Column(BigInteger, nullable=True, comment='发件邮箱账号ID')
    sender_email = Column(String(128), nullable=False, comment='发件邮箱地址')
    recipient = Column(String(256), nullable=False, comment='收件邮箱地址')
    subject = Column(String(256), nullable=False, comment='邮件主题')
    mail_type = Column(String(32), nullable=False, default='other', comment='invite面试邀请/offer录用/entry入职包/test测试/other其他')
    status = Column(Integer, nullable=False, default=1, comment='1成功 0失败')
    error_msg = Column(String(512), nullable=True, comment='失败原因')


class ChatLog(BaseModel):
    __tablename__ = 't_hr_chat_log'

    resume_id = Column(BigInteger, nullable=True, comment='简历ID')
    demand_id = Column(BigInteger, nullable=True, comment='需求ID')
    chat_type = Column(Integer, nullable=False, comment='1AI自动对话 2人工HR')
    chat_content = Column(Text, nullable=False, comment='对话内容')
    operate_time = Column(DateTime, nullable=False, comment='对话发生时间')


class NotifyTemplate(BaseModel):
    __tablename__ = 't_hr_notify_template'

    template_name = Column(String(128), nullable=False, comment='模板名称')
    template_type = Column(String(32), nullable=False, comment='类型: interview/offer/reject/remind')
    send_method = Column(String(64), nullable=True, comment='发送方式: 飞书/短信/邮件/组合')
    subject = Column(String(256), nullable=True, comment='消息标题')
    body = Column(Text, nullable=True, comment='模板正文')
    status = Column(Integer, nullable=False, default=1, comment='1启用 0停用')


class AuditLog(BaseModel):
    __tablename__ = 't_hr_audit_log'

    operator_name = Column(String(64), nullable=False, comment='操作人姓名')
    module = Column(String(32), nullable=False, comment='模块: demand/interview/candidate/mail/config')
    action = Column(String(64), nullable=False, comment='动作描述')
    detail = Column(String(512), nullable=True, comment='详情')
    operate_time = Column(DateTime, nullable=False, comment='操作时间')


class ApiKeyConfig(BaseModel):
    __tablename__ = 't_hr_api_key'

    key_name = Column(String(64), unique=True, nullable=False, comment='密钥标识: deepseek/feishu/dify')
    value_encrypted = Column(String(512), nullable=False, comment='AES-256-GCM 加密后的值')
    display_label = Column(String(64), nullable=True, comment='前端显示名称')
    status = Column(Integer, nullable=False, default=1, comment='1启用 0停用')
