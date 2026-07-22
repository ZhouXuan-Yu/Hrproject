"""Tests for GET /api/talent/candidate/<id>/contact-info — full contact details.

Covers:
- returns full unmasked mobile/email for a known candidate
- empty strings when the candidate has no mobile/email on file
- 404-style AppError for unknown candidate
"""

import pytest


def _seed_candidate(app, **kw):
    from app.extensions import db
    from app.models.candidate import Candidate
    with app.app_context():
        c = Candidate(**kw)
        db.session.add(c)
        db.session.commit()


class TestCandidateContactInfo:
    def _get(self, client, auth_headers, cid):
        return client.get(f'/api/talent/candidate/{cid}/contact-info',
                          headers=auth_headers)

    def test_returns_full_contact(self, app, client, auth_headers):
        _seed_candidate(app, candidate_no='C2026071001', candidate_name='测试人',
                        mobile='13811112222', email='test@example.com')
        resp = self._get(client, auth_headers, 'C2026071001')
        assert resp.status_code == 200
        data = resp.get_json()['data']
        assert data['name'] == '测试人'
        assert data['mobile'] == '13811112222'  # 完整号码，不打码
        assert data['email'] == 'test@example.com'

    def test_empty_when_no_contact_on_file(self, app, client, auth_headers):
        _seed_candidate(app, candidate_no='C2026071002', candidate_name='无联系')
        resp = self._get(client, auth_headers, 'C2026071002')
        assert resp.status_code == 200
        data = resp.get_json()['data']
        assert data['mobile'] == ''
        assert data['email'] == ''

    def test_unknown_candidate(self, client, auth_headers):
        resp = self._get(client, auth_headers, 'C9999999999')
        assert resp.status_code in (400, 404)

    def test_requires_auth(self, client):
        resp = client.get('/api/talent/candidate/C2026071001/contact-info')
        assert resp.status_code in (401, 403)


class TestIngestLog:
    """GET /api/talent/ingest-log — recent resume ingestion records."""

    def test_returns_recent_ingestions(self, app, client, auth_headers):
        from datetime import datetime as _dt
        from app.extensions import db
        from app.models.candidate import Candidate, Resume
        with app.app_context():
            c = Candidate(candidate_no='C2026071003', candidate_name='入库人',
                          mobile='13833334444', email='ingest@example.com',
                          source_channel='邮箱')
            db.session.add(c)
            db.session.flush()
            r = Resume(candidate_id=c.id, storage_time=_dt(2026, 7, 21, 10, 30),
                       work_exp_text='5年后端经验', extract_json={'parse_engine': 'deepseek'})
            db.session.add(r)
            db.session.commit()

        resp = client.get('/api/talent/ingest-log?limit=5', headers=auth_headers)
        assert resp.status_code == 200
        items = resp.get_json()['data']['items']
        assert len(items) == 1
        it = items[0]
        assert it['candidate'] == '入库人'
        assert it['candidateNo'] == 'C2026071003'
        assert it['engine'] == 'deepseek'
        assert it['storageTime'] == '2026-07-21 10:30'

    def test_empty_when_no_resumes(self, client, auth_headers):
        resp = client.get('/api/talent/ingest-log', headers=auth_headers)
        assert resp.status_code == 200
        assert resp.get_json()['data']['items'] == []
