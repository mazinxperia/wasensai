import { normalizeZipPath } from "@/lib/format";

const EOCD_SIGNATURE = 0x06054b50;
const ZIP64_LOCATOR_SIGNATURE = 0x07064b50;
const ZIP64_EOCD_SIGNATURE = 0x06064b50;
const CENTRAL_DIRECTORY_SIGNATURE = 0x02014b50;
const LOCAL_FILE_HEADER_SIGNATURE = 0x04034b50;
const ZIP64_EXTRA_ID = 0x0001;
const UINT16_MAX = 0xffff;
const UINT32_MAX = 0xffffffff;

const textDecoder = new TextDecoder("utf-8");

export interface ZipEntryInfo {
  name: string;
  normalizedName: string;
  compressedSize: number;
  uncompressedSize: number;
  compressionMethod: number;
  flags: number;
  crc32: number;
  localHeaderOffset: number;
  dataOffset?: number;
}

export interface ZipDirectory {
  entries: ZipEntryInfo[];
  byName: Map<string, ZipEntryInfo>;
  centralDirectoryOffset: number;
  centralDirectorySize: number;
  isZip64: boolean;
}

interface EocdInfo {
  offset: number;
  totalEntries: number;
  centralDirectorySize: number;
  centralDirectoryOffset: number;
  isZip64: boolean;
}

function readUint64(view: DataView, offset: number) {
  const value = view.getBigUint64(offset, true);
  if (value > BigInt(Number.MAX_SAFE_INTEGER)) {
    throw new Error("Archive is too large for this browser runtime to address safely.");
  }
  return Number(value);
}

function findSignatureBackwards(view: DataView, signature: number) {
  for (let i = view.byteLength - 4; i >= 0; i -= 1) {
    if (view.getUint32(i, true) === signature) return i;
  }
  return -1;
}

async function readView(file: Blob, start: number, length: number) {
  const buffer = await file.slice(start, start + length).arrayBuffer();
  return new DataView(buffer);
}

async function locateEocd(file: File): Promise<EocdInfo> {
  const tailLength = Math.min(file.size, UINT16_MAX + 22 + 20);
  const tailStart = file.size - tailLength;
  const tail = await readView(file, tailStart, tailLength);
  const eocdRelativeOffset = findSignatureBackwards(tail, EOCD_SIGNATURE);

  if (eocdRelativeOffset < 0) {
    throw new Error("Invalid .waview file: ZIP end record was not found.");
  }

  const eocdOffset = tailStart + eocdRelativeOffset;
  const totalEntries16 = tail.getUint16(eocdRelativeOffset + 10, true);
  const cdSize32 = tail.getUint32(eocdRelativeOffset + 12, true);
  const cdOffset32 = tail.getUint32(eocdRelativeOffset + 16, true);
  const needsZip64 = totalEntries16 === UINT16_MAX || cdSize32 === UINT32_MAX || cdOffset32 === UINT32_MAX;

  if (!needsZip64) {
    return {
      offset: eocdOffset,
      totalEntries: totalEntries16,
      centralDirectorySize: cdSize32,
      centralDirectoryOffset: cdOffset32,
      isZip64: false,
    };
  }

  const locatorOffset = eocdOffset - 20;
  if (locatorOffset < 0) {
    throw new Error("Invalid ZIP64 .waview file: ZIP64 locator is missing.");
  }

  const locator = await readView(file, locatorOffset, 20);
  if (locator.getUint32(0, true) !== ZIP64_LOCATOR_SIGNATURE) {
    throw new Error("Invalid ZIP64 .waview file: ZIP64 locator signature is missing.");
  }

  const zip64EocdOffset = readUint64(locator, 8);
  const zip64 = await readView(file, zip64EocdOffset, 56);
  if (zip64.getUint32(0, true) !== ZIP64_EOCD_SIGNATURE) {
    throw new Error("Invalid ZIP64 .waview file: ZIP64 directory signature is missing.");
  }

  return {
    offset: eocdOffset,
    totalEntries: readUint64(zip64, 32),
    centralDirectorySize: readUint64(zip64, 40),
    centralDirectoryOffset: readUint64(zip64, 48),
    isZip64: true,
  };
}

function readZip64Extra(extra: Uint8Array, entry: ZipEntryInfo) {
  let offset = 0;
  const view = new DataView(extra.buffer, extra.byteOffset, extra.byteLength);
  const needsUncompressed = entry.uncompressedSize === UINT32_MAX;
  const needsCompressed = entry.compressedSize === UINT32_MAX;
  const needsOffset = entry.localHeaderOffset === UINT32_MAX;

  while (offset + 4 <= extra.byteLength) {
    const id = view.getUint16(offset, true);
    const size = view.getUint16(offset + 2, true);
    const dataStart = offset + 4;
    let cursor = dataStart;

    if (id === ZIP64_EXTRA_ID) {
      if (needsUncompressed && cursor + 8 <= dataStart + size) {
        entry.uncompressedSize = readUint64(view, cursor);
        cursor += 8;
      }
      if (needsCompressed && cursor + 8 <= dataStart + size) {
        entry.compressedSize = readUint64(view, cursor);
        cursor += 8;
      }
      if (needsOffset && cursor + 8 <= dataStart + size) {
        entry.localHeaderOffset = readUint64(view, cursor);
      }
      return;
    }

    offset += 4 + size;
  }
}

