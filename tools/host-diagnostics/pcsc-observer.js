'use strict';

// Observational hooks only: never replace functions, arguments or return values.
// No command payloads, PIN, CAN, ATR, certificates or process arguments are read.
const readers = new Set([
  'DNIe Bridge Android NFC Private Reader 0',
  'Windows Hello for Business 1',
  'Virtual Smart Card Architecture Virtual PCD Contactless 0'
]);
function label(name) { return readers.has(name) ? name : 'other_reader_redacted'; }
function emit(event, fields) { send({ time: Date.now(), event, fields }); }
function status(result) { return '0x' + (result.toUInt32()).toString(16).padStart(8, '0'); }
function multi(pointer, count, wide) {
  if (count < 1 || count > 65536) throw new Error('Invalid list length');
  const result = []; let word = '';
  for (let i = 0; i < count; i++) {
    const c = wide ? pointer.add(i * 2).readU16() : pointer.add(i).readU8();
    if (c === 0) {
      if (!word) break;
      result.push({ index: result.length, reader: label(word) }); word = '';
    } else { word += String.fromCharCode(c); }
  }
  return result;
}
const installed = new Set();
function install(module) {
    if (module.name.toLowerCase() !== 'winscard.dll') return;
    for (const suffix of ['W', 'A']) {
      const wide = suffix === 'W';
      const listName = 'SCardListReaders' + suffix;
      const connectName = 'SCardConnect' + suffix;
      const stateName = 'SCardGetStatusChange' + suffix;
      for (const name of [listName, connectName, stateName]) {
        const target = module.findExportByName(name);
        if (!target || target.isNull() || installed.has(name)) continue;
        installed.add(name);
        if (name === listName) {
          Interceptor.attach(target, {
            onEnter(args) {
              this.output = args[2]; this.length = args[3];
              this.auto = !this.length.isNull() && this.length.readU32() === 0xffffffff;
            },
            onLeave(result) {
              const fields = { api: name, status: status(result), bufferSupplied: !this.output.isNull() };
              if (result.toUInt32() === 0 && !this.output.isNull()) {
                try {
                  const pointer = this.auto ? this.output.readPointer() : this.output;
                  fields.readers = multi(pointer, this.length.readU32(), wide);
                } catch (_) { fields.decodeFailed = true; }
              }
              emit('reader_list', fields);
            }
          });
        } else if (name === stateName) {
          Interceptor.attach(target, {
            onEnter(args) { this.states = args[2]; this.count = args[3].toUInt32(); },
            onLeave(result) {
              const fields = { api: name, status: status(result), states: [] };
              if (result.toUInt32() === 0 && this.count <= 128) {
                try {
                  const size = Process.pointerSize === 8 ? 64 : 56;
                  for (let i = 0; i < this.count; i++) {
                    const row = this.states.add(i * size);
                    const reader = row.readPointer();
                    const flags = row.add(Process.pointerSize * 2 + 4).readU32();
                    fields.states.push({ reader: label(wide ? reader.readUtf16String() : reader.readUtf8String()),
                      present: (flags & 0x20) !== 0, flags });
                  }
                } catch (_) { fields.decodeFailed = true; }
              }
              emit('reader_presence', fields);
            }
          });
        } else {
          Interceptor.attach(target, {
            onEnter(args) {
              this.handle = args[4];
              let reader = 'unreadable';
              try { reader = label(wide ? args[1].readUtf16String() : args[1].readUtf8String()); } catch (_) {}
              this.fields = { api: name, reader, shareMode: args[2].toUInt32(), requestedProtocols: args[3].toUInt32() };
              emit('connect_requested', this.fields);
            },
            onLeave(result) {
              emit('connect_returned', { ...this.fields, status: status(result) });
            }
          });
        }
        emit('hook_ready', { api: name });
      }
    }
}
const moduleObserver = Process.attachModuleObserver({
  onAdded(module) {
    install(module);
    if (module.name.toLowerCase() === 'j2pcsc.dll') {
      const imports = module.enumerateImports();
      install({ name: 'winscard.dll', findExportByName(name) {
        const item = imports.find(item => item.name === name);
        return item ? item.address : null;
      } });
    }
  }
});
const getModule = new NativeFunction(Module.getGlobalExportByName('GetModuleHandleW'), 'pointer', ['pointer']);
const getExport = new NativeFunction(Module.getGlobalExportByName('GetProcAddress'), 'pointer', ['pointer', 'pointer']);
const moduleName = Memory.allocUtf16String('WinSCard.dll');
function discover() {
  if (installed.size === 6) return;
  const handle = getModule(moduleName);
  if (!handle.isNull()) install({ name: 'winscard.dll', findExportByName(name) { return getExport(handle, Memory.allocUtf8String(name)); } });
}
for (const name of ['LoadLibraryW', 'LoadLibraryA', 'LoadLibraryExW', 'LoadLibraryExA']) {
  const target = Module.findGlobalExportByName(name);
  if (target) Interceptor.attach(target, { onLeave() { discover(); Interceptor.flush(); } });
}
discover();
Interceptor.flush();
const discoveryTimer = setInterval(discover, 10);
emit('observer_loaded', { pid: Process.id, architecture: Process.arch });
