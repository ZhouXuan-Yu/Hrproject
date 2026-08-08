"""Dashboard service: aggregations for KPI, funnel, dept progress, channels, alerts."""
import logging
from datetime import datetime, timedelta
from app.extensions import db

log = logging.getLogger(__name__)


def _mock_enabled():
    from app.utils.response import should_mock_fallback
    return should_mock_fallback()


# ---------------------------------------------------------------------------
# Reusable helpers
# ---------------------------------------------------------------------------

def _gen_spark(count, points=7):
    """Generate an even 7-point spark distribution from a total count."""
    if count <= 0:
        return [0] * points
    base = count // points
    extra = count % points
    return [base + (1 if i < extra else 0) for i in range(points)]


def _resolve_dept_name(dept_id):
    """Resolve dept name from t_core_dept DB table with fallback dict."""
    _FALLBACK = {1: '技术部', 2: '产品部', 3: '运营部', 4: '数据部', 5: '财务部'}
    if dept_id is None:
        return '未知部门'
    try:
        from app.extensions import db
        from sqlalchemy import text
        name = db.session.execute(
            text("SELECT dept_name FROM t_core_dept WHERE dept_id = :did AND is_deleted = 0 LIMIT 1"),
            {'did': dept_id}
        ).scalar()
        if name:
            return name
    except Exception:
        pass
    return _FALLBACK.get(dept_id, f'部门({dept_id})')


def _resolve_position_name(position_id):
    """Resolve position name from t_core_position DB table with fallback dict."""
    _FALLBACK = {1: '高级Java工程师', 2: '前端工程师', 3: '产品经理', 4: '运营总监', 5: '数据分析师'}
    if position_id is None:
        return '未知岗位'
    try:
        from app.extensions import db
        from sqlalchemy import text
        name = db.session.execute(
            text("SELECT position_name FROM t_core_position WHERE position_id = :pid AND is_deleted = 0 LIMIT 1"),
            {'pid': position_id}
        ).scalar()
        if name:
            return name
    except Exception:
        pass
    return _FALLBACK.get(position_id, f'岗位({position_id})')


