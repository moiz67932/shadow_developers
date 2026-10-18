"""Real authenticated S3 round trip, using an isolated temporary bucket."""
import datetime
import hashlib
import hmac
import os
import unittest
import urllib.parse
import urllib.request
import uuid

ENDPOINT = os.environ["MINIO_ENDPOINT_URL"].rstrip("/")
ACCESS = os.environ.get("MINIO_ROOT_USER", "nasconminio")
SECRET = os.environ.get("MINIO_ROOT_PASSWORD", "change-this-minio-password")

def s3(method, path, body=b""):
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    day = stamp[:8]
    host = urllib.parse.urlsplit(ENDPOINT).netloc
    digest = hashlib.sha256(body).hexdigest()
    signed = "host;x-amz-content-sha256;x-amz-date"
    headers = f"host:{host}\nx-amz-content-sha256:{digest}\nx-amz-date:{stamp}\n"
    canonical = f"{method}\n{path}\n\n{headers}\n{signed}\n{digest}"
    scope = f"{day}/us-east-1/s3/aws4_request"
    message = f"AWS4-HMAC-SHA256\n{stamp}\n{scope}\n{hashlib.sha256(canonical.encode()).hexdigest()}"
    key = ("AWS4" + SECRET).encode()
    for part in (day, "us-east-1", "s3", "aws4_request"):
        key = hmac.new(key, part.encode(), hashlib.sha256).digest()
    signature = hmac.new(key, message.encode(), hashlib.sha256).hexdigest()
    request = urllib.request.Request(ENDPOINT + path, data=body if method == "PUT" else None,
        method=method, headers={"Host": host, "x-amz-date": stamp,
        "x-amz-content-sha256": digest,
        "Authorization": f"AWS4-HMAC-SHA256 Credential={ACCESS}/{scope}, SignedHeaders={signed}, Signature={signature}"})
    with urllib.request.urlopen(request, timeout=10) as response:
        return response.status, response.read()

class MinioStorage(unittest.TestCase):
    def test_authenticated_object_roundtrip(self):
        bucket = "/phase1-" + uuid.uuid4().hex
        self.assertEqual(s3("PUT", bucket)[0], 200)
        try:
            payload = b"NASCON Phase 1 storage verification\x00\xff"
            self.assertEqual(s3("PUT", bucket + "/probe.bin", payload)[0], 200)
            self.assertEqual(s3("GET", bucket + "/probe.bin"), (200, payload))
            self.assertEqual(s3("DELETE", bucket + "/probe.bin")[0], 204)
        finally:
            s3("DELETE", bucket)

if __name__ == "__main__":
    unittest.main()
