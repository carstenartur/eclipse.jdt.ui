"""Fork-only evidence collection. Never edit product sources or weaken test assertions."""
from __future__ import annotations

import argparse
import datetime
import json
import os
from pathlib import Path
import re
import shutil
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET

BASE = '6312a1cc907ad0a5ac5dd42e998eb0cbac686205'
CANDIDATE = 'bf24d6056342abb9a574c9f5ec0b9092b2ca238a'
MODULES = {'org.eclipse.jdt.ui.tests', 'org.eclipse.jdt.text.tests', 'org.eclipse.jdt.ui.tests.refactoring'}
LIFECYCLE = {
    'testRetiredSessionTerminationDoesNotStopActiveSession',
    'testRetiredSessionStopDoesNotStopActiveSession',
    'testRetiredSessionEndDoesNotStopActiveSession',
    'testActiveSessionTerminationStopsUpdateJobs',
    'testActiveSessionStopStopsUpdateJobs',
    'testActiveSessionEndStopsUpdateJobs',
}


def summarize(reports: Path, variant: str, maven_exit: int) -> dict:
    """Inspect real testcase elements, not only Maven's optional failure status."""
    required = {('org.eclipse.jdt.junit.tests.' + cls, 'testTerminateLaunch')
                for cls in ('TestRunListenerTest5', 'TestRunListenerTest6')}
    if variant == 'candidate':
        required.update(('org.eclipse.jdt.junit.tests.TestRunListenerTest5', name) for name in LIFECYCLE)
    executed, seen = set(), set()
    counts, failures, skipped, malformed = {}, [], 0, []
    total = 0
    for report in sorted(reports.glob('*/TEST-*.xml')):
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError as error:
            malformed.append({'report': str(report), 'error': str(error)})
            continue
        for case in root.iter('testcase'):
            total += 1
            key = (case.get('classname', ''), case.get('name', ''))
            seen.add(key)
            if case.find('skipped') is not None:
                skipped += 1
                continue
            counts[report.parent.name] = counts.get(report.parent.name, 0) + 1
            executed.add(key)
            for kind in ('failure', 'error'):
                failure = case.find(kind)
                if failure is not None:
                    failures.append({'module': report.parent.name, 'class': key[0], 'test': key[1],
                                     'kind': kind, 'message': failure.get('message', ''),
                                     'trace': failure.text or ''})
    missing = sorted(required - executed)
    absent_modules = sorted(MODULES - counts.keys())
    complete = sum(counts.values()) >= 9000 and not missing and not absent_modules and not malformed
    return {'variant': variant, 'maven_exit': maven_exit, 'tests': total,
            'unique_tests': len(seen), 'executed_unique': len(executed), 'skipped': skipped,
            'module_executions': counts, 'missing_required': missing, 'missing_modules': absent_modules,
            'malformed': malformed, 'failures': failures, 'inventory_complete': complete,
            'passed': complete and maven_exit == 0 and not failures}