def get_real_kpi_data(role='admin'):
    """Query the real database for KPI metrics. Returns None on failure."""
    try:
        from app.extensions import db
        from sqlalchemy import text

        now = datetime.now()
        month_start = now.strftime('%Y-%m-01')
        today = now.strftime('%Y-%m-%d')

        # Shared counts
        candidates = db.session.execute(
            text("SELECT COUNT(*) FROM t_hr_candidate WHERE status != 'archived' AND is_deleted = 0")
        ).scalar() or 0

        open_demands = db.session.execute(
            text("SELECT COUNT(*) FROM t_hr_recruit_demand WHERE demand_status = 2 AND is_deleted = 0")
        ).scalar() or 0

        monthly_hires = db.session.execute(
            text("SELECT COUNT(*) FROM t_hr_entry WHERE entry_date >= :ms AND is_deleted = 0"),
            {'ms': month_start}
        ).scalar() or 0

        pending_approvals = db.session.execute(
            text("SELECT COUNT(*) FROM t_hr_recruit_demand WHERE demand_status = 1 AND is_deleted = 0")
        ).scalar() or 0

        # Pending evaluations: interview books with records that lack evaluate_text
        pending_evals = db.session.execute(text("""
            SELECT COUNT(*) FROM t_hr_interview_book b
            INNER JOIN t_hr_interview_record r ON r.book_id = b.id
            WHERE (r.evaluate_text IS NULL OR r.evaluate_text = '')
            AND b.is_deleted = 0 AND r.is_deleted = 0
        """)).scalar() or 0

        if role == 'admin':
            total_interviews = db.session.execute(
                text("SELECT COUNT(*) FROM t_hr_interview_book WHERE is_deleted = 0")
            ).scalar() or 0

            return [
                {'val': total_interviews, 'label': '全公司待面试', 'trend': '实时'},
                {'val': pending_evals, 'label': '待评价', 'trend': '实时'},
                {'val': open_demands, 'label': '在招岗位', 'trend': '实时'},
                {'val': monthly_hires, 'label': '本月入职总量', 'trend': '实时'},
            ]

        elif role == 'hr':
            total_interviews = db.session.execute(
                text("SELECT COUNT(*) FROM t_hr_interview_book WHERE is_deleted = 0")
            ).scalar() or 0

            return [
                {'val': total_interviews, 'label': '全部待面试', 'trend': '实时'},
                {'val': pending_evals, 'label': '待评价面试', 'trend': '实时'},
                {'val': pending_approvals, 'label': '待审批岗位', 'trend': '实时'},
                {'val': monthly_hires, 'label': '本月入职总量', 'trend': '实时'},
            ]

        elif role == 'interviewer':
            from flask import g
            uid = getattr(g, 'current_user_id', None)
            my_pending = my_evals = my_completed = 0
            avg_score = 0
            if uid:
                my_pending = db.session.execute(text("""
                    SELECT COUNT(*) FROM t_hr_interview_book b
                    INNER JOIN t_hr_interview_slot s ON s.id = b.slot_id
                    WHERE s.interviewer_id = :uid AND b.is_deleted = 0 AND s.is_deleted = 0
                """), {'uid': uid}).scalar() or 0

                my_evals = db.session.execute(text("""
                    SELECT COUNT(*) FROM t_hr_interview_book b
                    INNER JOIN t_hr_interview_slot s ON s.id = b.slot_id
                    INNER JOIN t_hr_interview_record r ON r.book_id = b.id
                    WHERE s.interviewer_id = :uid
                    AND (r.evaluate_text IS NULL OR r.evaluate_text = '')
                    AND b.is_deleted = 0 AND s.is_deleted = 0 AND r.is_deleted = 0
                """), {'uid': uid}).scalar() or 0

                my_completed = db.session.execute(text("""
                    SELECT COUNT(*) FROM t_hr_interview_book b
                    INNER JOIN t_hr_interview_slot s ON s.id = b.slot_id
                    INNER JOIN t_hr_interview_record r ON r.book_id = b.id
                    WHERE s.interviewer_id = :uid
                    AND r.evaluate_text IS NOT NULL AND r.evaluate_text != ''
                    AND b.is_deleted = 0 AND s.is_deleted = 0 AND r.is_deleted = 0
                """), {'uid': uid}).scalar() or 0

                avg_row = db.session.execute(text("""
                    SELECT AVG(CAST(JSON_EXTRACT(r.score_json, '$.total') AS DECIMAL))
                    FROM t_hr_interview_record r
                    INNER JOIN t_hr_interview_book b ON b.id = r.book_id
                    INNER JOIN t_hr_interview_slot s ON s.id = b.slot_id
                    WHERE s.interviewer_id = :uid
                    AND r.score_json IS NOT NULL
                    AND b.is_deleted = 0 AND r.is_deleted = 0 AND s.is_deleted = 0
                """), {'uid': uid}).scalar()
                avg_score = round(float(avg_row), 1) if avg_row else 0

            return [
                {'val': my_pending, 'label': '本人待面试', 'trend': '实时'},
                {'val': my_evals, 'label': '待评价', 'trend': '实时'},
                {'val': avg_score, 'label': '均分', 'trend': '—'},
                {'val': my_completed, 'label': '已完成', 'trend': '实时'},
            ]

        return None
    except Exception as exc:
        log.error("DB query failed in get_real_kpi_data: %s", exc, exc_info=True)
        return None


