# Copyright (c) 2026 Carsten Hammer.
# SPDX-License-Identifier: EPL-2.0
"""Install diagnostic-only sources and validate actual Platform events against XML."""
from __future__ import annotations
import argparse
from collections import Counter, defaultdict, deque
import hashlib
import json
from pathlib import Path
import tempfile
import xml.etree.ElementTree as ET

MODULE = Path('org.eclipse.jdt.ui.tests')
JAVA = MODULE / 'ui/org/eclipse/jdt/ui/tests'
HELPER = JAVA / 'quickfix/AbstractAnnotateAssistTests.java'
TARGET = 'testAnnotateParameter_WildcardBound'
PREDECESSORS = ['NullAnnotationsQuickFixTest', 'NullAnnotationsQuickFixTest1d8',
                'NullAnnotationsQuickFixTest1d8Mix', 'NullAnnotationsQuickFixTest9',
                'AnnotateAssistTest1d5', 'AnnotateAssistTest1d8']


def install():
    here = Path(__file__).resolve().parent
    for name in ('StateTraceListener.java', 'StateTraceSmokeSuite.java'):
        dest = JAVA / name
        assert not dest.exists(), f'Diagnostic source already exists: {dest}'
        dest.write_bytes((here / name).read_bytes())
    service = MODULE / 'META-INF/services/org.junit.platform.launcher.TestExecutionListener'
    assert not service.exists(), 'Do not replace an existing service registration'
    service.parent.mkdir(parents=True, exist_ok=True)
    service.write_text('org.eclipse.jdt.ui.tests.StateTraceListener\n')
    (JAVA / 'StatePredecessorSuite.java').write_text(
        '// SPDX-License-Identifier: EPL-2.0\npackage org.eclipse.jdt.ui.tests;\n'
        'import org.junit.platform.suite.api.SelectClasses;\nimport org.junit.platform.suite.api.Suite;\n'
        '@Suite\n@SelectClasses({' + ','.join('org.eclipse.jdt.ui.tests.quickfix.' + c + '.class' for c in PREDECESSORS)
        + '})\npublic class StatePredecessorSuite {}\n')
    data = HELPER.read_bytes()
    actual = hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()
    assert actual == '6e2e3d4a782ffef35858406dec86696b66de89f4', f'Unreviewed helper blob: {actual}'
    old = '\t\t\tif (proposals==null) {\n'
    new = old + '\t\t\t\torg.eclipse.jdt.ui.tests.StateTraceListener.missingSource(((IClassFileEditorInput) javaEditor.getEditorInput()).getClassFile());\n'
    text = data.decode()
    assert text.count(old) == 1
    HELPER.write_text(text.replace(old, new, 1))


def read_events(folder):
    files = sorted(folder.glob('trace-*.jsonl'))
    if not files:
        raise AssertionError('Listener not registered or trace not written; no evidence')
    all_events = []
    for file in files:
        last = 0
        for line in file.read_text().splitlines():
            row = json.loads(line)
            assert row['seq'] > last, 'Non-monotonic event sequence'
            last = row['seq']
            all_events.append(row)
    assert not any(e['event'] == 'OBSERVER_ERROR' for e in all_events), 'Observer snapshot failed'
    streams = defaultdict(list)
    for event in all_events:
        if 'listener' in event:
            streams[event['pid'], event['listener']].append(event)
    if not streams:
        raise AssertionError('No listener stream')
    # Nested Suite engines may instantiate additional listeners. Select the outer complete stream;
    # keep ALL streams in the raw evidence, and report their inventories for review.
    events = max(streams.values(), key=lambda s: sum(e['event'] == 'FINISH' and e.get('isTest', False) for e in s))
    return all_events, streams, events


def check_lifecycle(events):
    active = set()
    finished = []
    overlaps = []
    for row in events:
        if not row.get('isTest'):
            continue
        key = row['id']
        if row['event'] == 'START':
            assert key not in active, f'Duplicate active ID: {key}'
            if active:
                overlaps.append({'seq': row['seq'], 'active': sorted(active), 'starting': key})
            active.add(key)
        elif row['event'] == 'FINISH':
            assert key in active, f'Finish without start: {key}'
            active.remove(key)
            finished.append(row)
    assert not active, f'Unfinished tests: {active}'
    assert any(e['event'] == 'PLAN_START' for e in events), 'Missing plan start'
    assert any(e['event'] == 'PLAN_FINISH' for e in events), 'Missing plan completion'
    return finished, overlaps


