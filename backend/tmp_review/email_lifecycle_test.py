# -*- coding: utf-8 -*-
"""Email account lifecycle: create -> dup -> soft-delete -> revive -> hard cleanup."""
import sys, os, io, json
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from app import create_app
app = create_app()
c = app.test_client()
r = c.post('/api/auth/login', json={'username': 'admin', 'password': 'admin123'})
H = {'Authorization': 'Bearer ' + (r.get_json()['data'].get('token'))}
ADDR = 'adversarial-test@example.com'
acct_id = None
try:
    r = c.post('/api/config/email-accounts', json={'address': ADDR, 'type': 'corp', 'server': 'imap.example.com',
                                                   'port': '993', 'pass': 'fake-pass-123', 'freq': '每 30 分钟'}, headers=H)
    print('create:', r.status_code, str(r.get_json())[:200])
    acct_id = (r.get_json().get('data') or {}).get('id')

    r = c.post('/api/config/email-accounts', json={'address': ADDR, 'pass': 'x'}, headers=H)
    print('dup (expect 4xx 已存在):', r.status_code, str(r.get_json())[:200])

    r = c.post('/api/config/email-accounts', json={'address': 'bad-format', 'pass': 'x'}, headers=H)
    print('bad format (expect 4xx):', r.status_code, str(r.get_json())[:200])

    r = c.delete(f'/api/config/email-accounts/{acct_id}', headers=H)
    print('delete:', r.status_code, str(r.get_json())[:160])

    r = c.post('/api/config/email-accounts', json={'address': ADDR, 'type': 'qq', 'server': 'imap.qq.com',
                                                   'port': '993', 'pass': 'new-pass-456'}, headers=H)
    print('revive:', r.status_code, str(r.get_json())[:200])
    acct_id = (r.get_json().get('data') or {}).get('id') or acct_id

    # check list reflects revived account status/mail_type
    r = c.get('/api/config/email-accounts', headers=H)
    row = next((a for a in r.get_json()['data'] if a['address'] == ADDR), None)
    print('revived row:', json.dumps(row, ensure_ascii=False))
finally:
    with app.app_context():
        from app.extensions import db
        from app.models.auxiliary import RecruitMailAccount
        RecruitMailAccount.query.filter_by(email_address=ADDR).delete()
        db.session.commit()
        print('cleanup: hard-deleted', ADDR)
