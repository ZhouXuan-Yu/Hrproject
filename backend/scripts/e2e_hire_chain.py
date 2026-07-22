# -*- coding: utf-8 -*-
"""真实接口链路验证（一次性）：面试评价→淘汰/Offer→候选人确认→录用/拒绝/超时淘汰。

走完即清理自己产生的脏数据（软删 interview/record/offer/process/entry/remind_log），
不触碰库内既有测试数据。

用法：./.venv/Scripts/python.exe scripts/e2e_hire_chain.py
"""
import json
import os
import sys
import urllib.request

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

BASE = 'http://127.0.0.1:5000'
PASS = True


def req(method, path, body=None, token=None, raw=False):
    r = urllib.request.Request(BASE + path, method=method)
    r.add_header('Content-Type', 'application/json')
    if token:
        r.add_header('Authorization', f'Bearer {token}')
    data = json.dumps(body).encode() if body is not None else None
    with urllib.request.urlopen(r, data=data, timeout=30) as resp:
        text = resp.read().decode()
        return text if raw else json.loads(text)


def check(label, cond, detail=''):
    global PASS
    mark = 'PASS' if cond else 'FAIL'
    if not cond:
        PASS = False
    print(f'[{mark}] {label} {detail if not cond else ""}')


def login():
    return req('POST', '/api/auth/login', {'username': 'admin', 'password': 'admin123'})['data']['token']


def list_item(token, book_id):
    items = req('GET', '/api/interview/list?pageSize=100', token=token)['data']
    for it in items:
        if it['id'] == f'INT{book_id:04d}':
            return it
    return None


def make_interview(token, candidate_no, demand_no):
    r = req('POST', '/api/interview/create', {
        'candidateNo': candidate_no, 'demandNo': demand_no,
        'date': '08-01', 'time': '10:00', 'mode_id': '4', 'mode': '线下',
        'round': '终面(2轮)', 'address': '北京市朝阳区',
    }, token=token)['data']
    return r['book_id']


def complete_and_evaluate(token, book_id, result):
    req('POST', f'/api/interview/{book_id}/complete', {'is_arrive': 1}, token)
    return req('POST', f'/api/interview/{book_id}/evaluate', {
        'result': result, 'score': 80, 'comment': '链路验证：综合评价理由'}, token)['data']


