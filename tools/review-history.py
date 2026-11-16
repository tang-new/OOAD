"""口试审查辅助：检查分工索引，或只读汇总真实 Git 提交；不提交、不重写历史。"""

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys


ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "docs/oral-review/coverage.json"
SOURCE_SUFFIXES = {
    ".java", ".ts", ".vue", ".css", ".html", ".py", ".js", ".mjs", ".cjs", ".sh",
    ".sql", ".yaml", ".yml", ".xml", ".json", ".conf", ".patch", ".http",
    ".properties", ".example", ".cmd", ".bat", ".ps1",
    ".mmd", ".puml", ".dot", ".mdj",
}
SOURCE_ROOTS = {
    "rbac-contract", "rbac-structured", "rbac-ddd", "rbac-gateway", "rbac-web",
    "course-integration", "deploy", "tools", "diagrams", ".github", ".mvn",
}
ROOT_FILES = {"pom.xml", "Dockerfile", ".dockerignore", "docker-compose.yml", "mvnw", "mvnw.cmd"}


def git(root, *args):
    """仅在指定仓库执行只读 Git 子命令，不经 shell 拼接。"""
    result = subprocess.run(
        ["git", "-c", "safe.directory=" + root.as_posix(), "-C", str(root), *args],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False,
    )
    if result.returncode:
        raise ValueError(result.stderr.decode("utf-8", errors="replace").strip())
    return result.stdout.decode("utf-8", errors="replace")


def source_paths(root):
    """清点 Git 跟踪及未忽略的新源码；不遍历临时目录、缓存或依赖安装目录。"""
    candidates = set(git(root, "ls-files", "--cached", "--others", "--exclude-standard", "-z").split("\0"))
    found = set()
    for name in candidates:
        path = Path(name)
        if not name or not (root / path).is_file():
            continue
        if name in ROOT_FILES:
            found.add(name)
        elif path.parts[0] in SOURCE_ROOTS:
            if any(part in {"target", "node_modules", "__pycache__", ".cache", ".runtime", ".tmp"} for part in path.parts):
                continue
            if path.name == "package-lock.json":
                continue
            if path.suffix in SOURCE_SUFFIXES or path.name in {"Dockerfile", ".dockerignore"} or path.name.endswith(".Dockerfile"):
                found.add(name)
    return sorted(found)


def operations(root):
    """读取本仓库现行 OAS 的路径/方法/operationId；不依赖生成的展示页面。"""
    source = root / "rbac-contract/src/main/resources/openapi/rbac-api.yaml"
    path = method = None
    result = []
    for line in source.read_text(encoding="utf-8-sig").splitlines():
        match = re.match(r"^  (/.*):\s*$", line)
        if match:
            path, method = match.group(1), None
        match = re.match(r"^    (get|post|put|patch|delete|head|options):\s*$", line)
        if match:
            method = match.group(1).upper()
        match = re.match(r"^      operationId:\s*([^\s]+)\s*$", line)
        if match and path and method:
            result.append((method, path, match.group(1).strip("\"'")))
    if not result or len({row[2] for row in result}) != len(result):
        raise ValueError("无法唯一读取现行 OAS operationId；请检查格式或重复声明")
    return result


