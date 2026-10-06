#!/usr/bin/env python3
from __future__ import annotations

import argparse
import json
import threading
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlparse


class CloudState:
    def __init__(self) -> None:
        self.lock = threading.Lock()
        self.data: dict[str, object] = {}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", required=True)
    parser.add_argument("--port", type=int, required=True)
    args = parser.parse_args()

    root = Path(args.root).resolve()
    state = CloudState()

    class Handler(SimpleHTTPRequestHandler):
        def __init__(self, *handler_args, **handler_kwargs):
            super().__init__(*handler_args, directory=str(root), **handler_kwargs)

        def log_message(self, format: str, *values) -> None:
            pass

        def do_GET(self) -> None:
            if urlparse(self.path).path != "/__cloud":
                super().do_GET()
                return
            with state.lock:
                payload = json.dumps(state.data, separators=(",", ":")).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

        def do_POST(self) -> None:
            if urlparse(self.path).path != "/__cloud":
                self.send_error(404)
                return
            length = int(self.headers.get("Content-Length", "0") or "0")
            if length <= 0 or length > 256 * 1024:
                self.send_error(413)
                return
            try:
                body = self.rfile.read(length)
                decoded = json.loads(body.decode("utf-8"))
                if not isinstance(decoded, dict):
                    raise ValueError("cloud body must be an object")
            except Exception:
                self.send_error(400)
                return
            with state.lock:
                state.data = decoded
            payload = b'{"ok":true}'
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(payload)))
            self.end_headers()
            self.wfile.write(payload)

    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
