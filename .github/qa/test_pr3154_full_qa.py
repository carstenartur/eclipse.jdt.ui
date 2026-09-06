"""Checks that diagnostic inventory validation cannot turn partial/red builds green."""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
import xml.etree.ElementTree as ET

from pr3154_full_qa import LIFECYCLE, summarize, toolchains


class EvidenceChecks(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)

    def report(self, module, count, required=False):
        root = ET.Element('testsuite')
        for number in range(count):
            ET.SubElement(root, 'testcase', classname='example.Tests', name=f'test{number}')
        if required:
            for cls in ('TestRunListenerTest5', 'TestRunListenerTest6'):
                ET.SubElement(root, 'testcase', classname='org.eclipse.jdt.junit.tests.' + cls,
                              name='testTerminateLaunch')
            for name in sorted(LIFECYCLE):
                ET.SubElement(root, 'testcase', classname='org.eclipse.jdt.junit.tests.TestRunListenerTest5', name=name)
        path = self.root / module / 'TEST-suite.xml'
        path.parent.mkdir(parents=True, exist_ok=True)
        ET.ElementTree(root).write(path)
        return path

    def complete(self):
        main = self.report('org.eclipse.jdt.ui.tests', 9000, True)
        self.report('org.eclipse.jdt.text.tests', 1)
        self.report('org.eclipse.jdt.ui.tests.refactoring', 1)
        return main

    def test_complete_passes(self):
        self.complete()
        self.assertTrue(summarize(self.root, 'candidate', 0)['passed'])

    def test_empty_and_partial_are_not_success(self):
        self.assertFalse(summarize(self.root, 'candidate', 0)['passed'])
        self.report('org.eclipse.jdt.ui.junit.sampleproject', 58)
        self.assertFalse(summarize(self.root, 'base', 0)['passed'])

    def test_maven_failure_stays_failure(self):
        self.complete()
        self.assertFalse(summarize(self.root, 'candidate', 1)['passed'])

    def test_ignored_test_failure_stays_failure(self):
        path = self.complete()
        tree = ET.parse(path)
        ET.SubElement(tree.getroot()[0], 'failure', message='not ignored')
        tree.write(path)
        result = summarize(self.root, 'candidate', 0)
        self.assertFalse(result['passed'])
        self.assertEqual(1, len(result['failures']))

    def test_required_test_must_not_be_skipped(self):
        path = self.complete()
        tree = ET.parse(path)
        ET.SubElement(tree.getroot()[-1], 'skipped')
        tree.write(path)
        result = summarize(self.root, 'candidate', 0)
        self.assertFalse(result['passed'])
        self.assertEqual(1, len(result['missing_required']))

    def test_large_skipped_inventory_is_not_success(self):
        path = self.complete()
        tree = ET.parse(path)
        for case in list(tree.getroot())[:9000]:
            ET.SubElement(case, 'skipped')
        tree.write(path)
        self.assertFalse(summarize(self.root, 'candidate', 0)['passed'])

    def test_missing_module_is_not_success(self):
        self.report('org.eclipse.jdt.ui.tests', 9000, True)
        result = summarize(self.root, 'candidate', 0)
        self.assertFalse(result['passed'])
        self.assertEqual(2, len(result['missing_modules']))

    def test_malformed_report_is_not_success(self):
        self.complete()
        (self.root / 'org.eclipse.jdt.ui.tests' / 'TEST-broken.xml').write_text('<testsuite>')
        result = summarize(self.root, 'candidate', 0)
        self.assertFalse(result['passed'])
        self.assertEqual(1, len(result['malformed']))

    def test_base_does_not_require_new_regressions(self):
        path = self.complete()
        tree = ET.parse(path)
        for case in list(tree.getroot()):
            if case.get('name') in LIFECYCLE:
                tree.getroot().remove(case)
        tree.write(path)
        self.assertTrue(summarize(self.root, 'base', 0)['passed'])
        self.assertFalse(summarize(self.root, 'candidate', 0)['passed'])

    def test_toolchain_ids_match_bree_not_setup_java_defaults(self):
        env = {}
        for major, version in ((8, '1.8.0_472'), (17, '17.0.16'), (21, '21.0.8')):
            home = self.root / f'jdk{major}'
            (home / 'bin').mkdir(parents=True)
            (home / 'bin' / 'javac').touch()
            (home / 'release').write_text(f'JAVA_VERSION="{version}"\n')
            env[f'JDK{major}'] = str(home)
        import os
        previous = Path.cwd()
        os.chdir(self.root)
        self.addCleanup(os.chdir, previous)
        with patch.dict(os.environ, env), patch('pathlib.Path.home', return_value=self.root):
            toolchains()
        result = ET.parse(self.root / '.m2' / 'toolchains.xml')
        self.assertEqual({'JavaSE-1.8', 'JavaSE-17', 'JavaSE-21'},
                         {node.text for node in result.findall('./toolchain/provides/id')})


if __name__ == '__main__':
    unittest.main()
