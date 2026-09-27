from pathlib import Path
import hashlib
import json
import re
import subprocess

v = Path('_verified/views')
r = Path('_verified/restart')
a = json.loads((v / 'views-source-hashes.json').read_text())
b = json.loads((r / 'allpaths-source-hashes.json').read_text())
expected = '97aeeaacad2c1af8bb08d1494ddfd192d5f96f67'
paths = {
    'org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/packageview/PackageExplorerPart.java',
    'org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/typehierarchy/TypeHierarchyViewPart.java',
    'org.eclipse.jdt.ui/ui/org/eclipse/jdt/internal/ui/typehierarchy/TypeHierarchyLifeCycle.java',
    'org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/packageview/ViewStartupTests.java',
    'org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/packageview/PackageExplorerTests.java'
}
assert subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip() == expected
assert a['base'] == b['base'] == expected and a['implementation'] == 'fixed'
assert set(a['files']) == paths
patch = (v / 'views-candidate.patch').read_bytes()
assert hashlib.sha256(patch).hexdigest() == a['patch_sha256'] == '03dcb3949a27304ec1b0a574da953931ee18293fb9895fce73c3776feaa5a0e1'
for p in paths:
    if p.startswith('org.eclipse.jdt.ui/'):
        assert a['files'][p] == b['files'][p], p
results = json.loads((r / 'restart-evidence/results.json').read_text())
prs = [x for x in results if x.get('implementation') == 'pr']
assert len(prs) == 3
for x in prs:
    assert x['exit'] == 0 and x['finished'] and not x['fatal'], x
    assert x['initializer_threads'] and not x['initializer_on_main'], x
    assert x['restored_editor'] and x['final_editor'] and x['final_breadcrumb'], x
    assert x['mode'] != 'link' or x['final_link'], x
    assert x['mode'] != 'hierarchy' or x['final_hierarchy'], x
    text = (r / f"restart-evidence/{x['mode']}/pr/debugger.log").read_text()
    pause = text.split('INITIALIZER_HIT number=1 ')[1].split('INITIALIZER_HIT number=2')[0]
    beats = [int(n) for n in re.findall(r'heartbeat=(\d+)', pause)]
    assert len(beats) >= 3 and beats[-1] > beats[0], (x, beats)
subprocess.run(['git', 'apply', '-'], input=patch, check=True)
for p in sorted(paths):
    assert hashlib.sha256(Path(p).read_bytes()).hexdigest() == a['files'][p], p
subprocess.run(['git', 'add', '--', *sorted(paths)], check=True)
assert set(subprocess.check_output(['git', 'diff', '--cached', '--name-only'], text=True).splitlines()) == paths
subprocess.run(['git', 'diff', '--cached', '--check'], check=True)
print('Verified exact source patch, regression provenance and three live-UI PDE restart scenarios.')
