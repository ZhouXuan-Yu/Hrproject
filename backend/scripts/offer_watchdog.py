# -*- coding: utf-8 -*-
"""Offer 确认倒计时巡检 CLI（无 celery/Redis 环境的替代方案）。

用法（在 backend/ 目录下）：
    ./.venv/Scripts/python.exe scripts/offer_watchdog.py

每次执行做一轮巡检：
  1. 已发送 Offer 距上次提醒超过 OFFER_REMINDER_INTERVAL_HOURS（默认24h）
     → 给候选人发倒计时提醒邮件（复用招聘配置邮箱 SMTP）
  2. 超过 OFFER_CONFIRM_DEADLINE_DAYS（默认3天）未确认 → Offer 置为已过期，
     流程状态→已淘汰，候选人回流人才库

部署建议：Windows 可用「任务计划程序」每小时跑一次本脚本；
有 Redis 的环境直接起 celery beat 即可（tasks.notify.offer_followup 已注册）。
"""
import os
import sys

# 保证从任意目录运行都能 import app
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app import create_app
from app.services import hire_service


def main():
    app = create_app()
    with app.app_context():
        result = hire_service.offer_followup()
    print('巡检完成:')
    print('  倒计时提醒:', result['reminded'] or '无需提醒')
    print('  超时淘汰  :', result['expired'] or '无')
    print('  截止天数  :', result['deadlineDays'])


if __name__ == '__main__':
    main()
