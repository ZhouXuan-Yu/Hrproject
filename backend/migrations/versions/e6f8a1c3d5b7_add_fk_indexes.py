"""Add database indexes on all foreign-key columns for query performance.

Revision ID: e6f8a1c3d5b7
Revises: c3d5e7f9a1b2
Create Date: 2026-08-05
"""
from alembic import op
import sqlalchemy as sa


revision = 'e6f8a1c3d5b7'
down_revision = 'c3d5e7f9a1b2'
branch_labels = None
depends_on = None

# All FK columns that need indexes: (table, column)
INDEXES = [
    # candidate
    ('t_hr_resume', 'candidate_id'),
    ('t_hr_resume', 'resume_file_id'),
    ('t_hr_resume', 'source_channel_id'),
    ('t_hr_resume', 'mail_account_id'),
    ('t_hr_candidate_tag_rel', 'candidate_id'),
    ('t_hr_candidate_tag_rel', 'tag_id'),
    # hire
    ('t_hr_offer', 'resume_id'),
    ('t_hr_offer', 'process_id'),
    ('t_hr_offer', 'demand_id'),
    ('t_hr_offer', 'last_interview_id'),
    ('t_hr_entry', 'event_id'),
    ('t_hr_entry', 'resume_id'),
    ('t_hr_entry', 'dept_id'),
    ('t_hr_offer_remind_log', 'offer_id'),
    # interview
    ('t_hr_interview_slot', 'interviewer_id'),
    ('t_hr_interview_slot', 'demand_id'),
    ('t_hr_interview_book', 'demand_id'),
    ('t_hr_interview_book', 'resume_id'),
    ('t_hr_interview_book', 'process_id'),
    ('t_hr_interview_book', 'slot_id'),
    ('t_hr_interview_record', 'book_id'),
    ('t_hr_interview_record', 'process_id'),
    # process
    ('t_hr_recruit_process', 'demand_id'),
    ('t_hr_recruit_process', 'resume_id'),
    ('t_hr_recruit_process', 'candidate_id'),
    ('t_hr_resume_match', 'resume_id'),
    ('t_hr_resume_match', 'demand_id'),
    # internal
    ('t_hr_employee', 'dept_id'),
    ('t_hr_employee', 'position_id'),
    ('t_hr_employee_tag_rel', 'employee_id'),
    ('t_hr_employee_tag_rel', 'tag_id'),
    ('t_hr_internal_match_log', 'demand_id'),
    ('t_hr_internal_match_log', 'employee_id'),
    # demand
    ('t_hr_recruit_demand', 'dept_id'),
    ('t_hr_recruit_demand', 'position_id'),
    ('t_hr_demand_approval', 'demand_id'),
    # iam
    ('t_core_user', 'dept_id'),
    ('t_core_user', 'position_id'),
    # auxiliary
    ('t_hr_recruit_mail_account', 'owner_user_id'),
]


def upgrade():
    for table, column in INDEXES:
        idx_name = f'idx_{table}_{column}'
        op.create_index(idx_name, table, [column])


def downgrade():
    for table, column in INDEXES:
        idx_name = f'idx_{table}_{column}'
        op.drop_index(idx_name, table_name=table)
