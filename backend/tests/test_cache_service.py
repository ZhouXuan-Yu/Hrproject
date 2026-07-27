class FakeRedis:
    def __init__(self):
        self.store = {}
        self.expire_calls = []
        self.deleted = []
        self.set_calls = []

    def get(self, key):
        return self.store.get(key)

    def setex(self, key, ttl, value):
        self.expire_calls.append((key, ttl))
        self.store[key] = value
        return True

    def scan_iter(self, match=None, count=None):
        prefix = (match or '').replace('*', '')
        for key in list(self.store):
            if key.startswith(prefix):
                yield key

    def delete(self, *keys):
        for key in keys:
            self.deleted.append(key)
            self.store.pop(key, None)
        return len(keys)

    def set(self, key, value, nx=False, ex=None):
        self.set_calls.append((key, value, nx, ex))
        if nx and key in self.store:
            return False
        self.store[key] = value
        return True

    def ping(self):
        return True


def test_cached_uses_loader_once_on_hit(app, monkeypatch):
    from app.services import cache_service

    fake = FakeRedis()
    calls = {'count': 0}
    monkeypatch.setattr(cache_service, 'get_client', lambda: fake)
    app.config['CACHE_ENABLED'] = True

    def load():
        calls['count'] += 1
        return {'items': [{'id': 'A1'}], 'total': 1}

    with app.app_context():
        first = cache_service.cached('talent:list', {'page': 1}, load, ttl=30)
        second = cache_service.cached('talent:list', {'page': 1}, load, ttl=30)

    assert first == second
    assert calls['count'] == 1
    assert fake.expire_calls[0][1] == 30


def test_invalidate_namespace_deletes_only_matching_keys(app, monkeypatch):
    from app.services import cache_service

    fake = FakeRedis()
    monkeypatch.setattr(cache_service, 'get_client', lambda: fake)
    app.config['CACHE_ENABLED'] = True

    with app.app_context():
        talent_key = cache_service.build_key('talent:list', {'page': 1})
        demand_key = cache_service.build_key('demand:list', {'page': 1})
        fake.store[talent_key] = '{"ok": true}'
        fake.store[demand_key] = '{"ok": true}'
        deleted = cache_service.invalidate_namespace('talent:list')

    assert deleted == 1
    assert talent_key not in fake.store
    assert demand_key in fake.store


def test_lock_allows_progress_when_redis_is_absent(app, monkeypatch):
    from app.services import cache_service

    monkeypatch.setattr(cache_service, 'get_client', lambda: None)

    with app.app_context():
        with cache_service.lock('interview:create:test') as acquired:
            assert acquired is True