def analyze(folder, reports, phase, exit_code):
    all_events, streams, events = read_events(folder)
    finished, overlaps = check_lifecycle(events)
    cases = [case for report in reports.glob('TEST-*.xml') for case in ET.parse(report).getroot().iter('testcase')]
    assert cases, 'No original Surefire XML reports'
    skipped = sum(c.find('skipped') is not None for c in cases)
    failures = [c.attrib for c in cases if c.find('failure') is not None or c.find('error') is not None]
    assert len(finished) == len(cases) - skipped, f'Trace/XML inventory mismatch: {len(finished)} vs {len(cases)} minus {skipped}'
    failed_events = [e for e in finished if e['status'] == 'FAILED']
    assert len(failed_events) == len(failures), 'Trace/XML failure mismatch'
    def covered(text):
        return any(text in e.get('source', '') or text in e.get('id', '') for e in finished)
    if phase == 'smoke':
        assert len(finished) == 3 and not skipped and not failures
        assert all(covered(name) for name in ('test01Write', 'test02Read', 'jupiterInsideNestedSuite'))
        assert any('junit-vintage' in e['id'] for e in finished)
        assert any('junit-jupiter' in e['id'] for e in finished)
        writes = [e for e in events if 'property/jdt.stateTrace.smoke' in e.get('observedDelta', {})]
        assert any(e['observedDelta']['property/jdt.stateTrace.smoke']['after'] == 'deliberate-observer-sentinel' for e in writes)
        assert any(not e['observedDelta']['property/jdt.stateTrace.smoke']['afterPresent'] for e in writes), 'Class cleanup not observed'
    else:
        assert all(covered(name) for name in PREDECESSORS), 'Missing predecessor class'
        assert covered(TARGET), 'Missing failing test'
        if phase == 'full-state':
            assert len(finished) >= 3000, 'Not the full UI AutomatedSuite'
            assert covered('testTerminateLaunch') and covered('testRetiredSessionEndDoesNotStopActiveSession'), 'Lifecycle tests absent'
    state = {}
    recent = deque(maxlen=30)
    boundaries = []
    contexts = []
    for row in events:
        for key, change in row.get('observedDelta', {}).items():
            if change['afterPresent']:
                state[key] = change['after']
            else:
                state.pop(key, None)
        if row['event'] == 'START' and row.get('isTest') and TARGET in row['id']:
            contexts.append({'target': row['id'], 'seq': row['seq'], 'predecessors': list(recent), 'observedState': dict(state)})
        if row['event'] == 'FINISH' and row.get('isTest'):
            recent.append({'seq': row['seq'], 'id': row['id'], 'status': row['status']})
            stable = {key: value for key, value in row.get('boundaryDelta', {}).items()
                      if key.startswith(('property/', 'javaOption/', 'preference/', 'locale/')) or key in ('timezone', 'workspace/autoBuilding')}
            if stable:
                boundaries.append({'id': row['id'], 'seq': row['seq'], 'changes': stable})
    summary = {'phase': phase, 'maven_exit': exit_code, 'xml_cases': len(cases), 'skipped': skipped,
               'executed': len(finished), 'failures': failures, 'test_overlap_count': len(overlaps),
               'state_boundary_changes': len(boundaries), 'missing_source_events': sum(e['event'] == 'MISSING_SOURCE' for e in all_events),
               'listener_streams': {str(k): sum(e['event'] == 'FINISH' and e.get('isTest', False) for e in v) for k, v in streams.items()},
               'evidence_valid': True, 'tests_passed': exit_code == 0 and not failures}
    (folder / 'summary.json').write_text(json.dumps(summary, indent=2))
    (folder / 'boundary-deltas.json').write_text(json.dumps(boundaries, indent=2))
    (folder / 'target-context.json').write_text(json.dumps(contexts, indent=2))
    (folder / 'overlaps.json').write_text(json.dumps(overlaps, indent=2))
    (folder / 'order.txt').write_text('\n'.join(str(e['seq']) + '\t' + e['id'] for e in events if e['event'] == 'START' and e.get('isTest')) + '\n')
    print(json.dumps(summary, indent=2))
    return summary


def selftest():
    def event(n, kind, **kw):
        return dict(seq=n, pid=1, listener=1, event=kind, **kw)
    valid = [event(1, 'PLAN_START'), event(2, 'START', id='x', isTest=True),
             event(3, 'FINISH', id='x', isTest=True, status='SUCCESSFUL'), event(4, 'PLAN_FINISH')]
    assert len(check_lifecycle(valid)[0]) == 1
    for bad in (valid[:-2], valid[:1] + valid[2:], valid[:2] + valid[1:], valid[:-1]):
        try:
            check_lifecycle(bad)
        except AssertionError:
            pass
        else:
            raise AssertionError('Invalid lifecycle accepted')
    with tempfile.TemporaryDirectory() as tmp:
        folder = Path(tmp)
        try:
            read_events(folder)
        except AssertionError:
            pass
        else:
            raise AssertionError('Empty trace accepted')
        file = folder / 'trace-1.jsonl'
        file.write_text('\n'.join(map(json.dumps, valid)))
        assert read_events(folder)[2] == valid
        file.write_text(file.read_text() + '\n{"seq":')
        try:
            read_events(folder)
        except json.JSONDecodeError:
            pass
        else:
            raise AssertionError('Truncated trace accepted')
    print('8 trace-validator checks passed')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('install', 'analyze', 'selftest'))
    parser.add_argument('--folder', type=Path)
    parser.add_argument('--reports', type=Path, default=MODULE / 'target/surefire-reports')
    parser.add_argument('--phase')
    parser.add_argument('--exit-code', type=int, default=0)
    args = parser.parse_args()
    if args.mode == 'install':
        install()
    elif args.mode == 'selftest':
        selftest()
    else:
        assert args.folder is not None and args.phase is not None
        result = analyze(args.folder, args.reports, args.phase, args.exit_code)
        if args.phase == 'smoke':
            assert result['tests_passed'], 'Observer smoke test failed'
