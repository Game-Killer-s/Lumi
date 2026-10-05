const SENSITIVE_KEY = /password|token|secret|hash|authorization/i;

export function redact(value: unknown): unknown {
  if (Array.isArray(value)) {
    return value.map(redact);
  }

  if (value && typeof value === 'object') {
    const result: Record<string, unknown> = {};

    for (const [key, val] of Object.entries(value)) {
      result[key] = SENSITIVE_KEY.test(key) ? '[REDACTED]' : redact(val);
    }

    return result;
  }

  return value;
}

export function safeJson(value: unknown): string {
  try {
    return JSON.stringify(value);
  } catch {
    return '[unserializable]';
  }
}