def get_kpi_data(role='admin'):
    """Return role-specific KPI data — DB first, mock fallback."""
    real = get_real_kpi_data(role)
    if real:
        return real
    if not _mock_enabled():
        return []
    log.warning("Using mock KPI data for role=%s", role)

    kpi_sets = {
        'admin': [
            {'val': 8, 'label': '全公司待面试', 'trend': '+2 昨日'},
            {'val': 12, 'label': '待评价', 'trend': '+3 昨日'},
            {'val': 8, 'label': '在招岗位', 'trend': '+1 昨日'},
            {'val': 5, 'label': '本月入职总量', 'trend': '持平'},
        ],
        'hr': [
            {'val': 3, 'label': '本人今日待面试', 'trend': '+1 昨日'},
            {'val': 5, 'label': '待评价面试', 'trend': '+2 昨日'},
            {'val': 7, 'label': '待跟进候选人', 'trend': '+3 昨日'},
            {'val': 2, 'label': '待审批岗位', 'trend': '+1 昨日'},
        ],
        'interviewer': [
            {'val': 2, 'label': '本人待面试', 'trend': '+1 昨日'},
            {'val': 1, 'label': '待评价', 'trend': '持平'},
            {'val': 8.5, 'label': '均分', 'trend': '—'},
            {'val': 0, 'label': '已完成', 'trend': '—'},
        ],
    }
    return kpi_sets.get(role, kpi_sets['admin'])


def get_real_funnel_data():
    """Query real database for recruitment funnel data."""
    try:
        from app.extensions import db
        from sqlalchemy import text

        now = datetime.now()
        period = now.strftime('%Y-%m')

        # Stage 1: 收简历 — try t_hr_resume first, then t_hr_candidate
        resumes = db.session.execute(
            text("SELECT COUNT(*) FROM t_hr_resume WHERE is_deleted = 0")
        ).scalar() or 0
        if resumes == 0:
            resumes = db.session.execute(
                text("SELECT COUNT(*) FROM t_hr_candidate WHERE status != 'archived' AND is_deleted = 0")
            ).scalar() or 0

        # Stage 2: 筛选通过 — process_status >= 1
        screened = db.session.execute(
            text("SELECT COUNT(DISTINCT candidate_id) FROM t_hr_recruit_process WHERE process_status >= 1 AND is_deleted = 0")
        ).scalar() or 0

        # Stage 3: 面试 — t_hr_interview_book
        interviewed = db.session.execute(
            text("SELECT COUNT(*) FROM t_hr_interview_book WHERE is_deleted = 0")
        ).scalar() or 0

        # Stage 4: Offer — t_hr_offer
        offered = db.session.execute(
            text("SELECT COUNT(*) FROM t_hr_offer WHERE is_deleted = 0")
        ).scalar() or 0

        # Stage 5: 入职 — t_hr_entry
        hired = db.session.execute(
            text("SELECT COUNT(*) FROM t_hr_entry WHERE is_deleted = 0")
        ).scalar() or 0

        total = resumes if resumes > 0 else 1

        def _pct(n):
            return f"{(n / total * 100):.1f}%" if total > 0 else "0%"

        def _conv(curr, prev):
            if prev and prev > 0 and curr > 0:
                return f"{(curr / prev * 100):.1f}%"
            return None

        def _health(cv):
            if cv is None:
                return 'good'
            try:
                v = float(cv.rstrip('%'))
                if v >= 30:
                    return 'good'
                elif v >= 15:
                    return 'watch'
                return 'risk'
            except Exception:
                return 'good'

        stages = [
            {
                'label': '收简历',
                'count': resumes,
                'pct': _pct(resumes),
                'link': '/recruit-talent',
                'conv': None,
                'wow': '--',
                'wowUp': True,
                'dwell': '--',
                'health': 'good',
                'owner': 'HR 团队',
                'spark': _gen_spark(resumes),
                'note': f'简历池共 {resumes} 份，建议持续拓展招聘渠道。',
                'bottleneck': False,
            },
            {
                'label': '筛选通过',
                'count': screened,
                'pct': _pct(screened),
                'link': '/recruit-demand',
                'conv': _conv(screened, resumes),
                'wow': '--',
                'wowUp': True,
                'dwell': '--',
                'health': _health(_conv(screened, resumes)),
                'owner': '用人部门 · HR',
                'spark': _gen_spark(screened),
                'note': f'筛选通过 {screened} 人，通过率 {_pct(screened)}。',
                'bottleneck': False,
            },
            {
                'label': '面试',
                'count': interviewed,
                'pct': _pct(interviewed),
                'link': '/recruit-interview',
                'conv': _conv(interviewed, screened),
                'wow': '--',
                'wowUp': True,
                'dwell': '--',
                'health': _health(_conv(interviewed, screened)),
                'owner': '面试官团队',
                'spark': _gen_spark(interviewed),
                'note': f'共 {interviewed} 场面试安排。',
                'bottleneck': False,
            },
            {
                'label': 'Offer',
                'count': offered,
                'pct': _pct(offered),
                'link': '/recruit-interview',
                'conv': _conv(offered, interviewed),
                'wow': '--',
                'wowUp': True,
                'dwell': '--',
                'health': _health(_conv(offered, interviewed)),
                'owner': 'HR · 用人经理',
                'spark': _gen_spark(offered),
                'note': f'已发放 {offered} 份 Offer。',
                'bottleneck': False,
            },
            {
                'label': '入职',
                'count': hired,
                'pct': _pct(hired),
                'link': '/recruit-demand',
                'conv': _conv(hired, offered),
                'wow': '--',
                'wowUp': True,
                'dwell': '--',
                'health': _health(_conv(hired, offered)),
                'owner': 'HR 团队',
                'spark': _gen_spark(hired),
                'note': f'本月已入职 {hired} 人。',
                'bottleneck': False,
            },
        ]

        # Mark bottleneck stages where conversion rate is under 20%
        for s in stages:
            if s['conv'] is not None:
                try:
                    cv = float(s['conv'].rstrip('%'))
                    if cv < 20:
                        s['bottleneck'] = True
                except Exception:
                    pass

        return {
            'period': period,
            'overallRate': _pct(hired),
            'stages': stages,
        }
    except Exception as exc:
        log.error("DB query failed in get_real_funnel_data: %s", exc, exc_info=True)
        return None