export async function readZipDirectory(file: File): Promise<ZipDirectory> {
  const eocd = await locateEocd(file);
  const directoryView = await readView(file, eocd.centralDirectoryOffset, eocd.centralDirectorySize);
  const bytes = new Uint8Array(directoryView.buffer);
  const entries: ZipEntryInfo[] = [];
  const byName = new Map<string, ZipEntryInfo>();
  let offset = 0;

  while (offset + 46 <= directoryView.byteLength && entries.length < eocd.totalEntries) {
    const signature = directoryView.getUint32(offset, true);
    if (signature !== CENTRAL_DIRECTORY_SIGNATURE) {
      throw new Error(`Invalid ZIP central directory near byte ${eocd.centralDirectoryOffset + offset}.`);
    }

    const flags = directoryView.getUint16(offset + 8, true);
    const compressionMethod = directoryView.getUint16(offset + 10, true);
    const crc32 = directoryView.getUint32(offset + 16, true);
    const compressedSize = directoryView.getUint32(offset + 20, true);
    const uncompressedSize = directoryView.getUint32(offset + 24, true);
    const fileNameLength = directoryView.getUint16(offset + 28, true);
    const extraLength = directoryView.getUint16(offset + 30, true);
    const commentLength = directoryView.getUint16(offset + 32, true);
    const localHeaderOffset = directoryView.getUint32(offset + 42, true);
    const nameStart = offset + 46;
    const extraStart = nameStart + fileNameLength;
    const commentStart = extraStart + extraLength;

    const name = textDecoder.decode(bytes.slice(nameStart, extraStart));
    const entry: ZipEntryInfo = {
      name,
      normalizedName: normalizeZipPath(name),
      compressedSize,
      uncompressedSize,
      compressionMethod,
      flags,
      crc32,
      localHeaderOffset,
    };

    if (
      compressedSize === UINT32_MAX ||
      uncompressedSize === UINT32_MAX ||
      localHeaderOffset === UINT32_MAX
    ) {
      readZip64Extra(bytes.slice(extraStart, commentStart), entry);
    }

    entries.push(entry);
    byName.set(entry.normalizedName, entry);
    offset = commentStart + commentLength;
  }

  return {
    entries,
    byName,
    centralDirectoryOffset: eocd.centralDirectoryOffset,
    centralDirectorySize: eocd.centralDirectorySize,
    isZip64: eocd.isZip64,
  };
}

export async function resolveDataOffset(file: File, entry: ZipEntryInfo) {
  if (entry.dataOffset !== undefined) return entry.dataOffset;

  const view = await readView(file, entry.localHeaderOffset, 30);
  if (view.getUint32(0, true) !== LOCAL_FILE_HEADER_SIGNATURE) {
    throw new Error(`Invalid local file header for ${entry.name}.`);
  }

  const fileNameLength = view.getUint16(26, true);
  const extraLength = view.getUint16(28, true);
  entry.dataOffset = entry.localHeaderOffset + 30 + fileNameLength + extraLength;
  return entry.dataOffset;
}

export async function readStoredEntryBlob(file: File, entry: ZipEntryInfo, mimeType = "application/octet-stream") {
  if (entry.compressionMethod !== 0) {
    throw new Error(`${entry.name} is compressed. Large media preview is only supported for STORE entries.`);
  }

  const dataOffset = await resolveDataOffset(file, entry);
  return file.slice(dataOffset, dataOffset + entry.compressedSize, mimeType);
}

export async function readStoredEntryText(file: File, entry: ZipEntryInfo) {
  const blob = await readStoredEntryBlob(file, entry, "text/plain;charset=utf-8");
  return await blob.text();
}

export async function readZipEntryBlob(file: File, entry: ZipEntryInfo, mimeType = "application/octet-stream") {
  if (entry.compressionMethod === 0) {
    return readStoredEntryBlob(file, entry, mimeType);
  }

  if (entry.compressionMethod !== 8) {
    throw new Error(`${entry.name} uses unsupported ZIP compression method ${entry.compressionMethod}.`);
  }

  if (!("DecompressionStream" in globalThis)) {
    throw new Error("This browser cannot decompress ZIP entries directly. Update Chrome/Edge and retry.");
  }

  const dataOffset = await resolveDataOffset(file, entry);
  const compressedSlice = file.slice(dataOffset, dataOffset + entry.compressedSize);
  const decompressed = compressedSlice.stream().pipeThrough(new DecompressionStream("deflate-raw"));
  const blob = await new Response(decompressed).blob();
  return blob.slice(0, blob.size, mimeType);
}

export async function readZipEntryText(file: File, entry: ZipEntryInfo) {
  const blob = await readZipEntryBlob(file, entry, "text/plain;charset=utf-8");
  return await blob.text();
}
