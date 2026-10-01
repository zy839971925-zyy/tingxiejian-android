#!/usr/bin/env python3
"""Call-chain/version/permission integration guards; behavior lives in live-update-check.sh."""
from pathlib import Path
import re
import xml.etree.ElementTree as E
root=Path(__file__).resolve().parents[1]
src=root/'src/com/example/tingxiejian'
service=(src/'LocalService.java').read_text()
publisher=(src/'AndroidLiveUpdatePublisher.java').read_text()
capability=(src/'AndroidLiveUpdateCapability.java').read_text()
settings=(src/'SettingsActivity.java').read_text()
manifest=E.parse(root/'AndroidManifest.xml').getroot()
a='{http://schemas.android.com/apk/res/android}'
permissions={n.get(a+'name') for n in manifest.findall('uses-permission')}
assert 'android.permission.POST_PROMOTED_NOTIFICATIONS' in permissions
assert 'android.permission.SYSTEM_ALERT_WINDOW' not in permissions
assert not manifest.findall('.//service/intent-filter/action[@'+a+'name="android.service.notification.NotificationListenerService"]')
assert not manifest.findall('.//service/intent-filter/action[@'+a+'name="android.accessibilityservice.AccessibilityService"]')
assert 'Build.VERSION.SDK_INT<36' in capability and 'Build.VERSION.SDK_INT>=36' in publisher
assert 'private static final class Api36' in capability and 'private static final class Api36' in publisher
assert 'Shizuku' not in re.sub(r'/\*.*?\*/','',capability,flags=re.S)
assert 'setRequestPromotedOngoing(' not in re.sub(r'//[^\n]*','',publisher)
assert 'android.requestPromotedOngoing' in publisher and '.setStyle(new Notification.ProgressStyle()' in publisher
assert 'new NotificationUpdateGate(1500)' in publisher and 'now - lastNotifyAt < 5000' in service
assert service.index('ensureForeground(requestIsland)') < service.index('startRun(new File(path)')
start=service[service.index('ensureForeground(requestIsland)'):service.index('startRun(new File(path)')]
assert 'if(requestIsland&&!foregroundRefused)' in start and 'XiaomiIslandPublisher.cancel(this,manager,NOTIFICATION_ID)' in start
dispatch=service[service.index('private void publishProgress'):service.index('private Notification buildNotification(Job job)')]
assert dispatch.index('updateNotice(job)') < dispatch.index('liveUpdates.publish(')
assert 'if(islandWanted)return;' in dispatch and 'manager.notify(NOTIFICATION_ID,notification)' in dispatch
assert 'islandWanted=false;Log.w' in dispatch
assert 'stopForeground' not in dispatch and 'XiaomiIslandPublisher.cancel' not in dispatch
assert service.count('liveUpdates.reset()')==2
assert 'new CloudFileTranscriber(this, sessionId, this::checkCancellation, this::handle)' in service
assert 'getBoolean(AndroidLiveUpdateCapability.PREF,true)' in settings
assert 'prefs().edit().putBoolean("island", checked)' in settings
assert '--min-sdk-version 26 --target-sdk-version 35' in (root/'build.sh').read_text()
assert 'platforms/android-36/android.jar' in (root/'scripts/prepare-libraries.sh').read_text()
print('PASS: Live Update integration guards (API/version, lifecycle base, routing, independent settings/throttles)')
