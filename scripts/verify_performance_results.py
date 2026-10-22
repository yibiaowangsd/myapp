#!/usr/bin/env python3
"""Fail CI for missing/skipped journeys, empty metrics, or unbundled ART profiles."""
import json
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GENERATED = ROOT / 'performance-results/profile-generation'
MEASURED = ROOT / 'benchmark/build/outputs'


def require_tests(directory, classname, expected):
    cases = []
    for path in directory.rglob('TEST-*.xml'):
        cases.extend(case for case in ET.parse(path).iter('testcase')
                     if case.get('classname') == classname)
    seen = set()
    for case in cases:
        name = case.get('name', '').split('[')[0]
        assert not any(case.find(tag) is not None for tag in ('failure', 'error', 'skipped')), (
            classname, name, 'failed or skipped')
        seen.add(name)
    assert seen == expected, (classname, 'missing/unexpected tests', seen, expected)
    print(classname + ': ' + ', '.join(sorted(seen)) + ' PASS (0 skipped)')


def main():
    require_tests(GENERATED, 'com.personal.wo.benchmark.BaselineProfileGenerator',
                  {'firstRunStartup', 'startup', 'timelineDatesAndAchievements'})
    expected = {'firstRunStartup': 10, 'coldStartup': 10, 'warmStartup': 10,
                'timelineFrames': 5, 'datesAndTabs': 5}
    require_tests(MEASURED, 'com.personal.wo.benchmark.StartupAndTimelineBenchmark', set(expected))

    # Check fresh device output, not the previously committed source profiles.
    for name in ('baseline-prof.txt', 'startup-prof.txt'):
        paths = list(GENERATED.rglob('*' + name))
        assert paths, 'No freshly generated ' + name
        rules = {line.strip() for p in paths for line in p.read_text().splitlines()
                 if 'Lcom/personal/wo/' in line and not line.startswith('#')}
        assert rules and any('WelcomeUiKt' in line for line in rules), name + ' missed first-run UI'
        assert any('DayDrumUiKt' in line for line in rules), name + ' missed returning-user home'
        print(name + ': ' + str(len(rules)) + ' freshly collected app rules')

    measured = {}
    for path in MEASURED.rglob('*benchmarkData.json'):
        for result in json.loads(path.read_text()).get('benchmarks', []):
            name = result['name']
            if name not in expected:
                continue
            assert result['repeatIterations'] == expected[name], (name, 'iteration count changed')
            metrics = result.get('metrics', {}) | result.get('sampledMetrics', {})
            required = ('timeToInitialDisplayMs', 'timeToFullDisplayMs') if 'Startup' in name else ('frameDurationCpuMs',)
            for metric in required:
                values = metrics[metric]
                assert values['runs'], (name, metric, 'no measurements')
                print(f"{name}: {metric} median={values['median']}")
            measured[name] = metrics
    assert set(measured) == set(expected), ('Missing metric JSON', set(expected) - set(measured))

    apks = list((ROOT / 'app/build/outputs/apk/personalOffline/benchmark').glob('*.apk'))
    assert apks, 'No measured APK'
    for apk in apks:
        with zipfile.ZipFile(apk) as archive:
            for name in ('assets/dexopt/baseline.prof', 'assets/dexopt/baseline.profm'):
                assert len(archive.read(name)) > 0, (apk, 'Empty ' + name)
        print(apk.name + ': embedded ART profiles PASS')


if __name__ == '__main__':
    main()