def get_funnel_data():
    """Return recruitment funnel with 5 stages — DB first, mock fallback."""
    real = get_real_funnel_data()
    if real:
        return real
    if not _mock_enabled():
        return {}
    log.warning("Using mock funnel data")

    return {
        'period': '2026-07',
        'overallRate': '1.4%',
        'stages': [
            {'label': '收简历', 'count': 346, 'pct': '100%', 'link': '/recruit-talent',
             'conv': None, 'wow': '+8.4%', 'wowUp': True, 'dwell': '1.2d', 'health': 'good',
             'owner': 'HR 团队', 'spark': [38, 42, 51, 47, 55, 60, 53],
             'note': '本月入口流量稳定，邮箱采集与内推贡献最高，简历池充足。', 'bottleneck': False},
            {'label': '筛选通过', 'count': 89, 'pct': '25.7%', 'link': '/recruit-demand',
             'conv': '25.7%', 'wow': '+2.1%', 'wowUp': True, 'dwell': '2.4d', 'health': 'good',
             'owner': '用人部门 · HR', 'spark': [12, 10, 14, 11, 13, 15, 14],
             'note': '筛选通过率 25.7%，可继续优化 JD 精准度，减少无效投递。', 'bottleneck': False},
            {'label': '面试', 'count': 42, 'pct': '12.1%', 'link': '/recruit-interview',
             'conv': '47.2%', 'wow': '-6.3%', 'wowUp': False, 'dwell': '3.6d', 'health': 'watch',
             'owner': '面试官团队', 'spark': [7, 6, 5, 6, 5, 7, 6],
             'note': '面试排期充足，重点关注面试到 Offer 的转化质量与评估一致性。', 'bottleneck': False},
            {'label': 'Offer', 'count': 8, 'pct': '2.3%', 'link': '/recruit-interview',
             'conv': '19.0%', 'wow': '-4.5%', 'wowUp': False, 'dwell': '2.1d', 'health': 'risk',
             'owner': 'HR · 用人经理', 'spark': [1, 2, 1, 1, 2, 1, 0],
             'note': '面试→Offer 转化仅 19.0%，为当前最大瓶颈，建议复盘评估口径与决策时效。', 'bottleneck': True},
            {'label': '入职', 'count': 5, 'pct': '1.4%', 'link': '/recruit-demand',
             'conv': '62.5%', 'wow': '+0.4%', 'wowUp': True, 'dwell': '5.0d', 'health': 'good',
             'owner': 'HR 团队', 'spark': [1, 0, 1, 1, 1, 0, 1],
             'note': 'Offer 到入职转化健康，保持 offer 后跟进与入职关怀节奏。', 'bottleneck': False},
        ]
    }


