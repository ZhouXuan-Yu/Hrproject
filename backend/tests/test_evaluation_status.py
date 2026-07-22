"""Regression tests for the "淘汰后显示已入职" bug.

Root cause: interview_service._db_to_dict() always returned result=None,
so the frontend's done-branch fallback ('已入职') kicked in even after a
reject evaluation. These tests pin the backend contract:

- fail evaluation -> list item: status=done, statusLabel=已淘汰, result=reject
- pass evaluation -> list item: status=offer, statusLabel=待录用, result=pass
"""

import pytest


def _create_and_complete(client, auth_headers):
    resp = client.post('/api/interview/create', json={
        'candidate': '测试候选人',
        'position': '后端工程师',
        'date': '2026-08-01',
        'time': '10:00',
        'mode_id': '4',
        'mode': '线下',
        'round': '初试(1轮)',
    }, headers=auth_headers)
    assert resp.status_code == 200, f'create failed: {resp.get_json()}'
    book_id = resp.get_json()['data']['book_id']

    resp = client.post(f'/api/interview/{book_id}/complete',
                       json={'is_arrive': 1}, headers=auth_headers)
    assert resp.status_code == 200, f'complete failed: {resp.get_json()}'
    return book_id


def _evaluate(client, auth_headers, book_id, result):
    return client.post(f'/api/interview/{book_id}/evaluate', json={
        'result': result,
        'score': 60,
        'comment': '综合评估后给出的面试评价理由',
    }, headers=auth_headers)


def _list_item(client, auth_headers, book_id):
    resp = client.get('/api/interview/list?pageSize=100', headers=auth_headers)
    assert resp.status_code == 200
    items = resp.get_json()['data']
    target = f'INT{book_id:04d}'
    for item in items:
        if item['id'] == target:
            return item
    raise AssertionError(f'interview {target} not found in list')


class TestEvaluationStatusMapping:
    def test_fail_shows_eliminated_not_hired(self, client, auth_headers):
        """评价拒绝后：状态=已淘汰，result=reject，绝不能回退成"已入职"。"""
        book_id = _create_and_complete(client, auth_headers)
        resp = _evaluate(client, auth_headers, book_id, 'fail')
        assert resp.status_code == 200, f'evaluate failed: {resp.get_json()}'
        data = resp.get_json()['data']
        assert data['newStatus'] == 'done'
        assert data['newStatusLabel'] == '已淘汰'

        item = _list_item(client, auth_headers, book_id)
        assert item['status'] == 'done'
        assert item['statusLabel'] == '已淘汰'
        assert item['result'] == 'reject'  # 前端据此显示"已回流人才库"而非"已入职"

    def test_pass_shows_pending_offer(self, client, auth_headers):
        """评价通过后：状态=待录用(offer)，下一步是发 Offer 而不是直接已入职。"""
        book_id = _create_and_complete(client, auth_headers)
        resp = _evaluate(client, auth_headers, book_id, 'pass')
        assert resp.status_code == 200
        data = resp.get_json()['data']
        assert data['newStatus'] == 'offer'
        assert data['newStatusLabel'] == '待录用'

        item = _list_item(client, auth_headers, book_id)
        assert item['status'] == 'offer'
        assert item['statusLabel'] == '待录用'
        assert item['result'] == 'pass'

    def test_hold_stays_evaluating(self, client, auth_headers):
        """暂缓评价：保持待评价，不误推进。"""
        book_id = _create_and_complete(client, auth_headers)
        resp = _evaluate(client, auth_headers, book_id, 'hold')
        assert resp.status_code == 200
        item = _list_item(client, auth_headers, book_id)
        assert item['status'] == 'evaluating'
        assert item['statusLabel'] == '待评价'
        assert item['result'] == 'hold'
