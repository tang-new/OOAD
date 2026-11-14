"""专用 MySQL 上真实重启 JVM，验证五次失败锁定不依赖进程内存。"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request
import uuid

parser = argparse.ArgumentParser()
parser.add_argument("--jar", required=True)
parser.add_argument("--log-dir", required=True)
parser.add_argument("--port", type=int, default=18082)
args = parser.parse_args()
logs = Path(args.log_dir)
logs.mkdir(parents=True, exist_ok=True)
env = os.environ.copy()
env.update({
    "SPRING_PROFILES_ACTIVE": "test",
    "SERVER_PORT": str(args.port),
    "RBAC_DB_URL": "jdbc:mysql://127.0.0.1:13306/rbac1_ddd_test?serverTimezone=UTC&useSSL=false&useLocalSessionState=true&connectTimeout=5000&socketTimeout=120000",
    "RBAC_JWT_ALLOW_EPHEMERAL": "true",
    "RBAC_BOOTSTRAP_PASSWORD": "Ddd-Test-Only-Password-2026!",
    "RBAC_AUDIT_SERVICE_KEY": "ddd-restart-service-only",
})
java = str(Path(env["JAVA_HOME"]) / "bin" / "java.exe") if "JAVA_HOME" in env else "java"
base = "http://127.0.0.1:" + str(args.port)


def request(path, data=None, bearer=None):
    headers = {"Content-Type": "application/json"}
    if bearer:
        headers["Authorization"] = "Bearer " + bearer
    body = None if data is None else json.dumps(data).encode()
    query = urllib.request.Request(base + path, body, headers)
    try:
        with urllib.request.urlopen(query, timeout=180) as response:
            return response.status, json.loads(response.read() or "null")
    except urllib.error.HTTPError as failure:
        return failure.code, json.loads(failure.read() or "null")


def start(round_number):
    stream = (logs / ("m5-ddd-restart-jvm-" + str(round_number) + ".log")).open("w", encoding="utf8")
    process = subprocess.Popen([java, "-Xmx384m", "-jar", str(Path(args.jar).resolve())],
                               env=env, stdout=stream, stderr=subprocess.STDOUT)
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        if process.poll() is not None:
            stream.close()
            raise AssertionError("JVM启动失败，退出码=" + str(process.returncode))
        try:
            with urllib.request.urlopen(base + "/api/v1/actuator/health", timeout=3) as response:
                if response.status == 200 and json.loads(response.read())["status"] == "UP":
                    print("JVM", round_number, "健康检查通过", flush=True)
                    return process, stream
        except (OSError, ValueError):
            pass
        time.sleep(1)
    process.terminate()
    process.wait(timeout=30)
    stream.close()
    raise AssertionError("JVM启动未在180秒内就绪；不继续假定健康")


def stop(process, stream):
    process.terminate()
    process.wait(timeout=30)
    stream.close()


process, stream = start(1)
username = "RESTART_" + uuid.uuid4().hex
password = "Ddd-Test-Only-Password-2026!"
try:
    status, login = request("/auth/login", {"username": "root", "password": password, "systems": ["rbac-center"]})
    assert status == 200, ("根登录失败", status)
    token = login["accessToken"]
    status, created = request("/api/v1/users", {"username": username, "name": "JVM重启锁定验证", "departmentId": 2, "password": password}, token)
    assert status == 200, ("创建测试身份失败", status)
    for _ in range(5):
        status, _ = request("/auth/login", {"username": username, "password": "wrong-test-password", "systems": ["course-project-management"]})
        assert status == 401, ("错误凭据未拒绝", status)
    status, _ = request("/auth/login", {"username": username, "password": password, "systems": ["course-project-management"]})
    assert status == 401, ("五次失败后正确密码绕过了锁", status)
    print("JVM1：五次错误及锁内正确密码均401", flush=True)
finally:
    stop(process, stream)
process, stream = start(2)
try:
    status, _ = request("/auth/login", {"username": username, "password": password, "systems": ["course-project-management"]})
    assert status == 401, ("JVM重启后持久锁失效", status)
    print("JVM2：重启后正确密码仍401；真实持久锁重启验证通过", flush=True)
finally:
    stop(process, stream)