def get_real_dept_progress_data():
    """Query real database for department HC progress."""
    try:
        from app.extensions import db
        from sqlalchemy import text

        rows = db.session.execute(
            text("""
                SELECT dept_id,
                       SUM(plan_headcount) AS total_hc,
                       SUM(filled_count) AS filled_hc
                FROM t_hr_recruit_demand
                WHERE demand_status = 2
                  AND is_deleted = 0
                  AND plan_headcount > 0
                GROUP BY dept_id
                ORDER BY dept_id
            """)
        ).fetchall()

        if not rows:
            return None

        result = []
        for row in rows:
            dept_name = _resolve_dept_name(row.dept_id)
            pct = round((row.filled_hc / row.total_hc) * 100) if row.total_hc > 0 else 0
            result.append({
                'dept': dept_name,
                'hired': row.filled_hc,
                'total': row.total_hc,
                'pct': pct,
            })

        return result
    except Exception as exc:
        log.error("DB query failed in get_real_dept_progress_data: %s", exc, exc_info=True)
        return None


def get_dept_progress_data():
    """Return department HC fill progress — DB first, mock fallback."""
    real = get_real_dept_progress_data()
    if real:
        return real
    if not _mock_enabled():
        return []
    log.warning("Using mock dept progress data")

    return [
        {'dept': '技术部', 'hired': 3, 'total': 5, 'pct': 60},
        {'dept': '产品部', 'hired': 1, 'total': 3, 'pct': 33},
        {'dept': '运营部', 'hired': 0, 'total': 2, 'pct': 0},
        {'dept': '数据部', 'hired': 2, 'total': 2, 'pct': 100},
    ]


def get_real_channel_data():
    """Query real database for channel effectiveness using a single JOIN query."""
    try:
        from app.extensions import db
        from sqlalchemy import text

        DISPLAY_MAP = {
            '邮箱': '邮箱采集',
            'Boss': 'Boss 直聘',
            '猎聘': '猎聘',
            '内推': '内推',
        }

        # Single query: aggregate all channel stats with LEFT JOINs
        rows = db.session.execute(
            text("""
                SELECT
                    c.source_channel,
                    COUNT(DISTINCT c.id) AS resume_cnt,
                    COUNT(DISTINCT CASE WHEN p.id IS NOT NULL AND p.process_status >= 1 THEN c.id END) AS pass_cnt,
                    COUNT(DISTINCT b.id) AS interview_cnt,
                    COUNT(DISTINCT e.id) AS hire_cnt
                FROM t_hr_candidate c
                LEFT JOIN t_hr_recruit_process p ON p.candidate_id = c.id AND p.is_deleted = 0
                LEFT JOIN t_hr_resume r ON r.candidate_id = c.id AND r.is_deleted = 0
                LEFT JOIN t_hr_interview_book b ON b.resume_id = r.id AND b.is_deleted = 0
                LEFT JOIN t_hr_entry e ON e.resume_id = r.id AND e.is_deleted = 0
                WHERE c.is_deleted = 0
                  AND c.source_channel IS NOT NULL
                GROUP BY c.source_channel
                ORDER BY c.source_channel
            """)
        ).fetchall()

        if not rows:
            # Fallback: try t_hr_recruit_channel master list
            channels = db.session.execute(
                text("SELECT channel_name FROM t_hr_recruit_channel WHERE status = 1")
            ).scalars().all()
            if not channels:
                return None
            result = []
            for ch in channels:
                display_name = DISPLAY_MAP.get(ch, ch)
                result.append({
                    'channel': display_name,
                    'resume': 0,
                    'pass': 0,
                    'interview': 0,
                    'hire': 0,
                    'cost': '--',
                })
            return result

        result = []
        for row in rows:
            display_name = DISPLAY_MAP.get(row.source_channel, row.source_channel)
            result.append({
                'channel': display_name,
                'resume': row.resume_cnt or 0,
                'pass': row.pass_cnt or 0,
                'interview': row.interview_cnt or 0,
                'hire': row.hire_cnt or 0,
                'cost': '--',
            })

        return result
    except Exception as exc:
        log.error("DB query failed in get_real_channel_data: %s", exc, exc_info=True)
        return None


