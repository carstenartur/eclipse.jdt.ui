#!/usr/bin/env python3
"""Isolated diagnostic harness. No changes to the PR branch or production code."""
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tarfile
import time
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path.cwd()
AREA = ROOT / 'restart-lab'
REPORT = ROOT / 'restart-evidence'
HERE = ROOT / '.github/pde-restart'
BASE = '9cd0c74a96e0344763194ccbb28339846e6d2a45'
HEAD = '97aeeaacad2c1af8bb08d1494ddfd192d5f96f67'
JAVA = str(Path(os.environ['JAVA_HOME']) / 'bin/java')
JAVAC = str(Path(os.environ['JAVA_HOME']) / 'bin/javac')


def run(args, **kwargs):
    print('+', ' '.join(map(str, args)), flush=True)
    return subprocess.run(list(map(str, args)), check=True, **kwargs)


def sha(path):
    h = hashlib.sha256()
    with Path(path).open('rb') as f:
        for chunk in iter(lambda: f.read(1024*1024), b''): h.update(chunk)
    return h.hexdigest()


def get(url):
    print('GET', url, flush=True)
    with urllib.request.urlopen(url, timeout=120) as response: return response.read()


def manifest(path):
    with zipfile.ZipFile(path) as jar:
        text = jar.read('META-INF/MANIFEST.MF').decode().replace('\r\n', '\n').replace('\n ', '')
    return dict(line.split(': ', 1) for line in text.splitlines() if ': ' in line)


def provision():
    AREA.mkdir(exist_ok=True); REPORT.mkdir(exist_ok=True)
    repository = 'https://download.eclipse.org/eclipse/updates/4.42-I-builds/'
    try:
        composite = get(repository + 'compositeArtifacts.xml')
    except Exception:
        import io
        with zipfile.ZipFile(io.BytesIO(get(repository + 'compositeArtifacts.jar'))) as jar:
            composite = jar.read('compositeArtifacts.xml')
    (REPORT / 'compositeArtifacts.xml').write_bytes(composite)
    locations = [item.attrib['location'] for item in ET.fromstring(composite).findall('.//child')]
    builds = sorted({match.group(0) for location in locations for match in re.finditer(r'I\d{8}-\d{4}', location)})
    if not builds: raise RuntimeError('Cannot identify an SDK I-build from ' + repr(locations))
    build = builds[-1]
    url = f'https://download.eclipse.org/eclipse/downloads/drops4/{build}/eclipse-SDK-{build}-linux-gtk-x86_64.tar.gz'
    archive = AREA / 'sdk.tar.gz'
    if not archive.exists(): archive.write_bytes(get(url))
    info = {'sdk_build': build, 'sdk_url': url, 'sdk_sha256': sha(archive), 'base': BASE, 'head': HEAD}
    (REPORT/'environment.json').write_text(json.dumps(info, indent=2))
    with tarfile.open(archive) as tar: tar.extractall(AREA, filter='data')


def install_bundle(installation, jar):
    attrs = manifest(jar)
    name = attrs['Bundle-SymbolicName'].split(';')[0]
    version = attrs['Bundle-Version']
    filename = name + '_' + version + '.jar'
    target = installation / 'plugins' / filename
    if Path(jar).resolve() != target.resolve(): shutil.copy2(jar, target)
    path = installation / 'configuration/org.eclipse.equinox.simpleconfigurator/bundles.info'
    lines = path.read_text().splitlines()
    old = [line for line in lines if line.startswith(name + ',')]
    lines = [line for line in lines if not line.startswith(name + ',')]
    lines.append(f'{name},{version},plugins/{filename},4,false')
    path.write_text('\n'.join(lines) + '\n')
    for line in old:
        location = line.split(',')[2]
        oldpath = installation/location
        if oldpath != target and oldpath.is_file(): oldpath.unlink()
    return {'symbolic_name': name, 'version': version, 'sha256': sha(jar)}


