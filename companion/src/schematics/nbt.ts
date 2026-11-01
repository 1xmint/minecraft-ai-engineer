import { SchematicError } from "./errors.js";

export type NbtType =
  | "end"
  | "byte"
  | "short"
  | "int"
  | "long"
  | "float"
  | "double"
  | "byteArray"
  | "string"
  | "list"
  | "compound"
  | "intArray"
  | "longArray";

export type NbtTag =
  | { readonly type: "byte"; readonly value: number }
  | { readonly type: "short"; readonly value: number }
  | { readonly type: "int"; readonly value: number }
  | { readonly type: "float"; readonly value: number }
  | { readonly type: "double"; readonly value: number }
  | { readonly type: "long"; readonly value: bigint }
  | { readonly type: "byteArray"; readonly value: Uint8Array }
  | { readonly type: "string"; readonly value: string }
  | {
      readonly type: "list";
      readonly elementType: NbtType;
      readonly value: readonly NbtTag[];
    }
  | { readonly type: "compound"; readonly value: ReadonlyMap<string, NbtTag> }
  | { readonly type: "intArray"; readonly value: Int32Array }
  | { readonly type: "longArray"; readonly value: BigInt64Array };

export interface NbtRoot {
  readonly name: string;
  readonly tag: Extract<NbtTag, { type: "compound" }>;
}

export interface NbtReadLimits {
  readonly maxDepth: number;
  readonly maxTags: number;
}

const TYPES: readonly NbtType[] = [
  "end",
  "byte",
  "short",
  "int",
  "long",
  "float",
  "double",
  "byteArray",
  "string",
  "list",
  "compound",
  "intArray",
  "longArray",
];
// Smallest encoded payload per type, used to reject lengths before allocating.
const MIN_PAYLOAD = [0, 1, 2, 4, 8, 4, 8, 4, 2, 5, 1, 4, 4] as const;

function malformed(message: string): never {
  throw new SchematicError("E_NBT_MALFORMED", message);
}

/** Bounded big-endian NBT reader. Rejects truncation, trailing bytes, excessive nesting and tag counts. */
export function readNbt(bytes: Uint8Array, limits: NbtReadLimits): NbtRoot {
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let pos = 0;
  let tags = 0;
  const need = (n: number, what: string): void => {
    if (n < 0 || pos + n > bytes.byteLength)
      malformed(`truncated ${what} at byte ${pos}`);
  };
  const countTag = (): void => {
    if (++tags > limits.maxTags)
      throw new SchematicError(
        "E_NBT_TOO_MANY_TAGS",
        `more than ${limits.maxTags} NBT tags`,
      );
  };
  const typeId = (): number => {
    need(1, "tag type");
    const id = view.getUint8(pos++);
    if (id >= TYPES.length) malformed(`unknown tag type ${id}`);
    return id;
  };
  const length = (what: string, elementSize: number): number => {
    need(4, `${what} length`);
    const n = view.getInt32(pos);
    pos += 4;
    if (n < 0) malformed(`negative ${what} length ${n}`);
    if (n * elementSize > bytes.byteLength - pos)
      malformed(`${what} length ${n} exceeds remaining input`);
    return n;
  };
  const string = (): string => {
    need(2, "string length");
    const n = view.getUint16(pos);
    pos += 2;
    need(n, "string");
    const s = decodeModifiedUtf8(bytes.subarray(pos, pos + n));
    pos += n;
    return s;
  };
  const payload = (id: number, depth: number): NbtTag => {
    countTag();
    switch (id) {
      case 1:
        need(1, "byte");
        return { type: "byte", value: view.getInt8(pos++) };
      case 2: {
        need(2, "short");
        const value = view.getInt16(pos);
        pos += 2;
        return { type: "short", value };
      }
      case 3: {
        need(4, "int");
        const value = view.getInt32(pos);
        pos += 4;
        return { type: "int", value };
      }
      case 4: {
        need(8, "long");
        const value = view.getBigInt64(pos);
        pos += 8;
        return { type: "long", value };
      }
      case 5: {
        need(4, "float");
        const value = view.getFloat32(pos);
        pos += 4;
        return { type: "float", value };
      }
      case 6: {
        need(8, "double");
        const value = view.getFloat64(pos);
        pos += 8;
        return { type: "double", value };
      }
      case 7: {
        const n = length("byte array", 1);
        const value = bytes.slice(pos, pos + n);
        pos += n;
        return { type: "byteArray", value };
      }
      case 8:
        return { type: "string", value: string() };
      case 9: {
        if (depth >= limits.maxDepth)
          throw new SchematicError(
            "E_NBT_TOO_DEEP",
            `NBT nesting deeper than ${limits.maxDepth}`,
          );
        const element = typeId();
        const n = length("list", Math.max(1, MIN_PAYLOAD[element] ?? 1));
        if (element === 0 && n > 0) malformed("non-empty list of end tags");
        const value: NbtTag[] = [];
        for (let i = 0; i < n; i++) value.push(payload(element, depth + 1));
        return { type: "list", elementType: TYPES[element] ?? "end", value };
      }
      case 10: {
        if (depth >= limits.maxDepth)
          throw new SchematicError(
            "E_NBT_TOO_DEEP",
            `NBT nesting deeper than ${limits.maxDepth}`,
          );
        const value = new Map<string, NbtTag>();
        for (;;) {
          const child = typeId();
          if (child === 0) break;
          const name = string();
          if (value.has(name)) malformed(`duplicate compound key "${name}"`);
          value.set(name, payload(child, depth + 1));
        }
        return { type: "compound", value };
      }
      case 11: {
        const n = length("int array", 4);
        const value = new Int32Array(n);
        for (let i = 0; i < n; i++) value[i] = view.getInt32(pos + i * 4);
        pos += n * 4;
        return { type: "intArray", value };
      }
      case 12: {
        const n = length("long array", 8);
        const value = new BigInt64Array(n);
        for (let i = 0; i < n; i++) value[i] = view.getBigInt64(pos + i * 8);
        pos += n * 8;
        return { type: "longArray", value };
      }
      default:
        return malformed(`unexpected tag type ${id}`);
    }
  };
  if (typeId() !== 10)
    throw new SchematicError("E_ROOT", "root tag is not a compound");
  const name = string();
  const tag = payload(10, 0) as NbtRoot["tag"];
  if (pos !== bytes.byteLength)
    throw new SchematicError(
      "E_NBT_TRAILING_DATA",
      `${bytes.byteLength - pos} unexpected bytes after the root tag`,
    );
  return { name, tag };
}

