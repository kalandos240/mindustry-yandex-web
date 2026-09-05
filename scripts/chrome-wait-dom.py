#!/usr/bin/env python3
"""Run headless Chrome and wait for asynchronous DOM markers through raw CDP.

`google-chrome --dump-dom` exits around the page load event and therefore cannot
reliably validate IndexedDB or other asynchronous browser subsystems. This helper
keeps Chrome alive normally, connects to the DevTools Protocol with only Python's
standard library, polls the live DOM, then prints the final HTML for shell asserts.
"""

from __future__ import annotations

import argparse
import base64
import json
import os
import socket
import struct
import subprocess
import sys
import time
import urllib.request
from pathlib import Path
from urllib.parse import urlparse


def recv_exact(sock: socket.socket, count: int) -> bytes:
    chunks: list[bytes] = []
    remaining = count
    while remaining:
        chunk = sock.recv(remaining)
        if not chunk:
            raise ConnectionError("WebSocket closed while receiving CDP data")
        chunks.append(chunk)
        remaining -= len(chunk)
    return b"".join(chunks)


class WebSocket:
    def __init__(self, url: str):
        parsed = urlparse(url)
        if parsed.scheme != "ws":
            raise ValueError(f"Unsupported CDP WebSocket URL: {url}")
        self.sock = socket.create_connection((parsed.hostname, parsed.port or 80), timeout=5)
        self.sock.settimeout(5)
        key = base64.b64encode(os.urandom(16)).decode("ascii")
        path = parsed.path or "/"
        if parsed.query:
            path += "?" + parsed.query
        request = (
            f"GET {path} HTTP/1.1\r\n"
            f"Host: {parsed.hostname}:{parsed.port or 80}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            "Sec-WebSocket-Version: 13\r\n\r\n"
        ).encode("ascii")
        self.sock.sendall(request)
        response = bytearray()
        while b"\r\n\r\n" not in response:
            response.extend(self.sock.recv(4096))
            if len(response) > 65536:
                raise ConnectionError("Oversized WebSocket handshake")
        status = bytes(response).split(b"\r\n", 1)[0]
        if b" 101 " not in status:
            raise ConnectionError(f"CDP WebSocket upgrade failed: {status!r}")

    def send_text(self, text: str) -> None:
        payload = text.encode("utf-8")
        mask = os.urandom(4)
        length = len(payload)
        header = bytearray([0x81])
        if length < 126:
            header.append(0x80 | length)
        elif length <= 0xFFFF:
            header.append(0x80 | 126)
            header.extend(struct.pack("!H", length))
        else:
            header.append(0x80 | 127)
            header.extend(struct.pack("!Q", length))
        header.extend(mask)
        masked = bytes(value ^ mask[index & 3] for index, value in enumerate(payload))
        self.sock.sendall(header + masked)

    def recv_text(self) -> str:
        fragments = bytearray()
        while True:
            first, second = recv_exact(self.sock, 2)
            final = bool(first & 0x80)
            opcode = first & 0x0F
            masked = bool(second & 0x80)
            length = second & 0x7F
            if length == 126:
                length = struct.unpack("!H", recv_exact(self.sock, 2))[0]
            elif length == 127:
                length = struct.unpack("!Q", recv_exact(self.sock, 8))[0]
            mask = recv_exact(self.sock, 4) if masked else b""
            payload = recv_exact(self.sock, length)
            if masked:
                payload = bytes(value ^ mask[index & 3] for index, value in enumerate(payload))
            if opcode == 0x8:
                raise ConnectionError("CDP WebSocket closed")
            if opcode == 0x9:
                self._send_control(0xA, payload)
                continue
            if opcode in (0x1, 0x0):
                fragments.extend(payload)
                if final:
                    return fragments.decode("utf-8")

    def _send_control(self, opcode: int, payload: bytes) -> None:
        mask = os.urandom(4)
        if len(payload) >= 126:
            raise ValueError("Oversized WebSocket control frame")
        header = bytes([0x80 | opcode, 0x80 | len(payload)]) + mask
        masked = bytes(value ^ mask[index & 3] for index, value in enumerate(payload))
        self.sock.sendall(header + masked)

    def close(self) -> None:
        try:
            self.sock.close()
        except OSError:
            pass


def target_websocket(port: int, wanted_url: str, deadline: float) -> str:
    endpoint = f"http://127.0.0.1:{port}/json/list"
    while time.monotonic() < deadline:
        try:
            with urllib.request.urlopen(endpoint, timeout=1) as response:
                targets = json.load(response)
            for target in targets:
                if target.get("type") == "page" and target.get("webSocketDebuggerUrl"):
                    current = target.get("url", "")
                    if current == wanted_url or wanted_url in current:
                        return str(target["webSocketDebuggerUrl"])
            for target in targets:
                if target.get("type") == "page" and target.get("webSocketDebuggerUrl"):
                    return str(target["webSocketDebuggerUrl"])
        except Exception:
            pass
        time.sleep(0.1)
    raise TimeoutError("Chrome DevTools target did not appear")


def evaluate(ws: WebSocket, message_id: int, expression: str) -> str:
    ws.send_text(json.dumps({
        "id": message_id,
        "method": "Runtime.evaluate",
        "params": {"expression": expression, "returnByValue": True},
    }))
    while True:
        payload = json.loads(ws.recv_text())
        if payload.get("id") != message_id:
            continue
        if "error" in payload:
            raise RuntimeError(f"CDP Runtime.evaluate failed: {payload['error']}")
        result = payload.get("result", {}).get("result", {})
        return str(result.get("value", ""))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--url", required=True)
    parser.add_argument("--profile", required=True)
    parser.add_argument("--timeout", type=float, default=25.0)
    parser.add_argument("--port", type=int, default=9223)
    parser.add_argument("--require", action="append", default=[])
    parser.add_argument("--chrome", default="google-chrome")
    args = parser.parse_args()

    profile = Path(args.profile)
    profile.mkdir(parents=True, exist_ok=True)
    deadline = time.monotonic() + args.timeout
    command = [
        args.chrome,
        "--headless=new",
        "--no-sandbox",
        "--disable-dev-shm-usage",
        "--use-gl=angle",
        "--use-angle=swiftshader",
        "--enable-unsafe-swiftshader",
        f"--remote-debugging-port={args.port}",
        f"--user-data-dir={profile}",
        args.url,
    ]
    process = subprocess.Popen(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    ws: WebSocket | None = None
    last_html = ""
    try:
        ws = WebSocket(target_websocket(args.port, args.url, deadline))
        message_id = 1
        while time.monotonic() < deadline:
            last_html = evaluate(ws, message_id, "document.documentElement.outerHTML")
            message_id += 1
            if all(marker in last_html for marker in args.require):
                sys.stdout.write("<!DOCTYPE html>\n" + last_html + "\n")
                return 0
            time.sleep(0.1)
        sys.stderr.write("Chrome marker wait timed out. Last <html> tag:\n")
        start = last_html.find("<html")
        end = last_html.find(">", start)
        if start >= 0 and end >= start:
            sys.stderr.write(last_html[start:end + 1] + "\n")
        else:
            sys.stderr.write(last_html[:1000] + "\n")
        return 1
    finally:
        if ws is not None:
            ws.close()
        process.terminate()
        try:
            process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=3)


if __name__ == "__main__":
    raise SystemExit(main())
