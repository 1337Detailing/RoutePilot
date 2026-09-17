from pathlib import Path
import shutil
import re
import subprocess

ROOT = Path('.')
OLD_ROOT = ROOT / 'RoutePilotStandalone'
NEW_ROOT = ROOT / 'RoutixStandalone'
WORKFLOW = ROOT / '.github/workflows/build-debug-apk.yml'

if OLD_ROOT.exists() and not NEW_ROOT.exists():
    OLD_ROOT.rename(NEW_ROOT)

old_pkg = NEW_ROOT / 'app/src/main/java/com/routepilot'
new_pkg = NEW_ROOT / 'app/src/main/java/com/routix'
if old_pkg.exists():
    new_pkg.parent.mkdir(parents=True, exist_ok=True)
    old_pkg.rename(new_pkg)

old_activity = new_pkg / 'app/RoutePilotProActivity.java'
new_activity = new_pkg / 'app/RoutixActivity.java'
if old_activity.exists():
    old_activity.rename(new_activity)

logo_src = ROOT / '.github/routix_logo.jpg'
logo_dir = NEW_ROOT / 'app/src/main/res/drawable-nodpi'
logo_dir.mkdir(parents=True, exist_ok=True)
logo_dst = logo_dir / 'routix_logo.jpg'
if logo_src.exists():
    shutil.copy2(logo_src, logo_dst)

replacements = [
    ('RoutePilotStandalone', 'RoutixStandalone'),
    ('RoutePilotProActivity', 'RoutixActivity'),
    ('com.routepilot.app', 'com.routix.app'),
    ('Theme.RoutePilot', 'Theme.Routix'),
    ('ic_routepilot', 'routix_logo'),
    ('ROUTEPILOT', 'ROUTIX'),
    ('RoutePilot', 'Routix'),
    ('routepilot', 'routix'),
]
text_suffixes = {'.java','.kt','.kts','.gradle','.xml','.md','.yml','.yaml','.properties','.txt','.json','.toml','.sh','.py','.gitignore','.gitattributes'}

for path in list(ROOT.rglob('*')):
    if not path.is_file() or '.git' in path.parts or path == logo_dst or path == WORKFLOW:
        continue
    if path.suffix.lower() not in text_suffixes and path.name not in {'.gitignore','.gitattributes'}:
        continue
    try:
        data = path.read_text(encoding='utf-8')
    except UnicodeDecodeError:
        continue
    new_data = data
    for old,new in replacements:
        new_data = new_data.replace(old,new)
    if new_data != data:
        path.write_text(new_data,encoding='utf-8')

for obsolete in [NEW_ROOT/'app/src/main/res/drawable/ic_routix.xml',NEW_ROOT/'app/src/main/res/drawable/ic_routepilot.xml']:
    if obsolete.exists():
        obsolete.unlink()

onboarding = new_pkg / 'app/OnboardingActivity.java'
if onboarding.exists():
    s = onboarding.read_text(encoding='utf-8')
    if 'import android.widget.ImageView;' not in s:
        s = s.replace('import android.widget.FrameLayout;\n','import android.widget.FrameLayout;\nimport android.widget.ImageView;\n')
    old = 'TextView mark=label("RP",15,Typeface.BOLD,Color.WHITE);mark.setGravity(Gravity.CENTER);mark.setBackground(liquid(BLUE,17));top.addView(mark,new LinearLayout.LayoutParams(dp(42),dp(42)));'
    new = 'ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.routix_logo);mark.setScaleType(ImageView.ScaleType.CENTER_CROP);mark.setBackground(liquid(Color.argb(80,10,132,255),17));top.addView(mark,new LinearLayout.LayoutParams(dp(42),dp(42)));'
    s = s.replace(old,new).replace('Routix 1.0.3','Routix 1.1.0').replace('Routix 1.0.4','Routix 1.1.0')
    onboarding.write_text(s,encoding='utf-8')

manifest = NEW_ROOT / 'app/src/main/AndroidManifest.xml'
if manifest.exists():
    s = manifest.read_text(encoding='utf-8')
    s = re.sub(r'android:icon="[^"]+"','android:icon="@drawable/routix_logo"',s)
    if 'android:roundIcon=' not in s:
        s = s.replace('android:icon="@drawable/routix_logo"','android:icon="@drawable/routix_logo"\n        android:roundIcon="@drawable/routix_logo"')
    manifest.write_text(s,encoding='utf-8')

app_gradle = NEW_ROOT / 'app/build.gradle'
if app_gradle.exists():
    s = app_gradle.read_text(encoding='utf-8')
    s = re.sub(r'versionCode\s+\d+','versionCode 16',s)
    s = re.sub(r"versionName\s+'[^']+'","versionName '1.1.0'",s)
    app_gradle.write_text(s,encoding='utf-8')

readme = ROOT / 'README.md'
if readme.exists():
    s = readme.read_text(encoding='utf-8')
    if not s.lstrip().startswith('# Routix'):
        s = '# Routix\n\n' + s
    readme.write_text(s,encoding='utf-8')

for temp in [ROOT/'.github/routix_logo.jpg',ROOT/'.github/routix_rebrand.py']:
    if temp.exists():
        temp.unlink()

leftovers = subprocess.run(['git','grep','-n','-i','routepilot','--',':!LICENSE',':!PULL_REQUEST.MIT.LICENSE',':!.github/workflows/build-debug-apk.yml'],text=True,capture_output=True)
if leftovers.returncode == 0 and leftovers.stdout.strip():
    print('Old branding remains:\n'+leftovers.stdout)
    raise SystemExit(3)

print('Routix rebrand prepared successfully.')