def check(root, manifest):
    """核对源码、64 操作分工、15 表和共享方法入口；不标记人工审查完成。"""
    errors = []
    actual = set(source_paths(root))
    listed = [row["path"] for row in manifest["sourceFiles"]]
    if len(set(listed)) != len(listed):
        errors.append("源码索引存在重复路径")
    errors.extend("未分配源码: " + name for name in sorted(actual - set(listed)))
    errors.extend("索引路径已移除: " + name for name in sorted(set(listed) - actual))
    for row in manifest["sourceFiles"]:
        owners = row.get("owners", [])
        if not owners or row.get("lead") not in owners or not set(owners) <= set("ABCDEF"):
            errors.append("责任人无效: " + row["path"])
        if row.get("reviewState") != "NOT_STARTED":
            errors.append("静态索引不能代签人工审查: " + row["path"])
    current = set(operations(root))
    assigned = [(row["method"], row["path"], row["operationId"]) for row in manifest["operations"]]
    if len(set(assigned)) != len(assigned) or current != set(assigned):
        errors.append("操作分工与现行 OAS 不一致")
    for row in manifest["operations"]:
        if row["lead"] not in "ABCDEF" or not set(row["partners"]) <= set("ABCDEF"):
            errors.append("操作责任人无效: " + row["operationId"])
    ddl = (root / "rbac-contract/src/main/resources/db/migration/V1__rbac1_schema.sql").read_text(encoding="utf-8")
    if set(re.findall(r"CREATE TABLE (\w+)", ddl)) != {row["table"] for row in manifest["tables"]}:
        errors.append("表分工与现行 V1 DDL 不一致")
    for row in manifest["sharedMethods"]:
        path = root / row["path"]
        if not path.is_file():
            errors.append("共享入口不存在: " + row["path"])
            continue
        source = path.read_text(encoding="utf-8-sig")
        for method in row["methods"]:
            declaration = (r"\b(?:public|private|protected)\s+"
                           r"(?:(?:static|final|synchronized|abstract|default)\s+)*"
                           r"(?:<[^>]+>\s+)?[\w.$<>, ?\[\]]+\s+"
                           + re.escape(method) + r"\s*\(")
            if not re.search(declaration, source):
                errors.append("共享方法待更新: " + row["path"] + "#" + method)
    # 本地链接仅检查工作包自身；原始材料是可缺失的本地输入，不成为分发后的硬依赖。
    for doc in (root / "docs/oral-review").glob("*.adoc"):
        for target in re.findall(r"link:([^\s\[]+)\[", doc.read_text(encoding="utf-8")):
            if target.startswith(("https:", "http:")):
                continue
            location = target.split("#", 1)[0]
            if location and not (doc.parent / location).is_file():
                errors.append("本地链接失效: " + doc.name + " -> " + target)
    return {"scope": "assignment-index-only", "humanReviewVerified": False,
            "sourceFiles": len(listed), "operations": len(current),
            "tables": len(manifest["tables"]), "errors": errors}


def context(root, expected):
    """只读核对成员本地原始资料指纹，不复制内容或将其归档。"""
    rows = []
    for item in expected["files"]:
        name = item["name"]
        if Path(name).name != name or "/" in name or "\\" in name:
            raise ValueError("上下文清单只能包含.tmp下的直接文件名")
        path = root / ".tmp" / name
        state = "MISSING"
        if path.is_file():
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            state = "MATCH" if digest == item["sha256"] and path.stat().st_size == item["bytes"] else "CHANGED"
        rows.append({"name": name, "state": state})
    return {"scope": "local-context-fingerprints-only", "files": rows,
            "allAvailableAndMatching": all(row["state"] == "MATCH" for row in rows),
            "missingOriginalsAtBaseline": expected["missingOriginals"],
            "humanReadConfirmed": False}


def resolve_commit(root, ref):
    """将用户指定引用解析成不可混入选项的真实 commit。"""
    if not ref or ref.startswith("-") or any(ord(ch) < 32 for ch in ref):
        raise ValueError("非法 Git 引用")
    return git(root, "rev-parse", "--verify", "--end-of-options", ref + "^{commit}").strip()


def responsibilities(path, manifest):
    """按路径建议关注人；共享文件不会被误称为某成员的独占代码。"""
    for row in manifest["sourceFiles"]:
        if row["path"] == path:
            return row["owners"]
    member = re.match(r"docs/oral-review/([A-F])-", path)
    if member:
        return [member.group(1)]
    if path.startswith(("docs/", "diagrams/")) or path == "README.md":
        return list("ABCDEF")
    return []


