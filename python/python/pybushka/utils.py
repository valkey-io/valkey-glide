def to_url(host, port=6379, user="", password="", tls_enabled=False) -> str:
    protocol = "rediss" if tls_enabled else "redis"
    auth = ""
    if user or password:
        auth = f"{user}{':' if user else ''}"
        auth += f"{password}{'@' if password else ''}"
    return f"{protocol}://{auth}{host}:{port}"
