# -*- coding: utf-8 -*-
"""MySQL 测试数据填充脚本：为全部 31 张表各插入恰好 4 条业务自洽的测试数据。

可重复运行：每次先 FOREIGN_KEY_CHECKS=0 清空所有表，再按外键依赖顺序插入。

用法：
    cd backend
    ./.venv/Scripts/python.exe scripts/seed_mysql_test.py

数据库连接取自 config（.env 的 DATABASE_URL），也可用环境变量覆盖。
"""
import os
import sys
from datetime import datetime, date, timedelta

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app import create_app  # noqa: E402
from app.extensions import db  # noqa: E402
from sqlalchemy import text  # noqa: E402

# 显式导入所有模型模块，确保 metadata 注册完整
from app.models import (  # noqa: E402,F401
    auxiliary, base, candidate, demand, hire, iam,
    infrastructure, internal, interview, process,
)
from app.models.infrastructure import File, TagDict, RecruitChannel, ScoreRule  # noqa: E402
from app.models.iam import IamDept, IamPosition, IamUser  # noqa: E402
from app.models.auxiliary import (  # noqa: E402
    RecruitMailAccount, MailLog, ChatLog, NotifyTemplate, AuditLog, ApiKeyConfig,
)
from app.models.candidate import Candidate, Resume, CandidateTagRel  # noqa: E402
from app.models.demand import DeptHC, RecruitDemand, DemandApproval  # noqa: E402
from app.models.process import RecruitProcess, ResumeMatch, SearchLog  # noqa: E402
from app.models.interview import InterviewSlot, InterviewBook, InterviewRecord  # noqa: E402
from app.models.hire import HireEvent, Offer, Entry  # noqa: E402
from app.models.internal import Employee, EmployeeTagRel, InternalMatchLog  # noqa: E402

NOW = datetime.now()
TODAY = date.today()


def clear_all_tables():
    """清空所有表（可重复运行的前提）。"""
    db.session.execute(text('SET FOREIGN_KEY_CHECKS=0'))
    for table in reversed(db.metadata.sorted_tables):
        db.session.execute(text(f'DELETE FROM `{table.name}`'))
    db.session.execute(text('SET FOREIGN_KEY_CHECKS=1'))
    db.session.commit()
    print(f'[seed] cleared {len(db.metadata.sorted_tables)} tables')


def add_all(objs):
    db.session.add_all(objs)
    db.session.flush()
    return objs


