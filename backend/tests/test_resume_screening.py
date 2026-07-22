"""Tests for resume ingest screening (is_resume / name / phone gate)
and live pipeline counts on the demand list.

Testing env has an invalid DeepSeek key, so parsing falls back to the
regex engine whose is_resume heuristic requires a phone number plus
experience/education markers — exactly what the gate enforces.
"""

import pytest


VALID_RESUME_TEXT = (
    '张三\n'
    '电话：13800138000\n'
    '邮箱：zhangsan@test.com\n'
    '求职意向：Java 开发工程师\n'
    '工作经验：5年 Java 后端开发，精通 Spring Boot、MySQL\n'
    '教育经历：本科 计算机科学与技术\n'
    '项目经验：电商订单系统架构设计与开发\n'
)


class TestResumeScreening:
    def test_non_resume_content_rejected(self, app):
        """Spam/notice text without phone or experience markers is rejected."""
        from app.services.resume_service import ingest_resume
        with app.app_context():
            with pytest.raises(ValueError):
                ingest_resume(b'x', 'notice.txt',
                              raw_text='恭喜您获得商城优惠券，点击领取，限时三天。' * 10,
                              source_channel='邮箱')

    def test_missing_phone_rejected(self, app):
        """A resume-like text without a phone number must not enter the pool."""
        from app.services.resume_service import ingest_resume
        with app.app_context():
            with pytest.raises(ValueError) as exc_info:
                ingest_resume(b'x', 'resume.txt',
                              raw_text='李四\n求职意向：产品经理\n工作经验：3年产品规划\n教育经历：本科\n',
                              source_channel='邮箱')
            assert '手机' in str(exc_info.value) or '简历' in str(exc_info.value)

    def test_missing_name_rejected(self, app):
        """Phone present but no extractable name (and no filename/subject hint) is rejected."""
        from app.services.resume_service import ingest_resume
        with app.app_context():
            with pytest.raises(ValueError) as exc_info:
                ingest_resume(b'x', 'attachment.txt',
                              raw_text='电话：13900139000\n工作经验：4年测试\n教育经历：本科\n项目经验：自动化平台\n',
                              source_channel='邮箱')
            assert '姓名' in str(exc_info.value) or '简历' in str(exc_info.value)

    def test_valid_resume_ingested(self, app):
        """A proper resume with name + phone passes the gate and persists."""
        from app.services.resume_service import ingest_resume
        from app.models.candidate import Candidate
        with app.app_context():
            r = ingest_resume(b'x', '张三-Java-5年.pdf',
                              raw_text=VALID_RESUME_TEXT, source_channel='邮箱')
            assert r['candidate_name'] == '张三'
            cand = Candidate.query.filter_by(candidate_no=r['candidate_no']).first()
            assert cand is not None
            assert cand.mobile == '13800138000'


class TestDemandLiveCounts:
    def test_linked_candidate_appears_in_demand_list(self, app, client, auth_headers):
        """link_candidate_to_demand must show up in /api/demand/list counts."""
        from app.services.resume_service import ingest_resume
        from app.services.demand_service import link_candidate_to_demand
        from app.models.demand import RecruitDemand
        from app.extensions import db

        # create demand (open status so pipeline counters are serialized)
        resp = client.post('/api/demand/create', json={
            'deptId': 1, 'positionId': 1, 'hc': 2, 'creatorId': 1,
            'description': '负责核心服务开发。要求精通Java、Spring Boot',
        }, headers=auth_headers)
        assert resp.status_code == 200
        demand_no = resp.get_json()['data']['id']

        with app.app_context():
            ingest_resume(b'x', '张三-Java-5年.pdf',
                          raw_text=VALID_RESUME_TEXT, source_channel='邮箱')
            link = link_candidate_to_demand(demand_no, '张三')
            assert link['linked'] is True
            # flip to open so directApply/systemRecommend/interviewing serialize
            d = RecruitDemand.query.filter_by(demand_no=demand_no).first()
            d.demand_status = 2
            db.session.commit()

        resp = client.get('/api/demand/list?pageSize=100', headers=auth_headers)
        assert resp.status_code == 200
        items = resp.get_json()['data']
        if isinstance(items, dict):
            items = items.get('items', [])
        row = next(d for d in items if d['id'] == demand_no)
        assert row['linkedCount'] == 1
        assert row['directApply'] == 1  # 邮箱来源计入直接投递
