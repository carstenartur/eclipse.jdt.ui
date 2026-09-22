from pathlib import Path
import hashlib
import os
import subprocess
import urllib.request

entries = {
    'jdt-ui': ('carstenartur/eclipse.jdt.ui', '7cf9c0a7d8c3090c4fd81470aed70dc3e1b2de6b', 'fix-template-registry-binary-compatibility', '4aa519c29f2d24168420d56b9aa8561954ce4e24da2e5fff4da4dc1078c97b5c', 'Distinguish the repaired template registry ABI by bundle version'),
    'debug': ('eclipse-jdt/eclipse.jdt.debug', '4e7cd73a7bac28514212c3402adab6610336d4b2', 'fix-template-registry-core-accessors', '9140619308f25e59a9034e73ce763302234edd5ee705525fe187ed77a1661d93', 'Use versioned JDT core template registry accessors'),
    'pde': ('eclipse-pde/eclipse.pde', '576cdd88ea0631c3514fedb0d329fffca24d5ec0', 'fix-template-registry-core-accessor', '60c6cec77a1d98ce19e93814430ebbbc8d1960905b5b47262d4cd534800bac61', 'Use the versioned JDT core registry for E4 template completion'),
}
selected = os.environ['SDK_COMPONENT']
root = Path(os.environ['RUNNER_TEMP']) / 'publish-sdk'
root.mkdir()
os.environ['SDK_ROOT'] = str(root)

def git(path, *args):
    return subprocess.check_output(['git', '-C', str(path), *args])

assert git('.', 'rev-parse', 'HEAD').decode().strip() == entries[selected][1], 'Refusing to overwrite a changed source branch'
for name, (repo, sha, branch, digest, title) in entries.items():
    if name == selected:
        subprocess.run(['git', 'worktree', 'add', '--detach', str(root / name), sha], check=True)
    else:
        subprocess.run(['git', 'clone', '-q', '--no-checkout', '--filter=blob:none', 'https://github.com/' + repo + '.git', str(root/name)], check=True)
        subprocess.run(['git', '-C', str(root/name), 'checkout', '-q', '--detach', sha], check=True)
    assert git(root/name, 'rev-parse', 'HEAD').decode().strip() == sha
pin = '133d795c6aa640c44426172d684d3b733b0e21c3'
for script in ['prepare-template-sdk.py', 'finalize-template-sdk.py']:
    url = 'https://raw.githubusercontent.com/carstenartur/eclipse.jdt.ui/' + pin + '/.github/scripts/' + script
    with urllib.request.urlopen(url, timeout=90) as response:
        contents = response.read()
    exec(compile(contents, script, 'exec'), {'__name__': '__main__'})
repo, sha, branch, digest, title = entries[selected]
path = root/selected
patch = git(path, 'diff', '--binary')
actual = hashlib.sha256(patch).hexdigest()
assert actual == digest, (selected, actual, digest)
git(path, 'diff', '--check')
print(git(path, 'diff', '--stat').decode())
git(path, 'add', '.')
message = title + '\n\n' + (
    'Retain the legacy compatibility methods, and make 3.40.100 the explicit minimum for the replacement accessors. Document the necessary, version-specific API Tools exception rather than disabling version checks globally.' if selected == 'jdt-ui' else
    'Migrate the affected SDK template consumers to getTemplateContextRegistryCore(), require JDT UI 3.40.100, and exercise content-assist construction and the dependency floor in plug-in tests.'
) + '\n\nRelated: eclipse-jdt/eclipse.jdt.ui#3212\nRelated: eclipse-platform/eclipse.platform.releng.aggregator#4059\n\nAssisted-by: OpenAI ChatGPT'
git(path, '-c', 'user.name=Carsten Hammer', '-c', 'user.email=carsten.hammer@t-online.de', 'commit', '-s', '-m', message)
git(path, 'push', 'origin', 'HEAD:refs/heads/' + branch)
print('PUBLISHED', selected, git(path, 'rev-parse', 'HEAD').decode().strip())
