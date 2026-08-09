"""AI 服务配置。"""
import os
from dotenv import load_dotenv

load_dotenv(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), '.env'))


class Settings:
    # DeepSeek
    DEEPSEEK_API_KEY: str = os.getenv('DEEPSEEK_API_KEY', '')
    DEEPSEEK_BASE_URL: str = os.getenv('DEEPSEEK_BASE_URL', 'https://api.deepseek.com')
    DEEPSEEK_MODEL: str = os.getenv('DEEPSEEK_MODEL', 'deepseek-chat')

    # 服务
    HOST: str = os.getenv('AI_SERVICE_HOST', '0.0.0.0')
    PORT: int = int(os.getenv('AI_SERVICE_PORT', '8100'))

    # Redis
    REDIS_URL: str = os.getenv('REDIS_URL', 'redis://127.0.0.1:6379/2')

    LOG_LEVEL: str = os.getenv('LOG_LEVEL', 'INFO')


settings = Settings()
