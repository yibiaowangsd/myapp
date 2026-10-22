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

    # AndroidX emits startup-only collections as startup-prof.txt; they must
    # contribute to both the Baseline Profile (ART) and Startup Profile (DEX layout).
    fresh = {}
    for name in ('baseline-prof.txt', 'startup-prof.txt'):
        paths = list(GENERATED.rglob('*' + name))
        assert paths, 'No freshly generated ' + name
        fresh[name] = {line.strip() for p in paths for line in p.read_text().splitlines()
                       if 'Lcom/personal/wo/' in line and not line.startswith('#')}
        assert fresh[name] and any('DayDrumUiKt' in line for line in fresh[name]), name + ' missed home'
    assert any('WelcomeUiKt' in line for line in fresh['startup-prof.txt']), 'Startup collection missed welcome'
    fresh['baseline-prof.txt'] |= fresh['startup-prof.txt']
    for name, required_rules in fresh.items():
        saved = set((ROOT / 'app/src/main/baselineProfiles' / name).read_text().splitlines())
        assert required_rules <= saved, name + ' dropped freshly collected rules'
        print(name + ': ' + str(len(required_rules)) + ' freshly collected app rules retained')

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
                assert len(values['runs']) == expected[name], (name, metric, 'missing iterations')
                assert all(run if isinstance(run, list) else run >= 0 for run in values['runs']), (name, metric, 'empty measurements')
                median = values.get('median', values.get('P50'))
                assert median is not None, (name, metric, 'missing median/P50')
                print(f"{name}: {metric} median={median}")
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