def get_channel_data():
    """Return channel effectiveness statistics — DB first, mock fallback."""
    real = get_real_channel_data()
    if real:
        return real
    if not _mock_enabled():
        return []
    log.warning("Using mock channel data")

    return [
        {'channel': '邮箱采集', 'resume': 120, 'pass': 35, 'interview': 18, 'hire': 2, 'cost': '¥0'},
        {'channel': 'Boss 直聘', 'resume': 98, 'pass': 28, 'interview': 12, 'hire': 1, 'cost': '¥8K'},
        {'channel': '猎聘', 'resume': 65, 'pass': 15, 'interview': 7, 'hire': 1, 'cost': '¥12K'},
        {'channel': '内推', 'resume': 42, 'pass': 8, 'interview': 4, 'hire': 1, 'cost': '¥3K'},
    ]


def get_real_risk_alerts():
    """Query real database for risk/success alert list."""
    try:
        from app.extensions import db
        from sqlalchemy import text

        now = datetime.now()
        seven_days_ago = now - timedelta(days=7)
        yesterday = now - timedelta(days=1)
        alerts = []

        # --- 1. Active demands with zero linked candidates ---
        zero_rows = db.session.execute(
            text("""
                SELECT d.id, d.dept_id, d.position_id, d.created_at
                FROM t_hr_recruit_demand d
                WHERE d.demand_status = 2
                  AND d.is_deleted = 0
                  AND d.id NOT IN (
                      SELECT DISTINCT p.demand_id FROM t_hr_recruit_process p WHERE p.is_deleted = 0
                  )
                LIMIT 5
            """)
        ).fetchall()

        for row in zero_rows:
            dept_name = _resolve_dept_name(row.dept_id)
            pos_name = _resolve_position_name(row.position_id)
            days_open = max(1, (now - row.created_at).days) if row.created_at else 1
            alerts.append({
                'text': f'{dept_name}·{pos_name} — 发布{days_open}天零简历',
                'type': 'reject',
                'link': '/recruit-demand-detail',
                'action': '查看',
            })

        # --- 2. HC nearly full (>80% filled) ---
        full_rows = db.session.execute(
            text("""
                SELECT d.id, d.dept_id, d.position_id, d.plan_headcount, d.filled_count
                FROM t_hr_recruit_demand d
                WHERE d.demand_status = 2
                  AND d.is_deleted = 0
                  AND d.plan_headcount > 0
                  AND d.filled_count * 1.0 / d.plan_headcount >= 0.8
                  AND d.filled_count < d.plan_headcount
                LIMIT 5
            """)
        ).fetchall()

        for row in full_rows:
            dept_name = _resolve_dept_name(row.dept_id)
            pos_name = _resolve_position_name(row.position_id)
            remaining = row.plan_headcount - row.filled_count
            alerts.append({
                'text': f'{dept_name}·{pos_name} — HC仅剩{remaining}个',
                'type': 'warn',
                'link': '/recruit-demand-detail',
                'action': '查看',
            })

        # --- 3. Overdue interviews (slotted >7 days ago, no record yet) ---
        overdue_cnt = db.session.execute(
            text("""
                SELECT COUNT(DISTINCT b.id)
                FROM t_hr_interview_book b
                JOIN t_hr_interview_slot s ON s.id = b.slot_id
                LEFT JOIN t_hr_interview_record r ON r.book_id = b.id
                WHERE r.id IS NULL
                  AND s.start_dt < :cutoff
                  AND b.is_deleted = 0
                  AND s.is_deleted = 0
            """),
            {'cutoff': seven_days_ago}
        ).scalar() or 0

        if overdue_cnt > 0:
            alerts.append({
                'text': f'{overdue_cnt}名候选人超7天未安排面试',
                'type': 'warn',
                'link': '/recruit-interview',
                'action': '安排',
            })

        # --- 4. Recently filled demands (closed within 1 day) ---
        recent_rows = db.session.execute(
            text("""
                SELECT d.id, d.dept_id, d.position_id
                FROM t_hr_recruit_demand d
                WHERE d.demand_status = 4
                  AND d.is_deleted = 0
                  AND (d.closed_at >= :yesterday OR d.updated_at >= :yesterday)
                LIMIT 5
            """),
            {'yesterday': yesterday}
        ).fetchall()

        for row in recent_rows:
            dept_name = _resolve_dept_name(row.dept_id)
            pos_name = _resolve_position_name(row.position_id)
            alerts.append({
                'text': f'{dept_name}·{pos_name} — 昨日已招满',
                'type': 'done',
                'link': '/recruit-demand-detail',
                'action': '查看',
            })

        return alerts if alerts else None
    except Exception as exc:
        log.error("DB query failed in get_real_risk_alerts: %s", exc, exc_info=True)
        return None


