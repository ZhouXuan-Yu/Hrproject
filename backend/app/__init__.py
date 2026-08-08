from flask import Flask, g, request
import uuid
from config import config_map
from app.extensions import db, migrate, ma, cors


def create_app(config_name=None):
    """Flask application factory."""
    if config_name is None:
        config_name = 'development'

    app = Flask(__name__)
    app.config.from_object(config_map[config_name])

    # Initialize extensions
    db.init_app(app)
    migrate.init_app(app, db)
    ma.init_app(app)
    cors.init_app(app, origins=app.config.get('CORS_ORIGINS', 'http://127.0.0.1:7100'))

    # Register blueprints
    from app.api import register_blueprints
    register_blueprints(app)

    # Register SSE streaming blueprint
    from app.api.ai_stream import ai_stream_bp
    app.register_blueprint(ai_stream_bp, url_prefix='/api/ai/stream')

    # Register error handlers
    from app.api.errors import errors_bp
    app.register_blueprint(errors_bp)

    # Health check — includes dependency status for production monitoring
    @app.route('/api/v1/health')
    def health():
        import time
        components = {}

        # DB check
        try:
            from app.extensions import db
            db.session.execute(db.text('SELECT 1'))
            components['database'] = 'ok'
        except Exception:
            components['database'] = {'status': 'error', 'message': '数据库连接异常'}

        # Redis check
        try:
            from app.services.cache_service import cache_status
            cs = cache_status()
            components['redis'] = 'ok' if cs.get('connected') else 'unreachable'
        except Exception:
            components['redis'] = {'status': 'error', 'message': 'Redis 连接异常'}

        # DeepSeek check (non-blocking — just reports configured/not)
        try:
            from flask import current_app
            key = current_app.config.get('DEEPSEEK_API_KEY', '')
            if key:
                components['deepseek'] = 'configured'
            else:
                components['deepseek'] = 'unconfigured'
        except Exception:
            components['deepseek'] = 'unknown'

        all_ok = all(
            v == 'ok' or v == 'configured' or v == 'unconfigured' or v == 'unknown'
            if isinstance(v, str) else False
            for v in components.values()
        )

        return {
            'status': 'ok' if all_ok else 'degraded',
            'version': '0.2.0',
            'components': components,
        }

    # Request ID injection
    @app.before_request
    def inject_request_id():
        g.request_id = request.headers.get('X-Request-ID', str(uuid.uuid4()))

    # Tenant context (v0.1: hardcoded default)
    @app.before_request
    def inject_tenant():
        g.tenant_id = 1

    # Auth middleware
    from app.middleware.auth import init_auth_middleware
    init_auth_middleware(app)

    # ---- Resilience layer ------------------------------------------------

    # Request/response structured logging
    from app.middleware.logging import init_request_logging
    init_request_logging(app)

    # Rate limiter (configurable via RATE_LIMIT_ENABLED, default on)
    if app.config.get('RATE_LIMIT_ENABLED', True):
        from app.middleware.rate_limit import init_rate_limiter
        init_rate_limiter(app)

    return app
