"""Add password_hash column to t_core_user for login authentication.

Revision ID: c3d5e7f9a1b2
Revises: a1b2c3d4e5f6
Create Date: 2026-08-05
"""
from alembic import op
import sqlalchemy as sa


revision = 'c3d5e7f9a1b2'
down_revision = 'a1b2c3d4e5f6'
branch_labels = None
depends_on = None


def upgrade():
    op.add_column('t_core_user',
        sa.Column('password_hash', sa.String(128), nullable=True,
                  comment='sha256(password+salt) 密码哈希')
    )


def downgrade():
    op.drop_column('t_core_user', 'password_hash')