def main():
    from app import create_app
    from app.extensions import db
    from app.models.hire import Offer, Entry
    from app.models.process import RecruitProcess

    app = create_app()
    token = login()
    created = {'books': [], 'offers': []}

    try:
        # ── 链路1：评价拒绝 → 已淘汰（回归 bug）──
        bid = make_interview(token, 'C2026070008', 'DM2026070002')
        created['books'].append(bid)
        ev = complete_and_evaluate(token, bid, 'fail')
        it = list_item(token, bid)
        check('链路1 评价拒绝 newStatusLabel=已淘汰', ev['newStatusLabel'] == '已淘汰')
        check('链路1 列表 statusLabel=已淘汰', it['statusLabel'] == '已淘汰', f"got {it['statusLabel']}")
        check('链路1 列表 result=reject（前端不再显示已入职）', it['result'] == 'reject')

        # ── 链路2：评价通过 → 发Offer → 候选人接受 → 已录用 ──
        bid2 = make_interview(token, 'C2026070009', 'DM2026070003')
        created['books'].append(bid2)
        ev2 = complete_and_evaluate(token, bid2, 'pass')
        check('链路2 评价通过 → 待录用', ev2['newStatusLabel'] == '待录用')

        send = req('POST', f'/api/interview/{bid2}/offer', {
            'offer_content': '链路验证 Offer', 'salary_json': {'baseSalary': 20000}}, token)['data']
        offer_no = send['id']
        created['offers'].append(offer_no)
        check('链路2 Offer 已发送(sent)', send['sent'] is True)
        it2 = list_item(token, bid2)
        check('链路2 面试列表=Offer待确认', it2['statusLabel'] == 'Offer待确认' and it2['offerStatus'] == 1,
              f"got {it2['statusLabel']}/{it2['offerStatus']}")

        with app.app_context():
            from app.services.confirm_service import generate_confirm_token
            tok = generate_confirm_token('offer', offer_no)
        page = req('GET', f'/confirm/{tok}', raw=True)
        check('链路2 候选人确认页可打开', '录用 Offer 确认' in page)
        cf = req('POST', f'/api/confirm/{tok}', {'action': 'accept'})
        check('链路2 候选人接受成功', cf['data']['ok'] is True)
        with app.app_context():
            offer = Offer.query.filter_by(offer_no=offer_no).first()
            check('链路2 Offer 状态=已接受(2)', offer.offer_status == 2)
            proc = RecruitProcess.query.filter_by(id=offer.process_id).first()
            check('链路2 流程状态=已录用(6)', proc and proc.process_status == 6,
                  f"got {proc.process_status if proc else None}")
            entry = Entry.query.filter_by(resume_id=offer.resume_id, is_deleted=0).first()
            check('链路2 自动生成入职单', entry is not None)
        it2b = list_item(token, bid2)
        check('链路2 面试列表=已录用', it2b['statusLabel'] == '已录用', f"got {it2b['statusLabel']}")

        # ── 链路3：候选人拒绝 Offer → 已拒绝、流程结束 ──
        bid3 = make_interview(token, 'C2026070010', 'DM2026070002')
        created['books'].append(bid3)
        complete_and_evaluate(token, bid3, 'pass')
        send3 = req('POST', f'/api/interview/{bid3}/offer', {
            'offer_content': '链路验证 Offer(拒)', 'salary_json': {}}, token)['data']
        offer_no3 = send3['id']
        created['offers'].append(offer_no3)
        with app.app_context():
            from app.services.confirm_service import generate_confirm_token as gen
            tok3 = gen('offer', offer_no3)
        cf3 = req('POST', f'/api/confirm/{tok3}', {'action': 'reject', 'reason': '薪资不达预期'})
        check('链路3 候选人拒绝成功', cf3['data']['ok'] is True)
        with app.app_context():
            o3 = Offer.query.filter_by(offer_no=offer_no3).first()
            check('链路3 Offer 状态=已拒绝(3)', o3.offer_status == 3)
            p3 = RecruitProcess.query.filter_by(id=o3.process_id).first()
            check('链路3 流程状态=放弃(7)', p3 and p3.process_status == 7)
        it3 = list_item(token, bid3)
        check('链路3 面试列表=已拒绝', it3['statusLabel'] == '已拒绝', f"got {it3['statusLabel']}")

        # ── 链路4：超 3 天未确认 → 自动淘汰 ──
        bid4 = make_interview(token, 'C2026070011', 'DM2026070003')
        created['books'].append(bid4)
        complete_and_evaluate(token, bid4, 'pass')
        send4 = req('POST', f'/api/interview/{bid4}/offer', {
            'offer_content': '链路验证 Offer(过期)', 'salary_json': {}}, token)['data']
        offer_no4 = send4['id']
        created['offers'].append(offer_no4)
        from datetime import datetime, timedelta
        with app.app_context():
            o4 = Offer.query.filter_by(offer_no=offer_no4).first()
            o4.send_time = datetime.now() - timedelta(days=4)  # 回溯发送时间
            db.session.commit()
        fu = req('POST', '/api/hire/offers/followup', {}, token)['data']
        check('链路4 巡检判定过期', offer_no4 in fu['expired'], f"got {fu['expired']}")
        with app.app_context():
            o4 = Offer.query.filter_by(offer_no=offer_no4).first()
            check('链路4 Offer 状态=已过期(4)', o4.offer_status == 4)
            p4 = RecruitProcess.query.filter_by(id=o4.process_id).first()
            check('链路4 流程状态=已淘汰(4)', p4 and p4.process_status == 4)
        it4 = list_item(token, bid4)
        check('链路4 面试列表=已淘汰', it4['statusLabel'] == '已淘汰', f"got {it4['statusLabel']}")

        # ── 链路5：倒计时提醒去重（间隔内不重复）──
        from app.models.hire import OfferRemindLog
        with app.app_context():
            n_before = OfferRemindLog.query.count()
        req('POST', '/api/hire/offers/followup', {}, token)
        with app.app_context():
            n_after = OfferRemindLog.query.count()
        check('链路5 间隔内巡检不重复发提醒', n_after == n_before, f'{n_before}->{n_after}')

    finally:
        # ── 清理本轮脏数据（软删），不碰既有测试数据 ──
        with app.app_context():
            from app.models.interview import InterviewBook, InterviewRecord
            from app.models.hire import OfferRemindLog, HireEvent
            for no in created['offers']:
                o = Offer.query.filter_by(offer_no=no).first()
                if o:
                    for e in HireEvent.query.filter_by(offer_id=o.id).all():
                        for en in Entry.query.filter_by(event_id=e.id).all():
                            en.soft_delete()
                        e.soft_delete()
                    for lg in OfferRemindLog.query.filter_by(offer_id=o.id).all():
                        lg.soft_delete()
                    p = RecruitProcess.query.filter_by(id=o.process_id).first()
                    if p and p.process_no.startswith('RP'):
                        p.soft_delete()
                    o.soft_delete()
            for b in created['books']:
                bk = InterviewBook.query.filter_by(id=b).first()
                if bk:
                    for rec in InterviewRecord.query.filter_by(book_id=bk.id).all():
                        rec.soft_delete()
                    p = RecruitProcess.query.filter_by(id=bk.process_id).first()
                    if p and p.process_no.startswith('RP'):
                        p.soft_delete()
                    bk.soft_delete()
            # 候选人锁定状态复位
            from app.models.candidate import Candidate
            for cid in (23, 24, 25, 26):
                c = db.session.get(Candidate, cid)
                if c and c.status == 'locked':
                    c.status = 'available'
            db.session.commit()
        print('脏数据已清理')

    print('\n总体结果:', 'ALL PASS' if PASS else 'HAS FAILURES')
    sys.exit(0 if PASS else 1)


if __name__ == '__main__':
    main()
