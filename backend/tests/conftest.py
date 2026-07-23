import os

# Set test environment variables BEFORE importing the app.
# Config reads these at module level, so they must be set first.
os.environ['SECRET_KEY'] = 'test-secret-key-for-testing'
os.environ['JWT_SECRET_KEY'] = 'test-jwt-secret-key-for-testing'
os.environ['DEEPSEEK_API_KEY'] = 'test-deepseek-api-key-for-testing'
os.environ['MOCK_FALLBACK'] = 'false'
os.environ['DATABASE_URL'] = 'mysql+pymysql://test:test@127.0.0.1:3306/hr_recruit_test'

import pytest

# Import create_app with a module-level alias so that the bare name 'app'
# is never bound to the 'app' package at module scope.  This avoids a Python
# scope conflict between the 'app' fixture and the 'app' package when pytest
# injects fixture values.
from app import create_app as _create_app
from app.extensions import db as _db

# Register all models with SQLAlchemy at module level so that
# _db.create_all() knows about every table.  Using 'as' prevents the
# 'app' name from leaking into conftest's module namespace.
import app.models as _app_models  # noqa: F401
from app.models.iam import IamUser, RecruitApprovalIdentity


def _seed_iam_data():
    users = [
        IamUser(user_id=1, username='admin', real_name='管理员', role_code='admin', status=1),
        IamUser(user_id=2, username='hr', real_name='张HR', role_code='hr', status=1),
        IamUser(user_id=3, username='dept_head', real_name='部门负责人', role_code='dept_head', status=1),
        IamUser(user_id=8, username='executive', real_name='高管', role_code='executive', status=1),
    ]
    identities = [
        RecruitApprovalIdentity(approve_level=1, identity_code='dept_head', identity_name='部门负责人', role_code='dept_head', user_id=3, status=1),
        RecruitApprovalIdentity(approve_level=2, identity_code='hr', identity_name='HR', role_code='hr', user_id=2, status=1),
        RecruitApprovalIdentity(approve_level=3, identity_code='executive', identity_name='高管', role_code='executive', user_id=8, status=1),
    ]
    _db.session.add_all(users + identities)
    _db.session.commit()


@pytest.fixture
def app():
    """Create Flask app for testing with in-memory SQLite.

    Uses the 'testing' config which sets SQLALCHEMY_DATABASE_URI to
    'sqlite:///:memory:'. All tables are created before each test and
    dropped after.
    """
    application = _create_app('testing')
    with application.app_context():
        _db.create_all()
        _seed_iam_data()
        yield application
        _db.session.remove()
        _db.drop_all()


@pytest.fixture
def client(app):
    """Flask test client bound to the in-memory app."""
    return app.test_client()


@pytest.fixture
def auth_headers(app):
    """Generate auth headers with a valid JWT token for the admin role.

    The token is signed with the app's JWT_SECRET_KEY, so the auth
    middleware will accept it.
    """
    import jwt
    from datetime import datetime, timedelta

    payload = {
        'user_id': 1,
        'role': 'admin',
        'tenant_id': 1,
        'exp': datetime.utcnow() + timedelta(hours=1),
    }
    token = jwt.encode(
        payload,
        app.config['JWT_SECRET_KEY'],
        algorithm='HS256',
    )
    return {'Authorization': f'Bearer {token}'}
