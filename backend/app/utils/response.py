from flask import jsonify, current_app

# JavaScript Number.MAX_SAFE_INTEGER = 2^53 - 1
_JS_MAX_SAFE_INTEGER = 2**53 - 1  # 9007199254740991


def _safe_json_value(obj):
    """递归将超过 JS 安全整数范围的 int 转为字符串，防止前端精度丢失。"""
    if isinstance(obj, dict):
        return {k: _safe_json_value(v) for k, v in obj.items()}
    if isinstance(obj, (list, tuple)):
        return [_safe_json_value(v) for v in obj]
    if isinstance(obj, int) and not isinstance(obj, bool):
        if obj > _JS_MAX_SAFE_INTEGER or obj < -_JS_MAX_SAFE_INTEGER:
            return str(obj)
    return obj


def should_mock_fallback():
    """Check if mock fallback is enabled.

    When False (default for production), DB errors are surfaced as real AppError.
    When True (development with no DB), services silently fall back to mock data.
    """
    try:
        return current_app.config.get('MOCK_FALLBACK', False)
    except RuntimeError:
        return False


def safe_int(val, default=0, min_val=None, max_val=None):
    """Safely convert a value to int with bounds checking.

    Returns (value, None) on success, or (default, error_msg) on failure.
    """
    if val is None or val == '':
        return default, None
    try:
        result = int(val)
        if min_val is not None and result < min_val:
            return default, f'值不能小于 {min_val}'
        if max_val is not None and result > max_val:
            return default, f'值不能大于 {max_val}'
        return result, None
    except (ValueError, TypeError):
        return default, f'无效的整数: {val}'


def success(data=None, msg='ok', **kwargs):
    """Unified success response: { "data": ..., ...extra }"""
    body = {'data': _safe_json_value(data), 'message': msg}
    body.update(kwargs)
    return jsonify(body), 200


def success_list(items, total, page=1, page_size=20):
    """Unified list response: { "data": [...], "total": N, "page": N, "pageSize": N }

    NOTE: The frontend consumes response.data as the direct array:
      interviewData = listRes.data ?? ...  → accesses [...]
      demandList = apiDemands?.data || ... → accesses [...]
    So data MUST be the raw array, not a wrapper object.
    """
    return jsonify({
        'data': _safe_json_value(items),
        'total': total,
        'page': page,
        'pageSize': page_size,
    }), 200


def error(code, message, status_code=400, fields=None):
    """Unified error response: { "error": { "code": "...", "message": "...", "fields": {...} } }

    Args:
        code: Machine-readable error code (e.g. 'VALIDATION_ERROR').
        message: Human-readable error description.
        status_code: HTTP status code (default 400).
        fields: Optional dict of field-level errors for validation responses.
    """
    body = {
        'error': {
            'code': code,
            'message': message,
        }
    }
    if fields:
        body['error']['fields'] = fields
    return jsonify(body), status_code


class AppError(Exception):
    """Business exception — caught by global error handler."""
    def __init__(self, code, message, status_code=400):
        self.code = code
        self.message = message
        self.status_code = status_code
        super().__init__(message)
