"""Candidate-facing confirm API (no login required).

  GET  /confirm/<token>      — H5 确认页（面试/Offer，接受/拒绝）
  POST /api/confirm/<token>  — 提交确认结果，数据回流系统
"""
import html as html_mod
import json
import logging

from flask import Blueprint, request

from app.utils.response import success, AppError

log = logging.getLogger(__name__)

bp = Blueprint('confirm', __name__)


@bp.route('/confirm/<token>')
def confirm_page(token):
    """候选人确认 H5 页面。"""
    from app.services.confirm_service import verify_confirm_token, get_confirm_page_data
    try:
        payload = verify_confirm_token(token)
        data = get_confirm_page_data(payload)
    except AppError as exc:
        return _render_error(exc.message), 400
    return _render_page(token, data)


@bp.route('/api/confirm/<token>', methods=['POST'])
def confirm_submit(token):
    """候选人提交 接受/拒绝。"""
    from app.services.confirm_service import verify_confirm_token, apply_confirm_action
    payload = verify_confirm_token(token)
    body = request.get_json(silent=True) or {}
    result = apply_confirm_action(
        payload,
        action=body.get('action', ''),
        reason=(body.get('reason') or '').strip(),
    )
    return success(result)


# ===========================================================================
# Inline H5 templates (mobile-first, no build step required)
# ===========================================================================

_PAGE = """<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>__TITLE__</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: -apple-system, "PingFang SC", "Microsoft YaHei", sans-serif;
         background: #f2f4f9; color: #222; padding: 16px; }
  .card { max-width: 480px; margin: 24px auto; background: #fff; border-radius: 14px;
          padding: 28px 22px; box-shadow: 0 4px 24px rgba(20,30,60,.08); }
  h1 { font-size: 20px; color: #4F6EF7; margin-bottom: 6px; }
  .sub { color: #888; font-size: 13px; margin-bottom: 18px; }
  .row { display: flex; padding: 10px 0; border-bottom: 1px solid #f0f2f7; font-size: 15px; }
  .row .k { width: 88px; color: #999; flex-shrink: 0; }
  .row .v { flex: 1; font-weight: 600; word-break: break-all; }
  .offer-body { background: #f6f8ff; border-radius: 10px; padding: 14px;
                font-size: 14px; line-height: 1.9; margin: 12px 0; white-space: pre-wrap; }
  .deadline { background: #fff8e6; color: #b45309; border-radius: 8px;
              padding: 10px 12px; font-size: 13px; margin: 16px 0; }
  .btns { display: flex; gap: 12px; margin-top: 20px; }
  button { flex: 1; padding: 14px; border: none; border-radius: 10px;
           font-size: 16px; font-weight: 700; cursor: pointer; }
  .accept { background: #4F6EF7; color: #fff; }
  .reject { background: #f2f4f9; color: #666; }
  button:disabled { opacity: .55; }
  .result { text-align: center; padding: 30px 0 10px; font-size: 16px; font-weight: 600; }
  .reason { width: 100%; margin-top: 14px; padding: 10px; border: 1px solid #e1e6ef;
            border-radius: 8px; font-size: 14px; display: none; font-family: inherit; }
  .already { text-align:center; color:#22a06b; font-weight:700; padding: 18px 0 4px; }
</style>
</head>
<body>
<div class="card">
  <h1>__TITLE__</h1>
  <div class="sub">__SUB__</div>
  __ROWS__
  __EXTRA__
  <div class="deadline">⏰ 请在 __DEADLINE__ 前完成确认</div>
  <div id="actionArea">
    <textarea id="reason" class="reason" rows="2" placeholder="可填写原因（选填）"></textarea>
    <div class="btns">
      <button class="accept" onclick="submit('accept')">✔ 接受</button>
      <button class="reject" onclick="toggleReject()">✘ 拒绝</button>
    </div>
  </div>
  <div class="result" id="resultArea" style="display:none"></div>
</div>
<script>
var TOKEN = "__TOKEN__";
var rejecting = false;
function toggleReject() {
  var r = document.getElementById('reason');
  if (!rejecting) {
    rejecting = true;
    r.style.display = 'block';
    event.target.textContent = '确认拒绝';
    return;
  }
  submit('reject');
}
function submit(action) {
  var btns = document.querySelectorAll('button');
  btns.forEach(function(b){ b.disabled = true; });
  fetch('/api/confirm/' + TOKEN, {
    method: 'POST',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({action: action, reason: document.getElementById('reason').value})
  }).then(function(r){ return r.json(); }).then(function(resp) {
    var d = resp.data || {};
    showResult(d.message || (action === 'accept' ? '已确认' : '已反馈'));
  }).catch(function() {
    showResult('网络异常，请稍后重试或联系 HR');
    btns.forEach(function(b){ b.disabled = false; });
  });
}
function showResult(text) {
  document.getElementById('actionArea').style.display = 'none';
  var el = document.getElementById('resultArea');
  el.style.display = 'block';
  el.textContent = text;
}
</script>
</body>
</html>"""

