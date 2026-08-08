"""Seed script — populates dev db with demo data matching frontend mock."""
import hashlib
import sys, os, random
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app import create_app
from app.extensions import db
from app.models import *
from datetime import datetime, timedelta

app = create_app('development')


def seed():
    with app.app_context():
        db.create_all()
        print("Tables ready.")

        # Password hash helper
        _salt = app.config.get('PASSWORD_SALT', '')
        def _hash(pw):
            return hashlib.sha256((pw + _salt).encode('utf-8')).hexdigest()

        # ── IAM base ──
        if not IamDept.query.first():
            db.session.add_all([
                IamDept(dept_id=1, dept_name='技术部', parent_dept_id=0, dept_path='/1', sort_num=1),
                IamDept(dept_id=2, dept_name='产品部', parent_dept_id=0, dept_path='/2', sort_num=2),
                IamDept(dept_id=3, dept_name='运营部', parent_dept_id=0, dept_path='/3', sort_num=3),
                IamDept(dept_id=4, dept_name='数据部', parent_dept_id=0, dept_path='/4', sort_num=4),
                IamDept(dept_id=5, dept_name='财务部', parent_dept_id=0, dept_path='/5', sort_num=5),
            ])
            db.session.add_all([
                # 管理员 — 拥有全部权限，可管理用户/配置
                IamUser(user_id=100, username='admin', real_name='系统管理员', role_code='admin', password_hash=_hash('admin123')),
                IamUser(user_id=1, username='liubo', real_name='刘博', dept_id=1, role_code='dept_head', password_hash=_hash('123456')),
                IamUser(user_id=2, username='zhanghr', real_name='张HR', dept_id=1, role_code='hr', password_hash=_hash('123456')),
                IamUser(user_id=3, username='chenzong', real_name='陈总', dept_id=5, role_code='dept_head', password_hash=_hash('123456')),
                IamUser(user_id=4, username='zhoubo', real_name='周博', dept_id=2, role_code='dept_head', password_hash=_hash('123456')),
                IamUser(user_id=5, username='limsg', real_name='李面试官', dept_id=1, role_code='interviewer', password_hash=_hash('123456')),
                IamUser(user_id=6, username='wangmsg', real_name='王面试官', dept_id=1, role_code='interviewer', password_hash=_hash('123456')),
                IamUser(user_id=7, username='zhaobo', real_name='赵博', dept_id=4, role_code='dept_head', password_hash=_hash('123456')),
            ])
            db.session.add_all([
                IamPosition(position_id=1, position_name='高级Java工程师', dept_id=1),
                IamPosition(position_id=2, position_name='前端工程师', dept_id=1),
                IamPosition(position_id=3, position_name='产品经理', dept_id=2),
                IamPosition(position_id=4, position_name='运营总监', dept_id=3),
                IamPosition(position_id=5, position_name='数据分析师', dept_id=4),
            ])
            db.session.commit()
            print("IAM seed done.")

        # ── Infrastructure ──
        if not RecruitChannel.query.first():
            db.session.add_all([
                RecruitChannel(channel_name='Boss直聘', channel_type=2, status=1),
                RecruitChannel(channel_name='猎聘', channel_type=2, status=1),
                RecruitChannel(channel_name='邮箱采集', channel_type=1, status=1),
                RecruitChannel(channel_name='内部推荐', channel_type=3, status=1),
            ])
            db.session.commit()

        if not ScoreRule.query.first():
            db.session.add(ScoreRule(score_scene=1, weight_json={
                "profileWeight": 0.10, "matchWeight": 0.90,
                "decay30": 1.0, "decay90": 0.85, "decayOver90": 0.70,
                "topCount": 5, "searchRange": "近3个月",
            }, pool_min_score=60, auto_invite_min_score=80, status=1))
            db.session.commit()

        # ── Demands ──
        if not RecruitDemand.query.first():
            demands = [
                RecruitDemand(demand_no='DM2026070001', dept_id=4, position_id=5,
                    recruit_type=1, plan_headcount=1, demand_status=0, creator_id=7, urgency='normal'),
                RecruitDemand(demand_no='DM2026070002', dept_id=1, position_id=2,
                    recruit_type=1, plan_headcount=3, demand_status=4, creator_id=1, urgency='high'),
                RecruitDemand(demand_no='DM2026070003', dept_id=3, position_id=4,
                    recruit_type=1, plan_headcount=1, demand_status=2, creator_id=3, urgency='very'),
                RecruitDemand(demand_no='DM2026070004', dept_id=2, position_id=3,
                    recruit_type=1, plan_headcount=1, demand_status=1, creator_id=4, urgency='normal'),
                RecruitDemand(demand_no='DM2026070005', dept_id=1, position_id=1,
                    recruit_type=1, plan_headcount=2, demand_status=2, creator_id=1, urgency='high',
                    jd_content='负责公司电商中台核心服务的架构设计与开发，主导微服务拆分和容器化改造。',
                    edu_min='本科', exp_min=5, work_city='北京', publishing_channels=[1, 2, 3]),
                RecruitDemand(demand_no='DM2026070006', dept_id=3, position_id=4,
                    recruit_type=1, plan_headcount=1, demand_status=1, creator_id=3, urgency='very'),
            ]
            db.session.add_all(demands)
            db.session.commit()
            print(f"Demands seed: {len(demands)}")

        # ── Candidates ──
        if not Candidate.query.first():
            import hashlib
            def _cand(no, name, mobile, email, **kw):
                return Candidate(candidate_no=no, candidate_name=name,
                    mobile=mobile, email=email,
                    mobile_hash=hashlib.sha256(mobile.encode()).hexdigest(), **kw)
            cands = [
                _cand('C2026070012', '张三', '13812345678', 'zhangsan@example.com',
                    static_ability_score=88, edu_level=2, school_level=3, work_years=5,
                    big_company_flag=1, status='locked', source_channel='邮箱'),
                _cand('C2026070010', '郑一', '13923456789', 'zhengyi@example.com',
                    static_ability_score=84, edu_level=2, school_level=2, work_years=4,
                    big_company_flag=1, status='locked', source_channel='猎聘'),
                _cand('C2026070011', '李四', '13734567890', 'lisi@example.com',
                    static_ability_score=76, edu_level=3, school_level=3, work_years=3,
                    big_company_flag=1, status='available', source_channel='Boss'),
                _cand('C2026070007', '王五', '13645678901', 'wangwu@example.com',
                    static_ability_score=80, edu_level=3, school_level=2, work_years=3,
                    big_company_flag=1, status='reserve', source_channel='Boss'),
                _cand('C2026070009', '孙九', '13556789012', 'sunjiu@example.com',
                    static_ability_score=68, edu_level=2, school_level=1, work_years=6,
                    big_company_flag=1, status='locked', source_channel='内推'),
                _cand('C2024070001', '孙七', '13467890123', 'sunqi@example.com',
                    static_ability_score=55, edu_level=3, school_level=1, work_years=7,
                    status='archived', source_channel='猎聘'),
            ]
            db.session.add_all(cands)
            db.session.commit()
            print(f"Candidates seed: {len(cands)}")

        # ── RecruitProcess ──
        if not RecruitProcess.query.first():
            dm5 = RecruitDemand.query.filter_by(demand_no='DM2026070005').first()
            dm3 = RecruitDemand.query.filter_by(demand_no='DM2026070003').first()
            for cn, ds in [('C2026070012', dm5), ('C2026070009', dm5), ('C2026070007', dm5),
                           ('C2026070011', dm5), ('C2026070010', dm3)]:
                c = Candidate.query.filter_by(candidate_no=cn).first()
                if c and ds:
                    db.session.add(RecruitProcess(demand_id=ds.id, candidate_id=c.id,
                        resume_id=c.id, process_no=f'PRC{c.id:04d}', process_status=0))
            db.session.commit()
            print(f"Process seed: {RecruitProcess.query.count()}")

        # ── Resumes ──
        if not Resume.query.first():
            for c in Candidate.query.all():
                db.session.add(Resume(candidate_id=c.id, storage_time=datetime.now(), base_score=50))
            db.session.commit()
            print(f"Resume seed: {Resume.query.count()}")

        # ── Interview slots + bookings ──
        if not InterviewSlot.query.first():
            now = datetime.now()
            for i in range(6):
                db.session.add(InterviewSlot(interviewer_id=(i % 6) + 1,
                    start_dt=now + timedelta(days=i), end_dt=now + timedelta(days=i, hours=1),
                    is_booked=1 if i < 3 else 0))
            db.session.commit()
            print(f"Slots seed: {InterviewSlot.query.count()}")

        if not InterviewBook.query.first():
            slots = InterviewSlot.query.all()
            dm5 = RecruitDemand.query.filter_by(demand_no='DM2026070005').first()
            procs = RecruitProcess.query.filter_by(demand_id=dm5.id).all()[:3]
            for idx, p in enumerate(procs):
                if idx < len(slots):
                    db.session.add(InterviewBook(demand_id=dm5.id, resume_id=p.candidate_id,
                        process_id=p.id, slot_id=slots[idx].id, interview_round=1,
                        interview_type=1, book_time=datetime.now()))
            db.session.commit()
            print(f"Books seed: {InterviewBook.query.count()}")

        # ── Score matches ──
        if not ResumeMatch.query.first():
            dm5_m = RecruitDemand.query.filter_by(demand_no='DM2026070005').first()
            if dm5_m:
                for c in Candidate.query.all():
                    ps = float(c.static_ability_score or 60)
                    ms = max(40, min(95, ps * 0.85 + random.uniform(-10, 10)))
                    db.session.add(ResumeMatch(resume_id=c.id, demand_id=dm5_m.id,
                        match_score=round(ms, 1), calculate_time=datetime.now()))
            db.session.commit()
            print(f"Matches seed: {ResumeMatch.query.count()}")

        # ── Employees ──
        if not Employee.query.first():
            db.session.add_all([
                Employee(user_id=1, dept_id=1, position_id=1, work_years=8,
                    perf_score=4.5, can_transfer=1, compositive_score=92),
                Employee(user_id=4, dept_id=2, position_id=3, work_years=5,
                    perf_score=4.8, can_transfer=1, compositive_score=95),
                Employee(user_id=7, dept_id=4, position_id=5, work_years=2,
                    perf_score=3.5, can_transfer=0, compositive_score=65,
                    transfer_restrict_reason='入职不满2年'),
            ])
            db.session.commit()

        print("Seed complete.")


if __name__ == '__main__':
    seed()
