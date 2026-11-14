"""Renderer checks only. These are NOT RBAC business/integration tests."""
import importlib.util
from pathlib import Path
import unittest
import hashlib
import tempfile

SCRIPT = Path(__file__).with_name('render-rbac1-design.py')
spec = importlib.util.spec_from_file_location('rbac_design', SCRIPT)
design = importlib.util.module_from_spec(spec)
spec.loader.exec_module(design)

class RendererChecks(unittest.TestCase):
    def setUp(self):
        design.INDEX.clear()
        design.REFS.clear()
        self.path = 'rbac-ddd/src/main/java/com/xmu/rbac/ddd/domain/RoleHierarchy.java'
        source = (design.ROOT / self.path).read_text(encoding='utf-8')
        design.INDEX['com.xmu.rbac.ddd.domain.RoleHierarchy'] = {
            'path': self.path, 'source': source, 'sha256': design.sha(design.ROOT / self.path),
            'bytecode': 'PENDING_COMPILE', 'signatures': []}

    def test_actual_behavior_method(self):
        name = design.key('RoleHierarchy', 'ddd')
        self.assertIn('link(', design.method(name, 'link'))
        self.assertEqual(design.REFS[-1]['method'], 'link')

    def test_nonexistent_method_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Missing method declaration'):
            design.method(design.key('RoleHierarchy', 'ddd'), 'inventedValidateAcyclic')

    def test_nonexistent_class_is_rejected(self):
        with self.assertRaisesRegex(ValueError, 'Class not unique/existing'):
            design.key('RoleHierarchyPolicyDoesNotExist', 'ddd')

    def test_missing_compiled_signature_is_rejected(self):
        name = design.key('RoleHierarchy', 'ddd')
        design.INDEX[name]['bytecode'] = 'COMPILED'
        with self.assertRaisesRegex(ValueError, 'javap missing method'):
            design.method(name, 'link')

    def test_text_hash_normalizes_only_crlf(self):
        with tempfile.TemporaryDirectory(prefix='rbac-design-eol-') as directory:
            for suffix in ['.java','.yml','.json','.py','.adoc']:
                path=Path(directory)/('input'+suffix)
                lf='class 示例 {\n  int value;\n}\n'.encode('utf-8')
                path.write_bytes(lf);expected=design.sha(path)
                path.write_bytes(lf.replace(b'\n',b'\r\n'))
                self.assertEqual(design.sha(path),expected,suffix)
                path.write_bytes(lf.replace(b'\n',b'\r'))
                self.assertNotEqual(design.sha(path),expected,'standalone CR must not normalize')
                path.write_bytes(lf+b' ')
                self.assertNotEqual(design.sha(path),expected,'whitespace must not disappear')
                path.write_bytes(b'\xef\xbb\xbf'+lf)
                self.assertNotEqual(design.sha(path),expected,'BOM must not disappear')

    def test_binary_hash_never_normalizes_crlf(self):
        with tempfile.TemporaryDirectory(prefix='rbac-design-binary-') as directory:
            for suffix in ['.class','.jar','.png','.dat']:
                path=Path(directory)/('input'+suffix)
                data=b'\x00binary\r\npayload\xff'
                path.write_bytes(data)
                self.assertEqual(design.sha(path),hashlib.sha256(data).hexdigest(),suffix)
                original=design.sha(path)
                path.write_bytes(data.replace(b'\r\n',b'\n'))
                self.assertNotEqual(design.sha(path),original,suffix)

    def test_fifteen_physical_tables(self):
        import re
        ddl = (design.ROOT / 'rbac-contract/src/main/resources/db/migration/V1__rbac1_schema.sql').read_text(encoding='utf-8')
        self.assertEqual(len(re.findall(r'CREATE TABLE\s+\w+', ddl)), 15)

if __name__ == '__main__':
    unittest.main(verbosity=2)
