#!/usr/bin/env python3
"""Serialize lifecycle changes with view session switches and reject stale callbacks."""
from pathlib import Path
import re
import sys


def patch(text):
    start = text.index('\tprivate class TestSessionListener implements ITestSessionListener {')
    end = text.index('\n\tprivate class UpdateUIJob', start)
    body = text[start:end]
    start_sort = '''\t\t\tgetDisplay().asyncExec(new Runnable() {
\t\t\t\t@Override
\t\t\t\tpublic void run() {
\t\t\t\t\tfTestViewer.setSortingCriterion(SortingCriterion.SORT_BY_EXECUTION_ORDER);
\t\t\t\t}
\t\t\t});'''
    end_sort = '''\t\t\tgetDisplay().asyncExec(new Runnable() {
\t\t\t\t@Override
\t\t\t\tpublic void run() {
\t\t\t\t\tsetSortingCriterion(fSortingCriterion);
\t\t\t\t}
\t\t\t});'''
    if body.count(start_sort) != 1 or body.count(end_sort) != 1:
        raise ValueError('Unexpected lifecycle sorting code')
    body = body.replace(start_sort, '\t\t\tfTestViewer.setSortingCriterion(SortingCriterion.SORT_BY_EXECUTION_ORDER);')
    body = body.replace(end_sort, '\t\t\tsetSortingCriterion(fSortingCriterion);')
    pattern = re.compile(r'(\t\tpublic void (sessionStarted|sessionEnded|sessionStopped|sessionTerminated)\([^)]*\)\s*\{)\n(.*?)(\n\t\t\})', re.S)
    seen = set()
    def wrap(match):
        seen.add(match[2])
        indented = '\n'.join(('\t' + line) if line else '' for line in match[3].split('\n'))
        return match[1] + '\n\t\t\trunForActiveSession(() -> {\n' + indented + '\n\t\t\t});' + match[4]
    body, count = pattern.subn(wrap, body)
    if count != 4 or seen != {'sessionStarted', 'sessionEnded', 'sessionStopped', 'sessionTerminated'}:
        raise ValueError('Expected all four lifecycle callbacks')
    helper = '''
\t\tprivate void runForActiveSession(Runnable runnable) {
\t\t\t// A notifier may retain this listener after the view switches sessions.
\t\t\t// Check its identity and update the jobs together on the UI thread.
\t\t\tpostSyncRunnable(() -> {
\t\t\t\tif (!isDisposed() && fTestSessionListener == this)
\t\t\t\t\trunnable.run();
\t\t\t});
\t\t}
'''
    closing = body.rfind('\n\t}')
    if closing < 0:
        raise ValueError('Missing listener boundary')
    body = body[:closing] + helper + body[closing:]
    return text[:start] + body + text[end:]


if __name__ == '__main__':
    path = Path(sys.argv[1])
    path.write_text(patch(path.read_text()))
