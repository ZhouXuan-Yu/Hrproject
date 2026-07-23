"""IAM base tables: t_core_dept, t_core_position, t_core_user."""
from sqlalchemy import Column, BigInteger, String, Integer, DateTime
from app.models.base import BaseModel


class IamDept(BaseModel):
    __tablename__ = 't_core_dept'

    dept_id = Column(BigInteger, nullable=False, comment='组织ID')
    dept_name = Column(String(100), nullable=False, comment='组织名称')
    parent_dept_id = Column(BigInteger, nullable=True, comment='上级组织ID')
    dept_path = Column(String(512), nullable=True, comment='组织路径')
    sort_num = Column(Integer, nullable=False, default=0, comment='排序号')
    status = Column(Integer, nullable=False, default=1, comment='状态: 1启用 0停用')


class IamPosition(BaseModel):
    __tablename__ = 't_core_position'

    position_id = Column(BigInteger, nullable=False, comment='岗位ID')
    position_name = Column(String(100), nullable=False, comment='岗位名称')
    dept_id = Column(BigInteger, nullable=True, comment='所属组织ID')
    status = Column(Integer, nullable=False, default=1, comment='状态: 1启用 0停用')


class IamUser(BaseModel):
    __tablename__ = 't_core_user'

    user_id = Column(BigInteger, nullable=False, comment='用户ID')
    username = Column(String(64), nullable=False, comment='登录用户名')
    real_name = Column(String(64), nullable=False, comment='真实姓名')
    dept_id = Column(BigInteger, nullable=True, comment='所属组织ID')
    position_id = Column(BigInteger, nullable=True, comment='岗位ID')
    role_code = Column(String(32), nullable=False, default='employee', comment='角色编码')
    email = Column(String(128), nullable=True, comment='邮箱')
    mobile = Column(String(20), nullable=True, comment='手机号')
    feishu_open_id = Column(String(64), nullable=True, comment='飞书open_id')
    status = Column(Integer, nullable=False, default=1, comment='状态: 1启用 0停用')


class RecruitApprovalIdentity(BaseModel):
    """Recruit demand approval identity mapping.

    管理员不写入这里：管理员是平台最高权限，审批时可越过任一身份，
    但审批意见会自动标记为管理员代审批。
    """
    __tablename__ = 't_hr_approval_identity'

    approve_level = Column(Integer, nullable=False, comment='审批层级 1部门负责人/2HR/3高管')
    identity_code = Column(String(32), nullable=False, comment='身份编码 dept_head/hr/executive')
    identity_name = Column(String(64), nullable=False, comment='身份名称')
    role_code = Column(String(32), nullable=False, comment='允许审批的登录角色')
    user_id = Column(BigInteger, nullable=True, comment='可选指定审批人，为空表示该角色均可审批')
    status = Column(Integer, nullable=False, default=1, comment='1启用 0停用')
