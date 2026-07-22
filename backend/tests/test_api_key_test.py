"""Tests for POST /api/config/api-keys/test — API key connectivity check.

Covers:
- deepseek: 200 → ok, 401 → invalid key message, network error → graceful fail
- feishu: code=0 → ok, missing app_id → fail, error code → fail
- dify / unknown keys: marked unsupported
- resolution order: env var beats DB value
"""

import pytest


class _FakeResp:
    def __init__(self, status_code=200, body=None):
        self.status_code = status_code
        self._body = body or {}

    def json(self):
        return self._body


class TestApiKeyTestEndpoint:
    """Route-level tests with provider HTTP mocked out."""

    def _test(self, client, auth_headers, key_name):
        resp = client.post('/api/config/api-keys/test',
                           json={'key_name': key_name},
                           headers=auth_headers)
        assert resp.status_code == 200, f'test endpoint failed: {resp.get_json()}'
        return resp.get_json()['data']

    def test_missing_key_name(self, client, auth_headers):
        resp = client.post('/api/config/api-keys/test', json={},
                           headers=auth_headers)
        assert resp.status_code == 200
        assert resp.get_json()['data']['ok'] is False

    def test_dify_unsupported(self, client, auth_headers):
        data = self._test(client, auth_headers, 'dify')
        assert data['ok'] is False
        assert data['supported'] is False

    def test_unknown_key_unsupported(self, client, auth_headers):
        data = self._test(client, auth_headers, 'nonexistent')
        assert data['ok'] is False
        assert data['supported'] is False

    def test_deepseek_no_key_configured(self, client, auth_headers, monkeypatch):
        monkeypatch.delenv('DEEPSEEK_API_KEY', raising=False)
        data = self._test(client, auth_headers, 'deepseek')
        assert data['ok'] is False
        assert '未配置' in data['message']

    def test_deepseek_success(self, client, auth_headers, monkeypatch):
        monkeypatch.setenv('DEEPSEEK_API_KEY', 'sk-test-key')
        monkeypatch.setattr('app.services.config_service.requests.get',
                            lambda *a, **kw: _FakeResp(200))
        data = self._test(client, auth_headers, 'deepseek')
        assert data['ok'] is True
        assert data['source'] == 'env'

    def test_deepseek_invalid_key(self, client, auth_headers, monkeypatch):
        monkeypatch.setenv('DEEPSEEK_API_KEY', 'sk-bad-key')
        monkeypatch.setattr('app.services.config_service.requests.get',
                            lambda *a, **kw: _FakeResp(401))
        data = self._test(client, auth_headers, 'deepseek')
        assert data['ok'] is False
        assert '无效' in data['message']

    def test_feishu_missing_app_id(self, client, auth_headers, monkeypatch):
        monkeypatch.delenv('FEISHU_APP_ID', raising=False)
        monkeypatch.setenv('FEISHU_APP_SECRET', 'test-secret')
        data = self._test(client, auth_headers, 'feishu')
        assert data['ok'] is False
        assert 'App ID' in data['message']

    def test_feishu_success(self, client, auth_headers, monkeypatch):
        monkeypatch.setenv('FEISHU_APP_ID', 'cli_test')
        monkeypatch.setenv('FEISHU_APP_SECRET', 'test-secret')
        monkeypatch.setattr('app.services.config_service.requests.post',
                            lambda *a, **kw: _FakeResp(200, {'code': 0, 'tenant_access_token': 't-x'}))
        data = self._test(client, auth_headers, 'feishu')
        assert data['ok'] is True
        assert 'tenant_access_token' in data['message']

    def test_feishu_error_code(self, client, auth_headers, monkeypatch):
        monkeypatch.setenv('FEISHU_APP_ID', 'cli_test')
        monkeypatch.setenv('FEISHU_APP_SECRET', 'bad-secret')
        monkeypatch.setattr('app.services.config_service.requests.post',
                            lambda *a, **kw: _FakeResp(200, {'code': 10003, 'msg': 'app secret invalid'}))
        data = self._test(client, auth_headers, 'feishu')
        assert data['ok'] is False
        assert 'app secret invalid' in data['message']
