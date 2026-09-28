#!/usr/bin/env python3
"""Guard material safety fixes found in the source audit; runtime behavior still needs a phone."""
from pathlib import Path

root = Path(__file__).resolve().parent.parent
src = root / 'src/com/example/tingxiejian'
read = lambda name: (src / name).read_text(encoding='utf-8')
main, service, chat = read('MainActivity.java'), read('LocalService.java'), read('ChatActivity.java')
transcript, history, cloud = read('TranscriptActivity.java'), read('History.java'), read('Cloud.java')
bridge, report = read('ShizukuIslandBridge.java'), read('Diagnostics.java')
checks = {
    'imports use distinct cache paths': 'File.createTempFile("input-"' in main and 'new File(getCacheDir(), "input")' not in main,
    'busy service cannot accept another run': 'if (path != null && busy) return' in service and 'LocalService.isBusy()' in main,
    'service saves completed history before publishing': service.index('History.save(this, savedResult(event))') < service.index('Bus.post(event);'),
    'history writes atomically with collision-resistant ids': 'UUID.randomUUID()' in history and 'atomic.finishWrite(out)' in history,
    'history read is size-bounded': 'length > MAX_RESULT_BYTES' in history,
    'both exports restore picker state': 'state.putInt(STATE_EXPORT_KIND, exportKind)' in main and 'state.putInt(STATE_EXPORT_FORMAT, pendingFormat)' in transcript and 'pendingExport' not in transcript,
    'chat clear invalidates pending replies': 'conversationGeneration++' in chat and 'generation != conversationGeneration' in chat,
    'cloud validates actual URL host and blocks redirects': 'parsed.getHost()' in cloud and 'setInstanceFollowRedirects(false)' in cloud,
    'global firewall chain not enabled in new arm path': 'if (!oldChain)' in bridge and 'setFirewallChainEnabled", OEM_DENY_3, true' not in bridge,
    'private diagnostics do not auto-publish': 'MediaStore' not in report and 'writeShared' not in report,
}
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
if not all(checks.values()):
    raise SystemExit(1)
