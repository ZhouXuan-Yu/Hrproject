"""Add missing tables and columns not covered by the initial migration.

Revision ID: a1b2c3d4e5f6
Revises: fb166b2c1092
Create Date: 2026-08-05

This migration adds:
  - 7 tables that exist in models but were missing from the initial migration:
    t_hr_ai_knowledge_base, t_hr_mail_log, t_hr_notify_template, t_hr_audit_log,
    t_hr_api_key, t_hr_offer_remind_log, t_hr_approval_identity
  - meeting_url column on t_hr_interview_book (previously added via standalone script)
  - Extended mail account columns on t_hr_recruit_mail_account
"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = 'a1b2c3d4e5f6'
down_revision = 'fb166b2c1092'
branch_labels = None
depends_on = None


def upgrade():
    # ── 1. t_hr_approval_identity ──────────────────────────────────────
    op.create_table('t_hr_approval_identity',
        sa.Column('approve_level', sa.Integer(), nullable=False, comment='审批层级 1部门负责人/2HR/3高管'),
        sa.Column('identity_code', sa.String(32), nullable=False, comment='身份编码 dept_head/hr/executive'),
        sa.Column('identity_name', sa.String(64), nullable=False, comment='身份名称'),
        sa.Column('role_code', sa.String(32), nullable=False, comment='允许审批的登录角色'),
        sa.Column('user_id', sa.BigInteger(), nullable=True, comment='可选指定审批人，为空表示该角色均可审批'),
        sa.Column('status', sa.Integer(), nullable=False, server_default='1', comment='1启用 0停用'),
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('created_at', sa.DateTime(), nullable=False),
        sa.Column('created_by', sa.BigInteger(), nullable=True),
        sa.Column('updated_at', sa.DateTime(), nullable=False),
        sa.Column('updated_by', sa.BigInteger(), nullable=True),
        sa.Column('is_deleted', sa.Integer(), nullable=False),
        sa.PrimaryKeyConstraint('id')
    )

    # ── 2. t_hr_api_key ───────────────────────────────────────────────
    op.create_table('t_hr_api_key',
        sa.Column('key_name', sa.String(64), nullable=False, comment='密钥标识: deepseek/feishu/dify'),
        sa.Column('value_encrypted', sa.String(512), nullable=False, comment='AES-256-GCM 加密后的值'),
        sa.Column('display_label', sa.String(64), nullable=True, comment='前端显示名称'),
        sa.Column('status', sa.Integer(), nullable=False, server_default='1', comment='1启用 0停用'),
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('created_at', sa.DateTime(), nullable=False),
        sa.Column('created_by', sa.BigInteger(), nullable=True),
        sa.Column('updated_at', sa.DateTime(), nullable=False),
        sa.Column('updated_by', sa.BigInteger(), nullable=True),
        sa.Column('is_deleted', sa.Integer(), nullable=False),
        sa.PrimaryKeyConstraint('id'),
        sa.UniqueConstraint('key_name')
    )

    # ── 3. t_hr_ai_knowledge_base ─────────────────────────────────────
    op.create_table('t_hr_ai_knowledge_base',
        sa.Column('company_name', sa.String(128), nullable=False, server_default="'XX公司'"),
        sa.Column('industry', sa.String(128), nullable=True),
        sa.Column('website', sa.String(256), nullable=True),
        sa.Column('company_profile', sa.Text(), nullable=True),
        sa.Column('hiring_principles', sa.Text(), nullable=True),
        sa.Column('ai_context', sa.Text(), nullable=True),
        sa.Column('status', sa.Integer(), nullable=False, server_default='1'),
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('created_at', sa.DateTime(), nullable=False),
        sa.Column('created_by', sa.BigInteger(), nullable=True),
        sa.Column('updated_at', sa.DateTime(), nullable=False),
        sa.Column('updated_by', sa.BigInteger(), nullable=True),
        sa.Column('is_deleted', sa.Integer(), nullable=False),
        sa.PrimaryKeyConstraint('id')
    )

    # ── 4. t_hr_offer_remind_log ──────────────────────────────────────
    op.create_table('t_hr_offer_remind_log',
        sa.Column('offer_id', sa.BigInteger(), nullable=False, comment='Offer主键ID'),
        sa.Column('offer_no', sa.String(32), nullable=False, comment='Offer编号'),
        sa.Column('remind_type', sa.String(16), nullable=False, server_default="'countdown'", comment='countdown倒计时提醒'),
        sa.Column('days_left', sa.Integer(), nullable=False, comment='发送时剩余天数'),
        sa.Column('sent_to', sa.String(128), nullable=True, comment='收件邮箱'),
        sa.Column('send_ok', sa.Integer(), nullable=False, server_default='0', comment='0失败 1成功'),
        sa.Column('send_msg', sa.String(255), nullable=True, comment='发送结果说明'),
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('created_at', sa.DateTime(), nullable=False),
        sa.Column('created_by', sa.BigInteger(), nullable=True),
        sa.Column('updated_at', sa.DateTime(), nullable=False),
        sa.Column('updated_by', sa.BigInteger(), nullable=True),
        sa.Column('is_deleted', sa.Integer(), nullable=False),
        sa.PrimaryKeyConstraint('id')
    )

    # ── 5. t_hr_mail_log ──────────────────────────────────────────────
    op.create_table('t_hr_mail_log',
        sa.Column('sender_account_id', sa.BigInteger(), nullable=True, comment='发件邮箱账号ID'),
        sa.Column('sender_email', sa.String(128), nullable=False, comment='发件邮箱地址'),
        sa.Column('recipient', sa.String(256), nullable=False, comment='收件邮箱地址'),
        sa.Column('subject', sa.String(256), nullable=False, comment='邮件主题'),
        sa.Column('mail_type', sa.String(32), nullable=False, server_default="'other'", comment='invite面试邀请/offer录用/entry入职包/test测试/other其他'),
        sa.Column('status', sa.Integer(), nullable=False, server_default='1', comment='1成功 0失败'),
        sa.Column('error_msg', sa.String(512), nullable=True, comment='失败原因'),
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('created_at', sa.DateTime(), nullable=False),
        sa.Column('created_by', sa.BigInteger(), nullable=True),
        sa.Column('updated_at', sa.DateTime(), nullable=False),
        sa.Column('updated_by', sa.BigInteger(), nullable=True),
        sa.Column('is_deleted', sa.Integer(), nullable=False),
        sa.PrimaryKeyConstraint('id')
    )

    # ── 6. t_hr_notify_template ───────────────────────────────────────
    op.create_table('t_hr_notify_template',
        sa.Column('template_name', sa.String(128), nullable=False, comment='模板名称'),
        sa.Column('template_type', sa.String(32), nullable=False, comment='类型: interview/offer/reject/remind'),
        sa.Column('send_method', sa.String(64), nullable=True, comment='发送方式: 飞书/短信/邮件/组合'),
        sa.Column('subject', sa.String(256), nullable=True, comment='消息标题'),
        sa.Column('body', sa.Text(), nullable=True, comment='模板正文'),
        sa.Column('status', sa.Integer(), nullable=False, server_default='1', comment='1启用 0停用'),
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('created_at', sa.DateTime(), nullable=False),
        sa.Column('created_by', sa.BigInteger(), nullable=True),
        sa.Column('updated_at', sa.DateTime(), nullable=False),
        sa.Column('updated_by', sa.BigInteger(), nullable=True),
        sa.Column('is_deleted', sa.Integer(), nullable=False),
        sa.PrimaryKeyConstraint('id')
    )

    # ── 7. t_hr_audit_log ─────────────────────────────────────────────
    op.create_table('t_hr_audit_log',
        sa.Column('operator_name', sa.String(64), nullable=False, comment='操作人姓名'),
        sa.Column('module', sa.String(32), nullable=False, comment='模块: demand/interview/candidate/mail/config'),
        sa.Column('action', sa.String(64), nullable=False, comment='动作描述'),
        sa.Column('detail', sa.String(512), nullable=True, comment='详情'),
        sa.Column('operate_time', sa.DateTime(), nullable=False, comment='操作时间'),
        sa.Column('id', sa.Integer(), nullable=False),
        sa.Column('created_at', sa.DateTime(), nullable=False),
        sa.Column('created_by', sa.BigInteger(), nullable=True),
        sa.Column('updated_at', sa.DateTime(), nullable=False),
        sa.Column('updated_by', sa.BigInteger(), nullable=True),
        sa.Column('is_deleted', sa.Integer(), nullable=False),
        sa.PrimaryKeyConstraint('id')
    )

    # ── 8. Add meeting_url to t_hr_interview_book ─────────────────────
    op.add_column('t_hr_interview_book',
        sa.Column('meeting_url', sa.String(500), nullable=True, server_default='', comment='视频会议链接')
    )

    # ── 9. Add extended mail account columns ──────────────────────────
    op.add_column('t_hr_recruit_mail_account',
        sa.Column('mail_type', sa.String(32), nullable=True, comment='邮箱类型: qq/163/gmail/corp/custom')
    )
    op.add_column('t_hr_recruit_mail_account',
        sa.Column('sync_freq', sa.Integer(), nullable=False, server_default='30', comment='同步周期(分钟)')
    )
    op.add_column('t_hr_recruit_mail_account',
        sa.Column('password_encrypted', sa.String(256), nullable=True, comment='加密存储的密码/授权码')
    )
    op.add_column('t_hr_recruit_mail_account',
        sa.Column('last_sync_time', sa.DateTime(), nullable=True, comment='最近同步时间')
    )


def downgrade():
    # Remove extended mail account columns
    op.drop_column('t_hr_recruit_mail_account', 'last_sync_time')
    op.drop_column('t_hr_recruit_mail_account', 'password_encrypted')
    op.drop_column('t_hr_recruit_mail_account', 'sync_freq')
    op.drop_column('t_hr_recruit_mail_account', 'mail_type')

    # Remove meeting_url
    op.drop_column('t_hr_interview_book', 'meeting_url')

    # Drop tables in reverse order
    op.drop_table('t_hr_audit_log')
    op.drop_table('t_hr_notify_template')
    op.drop_table('t_hr_mail_log')
    op.drop_table('t_hr_offer_remind_log')
    op.drop_table('t_hr_ai_knowledge_base')
    op.drop_table('t_hr_api_key')
    op.drop_table('t_hr_approval_identity')
