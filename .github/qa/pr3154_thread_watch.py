"""Capture bounded, read-only thread diagnostics for this checkout's Tycho test VMs."""
from __future__ import annotations

import argparse
import datetime
import json
import os
from pathlib import Path
import subprocess
import time


def is_test_vm(arguments: list[str], workspace: Path) -> bool:
    """Do not attach to unrelated JVMs, Maven itself or the diagnostic jcmd process."""
    if not arguments or Path(arguments[0]).name != 'java':
        return False
    try:
        application = arguments[arguments.index('-application') + 1]
        data = Path(arguments[arguments.index('-data') + 1]).resolve()
    except (ValueError, IndexError, OSError):
        return False
    return (application.startswith('org.eclipse.tycho.surefire.')
            and data.is_relative_to(workspace.resolve()) and 'target' in data.parts)


def test_vms(workspace: Path):
    for directory in Path('/proc').iterdir():
        if not directory.name.isdecimal():
            continue
        try:
            if directory.stat().st_uid != os.geteuid():
                continue
            arguments = [arg.decode('utf-8', errors='replace')
                         for arg in (directory / 'cmdline').read_bytes().split(b'\0') if arg]
            if not is_test_vm(arguments, workspace):
                continue
            # Include the process start tick in its identity so reused PIDs are not confused.
            stat = (directory / 'stat').read_text()
            identity = (int(directory.name), stat[stat.rfind(')') + 2:].split()[19])
            yield identity, arguments
        except (OSError, IndexError):
            continue  # A short-lived test VM may exit during enumeration.


def capture(identity, arguments, destination: Path, jcmd: str) -> dict:
    pid, start_tick = identity
    folder = destination / f'{pid}-{start_tick}'
    folder.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
    metadata = {'pid': pid, 'start_tick': start_tick, 'timestamp': timestamp,
                'arguments': arguments, 'operation': 'Thread.print -l'}
    for name in ('stat', 'status'):
        try:
            metadata[name] = (Path('/proc') / str(pid) / name).read_text()
        except OSError:
            pass
    output = folder / f'{timestamp}.threads.txt'
    try:
        with output.open('w', encoding='utf-8') as stream:
            result = subprocess.run([jcmd, str(pid), 'Thread.print', '-l'],
                                    stdout=stream, stderr=subprocess.STDOUT, timeout=20, check=False)
        metadata['exit_code'] = result.returncode
    except (OSError, subprocess.TimeoutExpired) as error:
        metadata['error'] = str(error)
    (folder / f'{timestamp}.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
    print(json.dumps({key: value for key, value in metadata.items()
                      if key not in ('stat', 'status', 'arguments')}), flush=True)
    return metadata


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--workspace', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--stop-file', type=Path, required=True)
    parser.add_argument('--jcmd', required=True)
    parser.add_argument('--once', action='store_true', help='Immediate single snapshot for diagnostic self-test')
    args = parser.parse_args()
    next_capture = {}
    counts = {}
    while not args.stop_file.exists():
        now = time.monotonic()
        for identity, arguments in test_vms(args.workspace):
            due = next_capture.setdefault(identity, now if args.once else now + 90)
            if now >= due and counts.get(identity, 0) < 12:
                capture(identity, arguments, args.output, args.jcmd)
                next_capture[identity] = time.monotonic() + 180
                counts[identity] = counts.get(identity, 0) + 1
        if args.once:
            return
        time.sleep(2)


if __name__ == '__main__':
    main()
