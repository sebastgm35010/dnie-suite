"""Read-only checks for module cycles and the repository's existing layer boundaries.

Run from any directory: python /path/to/repo/tools/check_architecture.py
This checks source imports/dependency declarations, not runtime behavior or reflection.
"""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
JVM_MODULES = (
    "dnie-core", "dnie-protocol", "dnie-icao", "dnie-vault-core",
    "dnie-diagnostics-core", "dnie-bridge-core",
)


def violations(root=ROOT):
    errors = []
    for module in JVM_MODULES:
        for source in (root / module / "src/main").rglob("*.kt"):
            for line, text in enumerate(source.read_text(encoding="utf-8-sig").splitlines(), 1):
                if re.match(r"\s*import\s+(android\.|androidx\.|pe\.gob\.reniec\.dnie\.(ui|data)\.)", text):
                    errors.append(f"{source.relative_to(root)}:{line}: JVM imports platform/app layer")
    app = root / "app/src/main/java/pe/gob/reniec/dnie"
    for layer, forbidden in (
        ("domain", r"android\.|androidx\.|com\.tom_roush\.|pe\.gob\.reniec\.dnie\.(ui|data)\."),
        ("data", r"androidx\.compose\.|pe\.gob\.reniec\.dnie\.ui\."),
    ):
        for source in (app / layer).rglob("*.kt"):
            for line, text in enumerate(source.read_text(encoding="utf-8-sig").splitlines(), 1):
                if re.match(r"\s*import\s+(?:" + forbidden + ")", text):
                    errors.append(f"{source.relative_to(root)}:{line}: {layer} imports forbidden layer")
    graph = {}
    for build in root.glob("*/build.gradle.kts"):
        graph[build.parent.name] = re.findall(r'\b(?:api|implementation|compileOnly|runtimeOnly)\(project\(":([^"\n]+)"\)\)',
                                             build.read_text(encoding="utf-8-sig"))
    done = set()

    def visit(module, path):
        if module in path:
            errors.append("Module dependency cycle: " + " -> ".join(path + [module]))
            return
        if module in done:
            return
        for dependency in graph.get(module, []):
            if dependency == "app":
                errors.append(f"{module}: library depends on app")
            visit(dependency, path + [module])
        done.add(module)

    for module in graph:
        visit(module, [])
    return errors


if __name__ == "__main__":
    errors = violations()
    print("\n".join(errors) if errors else "Architecture boundaries OK (imports and project dependencies).")
    sys.exit(bool(errors))
