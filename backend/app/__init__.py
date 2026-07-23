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
    cors.init_app(app, origins=app.config.get('CORS_ORIGINS', '*'))

    # Register blueprints
    from app.api import register_blueprints
    register_blueprints(app)

    # Register SSE streaming blueprint
    from app.api.ai_stream import ai_stream_bp
    app.register_blueprint(ai_stream_bp, url_prefix='/api/ai/stream')

    # Register error handlers
    from app.api.errors import errors_bp
    app.register_blueprint(errors_bp)

    # Health check
    @app.route('/api/v1/health')
    def health():
        return {'status': 'ok', 'version': '0.1.0'}

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