def prepare():
    REPORT.mkdir(exist_ok=True)
    # The two full source builds were produced by the workflow from pinned commits.
    base_src = Path(os.environ['RUNNER_TEMP'])/'pr3231-upstream'
    jars = {}
    for name, source in [('upstream', base_src), ('pr', ROOT)]:
        candidates = [j for j in (source/'org.eclipse.jdt.ui/target').glob('*.jar')
                      if not j.name.endswith('-sources.jar') and manifest(j).get('Bundle-SymbolicName', '').split(';')[0]=='org.eclipse.jdt.ui']
        if len(candidates)!=1: raise RuntimeError(f'Expected one built UI bundle: {candidates}')
        jars[name] = candidates[0]
    sdk = AREA/'eclipse'
    classes = AREA/'probe-classes'; classes.mkdir(exist_ok=True)
    cp = os.pathsep.join(str(j) for j in (sdk/'plugins').glob('*.jar'))
    run([JAVAC, '-cp', cp, '-d', classes, HERE/'Probe.java'])
    run([JAVAC, '--add-modules', 'jdk.jdi', '-d', classes, HERE/'TraceInitializer.java'])
    probe = AREA/'probe.restart_1.0.0.jar'
    mf = ('Manifest-Version: 1.0\nBundle-ManifestVersion: 2\nBundle-SymbolicName: probe.restart;singleton:=true\n'
          'Bundle-Version: 1.0.0\nBundle-Name: Isolated PDE Restart Probe\nBundle-RequiredExecutionEnvironment: JavaSE-21\n'
          'Require-Bundle: org.eclipse.ui,org.eclipse.core.resources,org.eclipse.core.runtime,\n'
          ' org.eclipse.jdt.core,org.eclipse.jdt.ui,org.eclipse.pde.core,org.eclipse.jface,\n org.eclipse.swt,org.eclipse.ui.ide\n\n')
    with zipfile.ZipFile(probe, 'w') as jar:
        jar.writestr('META-INF/MANIFEST.MF', mf)
        jar.writestr('plugin.xml', '<?xml version="1.0"?><plugin><extension point="org.eclipse.ui.startup"><startup class="probe.Probe"/></extension></plugin>')
        for f in (classes/'probe').rglob('*.class'): jar.write(f, str(f.relative_to(classes)))
    installed = {}
    for name in jars:
        installation = AREA/name/'eclipse'
        shutil.copytree(sdk, installation)
        installed[name] = [install_bundle(installation, jars[name]), install_bundle(installation, probe)]
        # Keep identical source-built core.manipulation in both installations.
        manipulation = [j for j in (ROOT/'org.eclipse.jdt.core.manipulation/target').glob('*.jar') if not j.name.endswith('-sources.jar')]
        for jar in manipulation:
            if manifest(jar).get('Bundle-SymbolicName','').split(';')[0]=='org.eclipse.jdt.core.manipulation':
                installed[name].append(install_bundle(installation, jar))
    (REPORT/'installed-bundles.json').write_text(json.dumps(installed, indent=2))
    shutil.copytree(HERE, REPORT/'harness', dirs_exist_ok=True)


