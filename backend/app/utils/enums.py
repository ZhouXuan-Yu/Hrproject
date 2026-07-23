"""Enums used across the application. Maps TINYINT codes to readable strings."""


class DemandStatus:
    DRAFT = 0
    APPROVAL = 1
    APPROVED = 2
    REJECTED = 3
    CLOSED = 4
    CANCELLED = 5

    LABELS = {0: '草稿', 1: '审批中', 2: '招聘中', 3: '已驳回', 4: '已关闭', 5: '已取消'}
    STYLE = {0: 'draft', 1: 'warn', 2: 'progress', 3: 'reject', 4: 'draft', 5: 'draft'}


class DemandUrgency:
    VERY = 'very'
    HIGH = 'high'
    NORMAL = 'normal'

    LABELS = {'very': '非常紧急', 'high': '紧急', 'normal': '普通'}
    STYLE = {'very': 'reject', 'high': 'warn', 'normal': 'draft'}


class ApprovalStatus:
    PENDING = 1
    APPROVED = 2
    REJECTED = 3

    LABELS = {1: '待审批', 2: '通过', 3: '驳回'}


class ApprovalNodeState:
    DONE = 'done'
    CURRENT = 'current'
    PENDING = 'pending'

    LABELS = {'done': '已通过', 'current': '进行中', 'pending': '待审批'}


class CandidateStatus:
    AVAILABLE = 'available'
    LOCKED = 'locked'
    RESERVE = 'reserve'
    ARCHIVED = 'archived'

    LABELS = {
        'available': '可联系',
        'locked': '面试中(锁定)',
        'reserve': '储备',
        'archived': '已封存',
    }


class ProcessStatus:
    PENDING = 0       # 待筛
    INVITE = 1        # 邀约
    FIRST_INT = 2     # 一面
    SECOND_INT = 3    # 二面
    REJECTED = 4      # 淘汰
    PENDING_OFFER = 5 # 待Offer
    ACCEPTED = 6      # 接受
    GIVEUP = 7        # 放弃
    ONBOARD = 8       # 入职

    LABELS = {
        0: '待筛选', 1: '邀约中', 2: '一面', 3: '二面',
        4: '已淘汰', 5: '待Offer', 6: '已接受', 7: '已放弃', 8: '已入职',
    }


class InterviewStatus:
    PENDING = 'pending'       # 待安排
    SCHEDULED = 'scheduled'   # 待面试
    EVALUATING = 'evaluating' # 待评价
    OFFER = 'offer'           # 待录用
    ONBOARD = 'onboard'       # 待入职
    DONE = 'done'             # 已入职

    LABELS = {
        'pending': '待安排', 'scheduled': '待面试', 'evaluating': '待评价',
        'offer': '待录用', 'onboard': '待入职', 'done': '已入职',
    }
    STYLE = {
        'pending': 'draft', 'scheduled': 'progress', 'evaluating': 'warn',
        'offer': 'done', 'onboard': 'done', 'done': 'done',
    }


class InterviewResult:
    FAIL = 0
    PASS = 1

    LABELS = {0: '不通过', 1: '通过'}


class RecruitType:
    SOCIAL = 1
    CAMPUS = 2
    INTERN = 3
    REFERRAL = 4

    LABELS = {1: '社招', 2: '校招', 3: '实习', 4: '内推'}


class ChannelType:
    OFFICIAL = 1
    THIRD_PARTY = 2
    REFERRAL = 3

    LABELS = {1: '官网', 2: '第三方', 3: '内推'}


class AlertType:
    REJECT = 'reject'
    WARN = 'warn'
    DONE = 'done'


class SourceType:
    DIRECT = 'direct'
    EXTERNAL = 'external'
    INTERNAL = 'internal'

    LABELS = {'direct': '直接投递', 'external': '人才库检索', 'internal': '内部员工'}


class HealthStatus:
    GOOD = 'good'
    WATCH = 'watch'
    RISK = 'risk'


# Role definitions
ROLES = {
    'admin': '管理员',
    'hr': 'HR专员',
    'interviewer': '面试官',
    'temp_interviewer': '临时面试官',
    'dept_head': '部门负责人',
    'executive': '高管',
    'employee': '基层员工',
    'no_recruit': '无权限员工',
}

# Menu access per role
ROLE_MENUS = {
    'admin': ['recruit-dashboard', 'recruit-demand', 'recruit-talent',
              'recruit-interview', 'recruit-ai', 'recruit-config'],
    'hr': ['recruit-dashboard', 'recruit-demand', 'recruit-talent', 'recruit-interview'],
    'interviewer': ['recruit-dashboard', 'recruit-interview'],
    'temp_interviewer': ['recruit-dashboard', 'recruit-interview'],
    'dept_head': ['recruit-dashboard', 'recruit-demand'],
    'executive': ['recruit-dashboard', 'recruit-demand'],
    'employee': ['recruit-dashboard', 'recruit-demand'],
    'no_recruit': [],
}
