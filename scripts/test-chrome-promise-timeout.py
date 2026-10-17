#!/usr/bin/env python3
"""A long CDP Promise must use the test deadline and restore the short poll timeout."""
import importlib.util
import json
import socket
import struct
import threading
import time
import unittest
from pathlib import Path

spec = importlib.util.spec_from_file_location("chrome_wait", Path(__file__).with_name("chrome-wait-dom.py"))
chrome = importlib.util.module_from_spec(spec)
spec.loader.exec_module(chrome)


class PromiseTimeout(unittest.TestCase):
    def run_delayed(self, timeout):
        client, server = socket.socketpair()
        client.settimeout(0.03)
        ws = chrome.WebSocket.__new__(chrome.WebSocket)
        ws.sock = client

        def respond():
            try:
                server.recv(4096)
                time.sleep(0.12)
                data = json.dumps({"id": 1, "result": {"result": {"value": "completed"}}}).encode()
                server.sendall(bytes([0x81, len(data)]) + data)
            except BrokenPipeError:
                pass
            finally:
                server.close()

        thread = threading.Thread(target=respond)
        thread.start()
        try:
            return chrome.evaluate_await(ws, 1, "aLongPromise()", timeout)
        finally:
            self.assertEqual(client.gettimeout(), 0.03)
            client.close()
            thread.join()

    def test_promise_outlives_poll_timeout(self):
        self.assertEqual(self.run_delayed(1), "completed")

    def test_short_deadline_is_still_enforced(self):
        with self.assertRaises(TimeoutError):
            self.run_delayed(0.05)


if __name__ == "__main__":
    unittest.main()