/** Java modified UTF-8, as used by NBT strings. */
export function decodeModifiedUtf8(bytes: Uint8Array): string {
  const units: number[] = [];
  for (let i = 0; i < bytes.length;) {
    const a = bytes[i]!;
    if (a < 0x80 && a !== 0) {
      units.push(a);
      i += 1;
    } else if ((a & 0xe0) === 0xc0) {
      const b = bytes[i + 1];
      if (b === undefined || (b & 0xc0) !== 0x80)
        malformed("invalid string byte");
      units.push(((a & 0x1f) << 6) | (b & 0x3f));
      i += 2;
    } else if ((a & 0xf0) === 0xe0) {
      const b = bytes[i + 1];
      const c = bytes[i + 2];
      if (
        b === undefined ||
        c === undefined ||
        (b & 0xc0) !== 0x80 ||
        (c & 0xc0) !== 0x80
      )
        malformed("invalid string byte");
      units.push(((a & 0x0f) << 12) | ((b & 0x3f) << 6) | (c & 0x3f));
      i += 3;
    } else malformed("invalid string byte");
  }
  let out = "";
  for (let i = 0; i < units.length; i += 4096)
    out += String.fromCharCode(...units.slice(i, i + 4096));
  return out;
}

export function encodeModifiedUtf8(value: string): Uint8Array {
  const out: number[] = [];
  for (let i = 0; i < value.length; i++) {
    const c = value.charCodeAt(i);
    if (c >= 1 && c <= 0x7f) out.push(c);
    else if (c <= 0x7ff) out.push(0xc0 | (c >> 6), 0x80 | (c & 0x3f));
    else
      out.push(0xe0 | (c >> 12), 0x80 | ((c >> 6) & 0x3f), 0x80 | (c & 0x3f));
  }
  if (out.length > 0xffff)
    throw new SchematicError(
      "E_TOO_LARGE",
      "NBT string longer than 65535 bytes",
    );
  return Uint8Array.from(out);
}

/** Big-endian NBT writer for an uncompressed named root compound. */
export function writeNbt(root: NbtRoot): Uint8Array {
  const chunks: Uint8Array[] = [];
  const u8 = (n: number): void => {
    chunks.push(Uint8Array.of(n & 0xff));
  };
  const fixed = (size: number, set: (v: DataView) => void): void => {
    const b = new Uint8Array(size);
    set(new DataView(b.buffer));
    chunks.push(b);
  };
  const string = (s: string): void => {
    const b = encodeModifiedUtf8(s);
    fixed(2, (v) => v.setUint16(0, b.length));
    chunks.push(b);
  };
  const payload = (tag: NbtTag): void => {
    switch (tag.type) {
      case "byte":
        return u8(tag.value);
      case "short":
        return fixed(2, (v) => v.setInt16(0, tag.value));
      case "int":
        return fixed(4, (v) => v.setInt32(0, tag.value));
      case "long":
        return fixed(8, (v) => v.setBigInt64(0, tag.value));
      case "float":
        return fixed(4, (v) => v.setFloat32(0, tag.value));
      case "double":
        return fixed(8, (v) => v.setFloat64(0, tag.value));
      case "byteArray":
        fixed(4, (v) => v.setInt32(0, tag.value.length));
        chunks.push(tag.value);
        return;
      case "string":
        return string(tag.value);
      case "list":
        u8(TYPES.indexOf(tag.elementType));
        fixed(4, (v) => v.setInt32(0, tag.value.length));
        for (const item of tag.value) {
          if (item.type !== tag.elementType)
            throw new SchematicError("E_FIELD", "mixed NBT list element types");
          payload(item);
        }
        return;
      case "compound":
        for (const [name, child] of tag.value) {
          u8(TYPES.indexOf(child.type));
          string(name);
          payload(child);
        }
        return u8(0);
      case "intArray":
        return fixed(4 + tag.value.length * 4, (v) => {
          v.setInt32(0, tag.value.length);
          tag.value.forEach((n, i) => v.setInt32(4 + i * 4, n));
        });
      case "longArray":
        return fixed(4 + tag.value.length * 8, (v) => {
          v.setInt32(0, tag.value.length);
          tag.value.forEach((n, i) => v.setBigInt64(4 + i * 8, n));
        });
    }
  };
  u8(10);
  string(root.name);
  payload(root.tag);
  const total = chunks.reduce((n, c) => n + c.length, 0);
  const out = new Uint8Array(total);
  let at = 0;
  for (const c of chunks) {
    out.set(c, at);
    at += c.length;
  }
  return out;
}
