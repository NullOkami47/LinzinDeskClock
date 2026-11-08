"""Dedicated-emulator test; physical devices are forbidden."""
import argparse,json,pathlib,subprocess,time,xml.etree.ElementTree as ET
p=argparse.ArgumentParser()
p.add_argument('--adb',required=True);p.add_argument('--serial',required=True)
p.add_argument('--port',type=int,default=5037);p.add_argument('--output',required=True)
a=p.parse_args()
assert a.serial.startswith('emulator-'),'Physical devices are forbidden'
root=pathlib.Path(a.output);root.mkdir(parents=True,exist_ok=True)
base=[a.adb,'-H','127.0.0.1','-P',str(a.port),'-s',a.serial]
pkg='com.linzin.linzindeskclock'
def adb(*args,input=None):
    r=subprocess.run(base+list(args),input=input,capture_output=True,text=True,timeout=30)
    if r.returncode:raise RuntimeError(r.stderr or r.stdout)
    return r.stdout.strip()
def prefs(name):
    for _ in range(12):
        try:
            data=ET.fromstring(adb('shell','run-as',pkg,'cat','shared_prefs/'+name+'.xml'))
            return {n.get('name'):n.get('value') if n.get('value') is not None else n.text for n in data}
        except ET.ParseError:time.sleep(.1)
    raise AssertionError('Unreadable fixture preferences')
def focused():
    data=adb('shell','dumpsys','window')
    return 'mAwake=true' in data and any('mCurrentFocus=' in l and '.autostart.ClockSessionActivity' in l for l in data.splitlines())
def wait(predicate,message):
    deadline=time.monotonic()+15
    while time.monotonic()<deadline:
        if predicate():return
        time.sleep(.25)
    raise AssertionError(message)
hardware=adb('shell','getprop','ro.hardware');fingerprint=adb('shell','getprop','ro.build.fingerprint')
assert hardware in ('ranchu','goldfish') and ('sdk_' in fingerprint or 'generic' in fingerprint),'Emulator identity required'
assert adb('shell','am','get-current-user')=='0'
record={'emulator':a.serial,'steps':[],'success':False}
try:
    adb('shell','input','keyevent','KEYCODE_WAKEUP');adb('shell','wm','dismiss-keyguard')
    adb('shell','locksettings','set-disabled','false');adb('shell','am','force-stop',pkg)
    fixture=ET.Element('map')
    for name in ('pref_dismiss_gesture_tutorial','pref_dismiss_layout_tip','auto_clock_enabled','auto_launch_consent','auto_notification_asked','auto_hide_media'):
        ET.SubElement(fixture,'boolean',name=name,value='true')
    ET.SubElement(fixture,'int',name='pref_non_fold_bypass_count',value='3')
    ET.SubElement(fixture,'string',name='auto_charging_mode').text='NOT_REQUIRED'
    ET.SubElement(fixture,'string',name='pref_layout_mode').text='SINGLE'
    adb('shell','run-as '+pkg+" sh -c 'mkdir -p shared_prefs; cat > shared_prefs/desk_clock_prefs.xml'",input=ET.tostring(fixture,encoding='unicode'))
    adb('shell','appops','set','--user','0',pkg,'SYSTEM_ALERT_WINDOW','allow')
    adb('shell','am','start','-W','-n',pkg+'/.MainActivity')
    wait(lambda:'isForeground=true' in adb('shell','dumpsys','activity','services',pkg),'Enabled monitor did not start')
    adb('emu','sensor','set','acceleration','6.934:0:6.934')
    adb('shell','input','keyevent','KEYCODE_HOME');time.sleep(1)
    before=adb('shell','dumpsys','activity','activities')
    assert not any('ResumedActivity' in l and pkg in l for l in before.splitlines()),'Clock must be background before lock'
    adb('shell','input','keyevent','KEYCODE_SLEEP')
    wait(lambda:prefs('desk_clock_prefs').get('auto_last_result')=='SHOWN' and focused(),'Background lock did not show clock')
    time.sleep(3);assert focused(),'Clock disappeared after transient resume'
    first=prefs('clock_cycle');assert first['suppressed']=='false'
    record['steps'].append('Background lock opens focused awake clock without notification tap')
    adb('shell','cmd','uimode','night','yes');time.sleep(3);assert focused(),'Session lost on configuration recreation'
    record['steps'].append('Session survives configuration recreation')
    adb('shell','input','keyevent','KEYCODE_BACK')
    wait(lambda:not focused() and prefs('clock_cycle').get('suppressed')=='true','Exit did not suppress')
    time.sleep(3);assert not focused(),'Exit immediately relaunched'
    record['steps'].append('Exit suppresses same lock cycle')
    adb('shell','wm','dismiss-keyguard')
    wait(lambda:prefs('clock_cycle').get('armed')=='true','Unlock did not re-arm without main activity')
    adb('shell','input','keyevent','KEYCODE_SLEEP')
    wait(lambda:prefs('desk_clock_prefs').get('auto_last_result')=='SHOWN' and focused(),'Next lock failed without reopening app')
    time.sleep(3);assert focused();assert prefs('clock_cycle')['lock_at']!=first['lock_at']
    record['steps'].append('Next lock opens clock without reopening main activity')
    record['success']=True
finally:
    (root/'result.json').write_text(json.dumps(record,ensure_ascii=False,indent=2),encoding='utf8')
    for name,command in [('activities',('shell','dumpsys','activity','activities')),('power',('shell','dumpsys','power'))]:
        try:(root/(name+'.txt')).write_text(adb(*command),encoding='utf8')
        except Exception:pass
    adb('shell','am','force-stop',pkg)
    adb('shell','input','keyevent','KEYCODE_WAKEUP');adb('shell','wm','dismiss-keyguard')
    adb('shell','cmd','uimode','night','no')
print(json.dumps(record,ensure_ascii=False,indent=2))