_ERROR_PAGE = """<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>提示</title>
<style>body{font-family:-apple-system,"PingFang SC",sans-serif;background:#f2f4f9;padding:16px}
.card{max-width:480px;margin:60px auto;background:#fff;border-radius:14px;padding:40px 24px;
text-align:center;box-shadow:0 4px 24px rgba(20,30,60,.08);color:#666}</style></head>
<body><div class="card"><h2 style="color:#e11d48;margin-bottom:12px">⚠ 无法打开</h2>__MSG__</div></body></html>"""


def _esc(s):
    return html_mod.escape(str(s or ''))


def _render_page(token, data):
    rows = []
    kind = data.get('kind')
    if kind == 'interview':
        rows.append(('候选人', data.get('candidateName')))
        rows.append(('面试岗位', data.get('position')))
        rows.append(('轮次', data.get('round')))
        rows.append(('时间', data.get('time')))
        rows.append(('方式', data.get('method')))
        if data.get('meetingUrl'):
            url = _esc(data['meetingUrl'])
            rows.append(('会议链接', f'<a href="{url}" target="_blank">{url}</a>'))
        if data.get('address'):
            rows.append(('地点', data.get('address')))
        sub = '请核对以下面试安排，并确认是否参加'
        extra = ''
        accept_label_already = {'accept': '您已确认参加本场面试', 'reject': '您已反馈无法参加本场面试'}
    else:  # offer
        rows.append(('候选人', data.get('candidateName')))
        rows.append(('录用岗位', data.get('position')))
        rows.append(('薪资', data.get('salary')))
        sub = '请查收您的录用通知，并确认是否接受'
        extra = f'<div class="offer-body">{_esc(data.get("offerContent"))}</div>' if data.get('offerContent') else ''
        accept_label_already = {'accepted': '您已接受该 Offer', 'rejected': '您已拒绝该 Offer'}

    rows_html = ''.join(
        f'<div class="row"><div class="k">{_esc(k)}</div><div class="v">{v if str(v).startswith("<a") else _esc(v)}</div></div>'
        for k, v in rows
    )

    already = data.get('already')
    if already and already in accept_label_already:
        action_html = f'<div class="already">{accept_label_already[already]}</div>'
        page = _PAGE.replace(
            '<div id="actionArea">', f'<div id="actionArea" style="display:none">')
        page = page.replace(
            '<div class="result" id="resultArea" style="display:none"></div>',
            action_html)
    else:
        page = _PAGE

    page = (page
            .replace('__TITLE__', _esc(data.get('title', '确认')))
            .replace('__SUB__', _esc(sub))
            .replace('__ROWS__', rows_html)
            .replace('__EXTRA__', extra)
            .replace('__DEADLINE__', _esc(data.get('deadline', '—')))
            .replace('__TOKEN__', _esc(token)))
    return page


def _render_error(msg):
    return _ERROR_PAGE.replace('__MSG__', _esc(msg))
