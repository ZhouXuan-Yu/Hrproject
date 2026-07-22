# -*- coding: utf-8 -*-
"""Read-only adversarial verification against real hr_recruit.db via test_client."""
import json, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.dirname(__import__('os').path.abspath(__file__))))
from app import create_app
app = create_app()
c = app.test_client()

# auth
r = c.post('/api/auth/login', json={'username': 'admin', 'password': 'admin123'})
print('login:', r.status_code, r.get_json(silent=True) and list(r.get_json().keys()))
token = None
try:
    body = r.get_json()
    token = (body.get('data') or {}).get('token') or body.get('token')
except Exception:
    pass
headers = {'Authorization': f'Bearer {token}'} if token else {}
print('token acquired:', bool(token))

def probe(name, method, url, **kw):
    try:
        r = getattr(c, method)(url, headers=headers, **kw)
        body = r.get_json(silent=True)
        keys = None
        if isinstance(body, dict):
            d = body.get('data', body)
            if isinstance(d, list) and d:
                keys = sorted(d[0].keys())
            elif isinstance(d, dict):
                keys = sorted(d.keys())
        print(f'{name}: {r.status_code} keys={keys}')
        return body
    except Exception as e:
        print(f'{name}: EXC {e}')

probe('demand.list', 'get', '/api/demand/list')
probe('demand.list.pageSize100', 'get', '/api/demand/list?pageSize=100')
d = probe('demand.detail', 'get', '/api/demand/DM2026070001')
probe('talent.list', 'get', '/api/talent/list?tab=external')
probe('config.email-accounts', 'get', '/api/config/email-accounts')
probe('config.resolve', 'get', '/api/config/email-accounts/resolve?email=test@qq.com')
probe('config.resolve.bad', 'get', '/api/config/email-accounts/resolve?email=notanemail')
probe('health', 'get', '/api/health')

# print samples for shape check
r = c.get('/api/demand/list', headers=headers)
rows = (r.get_json() or {}).get('data') or []
if rows:
    print('SAMPLE demand row:', json.dumps(rows[0], ensure_ascii=False)[:600])
r = c.get('/api/talent/list?tab=external', headers=headers)
tb = r.get_json() or {}
trows = tb.get('data')
if isinstance(trows, dict):
    ext = trows.get('ext') or trows.get('external') or []
    if ext:
        print('SAMPLE talent row keys:', sorted(ext[0].keys()))
        print('SAMPLE linkedDemands:', json.dumps(ext[0].get('linkedDemands'), ensure_ascii=False),
              'targetPosition:', ext[0].get('targetPosition'))
elif isinstance(trows, list) and trows:
    print('SAMPLE talent row keys:', sorted(trows[0].keys()))
