import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('collector', Path(__file__).with_name('collect-acceptance-evidence.py'))
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)


class PortableEvidenceTest(unittest.TestCase):
    def test_public_observations_and_cleanup_flags_are_not_credentials(self):
        collector.ensure_portable({'cleanup': {'secrets': True}, 'rows': [{'status': 'pass', 'subject': 'synthetic-user'}]})

    def test_credential_fields_are_rejected_in_nested_arrays(self):
        for key in ['password', 'Authorization', 'accessToken', 'refreshToken', 'privateKey', 'serviceKey']:
            with self.subTest(key=key), self.assertRaises(ValueError):
                collector.ensure_portable({'rows': [{key: 'synthetic-not-a-credential'}]})

    def test_key_and_jwt_values_are_rejected(self):
        for value in ['-----BEGIN ' + 'PRIVATE KEY-----', 'eyJ' + 'mb28' + '.' + 'e30' + '.' + 'test']:
            with self.assertRaises(ValueError):
                collector.ensure_portable({'body': value})

    def test_sha_is_exact_binary(self):
        with tempfile.TemporaryDirectory(prefix='rbac-evidence-test-') as folder:
            file = Path(folder) / 'binary'
            file.write_bytes(b'\x00\r\n\xff')
            first = collector.sha(file)
            file.write_bytes(b'\x00\n\xff')
            self.assertNotEqual(first, collector.sha(file))


if __name__ == '__main__':
    unittest.main()