def seed():
    # ---- 基础主数据 -----------------------------------------------------
    files = add_all([
        File(file_name='张伟_简历.pdf', file_url='https://oss.example.com/resume/zhangwei.pdf',
             file_extension='pdf', file_size=245760, biz_type='resume'),
        File(file_name='李娜_简历.pdf', file_url='https://oss.example.com/resume/lina.pdf',
             file_extension='pdf', file_size=198400, biz_type='resume'),
        File(file_name='王强_Offer.pdf', file_url='https://oss.example.com/offer/wangqiang.pdf',
             file_extension='pdf', file_size=102400, biz_type='offer'),
        File(file_name='面试录音_赵敏.mp3', file_url='https://oss.example.com/audio/zhaomin.mp3',
             file_extension='mp3', file_size=5242880, biz_type='audio'),
    ])

    depts = add_all([
        IamDept(dept_id=1001, dept_name='技术研发部', parent_dept_id=None, dept_path='/技术研发部', sort_num=1),
        IamDept(dept_id=1002, dept_name='产品设计部', parent_dept_id=None, dept_path='/产品设计部', sort_num=2),
        IamDept(dept_id=1003, dept_name='人力资源部', parent_dept_id=None, dept_path='/人力资源部', sort_num=3),
        IamDept(dept_id=1004, dept_name='市场部', parent_dept_id=None, dept_path='/市场部', sort_num=4),
    ])

    positions = add_all([
        IamPosition(position_id=2001, position_name='高级后端工程师', dept_id=1001),
        IamPosition(position_id=2002, position_name='产品经理', dept_id=1002),
        IamPosition(position_id=2003, position_name='HRBP', dept_id=1003),
        IamPosition(position_id=2004, position_name='市场专员', dept_id=1004),
    ])

    users = add_all([
        IamUser(user_id=3001, username='zhouhr', real_name='周雨轩', dept_id=1003,
                position_id=2003, role_code='admin', email='zhouhr@example.com',
                mobile='13800000001', feishu_open_id='ou_test_001'),
        IamUser(user_id=3002, username='liwei', real_name='李伟', dept_id=1001,
                position_id=2001, role_code='interviewer', email='liwei@example.com',
                mobile='13800000002', feishu_open_id='ou_test_002'),
        IamUser(user_id=3003, username='chenpm', real_name='陈静', dept_id=1002,
                position_id=2002, role_code='interviewer', email='chenpm@example.com',
                mobile='13800000003', feishu_open_id='ou_test_003'),
        IamUser(user_id=3004, username='sunboss', real_name='孙总', dept_id=1001,
                position_id=2001, role_code='approver', email='sun@example.com',
                mobile='13800000004', feishu_open_id='ou_test_004'),
    ])

    tags = add_all([
        TagDict(tag_code='SKILL_PYTHON', tag_name='Python', tag_category='skill',
                match_weight=2.00, support_target=3, sort_num=1, remark='后端核心技能'),
        TagDict(tag_code='EDU_MASTER', tag_name='硕士学历', tag_category='edu',
                match_weight=1.50, support_target=3, sort_num=2),
        TagDict(tag_code='SCHOOL_985', tag_name='985院校', tag_category='school',
                match_weight=1.80, support_target=3, sort_num=3),
        TagDict(tag_code='CERT_PMP', tag_name='PMP认证', tag_category='cert',
                match_weight=1.20, support_target=3, sort_num=4, remark='项目管理认证'),
    ])

    channels = add_all([
        RecruitChannel(channel_name='公司官网', channel_type=1),
        RecruitChannel(channel_name='Boss直聘', channel_type=2),
        RecruitChannel(channel_name='猎聘', channel_type=2),
        RecruitChannel(channel_name='内部推荐', channel_type=3),
    ])

    add_all([
        ScoreRule(score_scene=1, weight_json={'decay_days': 90, 'fresh_score': 100, 'stale_score': 40},
                  pool_min_score=50.0, auto_invite_min_score=80.0),
        ScoreRule(score_scene=2, weight_json={'skill': 0.4, 'edu': 0.2, 'exp': 0.3, 'cert': 0.1},
                  pool_min_score=60.0, auto_invite_min_score=85.0),
        ScoreRule(score_scene=1, weight_json={'decay_days': 30, 'fresh_score': 95, 'stale_score': 30},
                  pool_min_score=45.0, auto_invite_min_score=75.0, status=0),
        ScoreRule(score_scene=2, weight_json={'skill': 0.5, 'edu': 0.1, 'exp': 0.4},
                  pool_min_score=55.0, auto_invite_min_score=88.0, status=0),
    ])

    mail_accounts = add_all([
        RecruitMailAccount(account_name='招聘主邮箱', email_address='hr-recruit@example.com',
                           imap_host='imap.example.com', imap_port=993, owner_user_id=3001,
                           mail_type='corp', sync_freq=30, password_encrypted='enc:mockpwd01',
                           last_sync_time=NOW - timedelta(minutes=15)),
        RecruitMailAccount(account_name='QQ采集邮箱', email_address='hr_collect@qq.com',
                           imap_host='imap.qq.com', imap_port=993, owner_user_id=3001,
                           mail_type='qq', sync_freq=60, password_encrypted='enc:mockpwd02'),
        RecruitMailAccount(account_name='163备用邮箱', email_address='hr_backup@163.com',
                           imap_host='imap.163.com', imap_port=993, owner_user_id=3001,
                           mail_type='163', sync_freq=120, password_encrypted='enc:mockpwd03', status=0),
        RecruitMailAccount(account_name='校招专用邮箱', email_address='campus@example.com',
                           imap_host='imap.example.com', imap_port=993, owner_user_id=3001,
                           monitor_folder='INBOX/校招', mail_type='corp', sync_freq=30,
                           password_encrypted='enc:mockpwd04'),
    ])

    add_all([
        ApiKeyConfig(key_name='deepseek', value_encrypted='enc:sk-test-deepseek',
                     display_label='DeepSeek 大模型'),
        ApiKeyConfig(key_name='feishu', value_encrypted='enc:fs-test-secret',
                     display_label='飞书应用密钥'),
        ApiKeyConfig(key_name='dify', value_encrypted='enc:dify-test-key',
                     display_label='Dify 工作流', status=0),
        ApiKeyConfig(key_name='boss_cli', value_encrypted='enc:boss-test-token',
                     display_label='Boss直聘采集令牌', status=0),
    ])

    add_all([
        NotifyTemplate(template_name='面试邀请模板', template_type='interview', send_method='邮件',
                       subject='【面试邀请】{company}-{position}',
                       body='尊敬的{candidate}：您好，诚邀您参加{position}岗位面试，时间{time}。'),
        NotifyTemplate(template_name='Offer发放模板', template_type='offer', send_method='邮件',
                       subject='【录用通知】{company} Offer Letter',
                       body='恭喜您通过全部面试，附件为正式录用通知书，请在{deadline}前回复。'),
        NotifyTemplate(template_name='婉拒通知模板', template_type='reject', send_method='邮件',
                       subject='【感谢投递】{company} 应聘结果通知',
                       body='感谢您的关注与投入，很遗憾本次岗位匹配度有限，简历已进入人才库。'),
        NotifyTemplate(template_name='面试提醒模板', template_type='remind', send_method='飞书+短信',
                       subject='【面试提醒】您明天有面试安排',
                       body='提醒：您预约的面试将于明天{time}开始，请提前10分钟到场/上线。'),
    ])

    # ---- 候选人 / 简历 ----------------------------------------------------
    candidates = add_all([
        Candidate(candidate_no='C202607001', candidate_name='张伟', mobile='13911110001',
                  mobile_hash='hash_zhangwei', email='zhangwei@mail.com',
                  static_ability_score=86.5, edu_level=3, school_level=3, work_years=5,
                  big_company_flag=1, cert_count=2, source_channel='Boss', status='available'),
        Candidate(candidate_no='C202607002', candidate_name='李娜', mobile='13911110002',
                  mobile_hash='hash_lina', email='lina@mail.com',
                  static_ability_score=78.0, edu_level=2, school_level=2, work_years=3,
                  source_channel='邮箱', status='locked'),
        Candidate(candidate_no='C202607003', candidate_name='王强', mobile='13911110003',
                  mobile_hash='hash_wangqiang', email='wangqiang@mail.com',
                  static_ability_score=91.0, edu_level=4, school_level=4, work_years=8,
                  big_company_flag=1, cert_count=3, source_channel='内推', status='available'),
        Candidate(candidate_no='C202607004', candidate_name='赵敏', mobile='13911110004',
                  mobile_hash='hash_zhaomin', email='zhaomin@mail.com',
                  static_ability_score=65.5, edu_level=2, school_level=1, work_years=2,
                  source_channel='猎聘', status='reserve', note='薪资期望偏高，先储备'),
    ])

    resumes = add_all([
        Resume(candidate_id=candidates[0].id, resume_file_id=files[0].id, storage_time=NOW - timedelta(days=2),
               base_score=95.0, work_exp_text='5年Python后端，主导高并发交易系统开发',
               extract_json={'skills': ['Python', 'Flask', 'MySQL'], 'edu': '硕士/浙江大学'},
               source_channel_id=channels[1].id),
        Resume(candidate_id=candidates[1].id, resume_file_id=files[1].id, storage_time=NOW - timedelta(days=5),
               base_score=82.0, work_exp_text='3年产品经理，负责B端SaaS产品线',
               extract_json={'skills': ['产品设计', 'Axure', 'SQL']},
               source_channel_id=channels[1].id, mail_account_id=mail_accounts[0].id),
        Resume(candidate_id=candidates[2].id, storage_time=NOW - timedelta(days=1),
               base_score=98.0, work_exp_text='8年分布式架构经验，前大厂技术专家',
               extract_json={'skills': ['Java', '微服务', 'K8s']},
               source_channel_id=channels[3].id),
        Resume(candidate_id=candidates[3].id, storage_time=NOW - timedelta(days=10),
               base_score=70.0, work_exp_text='2年市场推广，熟悉新媒体运营',
               extract_json={'skills': ['新媒体', '活动策划']},
               source_channel_id=channels[2].id),
    ])

    add_all([
        CandidateTagRel(candidate_id=candidates[0].id, tag_id=tags[0].id, tag_source=1),
        CandidateTagRel(candidate_id=candidates[0].id, tag_id=tags[2].id, tag_source=1),
        CandidateTagRel(candidate_id=candidates[1].id, tag_id=tags[3].id, tag_source=2,
                        valid_end=TODAY + timedelta(days=365)),
        CandidateTagRel(candidate_id=candidates[2].id, tag_id=tags[1].id, tag_source=3),
    ])

    # ---- 编制 / 需求 / 审批 -----------------------------------------------
    add_all([
        DeptHC(dept_id=1001, plan_year=2026, total_headcount=20, used_headcount=12, available_headcount=8),
        DeptHC(dept_id=1002, plan_year=2026, total_headcount=8, used_headcount=5, available_headcount=3),
        DeptHC(dept_id=1003, plan_year=2026, total_headcount=5, used_headcount=4, available_headcount=1),
        DeptHC(dept_id=1004, plan_year=2026, total_headcount=6, used_headcount=3, available_headcount=3),
    ])

    demands = add_all([
        RecruitDemand(demand_no='DM2026070001', dept_id=1001, position_id=2001, recruit_type=1,
                      plan_headcount=2, filled_count=0, expect_entry_date=TODAY + timedelta(days=45),
                      jd_content='负责核心业务后端开发，要求5年以上Python经验',
                      edu_min='本科', exp_min=5, work_city='北京',
                      publishing_channels=[channels[1].id, channels[2].id],
                      demand_status=2, creator_id=3001, hr_owner_id=3001, approved_at=NOW - timedelta(days=3),
                      salary_range='35k-50k', urgency='high',
                      required_skills=['Python', 'MySQL', 'Redis'], plus_skills=['K8s', 'Go'],
                      position_name='高级后端工程师', dept_name='技术研发部'),
        RecruitDemand(demand_no='DM2026070002', dept_id=1002, position_id=2002, recruit_type=1,
                      plan_headcount=1, expect_entry_date=TODAY + timedelta(days=60),
                      jd_content='负责招聘系统产品规划与迭代', edu_min='本科', exp_min=3,
                      work_city='上海', publishing_channels=[channels[0].id],
                      demand_status=1, creator_id=3001, hr_owner_id=3001,
                      salary_range='25k-35k', urgency='normal',
                      required_skills=['产品设计', 'B端SaaS'], position_name='产品经理', dept_name='产品设计部'),
        RecruitDemand(demand_no='DM2026070003', dept_id=1001, position_id=2001, recruit_type=2,
                      plan_headcount=3, expect_entry_date=TODAY + timedelta(days=90),
                      jd_content='2026届校招后端开发管培生', edu_min='硕士', exp_min=0,
                      work_city='北京', demand_status=2, creator_id=3001, hr_owner_id=3001,
                      approved_at=NOW - timedelta(days=1), salary_range='20k-28k', urgency='normal',
                      required_skills=['算法基础', '一门后端语言'],
                      position_name='后端开发（校招）', dept_name='技术研发部'),
        RecruitDemand(demand_no='DM2026070004', dept_id=1004, position_id=2004, recruit_type=1,
                      plan_headcount=1, work_city='广州', demand_status=0, creator_id=3001,
                      jd_content='负责市场活动策划与执行（草稿）', edu_min='大专', exp_min=2,
                      salary_range='12k-18k', urgency='very',
                      required_skills=['活动策划'], position_name='市场专员', dept_name='市场部'),
    ])

    add_all([
        DemandApproval(demand_id=demands[0].id, approve_user_id=3004, approve_level=1,
                       approve_result=2, approve_opinion='编制内，同意', approve_time=NOW - timedelta(days=3)),
        DemandApproval(demand_id=demands[1].id, approve_user_id=3004, approve_level=1,
                       approve_result=1, approve_opinion='待产品总监复核'),
        DemandApproval(demand_id=demands[2].id, approve_user_id=3004, approve_level=1,
                       approve_result=2, approve_opinion='校招统一批次，通过', approve_time=NOW - timedelta(days=1)),
        DemandApproval(demand_id=demands[0].id, approve_user_id=3001, approve_level=2,
                       approve_result=2, approve_opinion='HR复核通过', approve_time=NOW - timedelta(days=2)),
    ])

    add_all([
        SearchLog(demand_id=demands[0].id, search_type=1, search_at=NOW - timedelta(days=2),
                  match_total=3, remark='内部员工库检索：Python+5年'),
        SearchLog(demand_id=demands[0].id, search_type=2, search_at=NOW - timedelta(days=2),
                  match_total=12, remark='外部简历库检索：高级后端'),
        SearchLog(demand_id=demands[2].id, search_type=2, search_at=NOW - timedelta(days=1),
                  match_total=25, remark='校招简历批量初筛'),
        SearchLog(demand_id=demands[1].id, search_type=1, search_at=NOW - timedelta(hours=6),
                  match_total=1, remark='内部产品经理可调岗检索'),
    ])

    add_all([
        ResumeMatch(resume_id=resumes[0].id, demand_id=demands[0].id, match_score=88.5,
                    score_detail={'skill': 90, 'edu': 85, 'exp': 88}, calculate_time=NOW - timedelta(days=2)),
        ResumeMatch(resume_id=resumes[2].id, demand_id=demands[0].id, match_score=93.0,
                    score_detail={'skill': 95, 'edu': 92, 'exp': 94}, calculate_time=NOW - timedelta(days=2)),
        ResumeMatch(resume_id=resumes[1].id, demand_id=demands[1].id, match_score=81.0,
                    score_detail={'skill': 80, 'edu': 78, 'exp': 82}, calculate_time=NOW - timedelta(hours=6)),
        ResumeMatch(resume_id=resumes[3].id, demand_id=demands[3].id, match_score=68.5,
                    score_detail={'skill': 65, 'edu': 70, 'exp': 70}, calculate_time=NOW - timedelta(hours=2)),
    ])

    # ---- 招聘流程 / 面试 ---------------------------------------------------
    processes = add_all([
        RecruitProcess(process_no='P2026070001', demand_id=demands[0].id, resume_id=resumes[0].id,
                       candidate_id=candidates[0].id, process_status=3),
        RecruitProcess(process_no='P2026070002', demand_id=demands[0].id, resume_id=resumes[2].id,
                       candidate_id=candidates[2].id, process_status=6),
        RecruitProcess(process_no='P2026070003', demand_id=demands[1].id, resume_id=resumes[1].id,
                       candidate_id=candidates[1].id, process_status=2),
        RecruitProcess(process_no='P2026070004', demand_id=demands[3].id, resume_id=resumes[3].id,
                       candidate_id=candidates[3].id, process_status=0),
    ])

    slots = add_all([
        InterviewSlot(interviewer_id=3002, demand_id=demands[0].id,
                      start_dt=NOW + timedelta(days=1, hours=1), end_dt=NOW + timedelta(days=1, hours=2),
                      is_booked=1),
        InterviewSlot(interviewer_id=3002, demand_id=demands[0].id,
                      start_dt=NOW + timedelta(days=1, hours=3), end_dt=NOW + timedelta(days=1, hours=4),
                      is_booked=0),
        InterviewSlot(interviewer_id=3003, demand_id=demands[1].id,
                      start_dt=NOW + timedelta(days=2, hours=1), end_dt=NOW + timedelta(days=2, hours=2),
                      is_booked=1),
        InterviewSlot(interviewer_id=3002, demand_id=None,
                      start_dt=NOW + timedelta(days=3, hours=1), end_dt=NOW + timedelta(days=3, hours=2),
                      is_booked=0),
    ])

    books = add_all([
        InterviewBook(demand_id=demands[0].id, resume_id=resumes[0].id, process_id=processes[0].id,
                      slot_id=slots[0].id, interview_round=2, interview_type=1,
                      meeting_code='888-001', meeting_pwd='123456', meeting_url='https://vc.feishu.cn/j/888001',
                      book_time=NOW - timedelta(days=1),
                      invite_json={'sent_at': str(NOW - timedelta(days=1)), 'channel': 'feishu'}),
        InterviewBook(demand_id=demands[0].id, resume_id=resumes[2].id, process_id=processes[1].id,
                      slot_id=slots[0].id, interview_round=2, interview_type=2,
                      meeting_code='888-002', meeting_url='https://meeting.tencent.com/dm/888002',
                      book_time=NOW - timedelta(days=4)),
        InterviewBook(demand_id=demands[1].id, resume_id=resumes[1].id, process_id=processes[2].id,
                      slot_id=slots[2].id, interview_round=1, interview_type=4,
                      address='北京市海淀区中关村大厦 12 层 1203 会议室',
                      book_time=NOW - timedelta(hours=12)),
        InterviewBook(demand_id=demands[0].id, resume_id=resumes[0].id, process_id=processes[0].id,
                      slot_id=slots[0].id, interview_round=1, interview_type=1,
                      meeting_code='888-000', meeting_url='https://vc.feishu.cn/j/888000',
                      book_time=NOW - timedelta(days=5)),
    ])

    records = add_all([
        InterviewRecord(book_id=books[3].id, process_id=processes[0].id, interviewer_ids=[3002],
                        submit_interviewer_id=3002, is_arrive=1, interview_result=1,
                        evaluate_text='基础扎实，项目经验丰富，建议进入二面',
                        score_json={'专业': 88, '沟通': 85, '潜力': 90}, end_time=NOW - timedelta(days=4),
                        feishu_memo_url='https://feishu.cn/minutes/test001'),
        InterviewRecord(book_id=books[1].id, process_id=processes[1].id, interviewer_ids=[3002, 3004],
                        submit_interviewer_id=3002, is_arrive=1, interview_result=1,
                        evaluate_text='架构能力突出，薪资匹配，建议发Offer',
                        score_json={'专业': 95, '沟通': 90, '潜力': 92}, end_time=NOW - timedelta(days=3),
                        feishu_memo_url='https://feishu.cn/minutes/test002'),
        InterviewRecord(book_id=books[2].id, process_id=processes[2].id, interviewer_ids=[3003],
                        submit_interviewer_id=3003, is_arrive=1, interview_result=1,
                        evaluate_text='产品 sense 不错，B端经验符合要求',
                        score_json={'专业': 82, '沟通': 88, '潜力': 80}, end_time=NOW - timedelta(hours=10)),
        InterviewRecord(book_id=books[0].id, process_id=processes[0].id, interviewer_ids=[3002, 3003],
                        submit_interviewer_id=3002, is_arrive=1, interview_result=0,
                        evaluate_text='二面系统设计深度不足，暂不通过，进入人才库',
                        score_json={'专业': 70, '沟通': 82, '潜力': 75}, end_time=NOW - timedelta(hours=3)),
    ])

    # ---- Offer / 录用 / 入职 -----------------------------------------------
    offers = add_all([
        Offer(offer_no='OF202607001', resume_id=resumes[2].id, process_id=processes[1].id,
              demand_id=demands[0].id, last_interview_id=records[1].id,
              offer_content='兹录用王强先生为高级后端工程师，月薪45k，14薪',
              salary_json={'base': 45000, 'months': 14, 'probation_months': 6, 'probation_ratio': 1.0},
              valid_deadline=NOW + timedelta(days=5), offer_status=2,
              send_user_id=3001, send_time=NOW - timedelta(days=2), offer_file_id=files[2].id),
        Offer(offer_no='OF202607002', resume_id=resumes[0].id, process_id=processes[0].id,
              demand_id=demands[0].id, offer_content='（草稿）张伟 offer 待二面结果确认',
              salary_json={'base': 38000, 'months': 14},
              valid_deadline=NOW + timedelta(days=10), offer_status=0,
              send_user_id=3001, send_time=NOW),
        Offer(offer_no='OF202607003', resume_id=resumes[1].id, process_id=processes[2].id,
              demand_id=demands[1].id, offer_content='李娜 产品经理 offer 已发送',
              salary_json={'base': 28000, 'months': 13},
              valid_deadline=NOW + timedelta(days=7), offer_status=1,
              send_user_id=3001, send_time=NOW - timedelta(hours=5)),
        Offer(offer_no='OF202607004', resume_id=resumes[3].id, process_id=processes[3].id,
              demand_id=demands[3].id, offer_content='赵敏 offer（候选人已拒绝）',
              salary_json={'base': 15000, 'months': 12},
              valid_deadline=NOW - timedelta(days=1), offer_status=3,
              send_user_id=3001, send_time=NOW - timedelta(days=8)),
    ])

    events = add_all([
        HireEvent(event_no='HE202607001', process_id=processes[1].id, offer_id=offers[0].id,
                  hire_type=1, event_status=1),
        HireEvent(event_no='HE202607002', process_id=processes[0].id, offer_id=offers[1].id,
                  hire_type=1, event_status=0),
        HireEvent(event_no='HE202607003', employee_id=None, hire_type=2, event_status=0),
        HireEvent(event_no='HE202607004', process_id=processes[3].id, offer_id=offers[3].id,
                  hire_type=1, event_status=2),
    ])

    add_all([
        Entry(entry_no='EN202607001', event_id=events[0].id, resume_id=resumes[2].id,
              dept_id=1001, position_id=2001, entry_date=TODAY + timedelta(days=14),
              checklist_json=['背调', '体检', '签合同']),
        Entry(entry_no='EN202607002', event_id=events[1].id, resume_id=resumes[0].id,
              dept_id=1001, position_id=2001, entry_date=TODAY + timedelta(days=30),
              checklist_json=['体检']),
        Entry(entry_no='EN202607003', event_id=events[0].id, resume_id=resumes[2].id,
              dept_id=1001, position_id=2001, entry_date=TODAY + timedelta(days=45),
              checklist_json=['设备申请', '账号开通']),
        Entry(entry_no='EN202607004', event_id=events[2].id, resume_id=resumes[1].id,
              dept_id=1002, position_id=2002, entry_date=TODAY + timedelta(days=60),
              checklist_json=['调岗面谈', '权限变更']),
    ])

    # ---- 内部人才 -----------------------------------------------------------
    employees = add_all([
        Employee(user_id=3002, dept_id=1001, position_id=2001, work_years=6,
                 perf_score=4.5, last_promote_date=TODAY - timedelta(days=400),
                 can_transfer=1, compositive_score=88.0),
        Employee(user_id=3003, dept_id=1002, position_id=2002, work_years=4,
                 perf_score=4.0, can_transfer=0, compositive_score=80.0,
                 transfer_restrict_reason='核心项目负责人，暂不可调'),
        Employee(user_id=3004, dept_id=1001, position_id=2001, work_years=12,
                 perf_score=4.8, can_transfer=0, compositive_score=92.0),
        Employee(user_id=3001, dept_id=1003, position_id=2003, work_years=5,
                 perf_score=4.2, can_transfer=1, compositive_score=82.5),
    ])

    add_all([
        EmployeeTagRel(employee_id=employees[0].id, tag_id=tags[0].id, tag_source=1, tag_related_score=4.5),
        EmployeeTagRel(employee_id=employees[1].id, tag_id=tags[3].id, tag_source=2,
                       valid_end=TODAY + timedelta(days=700)),
        EmployeeTagRel(employee_id=employees[2].id, tag_id=tags[1].id, tag_source=1),
        EmployeeTagRel(employee_id=employees[0].id, tag_id=tags[2].id, tag_source=3, tag_related_score=4.0),
    ])

    add_all([
        InternalMatchLog(match_no='IM202607001', demand_id=demands[0].id, employee_id=employees[0].id,
                         match_score=85.0, match_status=10, operator_user_id=3001,
                         matched_at=NOW - timedelta(days=2)),
        InternalMatchLog(match_no='IM202607002', demand_id=demands[0].id, employee_id=employees[2].id,
                         match_score=79.5, match_status=30, operator_user_id=3001,
                         matched_at=NOW - timedelta(days=2)),
        InternalMatchLog(match_no='IM202607003', demand_id=demands[1].id, employee_id=employees[1].id,
                         match_score=76.0, match_status=20, operator_user_id=3001,
                         matched_at=NOW - timedelta(hours=6)),
        InternalMatchLog(match_no='IM202607004', demand_id=demands[2].id, employee_id=employees[3].id,
                         match_score=60.0, match_status=30, operator_user_id=3001,
                         matched_at=NOW - timedelta(days=1)),
    ])

    # ---- 邮件 / 对话 / 审计日志 ----------------------------------------------
    add_all([
        MailLog(sender_account_id=mail_accounts[0].id, sender_email='hr-recruit@example.com',
                recipient='wangqiang@mail.com', subject='【录用通知】Offer Letter - 王强',
                mail_type='offer', status=1),
        MailLog(sender_account_id=mail_accounts[0].id, sender_email='hr-recruit@example.com',
                recipient='zhangwei@mail.com', subject='【面试邀请】高级后端工程师二面',
                mail_type='invite', status=1),
        MailLog(sender_account_id=mail_accounts[1].id, sender_email='hr_collect@qq.com',
                recipient='lina@mail.com', subject='【面试提醒】产品经理一面',
                mail_type='invite', status=0, error_msg='SMTP 550 收件人地址被拒'),
        MailLog(sender_account_id=mail_accounts[0].id, sender_email='hr-recruit@example.com',
                recipient='zhaomin@mail.com', subject='【感谢投递】应聘结果通知',
                mail_type='other', status=1),
    ])

    add_all([
        ChatLog(resume_id=resumes[0].id, demand_id=demands[0].id, chat_type=1,
                chat_content='AI：您好，看到您投递高级后端岗位，方便确认下可到岗时间吗？',
                operate_time=NOW - timedelta(days=2)),
        ChatLog(resume_id=resumes[0].id, demand_id=demands[0].id, chat_type=1,
                chat_content='候选人：预计一个月内可以到岗。',
                operate_time=NOW - timedelta(days=2, hours=-1)),
        ChatLog(resume_id=resumes[1].id, demand_id=demands[1].id, chat_type=2,
                chat_content='HR周雨轩：一面已通过，约您下周二线下二面。',
                operate_time=NOW - timedelta(hours=12)),
        ChatLog(resume_id=resumes[2].id, demand_id=demands[0].id, chat_type=2,
                chat_content='HR周雨轩：Offer已发出，请在周五前确认。',
                operate_time=NOW - timedelta(days=2)),
    ])

    add_all([
        AuditLog(operator_name='周雨轩', module='demand', action='创建招聘需求',
                 detail='创建需求 DM2026070001 高级后端工程师 x2', operate_time=NOW - timedelta(days=4)),
        AuditLog(operator_name='孙总', module='demand', action='审批通过',
                 detail='需求 DM2026070001 一级审批通过', operate_time=NOW - timedelta(days=3)),
        AuditLog(operator_name='周雨轩', module='mail', action='新增邮箱账户',
                 detail='配置校招专用邮箱 campus@example.com', operate_time=NOW - timedelta(days=1)),
        AuditLog(operator_name='李伟', module='interview', action='提交面试评价',
                 detail='王强 二面通过，建议发Offer', operate_time=NOW - timedelta(days=3)),
    ])

    db.session.commit()


def verify_counts():
    insp_tables = sorted(db.metadata.tables.keys())
    ok = True
    print('\n[seed] row counts:')
    for t in insp_tables:
        n = db.session.execute(text(f'SELECT COUNT(*) FROM `{t}`')).scalar()
        flag = 'OK' if n == 4 else 'FAIL'
        if n != 4:
            ok = False
        print(f'  {t:32s} {n}  {flag}')
    return ok


def main():
    app = create_app()
    with app.app_context():
        uri = app.config['SQLALCHEMY_DATABASE_URI']
        print(f'[seed] database: {uri.split("@")[-1] if "@" in uri else uri}')
        clear_all_tables()
        seed()
        ok = verify_counts()
        if ok:
            print('\n[seed] SUCCESS: all 31 tables have exactly 4 rows.')
        else:
            print('\n[seed] FAILURE: some tables do not have 4 rows.')
            sys.exit(1)


if __name__ == '__main__':
    main()
