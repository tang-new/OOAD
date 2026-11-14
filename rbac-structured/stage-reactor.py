"""Copy only reactor build inputs to an external staging directory.
No shared source is edited; DDD's POM is present only so Maven can select structured with -pl/-am.
"""
from pathlib import Path
import shutil
root = Path('D:/m3-structured-reactor')
root.mkdir(exist_ok=True)
shutil.copy2('pom.xml',root/'pom.xml')
for module in ('rbac-contract','rbac-structured'):
    target=root/module
    if target.exists():shutil.rmtree(target)
    shutil.copytree(module,target,ignore=shutil.ignore_patterns('target','.git','*.log','__pycache__'))
import xml.etree.ElementTree as ET
parent = ET.parse('pom.xml').getroot()
for module in parent.findall('{http://maven.apache.org/POM/4.0.0}modules/{http://maven.apache.org/POM/4.0.0}module'):
    name = module.text.strip()
    if name not in ('rbac-contract', 'rbac-structured'):
        (root/name).mkdir(parents=True,exist_ok=True)
        shutil.copy2(Path(name)/'pom.xml',root/name/'pom.xml')
print(root)
