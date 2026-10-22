"""Contract checks for the real BaselineProfileRule output split."""
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


class ImportProfilesTest(unittest.TestCase):
    def test_startup_rules_compile_in_baseline_but_interaction_rules_do_not_enter_startup(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'scripts').mkdir()
            dest = root / 'app/src/main/baselineProfiles'
            dest.mkdir(parents=True)
            script = root / 'scripts/import_profiles.py'
            shutil.copyfile(Path(__file__).with_name('import_profiles.py'), script)
            outputs = root / 'outputs'
            outputs.mkdir()
            welcome = 'HSPLcom/personal/wo/ui/WelcomeUiKt;->WelcomeScreen()V'
            home = 'HSPLcom/personal/wo/ui/DayDrumUiKt;->DayDrumHome()V'
            interaction = 'HSPLcom/personal/wo/ui/TimelineWheelKt;->CylinderPages()V'
            seed = 'Lcom/personal/wo/MainActivity;'
            (dest / 'baseline-prof.txt').write_text(seed + '\n')
            (outputs / 'firstRun-startup-prof.txt').write_text(welcome + '\n')
            (outputs / 'returning-startup-prof.txt').write_text(home + '\n')
            (outputs / 'interactions-baseline-prof.txt').write_text(interaction + '\n')
            subprocess.run([sys.executable, str(script), str(outputs)], check=True, capture_output=True)
            baseline = (dest / 'baseline-prof.txt').read_text()
            startup = (dest / 'startup-prof.txt').read_text()
            for rule in (welcome, home, interaction, seed):
                self.assertIn(rule, baseline)
            self.assertIn(welcome, startup)
            self.assertIn(home, startup)
            self.assertNotIn(interaction, startup)

    def test_missing_startup_output_cannot_change_saved_profiles(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'scripts').mkdir()
            dest = root / 'app/src/main/baselineProfiles'
            dest.mkdir(parents=True)
            script = root / 'scripts/import_profiles.py'
            shutil.copyfile(Path(__file__).with_name('import_profiles.py'), script)
            outputs = root / 'outputs'
            outputs.mkdir()
            (dest / 'baseline-prof.txt').write_text('existing\n')
            (outputs / 'interactions-baseline-prof.txt').write_text('Lcom/personal/wo/MainActivity;\n')
            result = subprocess.run([sys.executable, str(script), str(outputs)], capture_output=True)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual('existing\n', (dest / 'baseline-prof.txt').read_text())


if __name__ == '__main__':
    unittest.main()