def get_risk_alerts():
    """Return risk/success alert list — DB first, mock fallback."""
    real = get_real_risk_alerts()
    if real:
        return real
    if not _mock_enabled():
        return []
    log.warning("Using mock risk alerts")

    return [
        {'text': '运营部·运营总监 — 发布20天零简历', 'type': 'reject', 'link': '/recruit-demand-detail', 'action': '查看'},
        {'text': '技术部·前端工程师 — HC仅剩1个', 'type': 'warn', 'link': '/recruit-demand-detail', 'action': '查看'},
        {'text': '3名候选人超7天未安排面试', 'type': 'warn', 'link': '/recruit-interview', 'action': '安排'},
        {'text': '数据部·数据分析师 — 昨日已招满', 'type': 'done', 'link': '/recruit-demand-detail', 'action': '查看'},
    ]


# ── Monthly aggregation for dashboard bar charts ────────────────────────

_MONTH_LABELS = ['1月', '2月', '3月', '4月', '5月', '6月',
                 '7月', '8月', '9月', '10月', '11月', '12月']


def get_monthly_stats(year=None, dept_id=None, position_id=None):
    """Return 12-month bar chart data: resumes / interviews / hires per month.

    Queries Candidate, InterviewBook, and HireEvent tables grouped by month.
    Optionally filters by dept_id and/or position_id via demand linkage.
    Falls back to empty arrays when DB is unavailable.
    """
    from datetime import datetime as dt
    y = int(year) if year else dt.now().year

    months = []
    for m in range(1, 13):
        months.append({'label': _MONTH_LABELS[m - 1], 'resumes': 0, 'interviews': 0, 'hires': 0})

    # Build dept/position subquery filter for demands (MySQL compatible)
    demand_filter_sql = ''
    demand_filter_params = {}
    if dept_id or position_id:
        demand_filter_sql = """ AND EXISTS (
            SELECT 1 FROM t_hr_recruit_process rp
            JOIN t_hr_recruit_demand d ON d.id = rp.demand_id AND d.is_deleted = 0
            WHERE rp.resume_id = r.id AND rp.is_deleted = 0"""
        if dept_id:
            demand_filter_sql += ' AND d.dept_id = :df_dept'
            demand_filter_params['df_dept'] = dept_id
        if position_id:
            demand_filter_sql += ' AND d.position_id = :df_pos'
            demand_filter_params['df_pos'] = position_id
        demand_filter_sql += ')'

    try:
        from app.models.candidate import Candidate
        from app.extensions import db
        from sqlalchemy import func, extract, text

        # Resumes by month (candidate created_at), optionally filtered by demand dept/position
        if dept_id or position_id:
            sql = """SELECT MONTH(c.created_at) AS mo, COUNT(DISTINCT c.id) AS cnt
            FROM t_hr_candidate c
            JOIN t_hr_resume r ON r.candidate_id = c.id AND r.is_deleted = 0""" + demand_filter_sql + """
            WHERE c.is_deleted = 0 AND YEAR(c.created_at) = :yr
            GROUP BY mo"""
            rows = db.session.execute(text(sql), {'yr': y, **demand_filter_params}).fetchall()
        else:
            rows = db.session.query(
                extract('month', Candidate.created_at).label('mo'),
                func.count(Candidate.id).label('cnt')
            ).filter(
                Candidate.is_deleted == 0,
                extract('year', Candidate.created_at) == y
            ).group_by('mo').all()
        for mo, cnt in rows:
            if 1 <= int(mo) <= 12:
                months[int(mo) - 1]['resumes'] = cnt

        # Interviews by month, optionally filtered
        if dept_id or position_id:
            sql = """SELECT MONTH(b.created_at) AS mo, COUNT(DISTINCT b.id) AS cnt
            FROM t_hr_interview_book b
            JOIN t_hr_resume r ON r.id = b.resume_id AND r.is_deleted = 0""" + demand_filter_sql + """
            WHERE b.is_deleted = 0 AND YEAR(b.created_at) = :yr
            GROUP BY mo"""
            rows = db.session.execute(text(sql), {'yr': y, **demand_filter_params}).fetchall()
        else:
            from app.models.interview import InterviewBook
            rows = db.session.query(
                extract('month', InterviewBook.created_at).label('mo'),
                func.count(InterviewBook.id).label('cnt')
            ).filter(
                InterviewBook.is_deleted == 0,
                extract('year', InterviewBook.created_at) == y
            ).group_by('mo').all()
        for mo, cnt in rows:
            if 1 <= int(mo) <= 12:
                months[int(mo) - 1]['interviews'] = cnt

        # Hires by month, optionally filtered
        if dept_id or position_id:
            sql = """SELECT MONTH(e.created_at) AS mo, COUNT(DISTINCT e.id) AS cnt
            FROM t_hr_entry e
            JOIN t_hr_resume r ON r.id = e.resume_id AND r.is_deleted = 0""" + demand_filter_sql + """
            WHERE e.is_deleted = 0 AND YEAR(e.created_at) = :yr
            GROUP BY mo"""
            rows = db.session.execute(text(sql), {'yr': y, **demand_filter_params}).fetchall()
        else:
            from app.models.hire import HireEvent
            rows = db.session.query(
                extract('month', HireEvent.created_at).label('mo'),
                func.count(HireEvent.id).label('cnt')
            ).filter(
                HireEvent.is_deleted == 0,
                extract('year', HireEvent.created_at) == y
            ).group_by('mo').all()
        for mo, cnt in rows:
            if 1 <= int(mo) <= 12:
                months[int(mo) - 1]['hires'] = cnt

    except Exception as exc:
        log.warning("get_monthly_stats DB query failed: %s", exc)

    return {'year': y, 'months': months}