def capture(workspace: Path, phase: str, variant: str, maven_exit: int) -> dict:
    dest = workspace / 'evidence' / phase
    reports = dest / 'reports'
    for module in workspace.iterdir():
        if not module.is_dir() or module.name.startswith('.') or module.name == 'evidence':
            continue
        for report in (module / 'target' / 'surefire-reports').glob('*'):
            if report.is_file():
                folder = reports / module.name
                folder.mkdir(parents=True, exist_ok=True)
                shutil.copy2(report, folder / report.name)
        for log in (module / 'target' / 'work' / 'data' / '.metadata').glob('*.log'):
            folder = dest / 'eclipse-logs' / module.name
            folder.mkdir(parents=True, exist_ok=True)
            shutil.copy2(log, folder / log.name)
    result = summarize(reports, variant, maven_exit)
    dest.mkdir(parents=True, exist_ok=True)
    (dest / 'summary.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    compact = {**result, 'failures': [{k: v for k, v in f.items() if k != 'trace'} for f in result['failures']]}
    print(json.dumps(compact, indent=2), flush=True)
    if summary_path := os.environ.get('GITHUB_STEP_SUMMARY'):
        with open(summary_path, 'a', encoding='utf-8') as out:
            out.write(f"### {phase} / {variant}\n{result['tests']} test executions; "
                      f"{len(result['failures'])} failures; {result['skipped']} skipped; "
                      f"complete inventory: {result['inventory_complete']}; "
                      f"Maven exit: {maven_exit}; passed: {result['passed']}.\n\n")
    return result


def toolchains() -> None:
    root = ET.Element('toolchains')
    supported = set()
    for major, bree in ((8, 'JavaSE-1.8'), (17, 'JavaSE-17'), (21, 'JavaSE-21')):
        home = Path(os.environ[f'JDK{major}'])
        release = (home / 'release').read_text(encoding='utf-8')
        match = re.search(r'^JAVA_VERSION="([^"]+)"', release, re.M)
        version = match.group(1) if match else ''
        actual = int(version.split('.')[1] if version.startswith('1.') else version.split('.')[0])
        if actual != major or not (home / 'bin' / 'javac').is_file():
            raise ValueError(f'Invalid JDK {major}: {home}')
        entry = ET.SubElement(root, 'toolchain')
        ET.SubElement(entry, 'type').text = 'jdk'
        provides = ET.SubElement(entry, 'provides')
        for name, value in (('id', bree), ('version', '1.8' if major == 8 else str(major)), ('vendor', 'temurin')):
            ET.SubElement(provides, name).text = value
        ET.SubElement(ET.SubElement(entry, 'configuration'), 'jdkHome').text = str(home)
        supported.add(bree)
    required = set()
    for manifest in Path('.').glob('*/META-INF/MANIFEST.MF'):
        text = re.sub(r'\r?\n ', '', manifest.read_text(encoding='utf-8'))
        match = re.search(r'^Bundle-RequiredExecutionEnvironment:\s*(.+)$', text, re.M)
        if match:
            required.update(part.strip() for part in match.group(1).split(','))
    if required - supported:
        raise ValueError(f'Unconfigured BREEs: {sorted(required - supported)}')
    target = Path.home() / '.m2' / 'toolchains.xml'
    target.parent.mkdir(parents=True, exist_ok=True)
    ET.indent(root)
    ET.ElementTree(root).write(target, encoding='utf-8', xml_declaration=True)
    Path('evidence').mkdir(exist_ok=True)
    shutil.copy2(target, 'evidence/toolchains.xml')
    print('Configured BREEs:', sorted(supported), 'Required BREEs:', sorted(required), flush=True)


def jenkins() -> None:
    """Read only public Jenkins resources; do not supply GitHub credentials."""
    dest = Path('evidence/jenkins')
    dest.mkdir(parents=True, exist_ok=True)
    outcomes = []
    for number in (1, 2):
        base = f'https://ci.eclipse.org/jdt/job/eclipse.jdt.ui-github/job/PR-3154/{number}/'
        paths = {
            'build.json': 'api/json?tree=number,building,result,url,timestamp,duration,actions[lastBuiltRevision[SHA1]]',
            'tests.json': 'testReport/api/json?tree=failCount,skipCount,totalCount,suites[cases[className,name,status,errorDetails,errorStackTrace]]',
            'console.log': 'consoleText',
        }
        for filename, suffix in paths.items():
            url = base + suffix
            outcome = {'build': number, 'file': filename, 'url': url,
                       'retrieved_at': datetime.datetime.now(datetime.timezone.utc).isoformat()}
            try:
                with urllib.request.urlopen(url, timeout=45) as response:
                    data = response.read(64 * 1024 * 1024 + 1)
                    if len(data) > 64 * 1024 * 1024:
                        raise ValueError('Response exceeded evidence size limit')
                    outcome['http_status'] = response.status
                parsed = json.loads(data) if filename.endswith('.json') else None
                (dest / f'{number}-{filename}').write_bytes(data)
                if parsed is not None:
                    outcome['summary'] = {key: value for key, value in parsed.items() if key != 'suites'}
                    if 'suites' in parsed:
                        outcome['failures'] = [case for suite in parsed['suites'] for case in suite.get('cases', [])
                                               if case.get('status') in ('FAILED', 'REGRESSION')]
            except (urllib.error.URLError, TimeoutError, OSError, ValueError) as error:
                outcome['error'] = str(error)
            outcomes.append(outcome)
            print(json.dumps(outcome, indent=2), flush=True)
    (dest / 'retrieval.json').write_text(json.dumps(outcomes, indent=2), encoding='utf-8')
    if not any('summary' in item for item in outcomes):
        raise SystemExit('No usable public Jenkins metadata retrieved; this is not CI evidence')


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('operation', choices=('capture', 'toolchains', 'jenkins'))
    parser.add_argument('--phase', choices=('upstream', 'tests-only'))
    parser.add_argument('--variant', choices=('base', 'candidate'))
    parser.add_argument('--exit-code', type=int)
    args = parser.parse_args()
    if args.operation == 'capture':
        if args.phase is None or args.variant is None or args.exit_code is None:
            parser.error('capture requires --phase, --variant and --exit-code')
        capture(Path.cwd(), args.phase, args.variant, args.exit_code)
    elif args.operation == 'toolchains':
        toolchains()
    else:
        jenkins()


if __name__ == '__main__':
    main()
