"""Tests for the demand API (/api/demand/*)."""

import pytest


def _role_headers(client, role, username=None):
    resp = client.post('/api/auth/login', json={
        'username': username or role,
        'role': role,
    })
    assert resp.status_code == 200, resp.get_json()
    token = resp.get_json()['data']['token']
    return {'Authorization': f'Bearer {token}'}


@pytest.fixture
def created_demand_no(client, auth_headers):
    """Create a demand and return its demand_no (e.g. 'DM2026070001')."""
    resp = client.post('/api/demand/create', json={
        'deptId': 1,
        'positionId': 1,
        'hc': 2,
        'urgency': 'normal',
        'description': (
            '负责公司核心服务的架构设计与开发。'
            '要求精通Java、Spring Boot、MySQL、Kubernetes'
        ),
        'creatorId': 1,
    }, headers=auth_headers)
    assert resp.status_code == 200, f'create demand failed: {resp.get_json()}'
    demand_id = resp.get_json()['data']['id']
    assert demand_id.startswith('DM')
    return demand_id


class TestDemand:
    """Demand CRUD and approval flow tests."""

    def test_create_demand(self, client, auth_headers):
        """POST /api/demand/create returns a demand_no starting with 'DM'."""
        resp = client.post('/api/demand/create', json={
            'deptId': 1,
            'positionId': 1,
            'hc': 2,
        }, headers=auth_headers)
        assert resp.status_code == 200
        body = resp.get_json()
        assert 'data' in body
        assert body['data']['id'].startswith('DM')
        assert body['data']['created'] is True

    def test_list_demands(self, client, auth_headers, created_demand_no):
        """GET /api/demand/list returns a paginated list of demands."""
        resp = client.get('/api/demand/list', headers=auth_headers)
        assert resp.status_code == 200
        body = resp.get_json()
        assert 'data' in body
        # Old format: {data: [...]}; new (pagination): {data: {items: [...], total: N}}
        demand_list = body['data']
        if isinstance(demand_list, dict):
            demand_list = demand_list.get('items', demand_list.get('data', []))
        assert isinstance(demand_list, list)
        assert len(demand_list) >= 1
        # The created demand should appear in the list
        demand_ids = [d['id'] for d in demand_list]
        assert created_demand_no in demand_ids

    def test_get_demand_detail(self, created_demand_no, client, auth_headers):
        """GET /api/demand/{id} returns the full demand detail.

        The response must include an approvalNodes key.
        """
        resp = client.get(
            f'/api/demand/{created_demand_no}',
            headers=auth_headers,
        )
        assert resp.status_code == 200
        body = resp.get_json()
        assert 'data' in body
        data = body['data']
        assert data['id'] == created_demand_no
        assert 'approvalNodes' in data
        assert 'position' in data
        assert 'dept' in data

    def test_demand_approve(self, created_demand_no, client, auth_headers):
        """Admin can approve any node and the opinion records admin bypass."""
        resp = client.post(
            f'/api/demand/{created_demand_no}/approve',
            json={'level': 1, 'approveUserId': 1, 'opinion': '同意'},
            headers=auth_headers,
        )
        assert resp.status_code == 200
        body = resp.get_json()
        assert body['data']['result'] == 'approved'
        assert body['data']['level'] == 1
        assert body['data']['identity'] == '管理员代审批'

        detail_resp = client.get(
            f'/api/demand/{created_demand_no}',
            headers=auth_headers,
        )
        node = detail_resp.get_json()['data']['approvalNodes'][0]
        assert node['opinion'].startswith('管理员代审批：')

    def test_approval_blocks_wrong_identity_and_level_jump(self, created_demand_no, client):
        """A caller cannot approve just by posting a level value."""
        hr_headers = _role_headers(client, 'hr', '张HR')

        wrong_identity = client.post(
            f'/api/demand/{created_demand_no}/approve',
            json={'level': 1, 'opinion': 'HR 不能批部门负责人'},
            headers=hr_headers,
        )
        assert wrong_identity.status_code == 403
        assert wrong_identity.get_json()['error']['code'] == 'FORBIDDEN'

        level_jump = client.post(
            f'/api/demand/{created_demand_no}/approve',
            json={'level': 2, 'opinion': '不能跳过部门负责人'},
            headers=hr_headers,
        )
        assert level_jump.status_code == 400
        assert level_jump.get_json()['error']['code'] == 'APPROVAL_ORDER_REQUIRED'

    def test_demand_full_approval_chain(self, created_demand_no, client, auth_headers):
        """Approving all three levels fully approves the demand.

        After level 3 is approved the demand's status is updated to
        'approved' (status code 2).
        """
        dept_head_headers = _role_headers(client, 'dept_head', '部门负责人')
        hr_headers = _role_headers(client, 'hr', '张HR')
        executive_headers = _role_headers(client, 'executive', '高管')

        # Level 1 — department head
        r1 = client.post(
            f'/api/demand/{created_demand_no}/approve',
            json={'level': 1},
            headers=dept_head_headers,
        )
        assert r1.status_code == 200
        assert r1.get_json()['data']['result'] == 'approved'

        # Level 2 — HR
        r2 = client.post(
            f'/api/demand/{created_demand_no}/approve',
            json={'level': 2},
            headers=hr_headers,
        )
        assert r2.status_code == 200
        assert r2.get_json()['data']['result'] == 'approved'

        # Level 3 — executive → all approved → demand approved
        r3 = client.post(
            f'/api/demand/{created_demand_no}/approve',
            json={'level': 3},
            headers=executive_headers,
        )
        assert r3.status_code == 200
        assert r3.get_json()['data']['result'] == 'approved'

        # Verify demand detail shows approved status
        detail_resp = client.get(
            f'/api/demand/{created_demand_no}',
            headers=auth_headers,
        )
        assert detail_resp.status_code == 200
        detail = detail_resp.get_json()['data']
        # After full approval the approval nodes should all show done
        for node in detail['approvalNodes']:
            assert node['state'] in ('done',)

    def test_demand_reject(self, created_demand_no, client, auth_headers):
        """POST /api/demand/{id}/reject rejects the demand and sets status."""
        resp = client.post(
            f'/api/demand/{created_demand_no}/reject',
            json={'level': 1, 'approveUserId': 1, 'opinion': 'HC不足，暂缓招聘'},
            headers=auth_headers,
        )
        assert resp.status_code == 200
        body = resp.get_json()
        assert body['data']['result'] == 'rejected'
        assert body['data']['level'] == 1