def launch(installation, workspace, output, phase, mode, debug):
    output.mkdir(parents=True, exist_ok=True)
    launcher = list((installation/'plugins').glob('org.eclipse.equinox.launcher_*.jar'))
    if len(launcher)!=1: raise RuntimeError(launcher)
    command = [JAVA, '-Xms256m', '-Xmx1536m', '-Dprobe.out='+str(output), '-Dprobe.phase='+phase, '-Dprobe.mode='+mode]
    port = 5007
    if debug: command.append(f'-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=127.0.0.1:{port}')
    command += ['-jar', str(launcher[0]), '-install', str(installation), '-configuration', str(installation/'configuration'),
                '-data', str(workspace), '-product', 'org.eclipse.sdk.ide', '-application', 'org.eclipse.ui.ide.workbench',
                '-clean', '-nosplash', '-consoleLog']
    (output/'command.json').write_text(json.dumps(command, indent=2))
    begin = time.monotonic()
    with (output/'process.log').open('w') as log:
        app = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT)
        debugger = None
        if debug:
            debugger = subprocess.Popen([JAVA, '--add-modules', 'jdk.jdi', '-cp', str(AREA/'probe-classes'), 'TraceInitializer', str(port), str(output)],
                                        stdout=log, stderr=subprocess.STDOUT)
        try:
            code = app.wait(timeout=150)
        except subprocess.TimeoutExpired:
            with (output/'timeout-threads.txt').open('w') as dump:
                subprocess.run([str(Path(os.environ['JAVA_HOME'])/'bin/jcmd'), str(app.pid), 'Thread.print'], stdout=dump, stderr=subprocess.STDOUT, timeout=15)
            app.kill(); code = app.wait(); (output/'timeout').write_text('150s')
        if debugger:
            try: debugger.wait(timeout=10)
            except subprocess.TimeoutExpired: debugger.kill(); debugger.wait()
    metadata = workspace/'.metadata'
    if (metadata/'.log').exists(): shutil.copy2(metadata/'.log', output/'eclipse.log')
    state = metadata/'.plugins/org.eclipse.e4.workbench/workbench.xmi'
    if state.exists(): shutil.copy2(state, output/'workbench.xmi')
    text = (output/'workbench.log').read_text() if (output/'workbench.log').exists() else ''
    debugger_text = (output/'debugger.log').read_text() if (output/'debugger.log').exists() else ''
    hits = re.findall(r'INITIALIZER_HIT number=\d+ thread=(.*)', debugger_text)
    result = {'phase': phase, 'mode': mode, 'pid': app.pid, 'exit': code, 'elapsed_seconds': round(time.monotonic()-begin,3),
              'finished': (output/'finished').exists(), 'fatal': (output/'fatal').exists(),
              'initializer_threads': hits, 'initializer_on_main': any(t=='main' for t in hits),
              'restored_editor': bool(re.search(r'RESTORED EDITOR .*Example.java instantiated=true',text)),
              'final_editor': bool(re.search(r'FINAL EDITOR .*Example.java instantiated=true',text)),
              'final_breadcrumb': 'FINAL BREADCRUMB=Example' in text,
              'final_link': 'FINAL LINK=true' in text, 'final_hierarchy': 'FINAL HIERARCHY=Example' in text}
    (output/'result.json').write_text(json.dumps(result, indent=2))
    print(json.dumps(result), flush=True)
    return result


def exercise():
    results=[]
    for mode in ['editor', 'link', 'hierarchy']:
        workspace=AREA/('workspace-'+mode)
        seed=launch(AREA/'upstream/eclipse',workspace,REPORT/mode/'seed','seed',mode,False)
        results.append(seed)
        if not seed['finished'] or not seed['final_editor']: raise RuntimeError('Seeding failed; inspect '+mode)
        state=workspace/'.metadata/.plugins/org.eclipse.e4.workbench/workbench.xmi'
        if not state.exists() or 'Example.java' not in state.read_text(): raise RuntimeError('No persisted Java editor')
        snapshot=AREA/('snapshot-'+mode); shutil.copytree(workspace,snapshot)
        for name in ['upstream','pr']:
            shutil.rmtree(workspace); shutil.copytree(snapshot,workspace)
            output=REPORT/mode/name
            output.mkdir(parents=True)
            shutil.copy2(state, output/'workbench-before.xmi')
            result=launch(AREA/name/'eclipse',workspace,output,'restart',mode,True)
            result['implementation']=name; results.append(result)
            (REPORT/'results.json').write_text(json.dumps(results,indent=2))
    # Test findings are data, not overwritten by the workflow success status.
    invalid=[r for r in results if not r['finished'] or r['fatal'] or (r['phase']=='restart' and (not r['restored_editor'] or not r['initializer_threads']))]
    if invalid: raise RuntimeError('Some runtime checks are incomplete; see results.json')


if __name__=='__main__':
    {'provision': provision, 'prepare': prepare, 'exercise': exercise}[sys.argv[1]]()
