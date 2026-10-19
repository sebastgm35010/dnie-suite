'use strict';

// Process-local reader filter. Attach only to the RENIEC Java child.
// Preserve API failures and card responses; never read APDU or process arguments.
const bridge = 'DNIe Bridge Android NFC Private Reader 0';
const bridgePrefix = 'DNIe Bridge Android NFC Private Reader ';
const hooks = new Set();
function install(module) {
  if (module.name.toLowerCase() !== 'winscard.dll') return;
  for (const suffix of ['A', 'W']) {
    const name = 'SCardListReaders' + suffix;
    const target = module.findExportByName(name);
    if (!target || target.isNull() || hooks.has(name)) continue;
    hooks.add(name);
    const wide = suffix === 'W';
    Interceptor.attach(target, {
      onEnter(args) {
        this.output = args[2]; this.length = args[3];
        this.auto = !this.length.isNull() && this.length.readU32() === 0xffffffff;
      },
      onLeave(result) {
        if (result.toUInt32() !== 0 || this.output.isNull() || this.length.isNull()) return;
        const count = this.length.readU32();
        if (count < 2 || count > 65536) return;
        const pointer = this.auto ? this.output.readPointer() : this.output;
        if (pointer.isNull()) return;
        let word = ''; let matchedReader = null;
        for (let i = 0; i < count; i++) {
          const c = wide ? pointer.add(i * 2).readU16() : pointer.add(i).readU8();
          if (c === 0) {
            if (!word) break;
            if (word === bridge || word.startsWith(bridgePrefix)) matchedReader = word;
            word = '';
          } else word += String.fromCharCode(c);
        }
        // Shorten in the existing caller/PCSC allocation, keeping ownership intact.
        const found = matchedReader !== null;
        const value = found ? matchedReader + '\0\0' : '\0\0';
        if (value.length > count) throw new Error('Reader filter capacity mismatch');
        for (let i = 0; i < value.length; i++) {
          if (wide) pointer.add(i * 2).writeU16(value.charCodeAt(i));
          else pointer.add(i).writeU8(value.charCodeAt(i));
        }
        this.length.writeU32(value.length);
        send({time: Date.now(), event: 'bridge_reader_list_filtered', fields: {api: name, bridgeVisible: found}});
      }
    });
  }
}
const moduleObserver = Process.attachModuleObserver({onAdded(module) {
  install(module);
  if (module.name.toLowerCase() === 'j2pcsc.dll') {
    const imports = module.enumerateImports();
    install({name: 'winscard.dll', findExportByName(name) {
      const item = imports.find(item => item.name === name);
      return item ? item.address : null;
    }});
  }
}});
const getModule = new NativeFunction(Module.getGlobalExportByName('GetModuleHandleW'), 'pointer', ['pointer']);
const getExport = new NativeFunction(Module.getGlobalExportByName('GetProcAddress'), 'pointer', ['pointer', 'pointer']);
const library = Memory.allocUtf16String('winscard.dll');
function discover() {
  if (hooks.size === 2) return;
  const handle = getModule(library);
  if (!handle.isNull()) install({name: 'winscard.dll', findExportByName(name) {
    return getExport(handle, Memory.allocUtf8String(name));
  }});
}
for (const name of ['LoadLibraryW', 'LoadLibraryA', 'LoadLibraryExW', 'LoadLibraryExA']) {
  const target = Module.findGlobalExportByName(name);
  if (target) Interceptor.attach(target, {onLeave() {discover(); Interceptor.flush();}});
}
discover(); Interceptor.flush();
const timer = setInterval(discover, 10);
send({time: Date.now(), event: 'bridge_selector_loaded', fields: {pid: Process.id}});
