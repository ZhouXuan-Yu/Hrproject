"""Tests for meeting_url generation in interview creation and the Feishu VC mock.

Covers:
- feishu_client.create_vc_meeting() Mock branch (fake vc.feishu.cn URL)
- interview_service.create_interview() meeting_url per interview type
- regression: frontend sends round as int (e.g. 1), must not raise TypeError
"""

import re

import pytest


class TestFeishuVcMock:
    """create_vc_meeting() in MOCK_MODE returns a deterministic-shaped fake URL."""

    def test_mock_returns_fake_vc_url(self, app):
        from app.services import feishu_client
        assert feishu_client.MOCK_MODE is True
        vc = feishu_client.create_vc_meeting('面试-张三-后端', '1785000000', duration_minutes=60)
        assert re.fullmatch(r'https://vc\.feishu\.cn/j/\d{9}', vc['meeting_url'])
        assert re.fullmatch(r'\d{9}', vc['meeting_code'])


class TestInterviewMeetingUrl:
    """create_interview() generates meeting_url by interview type and persists it."""

    BASE = {
        'candidate': '张三',
        'position': '后端工程师',
        'interviewer': '李四',
        'date': '2026-08-01',
        'time': '10:00',
    }

    def _create(self, client, auth_headers, **overrides):
        resp = client.post('/api/interview/create',
                           json={**self.BASE, **overrides},
                           headers=auth_headers)
        assert resp.status_code == 200, f'create interview failed: {resp.get_json()}'
        return resp.get_json()['data']

    def test_feishu_type_generates_vc_url(self, client, auth_headers):
        """mode_id=1 (飞书) yields a vc.feishu.cn URL in mock mode."""
        data = self._create(client, auth_headers,
                            mode_id='1', mode='飞书视频', round='初试(1轮)')
        assert re.fullmatch(r'https://vc\.feishu\.cn/j/\d{9}', data['meetingUrl'])

    def test_tencent_and_offline_types(self, client, auth_headers):
        """mode_id=2 (腾讯会议) builds a tencent URL; mode_id=4 (线下) stays empty."""
        data = self._create(client, auth_headers,
                            mode_id='2', mode='腾讯会议', round='初试(1轮)')
        assert re.fullmatch(r'https://meeting\.tencent\.com/dm/\d{10}', data['meetingUrl'])

        data = self._create(client, auth_headers,
                            mode_id='4', mode='线下', round='初试(1轮)', address='北京市朝阳区')
        assert data['meetingUrl'] == ''

    def test_int_round_does_not_raise(self, client, auth_headers):
        """Regression: frontend sends round as int (i + 1); must be coerced safely."""
        data = self._create(client, auth_headers,
                            mode_id='3', mode='其他线上',
                            meetingUrl='https://zoom.us/j/1234567890', round=1)
        assert data['meetingUrl'] == 'https://zoom.us/j/1234567890'
        assert data['book_id']
