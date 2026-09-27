from pathlib import Path
import base64
import hashlib
import json
import subprocess
import zlib

for name, digest in [('allpaths-tests.b64', 'efa9dfa1d2aae134a0b378506b093c286500be0ad65e90237323a9bc7b29382f'), ('remaining-paths-fix.b64', '5e62e96e96e3e836f9b2eb46cbf3397d8f758e3f40fd8566a71ec350d90bc916')]:
    text = (Path('.github') / name).read_text().replace('WutyeaU5', 'WutyeU5')
    patch = zlib.decompress(base64.b64decode(text))
    assert hashlib.sha256(patch).hexdigest() == digest, name
    subprocess.run(['git', 'apply', '-'], input=patch, check=True)
p = Path('org.eclipse.jdt.ui.tests/ui/org/eclipse/jdt/ui/tests/packageview/ViewStartupTests.java')
p.write_text(p.read_text().replace('import static org.junit.jupiter.api.Assertions.assertEquals;\n', ''))
subprocess.run(['git', 'add', '-N', str(p)], check=True)
subprocess.run(['git', 'diff', '--check'], check=True)
patch = subprocess.check_output(['git', 'diff', '--', 'org.eclipse.jdt.ui', 'org.eclipse.jdt.ui.tests'])
Path('allpaths-candidate.patch').write_bytes(patch)
paths = subprocess.check_output(['git', 'diff', '--name-only', '--', 'org.eclipse.jdt.ui', 'org.eclipse.jdt.ui.tests'], text=True).splitlines()
Path('allpaths-source-hashes.json').write_text(json.dumps({'base': '97aeeaacad2c1af8bb08d1494ddfd192d5f96f67', 'patch_sha256': hashlib.sha256(patch).hexdigest(), 'files': {p: hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in paths}}, indent=2))
