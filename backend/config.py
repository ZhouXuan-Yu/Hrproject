import os
from dotenv import load_dotenv

# Always load .env from backend/ directory regardless of CWD
_dotenv_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), '.env')
load_dotenv(dotenv_path=_dotenv_path)


class Config:
    """Base configuration."""
    SECRET_KEY = os.getenv('SECRET_KEY')
    if not SECRET_KEY:
        raise ValueError("SECRET_KEY environment variable is required")

    # Database — defaults to SQLite for zero-setup development
    SQLALCHEMY_DATABASE_URI = os.getenv(
        'DATABASE_URL',
        'sqlite:///' + os.path.join(os.path.dirname(os.path.abspath(__file__)), 'hr_recruit.db')
    )
    SQLALCHEMY_TRACK_MODIFICATIONS = False

    # SQLite 并发加固：Web 进程与 Celery 邮件同步任务共用同一个 sqlite 文件，
    # 同步任务长时间写库时 Web 请求直接 500（database is locked）。
    # busy_timeout 让写请求排队等待而不是立刻报错；WAL 允许读写并发。
    if SQLALCHEMY_DATABASE_URI.startswith('sqlite'):
        SQLALCHEMY_ENGINE_OPTIONS = {
            'connect_args': {'timeout': 30},  # busy_timeout = 30s
        }

    # Redis / Celery
    CELERY_BROKER_URL = os.getenv('REDIS_URL', 'redis://127.0.0.1:6379/0')
    CELERY_RESULT_BACKEND = os.getenv('REDIS_URL', 'redis://127.0.0.1:6379/0')

    # JWT — required in all environments
    JWT_SECRET_KEY = os.getenv('JWT_SECRET_KEY')
    if not JWT_SECRET_KEY:
        raise ValueError("JWT_SECRET_KEY environment variable is required")
    JWT_ACCESS_TOKEN_EXPIRES = int(os.getenv('JWT_ACCESS_TOKEN_EXPIRES', '3600'))

    # Dify AI (deprecated, kept for compatibility)
    DIFY_API_BASE_URL = os.getenv('DIFY_API_BASE_URL', '')
    DIFY_API_KEY = os.getenv('DIFY_API_KEY', '')
    DIFY_WORKFLOW_RESUME_PARSE = os.getenv('DIFY_WORKFLOW_RESUME_PARSE', '')
    DIFY_WORKFLOW_MATCH = os.getenv('DIFY_WORKFLOW_MATCH', '')
    DIFY_WORKFLOW_INTERVIEW_QA = os.getenv('DIFY_WORKFLOW_INTERVIEW_QA', '')

    # DeepSeek AI
    # REVIEW: 原为必填（空值抛 ValueError），改为可选。
    # 留空时系统从数据库 t_hr_api_key 读取网页端配置的 key，支持线上配置无需重启。
    DEEPSEEK_API_KEY = os.environ.get('DEEPSEEK_API_KEY', '')
    DEEPSEEK_BASE_URL = os.environ.get('DEEPSEEK_BASE_URL', 'https://api.deepseek.com')
    DEEPSEEK_MODEL = os.environ.get('DEEPSEEK_MODEL', 'deepseek-chat')

    # Feishu
    FEISHU_APP_ID = os.getenv('FEISHU_APP_ID', '')
    FEISHU_APP_SECRET = os.getenv('FEISHU_APP_SECRET', '')
    FEISHU_RECIPIENT_OPEN_IDS = os.getenv('FEISHU_RECIPIENT_OPEN_IDS', '')  # JSON map name→open_id

    # IAM
    IAM_DB_URL = os.getenv('IAM_DB_URL', '')

    # CORS
    CORS_ORIGINS = os.getenv('CORS_ORIGINS', 'http://127.0.0.1:7100').split(',')

    # Mock fallback — when False, DB errors are surfaced instead of silently
    # using mock data. Set to True only in development when DB is unavailable.
    MOCK_FALLBACK = os.environ.get('MOCK_FALLBACK', 'false').lower() in ('true', '1', 'yes')

    # Legacy alias — MOCK_MODE controls the same behavior
    MOCK_MODE = os.environ.get('MOCK_MODE', 'false').lower() in ('true', '1', 'yes')

    # Tenant
    DEFAULT_TENANT_ID = 'default'

    # ------------------------------------------------------------------
    # Resilience layer
    # ------------------------------------------------------------------
    RATE_LIMIT_ENABLED = os.environ.get('RATE_LIMIT_ENABLED', 'true').lower() == 'true'
    RATE_LIMIT_DEFAULT = int(os.environ.get('RATE_LIMIT_DEFAULT', '60'))  # requests per minute
    REQUEST_LOG_DIR = os.environ.get('REQUEST_LOG_DIR', os.path.join(os.path.dirname(__file__), 'logs'))
    CIRCUIT_BREAKER_THRESHOLD = int(os.environ.get('CIRCUIT_BREAKER_THRESHOLD', '5'))
    CIRCUIT_BREAKER_TIMEOUT = int(os.environ.get('CIRCUIT_BREAKER_TIMEOUT', '30'))  # seconds

    # Fallback strategies: 'cache' | 'local_ai' | 'mock' | 'error'
    DEEPSEEK_FALLBACK = os.environ.get('DEEPSEEK_FALLBACK', 'cache')

    # ------------------------------------------------------------------
    # Offer 候选人确认倒计时
    # OFFER_CONFIRM_DEADLINE_DAYS: Offer 发出后候选人确认截止天数（超时自动淘汰）
    # OFFER_REMINDER_INTERVAL_HOURS: 倒计时提醒邮件发送间隔（每天一次=24）
    # 测试可调小（如 0.04 小时≈2.5分钟）验证逻辑
    # ------------------------------------------------------------------
    OFFER_CONFIRM_DEADLINE_DAYS = float(os.environ.get('OFFER_CONFIRM_DEADLINE_DAYS', '3'))
    OFFER_REMINDER_INTERVAL_HOURS = float(os.environ.get('OFFER_REMINDER_INTERVAL_HOURS', '24'))


class DevelopmentConfig(Config):
    DEBUG = True


class ProductionConfig(Config):
    DEBUG = False
    # Production: override with MySQL via env var
    # DATABASE_URL=mysql+pymysql://root:password@127.0.0.1:3306/hr_recruit


class TestingConfig(Config):
    TESTING = True
    SQLALCHEMY_DATABASE_URI = 'sqlite:///:memory:'


config_map = {
    'development': DevelopmentConfig,
    'production': ProductionConfig,
    'testing': TestingConfig,
}
