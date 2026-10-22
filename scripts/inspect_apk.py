#!/usr/bin/env python3
"""Static APK checks; these are not a device compatibility or power test."""
import hashlib,json,struct,subprocess,sys,zipfile
from pathlib import Path

apk=Path(sys.argv[1]).resolve();sdk=Path(sys.argv[2]).resolve();tools=sdk/'build-tools/36.0.0'
permissions=subprocess.check_output([str(tools/'aapt'),'dump','permissions',str(apk)],text=True)
badging=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(apk)],text=True)
signature=subprocess.check_output([str(tools/'apksigner'),'verify','--verbose','--print-certs',str(apk)],text=True)
subprocess.run([str(tools/'zipalign'),'-c','-P','16','4',str(apk)],check=True)
native=[]
with zipfile.ZipFile(apk) as z:
 for info in z.infolist():
  if not info.filename.endswith('.so'):continue
  b=z.read(info);assert b[:4]==b'\x7fELF' and b[4]==2 and b[5]==1
  phoff=struct.unpack_from('<Q',b,32)[0];entry_size,count=struct.unpack_from('<HH',b,54)
  alignments=[]
  for i in range(count):
   off=phoff+i*entry_size
   if struct.unpack_from('<I',b,off)[0]==1:alignments.append(struct.unpack_from('<Q',b,off+48)[0])
  assert alignments and min(alignments)>=16384,(info.filename,alignments)
  native.append({'path':info.filename,'pt_load_align':alignments})
assert "sdkVersion:'29'" in badging and "targetSdkVersion:'36'" in badging
assert 'android.permission.RECORD_AUDIO' not in permissions and 'android.permission.CAMERA' not in permissions
assert 'android.permission.INTERNET' in permissions
print(json.dumps({'apk':apk.name,'sizeBytes':apk.stat().st_size,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'hasInternet':'android.permission.INTERNET' in permissions,'signature':signature.splitlines(),'native':native,'staticChecks':'PASS','runtimeDeviceTests':'NOT_RUN'},ensure_ascii=False,indent=2))
