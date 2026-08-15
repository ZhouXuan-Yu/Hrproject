"""AI 服务配置。"""
import os
from dotenv import load_dotenv

load_dotenv(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), '.env'))


class Settings:
    # DeepSeek
    DEEPSEEK_API_KEY: str = os.getenv('DEEPSEEK_API_KEY', '')
    DEEPSEEK_BASE_URL: str = os.getenv('DEEPSEEK_BASE_URL', 'https://api.deepseek.com')
    DEEPSEEK_MODEL: str = os.getenv('DEEPSEEK_MODEL', 'deepseek-chat')

    # Dify（配置驱动：配了才走 Dify 编排，未配/失败回退 DeepSeek）
    DIFY_API_KEY: str = os.getenv('DIFY_API_KEY', '')
    DIFY_BASE_URL: str = os.getenv('DIFY_BASE_URL', 'https://api.dify.ai/v1')

    # 服务
    HOST: str = os.getenv('AI_SERVICE_HOST', '0.0.0.0')
    PORT: int = int(os.getenv('AI_SERVICE_PORT', '8100'))

    # Redis
    REDIS_URL: str = os.getenv('REDIS_URL', 'redis://127.0.0.1:6379/2')
    INTERNAL_TOKEN: str = os.getenv('AI_INTERNAL_TOKEN', '')
    ALLOWED_ORIGINS: list[str] = [
        item.strip() for item in os.getenv('AI_ALLOWED_ORIGINS', '').split(',') if item.strip()
    ]

    LOG_LEVEL: str = os.getenv('LOG_LEVEL', 'INFO')


settings = Settings()
