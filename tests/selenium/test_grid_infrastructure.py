"""Infrastructure smoke only; guest-player acceptance belongs to Phase 3.

Run: python3 -m unittest discover -s tests/selenium -p 'test_grid_infrastructure.py'
"""
import concurrent.futures
import json
import os
import time
import unittest
import urllib.request

GRID = os.environ.get("SELENIUM_URL", "http://localhost:4444").rstrip("/")

def call(path, payload=None, method=None):
    request = urllib.request.Request(GRID + path,
        data=None if payload is None else json.dumps(payload).encode(),
        headers={"Content-Type": "application/json"}, method=method)
    with urllib.request.urlopen(request, timeout=35) as response:
        return json.load(response)["value"]

def browser_smoke(browser, barrier):
    capabilities = {"browserName": browser}
    if browser == "chrome":
        capabilities["goog:chromeOptions"] = {"args": ["--headless=new", "--no-sandbox"]}
    else:
        capabilities["moz:firefoxOptions"] = {"args": ["-headless"]}
    session = call("/session", {"capabilities": {"alwaysMatch": capabilities}})
    sid = session["sessionId"]
    try:
        barrier.wait(timeout=35)
        call(f"/session/{sid}/url", {"url": "http://api:8080/health"})
        return call(f"/session/{sid}/execute/sync", {"script": "return document.body.textContent", "args": []})
    finally:
        call(f"/session/{sid}", method="DELETE")

class GridInfrastructure(unittest.TestCase):
    def test_three_ready_nodes_and_parallel_real_browsers(self):
        import threading
        deadline = time.monotonic() + 110
        status = call("/status")
        while not (status["ready"] and len(status["nodes"]) >= 3 and all(n["availability"] == "UP" for n in status["nodes"])) and time.monotonic() < deadline:
            time.sleep(0.5)
            status = call("/status")
        self.assertTrue(status["ready"])
        self.assertGreaterEqual(len(status["nodes"]), 3)
        self.assertTrue(all(node["version"].startswith("4.25.0") for node in status["nodes"]))
        barrier = threading.Barrier(3)
        with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
            results = list(executor.map(lambda browser: browser_smoke(browser, barrier), ["chrome", "chrome", "firefox"]))
        self.assertTrue(all('"status":"ok"' in body for body in results))

if __name__ == "__main__":
    unittest.main()