def history(root, manifest, since_ref=None, until_ref="HEAD", member=None):
    """按真实提交和第一父差异记账；关注人由职责推断，不推断实际作者分工或签字。"""
    until = resolve_commit(root, until_ref)
    since = resolve_commit(root, since_ref or manifest["baselineCommit"])
    ancestor = subprocess.run(
        ["git", "-c", "safe.directory=" + root.as_posix(), "-C", str(root),
         "merge-base", "--is-ancestor", since, until], capture_output=True,
    )
    if ancestor.returncode != 0:
        raise ValueError("起点必须是终点的祖先；请明确选择实际审查区间")
    commits = []
    excluded = 0
    for sha in git(root, "log", "--reverse", "--format=%H", since + ".." + until).splitlines():
        date, author, subject = git(root, "show", "-s", "--format=%aI%x00%an%x00%s", sha).rstrip("\n").split("\0", 2)
        names = git(root, "diff-tree", "--root", "--no-commit-id", "--name-only", "-r", "--first-parent", "-m", "-z", sha).split("\0")
        files = []
        for name in sorted(set(names) - {""}):
            if name.startswith((".tmp/", ".runtime/", ".cache/", "data/raw/")):
                excluded += 1
                continue
            owners = responsibilities(name, manifest)
            if member is None or member in owners:
                files.append({"path": name, "attention": owners})
        if files:
            commits.append({"commit": sha, "date": date, "author": author,
                            "subject": subject, "files": files,
                            "humanReviewState": "NOT_RECORDED"})
    return {"scope": "git-history-summary-only", "sinceExclusive": since,
            "untilInclusive": until, "memberFilter": member,
            "workingTreeHasChanges": bool(git(root, "status", "--porcelain").strip()),
            "excludedPrivatePathCount": excluded, "commits": commits,
            "note": "不包含未提交内容；关注人不是实际作者或人工签字；不执行 commit/push/历史改写。"}


def adoc(report):
    """生成可保存的 AsciiDoc 文本，不自行写文件。"""
    def cell(value):
        return str(value).replace("|", "\\|").replace("\r", " ").replace("\n", " ")
    lines = ["= 审查阶段真实 Git 记录", "", report["note"], "",
             "起点（不含）：`" + report["sinceExclusive"] + "`；终点（含）：`" + report["untilInclusive"] + "`。",
             "工作区有未提交变化：" + str(report["workingTreeHasChanges"]).lower() + "。", ""]
    if not report["commits"]:
        return "\n".join(lines + ["该区间没有匹配提交；不据此生成模拟审查记录。", ""])
    for commit in report["commits"]:
        lines += ["== " + commit["commit"][:12] + " " + cell(commit["subject"]), "",
                  "日期：" + cell(commit["date"]) + "；Git 作者：" + cell(commit["author"]) + "；人工审查：未记录。", "",
                  '[cols="4,1",options="header"]', "|===", "|实际变化路径 |建议关注人"]
        for row in commit["files"]:
            lines.append("|`" + cell(row["path"]) + "` |" + ("/".join(row["attention"]) or "未分类，需分配"))
        lines += ["|===", ""]
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("check", help="只读检查分工覆盖与文件链接")
    commands.add_parser("context", help="只读核对本地.tmp上下文指纹，不复制原始材料")
    log = commands.add_parser("history", help="只读汇总真实提交；起点默认采用工作包基线")
    log.add_argument("--since-ref")
    log.add_argument("--until-ref", default="HEAD")
    log.add_argument("--member", choices=list("ABCDEF"))
    log.add_argument("--format", choices=["adoc", "json"], default="adoc")
    args = parser.parse_args()
    try:
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        if args.command == "check":
            report = check(ROOT, manifest)
            print(json.dumps(report, ensure_ascii=False, indent=2))
            return 1 if report["errors"] else 0
        if args.command == "context":
            expected = json.loads(MANIFEST.with_name("context-manifest.json").read_text(encoding="utf-8"))
            report = context(ROOT, expected)
            print(json.dumps(report, ensure_ascii=False, indent=2))
            return 0 if report["allAvailableAndMatching"] else 1
        report = history(ROOT, manifest, args.since_ref, args.until_ref, args.member)
        print(json.dumps(report, ensure_ascii=False, indent=2) if args.format == "json" else adoc(report))
        return 0
    except (OSError, ValueError, KeyError) as error:
        print("审查索引工具失败: " + str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stderr.reconfigure(encoding="utf-8")
    raise SystemExit(main())
