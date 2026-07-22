"""Tests for Offer 确认倒计时巡检 (hire_service.offer_followup).

验证业务目标链路最后一环：
- Offer 发出后按间隔（默认24h）给候选人发倒计时提醒，且不重复轰炸
- 超过截止时间（默认3天，可配置）未确认 → Offer 自动过期、流程→淘汰
"""

import pytest
from datetime import datetime, timedelta


def _make_sent_offer(app, days_ago, process_id=0):
    """直接落库一条已发送 Offer，send_time 可回溯。"""
    from app.extensions import db
    from app.models.hire import Offer

    with app.app_context():
        offer = Offer(
            offer_no=f'OFTEST{datetime.now().timestamp()}'.replace('.', '')[:28],
            resume_id=8,
            process_id=process_id,
            demand_id=5,
            offer_content='测试 Offer',
            salary_json={},
            valid_deadline=datetime.now() + timedelta(days=7),
            offer_status=1,  # sent
            send_user_id=1,
            send_time=datetime.now() - timedelta(days=days_ago),
        )
        db.session.add(offer)
        db.session.commit()
        return offer.offer_no


def _get_offer(app, offer_no):
    from app.models.hire import Offer
    with app.app_context():
        return Offer.query.filter_by(offer_no=offer_no).first()


class TestOfferFollowup:
    def test_overdue_offer_auto_expired(self, app):
        """超过截止时间未确认：Offer→已过期(4)，流程→淘汰(4)。"""
        from app.extensions import db
        from app.models.process import RecruitProcess

        # 造一条进行中的流程
        with app.app_context():
            p = RecruitProcess(
                process_no='RPTEST001', demand_id=5, resume_id=8,
                candidate_id=0, process_status=5,  # 待Offer
            )
            db.session.add(p)
            db.session.commit()
            pid = p.id

        offer_no = _make_sent_offer(app, days_ago=4, process_id=pid)  # 默认截止3天

        with app.app_context():
            from app.services import hire_service
            result = hire_service.offer_followup()
        assert offer_no in result['expired']

        with app.app_context():
            offer = _get_offer(app, offer_no)
            assert offer.offer_status == 4  # expired
            p = db.session.get(RecruitProcess, pid)
            assert p.process_status == 4  # 已淘汰

    def test_within_deadline_not_expired(self, app):
        """截止时间内：Offer 保持已发送，不误淘汰。"""
        offer_no = _make_sent_offer(app, days_ago=1)
        with app.app_context():
            from app.services import hire_service
            result = hire_service.offer_followup()
        assert offer_no not in result['expired']
        assert _get_offer(app, offer_no).offer_status == 1

    def test_reminder_sent_once_per_interval(self, app):
        """倒计时提醒：间隔内不重复发送（无邮箱账号时记日志但不轰炸）。"""
        from app.extensions import db
        from app.models.hire import Offer, OfferRemindLog

        offer_no = _make_sent_offer(app, days_ago=1)

        with app.app_context():
            from app.services import hire_service
            r1 = hire_service.offer_followup()
            offer = Offer.query.filter_by(offer_no=offer_no).first()
            logs1 = OfferRemindLog.query.filter_by(offer_id=offer.id).count()
            assert logs1 == 1  # 第一轮：记录一次提醒

            # 立即第二轮：间隔未到，不再提醒
            r2 = hire_service.offer_followup()
            logs2 = OfferRemindLog.query.filter_by(offer_id=offer.id).count()
            assert logs2 == 1

    def test_configurable_deadline(self, app):
        """截止时间做成配置项：调小窗口后，刚发出的 Offer 也会被判超时。"""
        offer_no = _make_sent_offer(app, days_ago=0)
        app.config['OFFER_CONFIRM_DEADLINE_DAYS'] = 0.0001  # ≈8.6秒
        try:
            import time
            time.sleep(0.05)
            with app.app_context():
                from app.services import hire_service
                # 把 send_time 往前拨一点，确保超过配置窗口
                from app.extensions import db
                from app.models.hire import Offer
                offer = Offer.query.filter_by(offer_no=offer_no).first()
                offer.send_time = datetime.now() - timedelta(seconds=10)
                db.session.commit()
                result = hire_service.offer_followup()
            assert offer_no in result['expired']
        finally:
            app.config['OFFER_CONFIRM_DEADLINE_DAYS'] = 3
