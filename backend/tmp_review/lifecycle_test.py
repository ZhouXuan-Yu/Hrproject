# -*- coding: utf-8 -*-
"""Write-path lifecycle test: create draft -> edit -> submit -> approve x3 -> delete. Cleans up (hard delete) after."""
import sys, os, io, json
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from app import create_app
app = create_app()
c = app.test_client()
r = c.post('/api/auth/login', json={'username': 'admin', 'password': 'admin123'})
token = (r.get_json().get('data') or {}).get('token')
H = {'Authorization': f'Bearer {token}'}

created_no = None
created_id = None
try:
    # 1. create draft
    r = c.post('/api/demand/create', json={'dept': '技术部', 'position': '对抗性审查测试岗', 'hc': 1,
                                           'urgency': '紧急', 'salary': '10K-20K', 'date': '2026-09-01',
                                           'desc': '临时测试需求，测完即删'}, headers=H)
    print('create:', r.status_code, r.get_json())
    created_no = r.get_json()['data']['id']

    # 2. edit draft (PATCH)
    r = c.patch(f'/api/demand/{created_no}', json={'position': '对抗性审查测试岗-改', 'urgency': '非常紧急'}, headers=H)
    print('patch:', r.status_code, r.get_json())

    # 3. submit
    r = c.post(f'/api/demand/{created_no}/submit', headers=H)
    print('submit:', r.status_code, r.get_json())
    # idempotent resubmit should fail cleanly (invalid transition), not 500
    r = c.post(f'/api/demand/{created_no}/submit', headers=H)
    print('resubmit (expect 4xx):', r.status_code, str(r.get_json())[:160])

    # 4. approve levels 1..3
    for lv in (1, 2, 3):
        r = c.post(f'/api/demand/{created_no}/approve', json={'level': lv, 'opinion': '测试通过'}, headers=H)
        print(f'approve L{lv}:', r.status_code, str(r.get_json())[:160])

    # 5. detail after full approval
    r = c.get(f'/api/demand/{created_no}', headers=H)
    d = r.get_json()['data']
    print('detail nodes:', json.dumps(d['approvalNodes'], ensure_ascii=False)[:400])

    # 6. delete open demand should be rejected
    r = c.delete(f'/api/demand/{created_no}', headers=H)
    print('delete open (expect 4xx):', r.status_code, str(r.get_json())[:160])
finally:
    # hard cleanup
    with app.app_context():
        from app.extensions import db
        from app.models.demand import RecruitDemand, DemandApproval
        d = RecruitDemand.query.filter_by(demand_no=created_no).first() if created_no else None
        if d:
            DemandApproval.query.filter_by(demand_id=d.id).delete()
            db.session.delete(d)
            db.session.commit()
            print('cleanup: hard-deleted', created_no)
        else:
            print('cleanup: nothing to delete')
