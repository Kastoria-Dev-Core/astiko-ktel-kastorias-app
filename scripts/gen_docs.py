"""Make Doxygen-style HTML documentation for the app's Kotlin code.

Doxygen doesn't read Kotlin, so this does its job for this project: it parses
every .kt file under app/src, and writes the same kind of site Doxygen would.

* a page per class, interface, object and enum, with its member tables, the
  full documentation of every member, and where each one is used
* a page per file, with its imports, a graph of the files it depends on and
  the files that depend on it, and its top-level functions and properties
* a page per package
* the source code of every file, coloured, with line numbers, and every name
  linked to its documentation
* class list, class index, class hierarchy, class members, file list, file
  members, topics (composables, view models, models, tests), the to-do list,
  the data files, and a search box

Documentation comes from KDoc (/** ... */). Where a declaration has none, the
// comment lines right above it are used instead, since that's how much of
this code is explained.

Run it again whenever the code changes:

    python scripts/gen_docs.py

The site is written to docs/html; open docs/html/index.html.
"""

import argparse
import html
import json
import re
import shutil
import subprocess
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOURCE_DIRS = [ROOT / "app" / "src" / "main" / "java", ROOT / "app" / "src" / "test" / "java"]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
OUT = ROOT / "docs" / "html"
REPO_URL = "https://github.com/Kastoria-Dev-Core/astiko-ktel-kastorias-app"

HARD_KEYWORDS = {
    "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in", "interface", "is",
    "null", "object", "package", "return", "super", "this", "throw", "true", "try", "typealias", "typeof",
    "val", "var", "when", "while",
}
SOFT_KEYWORDS = {
    "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally", "get", "import", "init",
    "param", "property", "receiver", "set", "setparam", "value", "where",
}
MODIFIERS = {
    "abstract", "actual", "annotation", "companion", "const", "crossinline", "data", "enum", "expect",
    "external", "final", "infix", "inline", "inner", "internal", "lateinit", "noinline", "open", "operator",
    "out", "override", "private", "protected", "public", "reified", "sealed", "suspend", "tailrec", "value",
    "vararg",
}
DECL_KEYWORDS = {"class", "interface", "object", "fun", "val", "var", "typealias", "constructor"}
CLASSLIKE = {"class", "interface", "object"}
OPENERS = {"(": ")", "[": "]", "{": "}"}
# A line ending in one of these goes on to the next line
CONT_END = {
    "=", "+", "-", "*", "/", "%", "&&", "||", "?:", ".", "?.", "::", "->", ",", "(", "[", "{", "<", ">",
    "as", "as?", "in", "!in", "is", "!is", "==", "!=", "===", "!==", "<=", ">=", "..", "..<", "!", "+=",
    "-=", "*=", "/=", "by", "else", ":",
}
# And a line starting with one of these carries on the one before
CONT_START = {
    ".", "?.", "?:", "&&", "||", "+", "*", "/", "%", "=", "==", "!=", "<", ">", "<=", ">=", "->", "as",
    "as?", "is", "!is", "in", "!in", "..", "..<", ")", "]", "else", ":", "by", "catch", "finally",
}
OPERATORS = sorted([
    "===", "!==", "..<", "?.", "?:", "::", "->", "..", "==", "!=", "&&", "||", "<=", ">=", "+=", "-=", "*=",
    "/=", "%=", "++", "--", "!!", "as?",
], key=len, reverse=True)


# ---------------------------------------------------------------- tokenizer

class Token:
    __slots__ = ("kind", "text", "start", "end", "line", "nl_before", "ws_before")

    def __init__(self, kind, text, start, end, line):
        self.kind = kind  # id, num, str, chr, op
        self.text = text
        self.start = start
        self.end = end
        self.line = line
        self.nl_before = False
        self.ws_before = False

    def __repr__(self):
        return f"{self.kind}:{self.text}@{self.line}"


class Comment:
    __slots__ = ("text", "start", "end", "line", "end_line", "kdoc", "block")

    def __init__(self, text, start, end, line, end_line):
        self.text = text
        self.start = start
        self.end = end
        self.line = line
        self.end_line = end_line
        self.kdoc = text.startswith("/**") and not text.startswith("/**/")
        self.block = text.startswith("/*")


def _scan_string(src, i):
    """End of the string literal starting at i (just past its closing quote), templates included"""
    raw = src.startswith('"""', i)
    i += 3 if raw else 1
    n = len(src)
    while i < n:
        c = src[i]
        if raw and src.startswith('"""', i):
            i += 3
            while i < n and src[i] == '"':  # """a"""" ends with the last three quotes
                i += 1
            return i
        if not raw and c == '"':
            return i + 1
        if not raw and c == "\\":
            i += 2
            continue
        if not raw and c == "\n":
            return i  # unterminated, stop at the end of the line
        if c == "$" and i + 1 < n and src[i + 1] == "{":
            i = _scan_template(src, i + 2)
            continue
        i += 1
    return n


def _scan_template(src, i):
    depth = 1
    n = len(src)
    while i < n and depth:
        c = src[i]
        if c == '"':
            i = _scan_string(src, i)
            continue
        if c == "{":
            depth += 1
        elif c == "}":
            depth -= 1
        i += 1
    return i


def tokenize(src):
    tokens, comments = [], []
    i, n, line = 0, len(src), 1
    nl_pending = False
    ws_pending = False
    while i < n:
        c = src[i]
        if c == "\n":
            line += 1
            nl_pending = ws_pending = True
            i += 1
            continue
        if c in " \t\r\f﻿":
            ws_pending = True
            i += 1
            continue
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j < 0 else j
            comments.append(Comment(src[i:j], i, j, line, line))
            ws_pending = True
            i = j
            continue
        if src.startswith("/*", i):
            depth, j = 1, i + 2
            while j < n and depth:
                if src.startswith("/*", j):
                    depth += 1
                    j += 2
                elif src.startswith("*/", j):
                    depth -= 1
                    j += 2
                else:
                    j += 1
            text = src[i:j]
            comments.append(Comment(text, i, j, line, line + text.count("\n")))
            if "\n" in text:
                nl_pending = True
            line += text.count("\n")
            ws_pending = True
            i = j
            continue
        start, start_line = i, line
        if c == '"':
            j = _scan_string(src, i)
            kind = "str"
        elif c == "'":
            j = i + 1
            while j < n and src[j] != "'" and src[j] != "\n":
                j += 2 if src[j] == "\\" else 1
            j = min(j + 1, n)
            kind = "chr"
        elif c == "`":
            j = src.find("`", i + 1)
            j = n if j < 0 else j + 1
            kind = "id"
        elif c.isdigit() or (c == "." and i + 1 < n and src[i + 1].isdigit() and not src.startswith("..", i)):
            m = re.compile(r"0[xX][0-9a-fA-F_]+[uUL]*|0[bB][01_]+[uUL]*|(\d[\d_]*)?(\.\d[\d_]*)?([eE][+-]?\d+)?[fFLuU]*").match(src, i)
            j = m.end() if m and m.end() > i else i + 1
            # 1..5 is a range, not the number 1.
            if "." in src[i:j] and src.startswith("..", src.index(".", i)):
                j = src.index(".", i)
            kind = "num"
        elif c.isalpha() or c == "_":
            j = i + 1
            while j < n and (src[j].isalnum() or src[j] == "_"):
                j += 1
            kind = "id"
        else:
            kind = "op"
            j = i + 1
            for op in OPERATORS:
                if src.startswith(op, i):
                    if op == "as?":
                        continue
                    j = i + len(op)
                    break
        text = src[start:j]
        tok = Token(kind, text, start, j, start_line)
        tok.nl_before = nl_pending
        tok.ws_before = ws_pending
        nl_pending = ws_pending = False
        line += text.count("\n")
        tokens.append(tok)
        i = j
    return tokens, comments


# ---------------------------------------------------------------- model

class Param:
    def __init__(self):
        self.name = ""
        self.type = ""
        self.default = None
        self.doc = None
        self.prop = None  # "val" / "var" for constructor properties
        self.modifiers = []
        self.annotations = []
        self.line = 0
        self.name_tok = None


class Symbol:
    def __init__(self, kind, name, src_file):
        self.kind = kind  # class, interface, object, fun, val, var, typealias, entry, constructor
        self.name = name
        self.file = src_file
        self.modifiers = []
        self.annotations = []
        self.parent = None
        self.children = []
        self.params = []
        self.type_params = ""
        self.receiver = ""
        self.type = ""  # return or property type
        self.initializer = ""
        self.supertypes = []
        self.signature = ""
        self.doc = None
        self.doc_from_comment = False
        self.line = 0
        self.end_line = 0
        self.start_tok = 0
        self.end_tok = 0
        self.name_tok = None
        self.page = ""
        self.anchor = ""
        self.refs = set()
        self.referenced_by = set()
        self.ctor_prop = None  # the Param a constructor property comes from

    # -- what it is

    @property
    def is_classlike(self):
        return self.kind in CLASSLIKE

    @property
    def visibility(self):
        for v in ("private", "protected", "internal"):
            if v in self.modifiers:
                return v
        return "public"

    @property
    def is_companion(self):
        return self.kind == "object" and "companion" in self.modifiers

    @property
    def is_composable(self):
        return any(a == "@Composable" for a in self.annotations)

    @property
    def is_test(self):
        return any(a.split("(")[0] in ("@Test", "@org.junit.Test") for a in self.annotations)

    @property
    def keyword(self):
        """How the declaration is introduced, e.g. "data class", "sealed interface", "companion object" """
        if self.kind == "entry":
            return "enum value"
        if self.kind in CLASSLIKE:
            mods = [m for m in self.modifiers if m in ("data", "enum", "sealed", "abstract", "open", "annotation", "value", "inner", "companion", "fun")]
            return " ".join(mods + [self.kind])
        if self.kind in ("val", "var"):
            mods = [m for m in self.modifiers if m in ("const", "lateinit")]
            return " ".join(mods + [self.kind])
        return self.kind

    @property
    def qualified(self):
        parts = []
        s = self
        while s:
            parts.append(s.name)
            s = s.parent
        return ".".join(([self.file.package] if self.file.package else []) + parts[::-1])

    @property
    def display_scope(self):
        """Name of the class it's in, Outer.Inner, or the file for top-level ones"""
        chain = []
        s = self.parent
        while s:
            chain.append(s.name)
            s = s.parent
        return ".".join(chain[::-1]) if chain else self.file.name

    @property
    def url(self):
        return f"{self.page}#{self.anchor}" if self.anchor else self.page

    def owner_class(self):
        s = self.parent
        while s and s.is_companion:
            s = s.parent
        return s


class SourceFile:
    def __init__(self, path):
        self.path = path
        self.rel = path.relative_to(ROOT).as_posix()
        self.name = path.name
        self.src = path.read_text(encoding="utf-8")
        self.tokens, self.comments = tokenize(self.src)
        self.package = ""
        self.imports = []  # (path, alias, line)
        self.file_annotations = []
        self.symbols = []  # top level
        self.all_symbols = []
        self.comments_before = defaultdict(list)  # token index -> comments right before it
        self.link_at = {}  # token index -> Symbol, for the source browser
        self.decl_at = {}  # token index -> Symbol whose name it is
        self.is_test = "/src/test/" in self.rel
        self.page = "file_" + safe(self.rel) + ".html"
        self.src_page = "src_" + safe(self.rel) + ".html"
        self.line_count = self.src.count("\n") + (0 if self.src.endswith("\n") else 1)
        self.deps = set()
        self.used_by = set()
        # Bracket pairs, to jump over arguments and bodies
        self.match = {}
        stack = []
        for idx, t in enumerate(self.tokens):
            if t.kind == "op" and t.text in OPENERS:
                stack.append(idx)
            elif t.kind == "op" and t.text in (")", "]", "}"):
                if stack:
                    o = stack.pop()
                    self.match[o] = idx
                    self.match[idx] = o
        ci = 0
        for idx, t in enumerate(self.tokens):
            while ci < len(self.comments) and self.comments[ci].start < t.start:
                self.comments_before[idx].append(self.comments[ci])
                ci += 1


def safe(s):
    return re.sub(r"[^A-Za-z0-9]+", "_", s).strip("_")


# ---------------------------------------------------------------- parser

class Parser:
    def __init__(self, f):
        self.f = f
        self.t = f.tokens
        self.n = len(f.tokens)

    def tx(self, i):
        return self.t[i].text if 0 <= i < self.n else ""

    def is_op(self, i, text):
        return 0 <= i < self.n and self.t[i].kind == "op" and self.t[i].text == text

    def is_id(self, i, text=None):
        return 0 <= i < self.n and self.t[i].kind == "id" and (text is None or self.t[i].text == text)

    def jump(self, i):
        """Past the bracket pair opening at i"""
        return self.f.match.get(i, i)

    def join(self, a, b):
        """Source text of tokens a..b (inclusive) on one line, comments dropped"""
        out = []
        for k in range(a, min(b, self.n - 1) + 1):
            tok = self.t[k]
            if k > a and tok.ws_before:
                out.append(" ")
            out.append(tok.text)
        return "".join(out)

    # -- entry

    def parse(self):
        f = self.f
        i = 0
        while i < self.n:
            if self.is_op(i, "@") and self.is_id(i + 1, "file") and self.is_op(i + 2, ":"):
                end = self.annotation_end(i)
                f.file_annotations.append(self.join(i, end))
                i = end + 1
                continue
            if self.is_id(i, "package"):
                j = i + 1
                while j + 1 < self.n and self.is_op(j + 1, ".") and not self.t[j + 1].nl_before:
                    j += 2
                f.package = self.join(i + 1, j).replace(" ", "")
                i = j + 1
                continue
            if self.is_id(i, "import"):
                j = i + 1
                while j + 1 < self.n and self.tx(j + 1) in (".",) and not self.t[j + 1].nl_before:
                    j += 2
                path = self.join(i + 1, j).replace(" ", "")
                alias = None
                if self.is_id(j + 1, "as") and not self.t[j + 1].nl_before:
                    alias = self.tx(j + 2)
                    j += 2
                f.imports.append((path, alias, self.t[i].line))
                i = j + 1
                continue
            break
        f.symbols = self.container(i, self.n, None)

    def container(self, i, end, parent):
        out = []
        if parent is not None and "enum" in parent.modifiers:
            i = self.enum_entries(i, end, parent, out)
        while i < end:
            t = self.t[i]
            if t.kind == "op" and t.text in OPENERS:
                i = self.jump(i) + 1
                continue
            sym = self.declaration(i, end, parent)
            if sym is None:
                i += 1
                continue
            out.append(sym)
            i = sym.end_tok + 1
        return out

    def annotation_end(self, i):
        j = i + 1
        if self.is_id(j) and self.is_op(j + 1, ":") and self.tx(j) in ("file", "field", "get", "set", "param", "property", "receiver", "setparam", "delegate"):
            j += 2
        if self.is_op(j, "["):
            return self.jump(j)
        while self.is_op(j + 1, ".") and self.is_id(j + 2):
            j += 2
        if self.is_op(j + 1, "<"):
            j = self.angle_end(j + 1)
        if self.is_op(j + 1, "(") and not self.t[j + 1].ws_before:
            j = self.jump(j + 1)
        return j

    def angle_end(self, i):
        depth = 0
        j = i
        while j < self.n:
            tx = self.tx(j)
            if self.t[j].kind == "op" and tx in OPENERS:
                j = self.jump(j) + 1
                continue
            if tx == "<":
                depth += 1
            elif tx == ">":
                depth -= 1
                if depth == 0:
                    return j
            elif tx == ">=" or tx == "->" and depth == 0:
                return j
            j += 1
        return self.n - 1

    def modifiers_and_annotations(self, i, end):
        mods, anns = [], []
        j = i
        while j < end:
            if self.is_op(j, "@"):
                e = self.annotation_end(j)
                anns.append(self.join(j, e))
                j = e + 1
            elif self.is_id(j) and self.tx(j) in MODIFIERS and self.is_id(j + 1):
                mods.append(self.tx(j))
                j += 1
            else:
                break
        return mods, anns, j

    def statement_start(self, i, parent):
        if i == 0:
            return True
        tok = self.t[i]
        prev = self.t[i - 1]
        return tok.nl_before or prev.text in ("{", "}", ";") or (prev.kind == "op" and prev.text == ")" and tok.nl_before)

    def declaration(self, i, end, parent):
        if not self.statement_start(i, parent):
            return None
        mods, anns, j = self.modifiers_and_annotations(i, end)
        if not self.is_id(j) or self.tx(j) not in DECL_KEYWORDS:
            return None
        kw = self.tx(j)
        if kw == "fun" and self.is_id(j + 1, "interface"):
            mods = mods + ["fun"]
            kw = "interface"
            j += 1
        if kw == "fun" and self.is_op(j + 1, "("):
            return None  # anonymous function
        if kw == "object" and not self.is_id(j + 1) and "companion" not in mods:
            return None  # object expression
        if kw == "constructor" and not self.is_op(j + 1, "("):
            return None
        if kw in CLASSLIKE:
            sym = self.classlike(i, j, kw, mods, anns, end, parent)
        elif kw in ("fun", "constructor"):
            sym = self.function(i, j, kw, mods, anns, end, parent)
        elif kw in ("val", "var"):
            sym = self.property(i, j, kw, mods, anns, end, parent)
        else:
            sym = self.typealias(i, j, mods, anns, end, parent)
        if sym is None:
            return None
        self.attach_doc(sym, i)
        self.f.all_symbols.append(sym)
        return sym

    def new_symbol(self, kind, name, i, mods, anns, parent):
        sym = Symbol(kind, name, self.f)
        sym.modifiers = mods
        sym.annotations = anns
        sym.parent = parent
        sym.start_tok = i
        sym.line = self.t[i].line
        return sym

    def attach_doc(self, sym, i):
        comments = self.f.comments_before.get(i, [])
        if not comments:
            return
        kdocs = [c for c in comments if c.kdoc]
        if kdocs:
            sym.doc = kdoc_text(kdocs[-1].text)
            return
        # Plain // comments right above, with no blank line in between
        run = []
        expect = self.t[i].line - 1
        for c in reversed(comments):
            if c.block or c.end_line != expect:
                break
            if c.line != c.end_line or self.f.src[:c.start].rsplit("\n", 1)[-1].strip():
                break  # a trailing comment at the end of a line of code
            run.append(c.text[2:].strip())
            expect = c.line - 1
        run = [r for r in reversed(run) if not re.fullmatch(r"[-=*/ ]*", r)]
        if run:
            sym.doc = "\n".join(run)
            sym.doc_from_comment = True

    # -- declarations

    def classlike(self, i, j, kw, mods, anns, end, parent):
        k = j + 1
        if self.is_id(k) and not (kw == "object" and self.tx(k) in ("{",)):
            name = self.tx(k)
            name_tok = k
            k += 1
        else:
            name, name_tok = "Companion", None
        sym = self.new_symbol(kw, name.strip("`"), i, mods, anns, parent)
        sym.name_tok = name_tok
        if self.is_op(k, "<"):
            e = self.angle_end(k)
            sym.type_params = self.join(k, e)
            k = e + 1
        # class Foo @Inject constructor(...)
        m2, a2, k2 = self.modifiers_and_annotations(k, end)
        if self.is_id(k2, "constructor") and not self.t[k2].nl_before:
            k = k2 + 1
        if self.is_op(k, "(") and not self.t[k].nl_before:
            close = self.jump(k)
            sym.params = self.params(k, close)
            k = close + 1
        if self.is_op(k, ":"):
            k += 1
            while k < end:
                a = k
                while self.is_id(k) and self.is_op(k + 1, "."):
                    k += 2
                if not self.is_id(k):
                    break
                if self.is_op(k + 1, "<"):
                    k = self.angle_end(k + 1)
                base = self.join(a, k)
                call = ""
                if self.is_op(k + 1, "(") and not self.t[k + 1].nl_before:
                    call = self.join(k + 1, self.jump(k + 1))
                    k = self.jump(k + 1)
                delegate = ""
                if self.is_id(k + 1, "by"):
                    e = self.expr_end(k + 2, end, stop={",", "{"})
                    delegate = self.join(k + 2, e)
                    k = e
                sym.supertypes.append((base, call, delegate))
                if self.is_op(k + 1, ","):
                    k += 2
                    continue
                k += 1
                break
        if self.is_id(k, "where"):
            while k < end and not self.is_op(k, "{") and not (self.t[k].nl_before and k > j + 1 and not self.is_op(k, ",")):
                k += 1
        header_end = k - 1
        sym.signature = self.join(i if not anns else self.skip_annotations(i), header_end)
        if self.is_op(k, "{"):
            close = self.jump(k)
            sym.children = self.container(k + 1, close, sym)
            sym.end_tok = close
        else:
            sym.end_tok = header_end
        sym.end_line = self.t[sym.end_tok].line
        # Constructor properties are members too
        for p in reversed(sym.params):
            if p.prop:
                prop = Symbol(p.prop, p.name, self.f)
                prop.parent = sym
                prop.type = p.type
                prop.initializer = p.default or ""
                prop.doc = p.doc
                prop.modifiers = p.modifiers
                prop.annotations = p.annotations
                prop.line = prop.end_line = p.line
                prop.start_tok = prop.end_tok = p.name_tok
                prop.name_tok = p.name_tok
                prop.ctor_prop = p
                prop.signature = f"{p.prop} {p.name}: {p.type}" + (f" = {p.default}" if p.default else "")
                sym.children.insert(0, prop)
                self.f.all_symbols.append(prop)
        return sym

    def skip_annotations(self, i):
        j = i
        while self.is_op(j, "@"):
            j = self.annotation_end(j) + 1
        return j

    def enum_entries(self, i, end, parent, out):
        while i < end:
            mods, anns, j = self.modifiers_and_annotations(i, end)
            if not self.is_id(j) or self.tx(j) in DECL_KEYWORDS or self.tx(j) in MODIFIERS:
                return i
            entry = self.new_symbol("entry", self.tx(j), i, [], anns, parent)
            entry.name_tok = j
            k = j
            if self.is_op(k + 1, "("):
                entry.signature = self.tx(j) + self.join(k + 1, self.jump(k + 1))
                k = self.jump(k + 1)
            else:
                entry.signature = self.tx(j)
            if self.is_op(k + 1, "{"):
                k = self.jump(k + 1)
            entry.end_tok = k
            entry.end_line = self.t[k].line
            self.attach_doc(entry, i)
            out.append(entry)
            self.f.all_symbols.append(entry)
            i = k + 1
            if self.is_op(i, ","):
                i += 1
                continue
            if self.is_op(i, ";"):
                return i + 1
            return i
        return i

    def params(self, open_i, close_i):
        out = []
        k = open_i + 1
        while k < close_i:
            # one parameter, up to the next comma at depth 0 (generics count too, until the default value)
            a = k
            depth = 0
            in_default = False
            while k < close_i:
                tx = self.tx(k)
                if self.t[k].kind == "op" and tx in OPENERS:
                    k = self.jump(k) + 1
                    continue
                if not in_default:
                    if tx == "<":
                        depth += 1
                    elif tx == ">":
                        depth = max(0, depth - 1)
                    elif tx == "=":
                        in_default = True
                if tx == "," and depth == 0:
                    break
                k += 1
            b = k - 1
            if b >= a:
                p = Param()
                comments = self.f.comments_before.get(a, [])
                kd = [c for c in comments if c.kdoc]
                if kd:
                    p.doc = kdoc_text(kd[-1].text)
                mods, anns, j = self.modifiers_and_annotations(a, b + 1)
                p.modifiers, p.annotations = mods, anns
                if self.is_id(j) and self.tx(j) in ("val", "var") and self.is_id(j + 1):
                    p.prop = self.tx(j)
                    j += 1
                p.name = self.tx(j).strip("`")
                p.name_tok = j
                p.line = self.t[j].line if j < self.n else 0
                if self.is_op(j + 1, ":"):
                    e = j + 2
                    depth = 0
                    while e <= b:
                        tx = self.tx(e)
                        if self.t[e].kind == "op" and tx in OPENERS:
                            e = self.jump(e) + 1
                            continue
                        if tx == "<":
                            depth += 1
                        elif tx == ">":
                            depth -= 1
                        elif tx == "=" and depth <= 0:
                            break
                        e += 1
                    p.type = self.join(j + 2, e - 1)
                    if e <= b and self.tx(e) == "=":
                        p.default = self.join(e + 1, b)
                out.append(p)
            k += 1
        return out

    def type_end(self, k, end):
        """Last token of the type starting at k"""
        depth = 0
        last = k
        while k < end:
            tx = self.tx(k)
            tok = self.t[k]
            if k > last and tok.nl_before and depth == 0 and self.tx(k - 1) not in CONT_END and tx not in (".", "?", "->", "<", ","):
                break
            if depth == 0 and (tx in ("{", "=", "where", "by") and tok.kind in ("op", "id") and (tx != "where" or tok.kind == "id")):
                break
            if tok.kind == "op" and tx in OPENERS and tx != "{":
                k = self.jump(k)
            elif tx == "<":
                depth += 1
            elif tx == ">":
                depth -= 1
            last = k
            k += 1
        return last

    def expr_end(self, k, end, stop=()):
        """Last token of the expression starting at k"""
        while k < end:
            tok = self.t[k]
            if tok.kind == "op" and tok.text in OPENERS:
                k = self.jump(k)
            nxt = k + 1
            if nxt >= end:
                return k
            nt = self.t[nxt]
            if nt.text in (";", "}") and nt.kind == "op":
                return k
            if nt.text in stop and nt.kind == "op" and nt.text != "{":
                return k
            if nt.text == "{" and "{" in stop and nt.kind == "op":
                return k
            if nt.nl_before and self.t[k].text not in CONT_END and nt.text not in CONT_START:
                return k
            k = nxt
        return end - 1

    def function(self, i, j, kw, mods, anns, end, parent):
        k = j + 1
        type_params = ""
        if kw == "fun" and self.is_op(k, "<"):
            e = self.angle_end(k)
            type_params = self.join(k, e)
            k = e + 1
        a = k
        depth = 0
        while k < end and not (depth == 0 and self.is_op(k, "(")):
            tx = self.tx(k)
            if tx == "<":
                depth += 1
            elif tx == ">":
                depth -= 1
            k += 1
        if k >= end:
            return None
        if kw == "constructor":
            name, name_tok, receiver = "constructor", j, ""
        else:
            name_tok = k - 1
            name = self.tx(name_tok)
            receiver = self.join(a, k - 3) if k - 2 > a and self.tx(k - 2) == "." else ""
        sym = self.new_symbol("constructor" if kw == "constructor" else "fun", name.strip("`") if not name.startswith("`") else name, i, mods, anns, parent)
        sym.name_tok = name_tok
        sym.type_params = type_params
        sym.receiver = receiver
        close = self.jump(k)
        sym.params = self.params(k, close)
        k = close + 1
        last = close
        if self.is_op(k, ":"):
            e = self.type_end(k + 1, end)
            sym.type = self.join(k + 1, e) if kw == "fun" else ""
            if kw == "constructor":  # delegation call: this(...) / super(...)
                e = self.expr_end(k + 1, end, stop={"{"})
            last = e
            k = e + 1
        if self.is_id(k, "where") and not self.t[k].nl_before:
            while k < end and not self.is_op(k, "{") and not self.is_op(k, "=") and not self.t[k + 1].nl_before:
                k += 1
            last = k
            k += 1
        sym.signature = self.join(self.skip_annotations(i), last)
        if self.is_op(k, "{"):
            sym.end_tok = self.jump(k)
        elif self.is_op(k, "="):
            sym.end_tok = self.expr_end(k + 1, end)
            sym.initializer = self.join(k + 1, sym.end_tok)
        else:
            sym.end_tok = last
        sym.end_line = self.t[sym.end_tok].line
        return sym

    def property(self, i, j, kw, mods, anns, end, parent):
        k = j + 1
        type_params = ""
        if self.is_op(k, "<"):
            e = self.angle_end(k)
            type_params = self.join(k, e)
            k = e + 1
        if self.is_op(k, "("):
            return None  # destructuring, only inside bodies
        a = k
        while self.is_id(k) and self.is_op(k + 1, "."):
            k += 2
        while k < end and self.is_op(k, "<"):  # receiver List<T>.foo
            k = self.angle_end(k) + 1
            while self.is_op(k, ".") and self.is_id(k + 1):
                k += 2
            k -= 1
            break
        if not self.is_id(k):
            return None
        name_tok = k
        sym = self.new_symbol(kw, self.tx(k).strip("`"), i, mods, anns, parent)
        sym.name_tok = name_tok
        sym.type_params = type_params
        sym.receiver = self.join(a, k - 2) if k > a else ""
        last = k
        k += 1
        if self.is_op(k, ":"):
            e = self.type_end(k + 1, end)
            sym.type = self.join(k + 1, e)
            last = e
            k = e + 1
        sig_last = last
        if self.is_op(k, "=") or self.is_id(k, "by"):
            delegated = self.is_id(k, "by")
            e = self.expr_end(k + 1, end)
            sym.initializer = ("by " if delegated else "") + self.join(k + 1, e)
            last = e
            k = e + 1
        # Accessors on the following lines
        for _ in range(2):
            m, an, g = self.modifiers_and_annotations(k, end)
            if (self.is_id(g, "get") or self.is_id(g, "set")) and self.t[k].nl_before:
                g += 1
                if self.is_op(g, "("):
                    g = self.jump(g) + 1
                if self.is_op(g, ":"):
                    g = self.type_end(g + 1, end) + 1
                if self.is_op(g, "{"):
                    last = self.jump(g)
                elif self.is_op(g, "="):
                    last = self.expr_end(g + 1, end)
                else:
                    last = g - 1
                k = last + 1
            else:
                break
        sym.signature = self.join(self.skip_annotations(i), sig_last)
        sym.end_tok = last
        sym.end_line = self.t[last].line
        return sym

    def typealias(self, i, j, mods, anns, end, parent):
        k = j + 1
        if not self.is_id(k):
            return None
        sym = self.new_symbol("typealias", self.tx(k), i, mods, anns, parent)
        sym.name_tok = k
        k += 1
        if self.is_op(k, "<"):
            e = self.angle_end(k)
            sym.type_params = self.join(k, e)
            k = e + 1
        if self.is_op(k, "="):
            e = self.type_end(k + 1, end)
            sym.type = self.join(k + 1, e)
            k = e
        sym.signature = self.join(self.skip_annotations(i), k)
        sym.end_tok = k
        sym.end_line = self.t[k].line
        return sym


def kdoc_text(raw):
    body = raw[3:-2] if raw.endswith("*/") else raw[3:]
    lines = []
    for line in body.split("\n"):
        s = line.strip()
        if s.startswith("*"):
            s = s[1:]
            if s.startswith(" "):
                s = s[1:]
        lines.append(s.rstrip())
    while lines and not lines[0].strip():
        lines.pop(0)
    while lines and not lines[-1].strip():
        lines.pop()
    return "\n".join(lines)


# ---------------------------------------------------------------- docs model

class Doc:
    """A KDoc comment split into its text and its tags"""

    def __init__(self, text):
        self.main = []
        self.params = {}
        self.properties = {}
        self.returns = None
        self.receiver = None
        self.throws = []
        self.see = []
        self.samples = []
        self.constructor = None
        self.since = None
        self.other = []
        current = None
        for line in (text or "").split("\n"):
            m = re.match(r"\s*@(\w+)\s*(.*)", line)
            if m:
                tag, rest = m.group(1), m.group(2)
                if tag in ("param", "property", "throws", "exception"):
                    nm = rest.split(None, 1)
                    name = nm[0].strip("[]") if nm else ""
                    val = [nm[1] if len(nm) > 1 else ""]
                    if tag == "param":
                        self.params[name] = val
                    elif tag == "property":
                        self.properties[name] = val
                    else:
                        self.throws.append((name, val))
                    current = val
                elif tag in ("return", "receiver", "constructor", "since"):
                    val = [rest]
                    setattr(self, {"return": "returns"}.get(tag, tag), val)
                    current = val
                elif tag == "see":
                    self.see.append(rest)
                    current = None
                elif tag == "sample":
                    self.samples.append(rest)
                    current = None
                else:
                    val = [rest]
                    self.other.append((tag, val))
                    current = val
            elif current is not None and line.strip():
                current.append(line.strip())
            else:
                current = None if not line.strip() and current is not None else current
                if current is None:
                    self.main.append(line)

    @property
    def text(self):
        return "\n".join(self.main).strip()

    @property
    def brief(self):
        t = self.text
        if not t:
            return ""
        para = t.split("\n\n")[0]
        para = " ".join(para.split())
        m = re.match(r"(.+?[.!?])(\s|$)", para)
        # Not cut at "e.g." or at a decimal point
        if m and not re.search(r"\b(e\.g|i\.e|etc|vs|approx)\.$", m.group(1)):
            return m.group(1)
        return para


# ---------------------------------------------------------------- project

class Project:
    def __init__(self):
        self.files = []
        for d in SOURCE_DIRS:
            if d.exists():
                self.files += [SourceFile(p) for p in sorted(d.rglob("*.kt"))]
        for f in self.files:
            Parser(f).parse()
        self.symbols = [s for f in self.files for s in f.all_symbols]
        self.classes = [s for s in self.symbols if s.is_classlike]
        self.packages = defaultdict(list)
        for f in self.files:
            self.packages[f.package].append(f)
        self.version = self.read_version()
        self.commit, self.commit_date = self.read_commit()
        self.assign_pages()
        self.by_name = defaultdict(list)
        for s in self.symbols:
            self.by_name[s.name].append(s)
        self.by_qualified = {s.qualified: s for s in self.symbols if s.parent is None or s.is_classlike}
        self.resolve_references()
        self.file_dependencies()

    def read_version(self):
        gradle = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
        m = re.search(r'gradleProperty\("versionName"\)\.getOrElse\("([^"]+)"\)', gradle)
        return m.group(1) if m else ""

    def read_commit(self):
        try:
            out = subprocess.run(["git", "log", "-1", "--format=%h %cs"], cwd=ROOT, capture_output=True, text=True, check=True).stdout.split()
            return out[0], out[1]
        except Exception:
            return "", ""

    def assign_pages(self):
        for s in self.symbols:
            if s.is_classlike and not s.is_companion:
                s.page = "class_" + safe(s.qualified) + ".html"
        used = defaultdict(int)
        for s in self.symbols:
            if s.is_classlike and not s.is_companion:
                continue
            holder = s.parent
            while holder is not None and holder.is_companion:
                holder = holder.parent
            s.page = holder.page if holder is not None else s.file.page
            base = ("c_" if s.parent is not None and s.parent.is_companion else "") + safe(s.name.strip("`")) or "x"
            key = (s.page, base)
            used[key] += 1
            s.anchor = base + (f"_{used[key]}" if used[key] > 1 else "")

    # -- who uses what

    def resolve_references(self):
        for f in self.files:
            owner = [None] * len(f.tokens)
            for s in sorted(f.all_symbols, key=lambda s: s.end_tok - s.start_tok, reverse=True):
                for k in range(s.start_tok, min(s.end_tok, len(f.tokens) - 1) + 1):
                    owner[k] = s
            for s in f.all_symbols:
                if s.name_tok is not None:
                    f.decl_at[s.name_tok] = s
                for p in s.params:
                    pass
            for k, tok in enumerate(f.tokens):
                if tok.kind != "id" or k in f.decl_at:
                    continue
                cands = self.by_name.get(tok.text.strip("`") if not tok.text.startswith("`") else tok.text)
                if not cands:
                    continue
                target = self.pick(f, k, owner[k], cands)
                if target is None:
                    continue
                f.link_at[k] = target
                src = owner[k]
                if src is not None and src is not target and not self.inside(target, src):
                    src.refs.add(target)
                    target.referenced_by.add(src)

    @staticmethod
    def inside(a, b):
        """True if a is declared inside b"""
        s = a.parent
        while s:
            if s is b:
                return True
            s = s.parent
        return False

    def pick(self, f, k, owner, cands):
        toks = f.tokens
        prev = toks[k - 1].text if k > 0 else ""
        nxt = toks[k + 1].text if k + 1 < len(toks) else ""
        called = nxt in ("(", "{") or prev == "::"
        if prev in ("package", "import"):
            return None
        if k >= 2 and toks[k - 2].text in ("package", "import") or self.in_import(f, k):
            # import com.x.y.Name links to Name itself
            if prev == ".":
                path = self.import_path_upto(f, k)
                hit = self.by_qualified.get(path)
                return hit
            return None
        ok = []
        for c in cands:
            if c.kind == "fun" and not called and prev != "::":
                continue
            if c.kind == "constructor":
                continue
            if c.kind in ("val", "var") and called and nxt == "(":
                # calling a property only works for function-typed ones; keep but rank lower
                pass
            if prev in (".", "?."):
                if c.parent is None and not c.receiver and not c.is_classlike:
                    continue
            else:
                # Unqualified: a top-level declaration, a class, or a member of an enclosing class
                if c.parent is not None and not c.is_classlike and c.kind != "entry":
                    chain = set()
                    s = owner
                    while s is not None:
                        chain.add(s)
                        if s.is_classlike:
                            for ch in s.children:
                                if ch.is_companion:
                                    chain.add(ch)
                        s = s.parent
                    if c.parent not in chain:
                        continue
            if c.is_classlike and not toks[k].text[:1].isupper():
                continue
            ok.append(c)
        if not ok:
            return None
        if len(ok) > 1:
            same = [c for c in ok if c.file is f]
            if same:
                ok = same
        if len(ok) > 1:
            # Members of the class the name is used in
            s = owner
            while s is not None and len(ok) > 1:
                inner = [c for c in ok if c.parent is s or (c.parent is not None and c.parent.parent is s and c.parent.is_companion)]
                if inner:
                    ok = inner
                s = s.parent
        if len(ok) > 1:
            # f(x) and f(x, y) overloads: the first of them stands for all
            if len({(c.parent, c.name, c.kind) for c in ok}) == 1:
                return ok[0]
            return None
        target = ok[0]
        # A local variable or parameter with the same name hides it
        if not called and prev not in (".", "?.", "::") and not target.is_classlike and target.kind != "entry" and owner is not None and owner.kind in ("fun", "constructor"):
            if self.declared_locally(f, owner, k, toks[k].text):
                return None
        return target

    def declared_locally(self, f, owner, k, name):
        toks = f.tokens
        for p in owner.params:
            if p.name == name:
                return True
        for j in range(owner.start_tok, k):
            if toks[j].text == name and j > 0 and toks[j - 1].text in ("val", "var", ",", "(", "{") and j + 1 < len(toks) and toks[j + 1].text in (":", "=", ",", "->", ")"):
                if toks[j - 1].text in ("val", "var") or self._lambda_param(toks, j):
                    return True
        return False

    @staticmethod
    def _lambda_param(toks, j):
        # { a, b -> ... }  or  { a -> ... }
        m = j + 1
        while m < len(toks) and toks[m].text in (",",) or (m < len(toks) and toks[m].kind == "id"):
            m += 1
            if m - j > 8:
                return False
        return m < len(toks) and toks[m].text == "->"

    @staticmethod
    def in_import(f, k):
        line = f.tokens[k].line
        return any(l == line for (_, _, l) in f.imports) or f.tokens[k].line == next((t.line for t in f.tokens if t.text == "package"), -1)

    @staticmethod
    def import_path_upto(f, k):
        j = k
        while j >= 2 and f.tokens[j - 1].text == "." and f.tokens[j - 2].kind == "id":
            j -= 2
        return "".join(t.text for t in f.tokens[j:k + 1])

    def file_dependencies(self):
        for f in self.files:
            for s in f.all_symbols:
                for r in s.refs:
                    if r.file is not f:
                        f.deps.add(r.file)
            for k, target in f.link_at.items():
                if target.file is not f:
                    f.deps.add(target.file)
        for f in self.files:
            for d in f.deps:
                d.used_by.add(f)


# ---------------------------------------------------------------- text to html

def esc(s):
    return html.escape(s or "", quote=True)


class Linker:
    def __init__(self, project):
        self.p = project

    def symbol(self, name, near=None):
        name = name.strip("`")
        if "." in name:
            parts = name.split(".")
            hit = self.p.by_qualified.get(name)
            if hit:
                return hit
            # Class.member
            for c in self.p.by_name.get(parts[-1], []):
                if c.parent is not None and c.parent.name == parts[-2]:
                    return c
                if c.is_classlike and len(parts) == 1:
                    return c
            name = parts[-1]
        cands = self.p.by_name.get(name, [])
        if not cands:
            return None
        if near is not None:
            for scope in (near, near.parent, near.owner_class() if near else None):
                if scope is None:
                    continue
                for c in cands:
                    if c.parent is scope:
                        return c
            for c in cands:
                if c.file is near.file:
                    return c
        classes = [c for c in cands if c.is_classlike]
        return (classes or cands)[0]

    def type_html(self, text, rel=""):
        """Type or signature text with known class names linked"""
        out = []
        for part in re.split(r"([A-Za-z_][A-Za-z0-9_]*)", text or ""):
            if re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", part):
                cands = [c for c in self.p.by_name.get(part, []) if c.is_classlike]
                if cands and part[:1].isupper():
                    out.append(f'<a class="el" href="{rel}{cands[0].url}">{esc(part)}</a>')
                    continue
            out.append(esc(part))
        return "".join(out)

    def inline(self, text, near=None):
        """Markdown-ish inline text: `code`, **bold**, *em*, [Symbol] and [text](url) links"""
        parts = re.split(r"(`[^`]+`)", text)
        out = []
        for part in parts:
            if part.startswith("`") and part.endswith("`") and len(part) > 1:
                inner = part[1:-1]
                hit = self.symbol(inner, near) if re.fullmatch(r"[A-Za-z_][\w.]*", inner) else None
                if hit is not None and hit.name == inner.split(".")[-1]:
                    out.append(f'<a class="el" href="{hit.url}"><code>{esc(inner)}</code></a>')
                else:
                    out.append(f"<code>{esc(inner)}</code>")
                continue
            s = esc(part)
            s = re.sub(r"\[([^\]]+)\]\(([^)\s]+)\)", lambda m: f'<a href="{fix_url(html.unescape(m.group(2)))}">{m.group(1)}</a>', s)

            def symlink(m):
                hit = self.symbol(html.unescape(m.group(1)), near)
                if hit is None:
                    return f"<code>{m.group(1)}</code>"
                return f'<a class="el" href="{hit.url}">{m.group(1)}</a>'

            s = re.sub(r"(?<![\w\]])\[([A-Za-z_][\w.]*)\](?!\()", symlink, s)
            s = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", s)
            s = re.sub(r"(?<![\w*])\*(?!\s)([^*]+?)(?<!\s)\*(?![\w*])", r"<em>\1</em>", s)
            s = re.sub(r"(?<![\"'=])(https?://[^\s<)]+)", r'<a href="\1">\1</a>', s)
            out.append(s)
        return "".join(out)

    def block(self, text, near=None):
        """Markdown-ish block text: paragraphs, lists, code blocks, headings, quotes, tables"""
        lines = (text or "").replace("\r\n", "\n").split("\n")
        out = []
        i = 0
        para = []

        def flush():
            if para:
                out.append("<p>" + self.inline(" ".join(x.strip() for x in para), near) + "</p>")
                para.clear()

        while i < len(lines):
            line = lines[i]
            st = line.strip()
            if st.startswith("```"):
                flush()
                lang = st[3:].strip()
                code = []
                i += 1
                while i < len(lines) and not lines[i].strip().startswith("```"):
                    code.append(lines[i])
                    i += 1
                i += 1
                body = highlight_snippet(code) if lang in ("kotlin", "kt") else esc("\n".join(code))
                out.append(f'<div class="fragment"><pre>{body}</pre></div>')
                continue
            if line.startswith("    ") and not para and st and (not out or not out[-1].endswith("</li></ul>")):
                flush()
                code = []
                while i < len(lines) and (lines[i].startswith("    ") or not lines[i].strip()):
                    code.append(lines[i][4:])
                    i += 1
                while code and not code[-1].strip():
                    code.pop()
                out.append(f'<div class="fragment"><pre>{esc(chr(10).join(code))}</pre></div>')
                continue
            if not st:
                flush()
                i += 1
                continue
            m = re.match(r"(#{1,6})\s+(.*)", st)
            if m:
                flush()
                level = min(len(m.group(1)) + 1, 6)
                out.append(f'<h{level} class="mdh" id="{safe(m.group(2)).lower()}">{self.inline(m.group(2), near)}</h{level}>')
                i += 1
                continue
            if st.startswith("<") and not para:
                flush()
                block = []
                while i < len(lines) and lines[i].strip():
                    block.append(lines[i])
                    i += 1
                raw = "\n".join(block)
                raw = re.sub(r'(src|href)="(?!https?:|#|/)([^"]+)"', lambda m: f'{m.group(1)}="{fix_url(m.group(2))}"', raw)
                out.append(raw)
                continue
            if re.match(r"([*+-]|\d+[.)])\s+", st):
                flush()
                ordered = bool(re.match(r"\d", st))
                items = []
                while i < len(lines) and lines[i].strip():
                    s2 = lines[i].strip()
                    mm = re.match(r"([*+-]|\d+[.)])\s+(.*)", s2)
                    if mm and not lines[i].startswith("   "):
                        items.append([mm.group(2)])
                    elif items:
                        items[-1].append(s2)
                    i += 1
                tag = "ol" if ordered else "ul"
                out.append(f"<{tag}>" + "".join("<li>" + self.inline(" ".join(it), near) + "</li>" for it in items) + f"</{tag}>")
                continue
            if st.startswith(">"):
                flush()
                q = []
                while i < len(lines) and lines[i].strip().startswith(">"):
                    q.append(lines[i].strip()[1:].strip())
                    i += 1
                out.append("<blockquote>" + self.block("\n".join(q), near) + "</blockquote>")
                continue
            if st.startswith("|") and i + 1 < len(lines) and re.match(r"\s*\|?\s*:?-{2,}", lines[i + 1]):
                flush()
                rows = []
                while i < len(lines) and lines[i].strip().startswith("|"):
                    rows.append([c.strip() for c in lines[i].strip().strip("|").split("|")])
                    i += 1
                head, body = rows[0], rows[2:]
                t = '<table class="doxtable"><tr>' + "".join(f"<th>{self.inline(c, near)}</th>" for c in head) + "</tr>"
                for r in body:
                    t += "<tr>" + "".join(f"<td>{self.inline(c, near)}</td>" for c in r) + "</tr>"
                out.append(t + "</table>")
                continue
            para.append(line)
            i += 1
        flush()
        return "\n".join(out)


def fix_url(url):
    """Links in the README are relative to the repo root; the site is two folders down"""
    if re.match(r"(https?:|mailto:|#|/)", url):
        return url
    if url.startswith("docs/"):
        return "../" + url[5:]
    return f"{REPO_URL}/blob/main/{url}"


KEYWORD_CLASS = HARD_KEYWORDS | {"import", "by", "get", "set", "init", "constructor", "where", "catch", "finally"} | MODIFIERS


def highlight_snippet(lines):
    src = "\n".join(lines)
    tokens, comments = tokenize(src)
    return render_highlight(src, tokens, comments, {}, {}, "")


def render_highlight(src, tokens, comments, link_at, decl_at, rel, current_page=None):
    items = [(t.start, t.end, "t", k) for k, t in enumerate(tokens)] + [(c.start, c.end, "c", c) for c in comments]
    items.sort(key=lambda x: x[0])
    out = []
    pos = 0
    for start, end, typ, obj in items:
        if start < pos:
            continue
        out.append(esc(src[pos:start]))
        text = src[start:end]
        if typ == "c":
            cls = "comment" + (" kdoc" if obj.kdoc else "")
            out.append(f'<span class="{cls}">{esc(text)}</span>')
        else:
            k = obj
            t = tokens[k]
            if t.kind == "str" or t.kind == "chr":
                out.append(f'<span class="stringliteral">{esc(text)}</span>')
            elif t.kind == "num":
                out.append(f'<span class="numberliteral">{esc(text)}</span>')
            elif t.kind == "id":
                prev = tokens[k - 1].text if k > 0 else ""
                if prev == "@" and tokens[k].start == tokens[k - 1].end:
                    target = link_at.get(k)
                    inner = f'<a class="code" href="{rel}{target.url}">{esc(text)}</a>' if target else esc(text)
                    out.append(f'<span class="annotation">{inner}</span>')
                elif k in decl_at:
                    s = decl_at[k]
                    out.append(f'<a class="code hl_decl" id="d{k}" href="{rel}{s.url}">{esc(text)}</a>')
                elif k in link_at:
                    s = link_at[k]
                    out.append(f'<a class="code" href="{rel}{s.url}" title="{esc(s.qualified)}">{esc(text)}</a>')
                elif text in HARD_KEYWORDS or (text in KEYWORD_CLASS and k + 1 < len(tokens) and tokens[k + 1].kind == "id"):
                    out.append(f'<span class="keyword">{esc(text)}</span>')
                elif text[:1].isupper() and not text.isupper():
                    out.append(f'<span class="typename">{esc(text)}</span>')
                else:
                    out.append(esc(text))
            elif t.text == "@" and k + 1 < len(tokens) and tokens[k + 1].start == t.end:
                out.append('<span class="annotation">@</span>')
            else:
                out.append(esc(text))
        pos = end
    out.append(esc(src[pos:]))
    return "".join(out)


# ---------------------------------------------------------------- site

CSS = r"""
:root {
  --page-bg: #ffffff; --page-fg: #1c1c1c; --page-link: #3d578c; --page-visited: #4665a2;
  --muted: #5a5a5a; --border: #a8b8d9; --border-soft: #d5ddec; --header-bg: #f9fafc;
  --title-grad-a: #f9fafc; --title-grad-b: #e2e8f2; --tab-bg: #e8eef7; --tab-active: #ffffff;
  --tab-fg: #283a5d; --nav-bg: #f9fafc; --nav-sel: #c4cfe5; --memproto-bg: #e2e8f2;
  --memproto-border: #a8b8d9; --memdoc-bg: #ffffff; --memtitle-bg: #dfe5f1; --memsep: #dee4f0;
  --member-bg: #f9fafc; --fragment-bg: #fbfcfd; --fragment-border: #c4cfe5; --lineno: #888;
  --kw: #008000; --type: #604020; --str: #002080; --num: #a0522d; --com: #800000; --ann: #9b4dca;
  --label-bg: #728dc1; --label-fg: #fff; --graph-node: #ffffff; --graph-self: #bfbfbf; --graph-edge: #63b8ff;
  --graph-text: #000; --hl: #ffffcc; --search-bg: #ffffff; --shadow: 0 2px 6px rgba(0,0,0,.15);
}
@media (prefers-color-scheme: dark) {
  :root:not([data-theme="light"]) {
    --page-bg: #1c1d1f; --page-fg: #d2dbde; --page-link: #90a5ce; --page-visited: #a3b4d7;
    --muted: #a0a4a8; --border: #4a5466; --border-soft: #2f3542; --header-bg: #252628;
    --title-grad-a: #252628; --title-grad-b: #2c3038; --tab-bg: #283041; --tab-active: #1c1d1f;
    --tab-fg: #c6cfe0; --nav-bg: #212224; --nav-sel: #354360; --memproto-bg: #2a3040;
    --memproto-border: #4a5466; --memdoc-bg: #1c1d1f; --memtitle-bg: #2c3446; --memsep: #2f3542;
    --member-bg: #212328; --fragment-bg: #17181a; --fragment-border: #3a4150; --lineno: #777;
    --kw: #7bc87b; --type: #d0a77a; --str: #8fb3ff; --num: #e0a070; --com: #d0807a; --ann: #c79bf2;
    --label-bg: #4f6290; --label-fg: #eef; --graph-node: #2a2d33; --graph-self: #4a5060; --graph-edge: #4f8fd0;
    --graph-text: #d2dbde; --hl: #3a3a20; --search-bg: #2a2c30; --shadow: 0 2px 8px rgba(0,0,0,.5);
  }
}
:root[data-theme="dark"] {
  --page-bg: #1c1d1f; --page-fg: #d2dbde; --page-link: #90a5ce; --page-visited: #a3b4d7;
  --muted: #a0a4a8; --border: #4a5466; --border-soft: #2f3542; --header-bg: #252628;
  --title-grad-a: #252628; --title-grad-b: #2c3038; --tab-bg: #283041; --tab-active: #1c1d1f;
  --tab-fg: #c6cfe0; --nav-bg: #212224; --nav-sel: #354360; --memproto-bg: #2a3040;
  --memproto-border: #4a5466; --memdoc-bg: #1c1d1f; --memtitle-bg: #2c3446; --memsep: #2f3542;
  --member-bg: #212328; --fragment-bg: #17181a; --fragment-border: #3a4150; --lineno: #777;
  --kw: #7bc87b; --type: #d0a77a; --str: #8fb3ff; --num: #e0a070; --com: #d0807a; --ann: #c79bf2;
  --label-bg: #4f6290; --label-fg: #eef; --graph-node: #2a2d33; --graph-self: #4a5060; --graph-edge: #4f8fd0;
  --graph-text: #d2dbde; --hl: #3a3a20; --search-bg: #2a2c30; --shadow: 0 2px 8px rgba(0,0,0,.5);
}
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; }
body { background: var(--page-bg); color: var(--page-fg); font: 14px/1.45 Roboto, "Segoe UI", Arial, sans-serif; }
a { color: var(--page-link); text-decoration: none; }
a:visited { color: var(--page-visited); }
a:hover { text-decoration: underline; }
a.el { font-weight: bold; }
code, pre, .memname, .memItemLeft, .memItemRight, .memTemplParams { font-family: "JetBrains Mono", Consolas, "DejaVu Sans Mono", monospace; }
code { font-size: 0.92em; }
#top { border-bottom: 1px solid var(--border); background: var(--header-bg); }
#titlearea { display: flex; align-items: center; gap: 14px; padding: 10px 16px; }
#projectlogo img { width: 48px; height: 48px; display: block; }
#projectname { font: 300 26px/1.2 Tahoma, Arial, sans-serif; }
#projectnumber { font-size: 50%; color: var(--muted); margin-left: 6px; }
#projectbrief { font-size: 13px; color: var(--muted); }
#theme-toggle { margin-left: auto; border: 1px solid var(--border); background: var(--tab-bg); color: var(--tab-fg); border-radius: 4px; padding: 4px 9px; cursor: pointer; font: inherit; }
.tabs, .tabs2 { display: flex; flex-wrap: wrap; align-items: stretch; margin: 0; padding: 0 8px; list-style: none; background: var(--tab-bg); border-top: 1px solid var(--border-soft); }
.tabs li, .tabs2 li { display: block; }
.tabs a, .tabs2 a { display: block; padding: 7px 13px; color: var(--tab-fg); font-weight: bold; font-size: 13px; text-shadow: 0 1px 1px rgba(255,255,255,.4); }
.tabs2 a { font-weight: normal; font-size: 12.5px; padding: 5px 12px; }
.tabs li.current a, .tabs2 li.current a { background: var(--tab-active); color: var(--page-link); }
.tabs li.search { margin-left: auto; position: relative; padding: 4px 0; }
#searchbox { width: 220px; max-width: 46vw; padding: 4px 10px; border: 1px solid var(--border); border-radius: 12px; background: var(--search-bg); color: var(--page-fg); font: inherit; }
#searchresults { position: absolute; right: 0; top: 34px; z-index: 50; width: 420px; max-width: 92vw; max-height: 70vh; overflow: auto; background: var(--search-bg); border: 1px solid var(--border); box-shadow: var(--shadow); display: none; }
#searchresults a { display: block; padding: 5px 10px; border-bottom: 1px solid var(--border-soft); font-weight: normal; }
#searchresults a:hover, #searchresults a.sel { background: var(--nav-sel); text-decoration: none; }
#searchresults .sk { color: var(--muted); font-size: 12px; margin-left: 6px; }
#searchresults .none { padding: 8px 10px; color: var(--muted); }
#layout { display: flex; align-items: stretch; min-height: calc(100vh - 120px); }
#side-nav { width: 280px; flex: none; background: var(--nav-bg); border-right: 1px solid var(--border-soft); overflow: auto; max-height: calc(100vh - 0px); position: sticky; top: 0; font-size: 13px; padding: 6px 0 20px; }
#side-nav ul { list-style: none; margin: 0; padding-left: 14px; }
#side-nav > ul { padding-left: 4px; }
#side-nav li > div { display: flex; align-items: center; white-space: nowrap; padding: 2px 6px 2px 0; border-radius: 3px; }
#side-nav li > div.selected { background: var(--nav-sel); }
#side-nav .arrow { width: 16px; flex: none; text-align: center; cursor: pointer; color: var(--muted); user-select: none; font-size: 10px; }
#side-nav .closed > ul { display: none; }
#side-nav a { color: var(--page-fg); font-weight: normal; overflow: hidden; text-overflow: ellipsis; }
#nav-toggle { display: none; }
#doc-content { flex: 1; min-width: 0; }
div.header { background: linear-gradient(var(--title-grad-a), var(--title-grad-b)); border-bottom: 1px solid var(--border-soft); padding: 4px 18px 6px; }
div.summary { float: right; font-size: 12px; padding-top: 6px; }
div.headertitle { padding: 5px 0; }
div.title { font: 400 22px/28px Tahoma, Arial, sans-serif; margin: 6px 0 4px; }
div.ingroups { font-size: 12px; color: var(--muted); }
div.contents { padding: 10px 18px 30px; max-width: 1180px; }
h2.groupheader { border-bottom: 1px solid var(--border); color: var(--tab-fg); font-size: 150%; font-weight: normal; margin-top: 1.6em; padding-bottom: 4px; }
h2.groupheader a { color: inherit; }
.mdh { color: var(--tab-fg); }
p { margin: .6em 0; }
.textblock { max-width: 900px; }
.textblock img { max-width: 100%; }
table.memberdecls { border-spacing: 0; width: 100%; margin-top: 6px; }
table.memberdecls td { padding: 3px 6px; vertical-align: top; }
.memItemLeft, .memItemRight { background: var(--member-bg); border-top: 1px solid var(--memsep); font-size: 13px; }
.memItemLeft { text-align: right; white-space: nowrap; color: var(--muted); width: 1%; }
.memItemRight { width: 99%; }
.mdescLeft, .mdescRight { background: var(--member-bg); font-size: 12.5px; color: var(--muted); padding-bottom: 6px !important; }
.memSeparator { height: 1px; padding: 0 !important; }
.memItemRight .params { color: var(--muted); font-weight: normal; }
.memtitle { display: inline-block; margin: 26px 0 0; padding: 6px 12px 4px 8px; background: var(--memtitle-bg); border: 1px solid var(--memproto-border); border-bottom: none; border-radius: 4px 4px 0 0; font: 400 15px/1.3 Tahoma, Arial, sans-serif; }
.memtitle .permalink { margin-right: 6px; color: var(--muted); }
.memitem { border: 1px solid var(--memproto-border); border-radius: 0 4px 4px 4px; overflow: hidden; margin-bottom: 8px; box-shadow: var(--shadow); }
.memproto { background: var(--memproto-bg); padding: 7px 10px; border-bottom: 1px solid var(--memproto-border); overflow-x: auto; }
table.mlabels { width: 100%; border-spacing: 0; }
table.mlabels td.mlabels-left { width: 100%; }
td.mlabels-right { vertical-align: bottom; white-space: nowrap; text-align: right; }
span.mlabel { background: var(--label-bg); color: var(--label-fg); border-radius: 3px; padding: 1px 6px; margin-left: 4px; font-size: 11px; white-space: nowrap; }
table.memname { border-spacing: 0; font-size: 13.5px; }
table.memname td { padding: 1px 0; vertical-align: top; white-space: nowrap; }
td.memname { font-weight: bold; }
td.paramname { color: #602020; font-style: italic; padding-left: 6px !important; }
:root[data-theme="dark"] td.paramname { color: #e0a4a4; }
@media (prefers-color-scheme: dark) { :root:not([data-theme="light"]) td.paramname { color: #e0a4a4; } }
td.paramtype { padding-left: 4px !important; }
.annotations { color: var(--ann); font-size: 12.5px; margin-bottom: 2px; }
.memdoc { background: var(--memdoc-bg); padding: 6px 14px 10px; }
.memdoc .definition, p.definition { font-size: 12.5px; color: var(--muted); }
dl.params, dl.retval, dl.section { margin: 8px 0; }
dl.section dt, dl.params dt { font-weight: bold; }
dl.section dd, dl.params dd { margin-left: 22px; }
dl.note { border-left: 4px solid #d0c000; padding-left: 8px; }
dl.see { border-left: 4px solid #4caf50; padding-left: 8px; }
dl.todo { border-left: 4px solid #e08000; padding-left: 8px; }
table.params, table.fieldtable, table.doxtable, table.directory { border-collapse: collapse; margin: 4px 0; }
table.params td { padding: 2px 10px 2px 0; vertical-align: top; }
table.params td.paramname { white-space: nowrap; }
table.fieldtable, table.doxtable { border: 1px solid var(--border); box-shadow: var(--shadow); }
table.fieldtable th, table.doxtable th { background: var(--memtitle-bg); text-align: left; padding: 5px 10px; border-bottom: 1px solid var(--border); }
table.fieldtable td, table.doxtable td { padding: 4px 10px; border-top: 1px solid var(--border-soft); vertical-align: top; }
table.directory { width: 100%; }
table.directory td { padding: 3px 8px; border-top: 1px solid var(--border-soft); vertical-align: top; }
table.directory tr:nth-child(even) td { background: var(--member-bg); }
table.directory td.entry { white-space: nowrap; }
table.directory td.desc { color: var(--muted); }
.icon { display: inline-block; width: 16px; height: 16px; line-height: 16px; border-radius: 3px; background: var(--label-bg); color: var(--label-fg); font: bold 10px/16px Arial, sans-serif; text-align: center; margin-right: 5px; vertical-align: 1px; }
.icon.f { background: #7a9a4a; } .icon.p { background: #a07040; } .icon.d { background: #c0a040; } .icon.i { background: #8a6dbf; } .icon.o { background: #3f8f8f; } .icon.e { background: #b05050; }
div.fragment { background: var(--fragment-bg); border: 1px solid var(--fragment-border); padding: 6px 10px; overflow-x: auto; margin: 6px 0; }
div.fragment pre { margin: 0; font-size: 12.5px; }
.srccode { background: var(--fragment-bg); border: 1px solid var(--fragment-border); overflow-x: auto; font: 12.5px/1.5 "JetBrains Mono", Consolas, monospace; }
.srccode .line { white-space: pre; padding-right: 12px; }
.srccode .line:target { background: var(--hl); }
.srccode .lineno { display: inline-block; width: 52px; padding-right: 10px; margin-right: 10px; text-align: right; color: var(--lineno); border-right: 1px solid var(--fragment-border); user-select: none; }
.srccode .lineno a { color: var(--lineno); font-weight: normal; }
a.code { color: var(--page-link); font-weight: normal; }
a.code.hl_decl { font-weight: bold; }
.keyword { color: var(--kw); font-weight: 600; }
.typename { color: var(--type); }
.stringliteral { color: var(--str); }
.numberliteral { color: var(--num); }
.comment { color: var(--com); font-style: italic; }
.annotation, .annotation a { color: var(--ann) !important; }
.qindex { text-align: center; margin: 10px 0 14px; font-size: 15px; }
.qindex a { padding: 0 4px; font-weight: bold; }
.classindex { columns: 4 200px; column-gap: 26px; }
.classindex .ah { font-size: 20px; font-weight: bold; color: var(--tab-fg); border-bottom: 1px solid var(--border); margin: 12px 0 4px; break-after: avoid; }
.classindex div.item { break-inside: avoid; padding: 1px 0; }
.classindex .scope { color: var(--muted); font-size: 12px; }
ul.memberlist { list-style: none; padding-left: 10px; }
ul.memberlist li { padding: 1px 0; }
.letter { font-size: 18px; font-weight: bold; color: var(--tab-fg); margin: 16px 0 4px; }
.hierarchy ul { list-style: none; padding-left: 22px; border-left: 1px dotted var(--border); margin: 2px 0 2px 6px; }
.hierarchy > ul { border-left: none; padding-left: 0; margin-left: 0; }
.hierarchy li { padding: 2px 0; }
.hierarchy .ext { color: var(--muted); }
.graph { overflow-x: auto; margin: 8px 0 12px; }
.graph svg text { font: 12px Arial, sans-serif; fill: var(--graph-text); }
.graph svg .node rect { fill: var(--graph-node); stroke: var(--page-fg); stroke-width: 1; }
.graph svg .node.self rect { fill: var(--graph-self); }
.graph svg .node.ext rect { stroke: var(--muted); stroke-dasharray: 3 2; }
.graph svg .node a:hover rect { stroke: var(--page-link); stroke-width: 2; }
.graph svg path.edge { stroke: var(--graph-edge); fill: none; stroke-width: 1.2; }
.graph svg .arrow { fill: var(--graph-edge); }
.dyncontent .legend { font-size: 12px; color: var(--muted); }
details.graphbox > summary { cursor: pointer; color: var(--page-link); margin: 6px 0; }
.statgrid { display: grid; grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 8px; margin: 10px 0 20px; }
.statgrid div { border: 1px solid var(--border-soft); background: var(--member-bg); padding: 8px 10px; border-radius: 4px; }
.statgrid b { display: block; font-size: 20px; color: var(--tab-fg); }
.statgrid span { color: var(--muted); font-size: 12px; }
.navpath { border-top: 1px solid var(--border-soft); background: var(--tab-bg); padding: 5px 18px; font-size: 12.5px; }
.navpath a { color: var(--tab-fg); }
.navpath .sep { color: var(--muted); margin: 0 5px; }
address.footer { text-align: right; padding: 8px 18px 14px; color: var(--muted); font-style: normal; font-size: 12px; }
.coverage { display: inline-block; width: 120px; height: 8px; background: var(--border-soft); border-radius: 4px; overflow: hidden; vertical-align: middle; margin-right: 6px; }
.coverage i { display: block; height: 100%; background: #6aa84f; }
.undoc { color: var(--muted); font-style: italic; }
.fromcomment { font-size: 11px; color: var(--muted); }
@media (max-width: 860px) {
  #side-nav { display: none; position: fixed; z-index: 40; left: 0; top: 0; bottom: 0; max-height: none; box-shadow: var(--shadow); }
  body.nav-open #side-nav { display: block; }
  #nav-toggle { display: inline-block; border: 1px solid var(--border); background: var(--tab-bg); color: var(--tab-fg); border-radius: 4px; padding: 4px 9px; font: inherit; cursor: pointer; }
  #titlearea { padding: 8px 16px; flex-wrap: wrap; }
  #projectname { font-size: 20px; }
  div.contents, div.header { padding-left: 16px; padding-right: 16px; }
  div.summary { float: none; }
  .memItemLeft { white-space: normal; }
  .tabs li.search { margin-left: 0; width: 100%; padding: 4px 8px 6px; }
  #searchbox { width: 100%; max-width: none; }
  #searchresults { left: 0; right: auto; width: 100%; }
}
"""

JS = r"""
(function () {
  var root = document.documentElement;
  try { var t = localStorage.getItem('doc-theme'); if (t) root.setAttribute('data-theme', t); } catch (e) {}
  window.toggleTheme = function () {
    var dark = root.getAttribute('data-theme') === 'dark' ||
      (!root.getAttribute('data-theme') && window.matchMedia('(prefers-color-scheme: dark)').matches);
    var next = dark ? 'light' : 'dark';
    root.setAttribute('data-theme', next);
    try { localStorage.setItem('doc-theme', next); } catch (e) {}
  };
  document.addEventListener('DOMContentLoaded', function () {
    buildTree();
    var nt = document.getElementById('nav-toggle');
    if (nt) nt.onclick = function () { document.body.classList.toggle('nav-open'); };
    setupSearch();
  });

  function here() {
    var p = location.pathname.split('/').pop() || 'index.html';
    return p;
  }

  function buildTree() {
    var nav = document.getElementById('side-nav');
    if (!nav || !window.NAVTREE) return;
    var current = here();
    var opened = {};
    try { opened = JSON.parse(sessionStorage.getItem('doc-nav') || '{}'); } catch (e) {}
    function contains(node) {
      if (node[1] === current) return true;
      return (node[2] || []).some(contains);
    }
    function make(node, depth, key) {
      var li = document.createElement('li');
      var row = document.createElement('div');
      var kids = node[2] || [];
      var arrow = document.createElement('span');
      arrow.className = 'arrow';
      row.appendChild(arrow);
      var a = document.createElement(node[1] ? 'a' : 'span');
      if (node[1]) a.href = node[1];
      a.textContent = node[0];
      a.title = node[0];
      row.appendChild(a);
      li.appendChild(row);
      if (node[1] === current) row.className = 'selected';
      if (kids.length) {
        var ul = document.createElement('ul');
        var open = opened[key] !== undefined ? opened[key] : (depth < 1 || contains(node));
        if (contains(node)) open = true;
        li.className = open ? '' : 'closed';
        arrow.textContent = open ? '▼' : '▶';
        arrow.onclick = function () {
          var closed = li.classList.toggle('closed');
          arrow.textContent = closed ? '▶' : '▼';
          opened[key] = !closed;
          try { sessionStorage.setItem('doc-nav', JSON.stringify(opened)); } catch (e) {}
        };
        kids.forEach(function (k, i) { ul.appendChild(make(k, depth + 1, key + '/' + i)); });
        li.appendChild(ul);
      }
      return li;
    }
    var ul = document.createElement('ul');
    window.NAVTREE.forEach(function (n, i) { ul.appendChild(make(n, 0, '' + i)); });
    nav.appendChild(ul);
    var sel = nav.querySelector('.selected');
    // Scroll the tree only, not the page
    if (sel) nav.scrollTop = sel.offsetTop - nav.clientHeight / 2;
  }

  function fold(s) {
    return s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
  }

  function setupSearch() {
    var box = document.getElementById('searchbox');
    var res = document.getElementById('searchresults');
    if (!box || !window.SEARCHDATA) return;
    var data = window.SEARCHDATA.map(function (d) { return { d: d, f: fold(d[0]) }; });
    var sel = -1;
    function show() {
      var q = fold(box.value.trim());
      res.innerHTML = '';
      sel = -1;
      if (!q) { res.style.display = 'none'; return; }
      var hits = data.filter(function (x) { return x.f.indexOf(q) >= 0; });
      hits.sort(function (a, b) {
        var ra = a.f === q ? 0 : a.f.indexOf(q) === 0 ? 1 : 2;
        var rb = b.f === q ? 0 : b.f.indexOf(q) === 0 ? 1 : 2;
        return ra - rb || a.f.length - b.f.length || (a.f < b.f ? -1 : 1);
      });
      hits.slice(0, 60).forEach(function (x) {
        var a = document.createElement('a');
        a.href = x.d[3];
        a.textContent = x.d[0];
        var s = document.createElement('span');
        s.className = 'sk';
        s.textContent = x.d[1] + (x.d[2] ? ' · ' + x.d[2] : '');
        a.appendChild(s);
        res.appendChild(a);
      });
      if (!hits.length) {
        var n = document.createElement('div');
        n.className = 'none';
        n.textContent = 'No matches';
        res.appendChild(n);
      }
      res.style.display = 'block';
    }
    box.addEventListener('input', show);
    box.addEventListener('focus', show);
    box.addEventListener('keydown', function (e) {
      var links = res.querySelectorAll('a');
      if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        if (!links.length) return;
        if (sel >= 0) links[sel].classList.remove('sel');
        sel = (sel + (e.key === 'ArrowDown' ? 1 : -1) + links.length) % links.length;
        links[sel].classList.add('sel');
        links[sel].scrollIntoView({ block: 'nearest' });
      } else if (e.key === 'Enter') {
        var a = links[sel >= 0 ? sel : 0];
        if (a) location.href = a.href;
      } else if (e.key === 'Escape') {
        res.style.display = 'none';
        box.blur();
      }
    });
    document.addEventListener('click', function (e) {
      if (e.target !== box && !res.contains(e.target)) res.style.display = 'none';
    });
  }
})();
"""

TABS = [
    ("Main Page", "index.html", "main"),
    ("Topics", "topics.html", "topics"),
    ("Packages", "packages.html", "packages"),
    ("Classes", "annotated.html", "classes"),
    ("Files", "files.html", "files"),
    ("Related Pages", "pages.html", "pages"),
]
SUBTABS = {
    "packages": [("Package List", "packages.html"), ("Package Members", "namespacemembers.html")],
    "classes": [("Class List", "annotated.html"), ("Class Index", "classes.html"), ("Class Hierarchy", "hierarchy.html"), ("Class Members", "functions.html")],
    "files": [("File List", "files.html"), ("File Members", "globals.html")],
}
MEMBER_SUBTABS = {
    "functions": [("All", "functions.html"), ("Functions", "functions_func.html"), ("Properties", "functions_vars.html"), ("Enum values", "functions_eval.html")],
    "globals": [("All", "globals.html"), ("Functions", "globals_func.html"), ("Properties", "globals_vars.html"), ("Type aliases", "globals_type.html")],
}


class Site:
    def __init__(self, project, out):
        self.p = project
        self.out = out
        self.link = Linker(project)
        self.search = []
        self.pages_written = 0

    # -- page frame

    def write(self, name, title, body, tab=None, subtab=None, summary="", navpath=None, header_extra="", member_tabs=None):
        tabs = "".join(
            f'<li class="{"current" if key == tab else ""}"><a href="{href}">{esc(label)}</a></li>'
            for label, href, key in TABS
        )
        tabs += '<li class="search"><input id="searchbox" type="search" placeholder="Search" autocomplete="off" aria-label="Search"><div id="searchresults"></div></li>'
        sub = ""
        if tab in SUBTABS:
            sub = '<ul class="tabs2">' + "".join(
                f'<li class="{"current" if href == subtab else ""}"><a href="{href}">{esc(label)}</a></li>' for label, href in SUBTABS[tab]
            ) + "</ul>"
        if member_tabs:
            sub += '<ul class="tabs2">' + "".join(
                f'<li class="{"current" if href == name else ""}"><a href="{href}">{esc(label)}</a></li>' for label, href in MEMBER_TABS_FOR(member_tabs)
            ) + "</ul>"
        nav = ""
        if navpath:
            crumbs = []
            for label, href in navpath:
                crumbs.append(f'<a href="{href}">{esc(label)}</a>' if href else esc(label))
            nav = '<div class="navpath">' + '<span class="sep">›</span>'.join(crumbs) + "</div>"
        version = f'<span id="projectnumber">{esc(self.p.version)}</span>' if self.p.version else ""
        page = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="generator" content="scripts/gen_docs.py">
<title>Kastoria City Buses: {esc(title)}</title>
<link rel="icon" href="../icon.svg" type="image/svg+xml">
<link rel="stylesheet" href="doxygen.css">
<script src="navtreedata.js"></script>
<script src="searchdata.js"></script>
<script src="docs.js"></script>
</head>
<body>
<div id="top">
<div id="titlearea">
<button id="nav-toggle" type="button" aria-label="Show navigation">☰</button>
<div id="projectlogo"><img alt="" src="../icon.svg"></div>
<div><div id="projectname">Kastoria City Buses{version}</div>
<div id="projectbrief">Αστικά Καστοριάς · Android app source documentation</div></div>
<button id="theme-toggle" type="button" onclick="toggleTheme()" title="Light or dark">◐</button>
</div>
<ul class="tabs">{tabs}</ul>
{sub}
</div>
<div id="layout">
<nav id="side-nav" aria-label="Contents"></nav>
<div id="doc-content">
<div class="header">
{f'<div class="summary">{summary}</div>' if summary else ""}
<div class="headertitle"><div class="title">{esc(title)}</div>{header_extra}</div>
</div>
<div class="contents">
{body}
</div>
{nav}
<address class="footer">Generated {esc(self.p.commit_date)}{(" from commit " + f'<a href="{REPO_URL}/commit/{esc(self.p.commit)}">{esc(self.p.commit)}</a>') if self.p.commit else ""} by <code>scripts/gen_docs.py</code>, a Doxygen-style generator for Kotlin</address>
</div>
</div>
</body>
</html>
"""
        (self.out / name).write_text(page, encoding="utf-8", newline="\n")
        self.pages_written += 1

    # -- pieces

    def doc_of(self, s):
        return Doc(s.doc) if s.doc else Doc("")

    def brief(self, s):
        b = self.doc_of(s).brief
        if not b and s.ctor_prop is not None and s.ctor_prop.doc:
            b = Doc(s.ctor_prop.doc).brief
        return b

    def labels(self, s):
        out = []
        if s.visibility != "public":
            out.append(s.visibility)
        for m in s.modifiers:
            if m in ("override", "suspend", "inline", "operator", "infix", "abstract", "open", "tailrec", "external", "lateinit", "const", "data", "sealed", "inner", "value", "enum"):
                out.append(m)
        if s.receiver:
            out.append("extension")
        if s.is_composable:
            out.append("@Composable")
        if s.is_test:
            out.append("@Test")
        if s.ctor_prop is not None:
            out.append("constructor property")
        if s.parent is not None and s.parent.is_companion:
            out.append("companion")
        seen = []
        for x in out:
            if x not in seen:
                seen.append(x)
        return seen

    def kind_icon(self, s):
        if s.kind == "interface":
            return '<span class="icon i">I</span>'
        if s.kind == "object":
            return '<span class="icon o">O</span>'
        if "enum" in s.modifiers:
            return '<span class="icon e">E</span>'
        if "data" in s.modifiers:
            return '<span class="icon d">D</span>'
        if s.kind == "fun":
            return '<span class="icon f">F</span>'
        if s.kind in ("val", "var"):
            return '<span class="icon p">P</span>'
        return '<span class="icon">C</span>'

    def params_inline(self, s):
        return "(" + ", ".join(
            ("vararg " if "vararg" in p.modifiers else "") + p.name + (": " + p.type if p.type else "") + (" = …" if p.default else "")
            for p in s.params
        ) + ")"

    def decl_row(self, s, rel=""):
        """A row of a member table: kind/type on the left, the linked name and parameters on the right"""
        if s.kind == "fun":
            left = "fun"
            if s.type_params:
                left = "fun " + esc(s.type_params)
            right = (esc(s.receiver) + "." if s.receiver else "") + f'<a class="el" href="{s.url}">{esc(s.name)}</a>'
            right += f' <span class="params">{self.link.type_html(self.params_inline(s))}</span>'
            if s.type:
                right += ": " + self.link.type_html(s.type)
        elif s.kind == "constructor":
            left = "constructor"
            right = f'<a class="el" href="{s.url}">constructor</a> <span class="params">{self.link.type_html(self.params_inline(s))}</span>'
        elif s.kind in ("val", "var"):
            left = esc(s.keyword)
            right = (esc(s.receiver) + "." if s.receiver else "") + f'<a class="el" href="{s.url}">{esc(s.name)}</a>'
            if s.type:
                right += ": " + self.link.type_html(s.type)
            elif s.initializer and len(s.initializer) <= 40 and not s.initializer.startswith("by"):
                right += " = " + esc(s.initializer)
        elif s.kind == "entry":
            left = "enum value"
            right = f'<a class="el" href="{s.url}">{esc(s.name)}</a>'
        elif s.kind == "typealias":
            left = "typealias"
            right = f'<a class="el" href="{s.url}">{esc(s.name)}</a> = {self.link.type_html(s.type)}'
        else:
            left = esc(s.keyword)
            right = f'<a class="el" href="{s.url}">{esc(s.name)}</a>'
            if s.supertypes:
                right += " : " + ", ".join(self.link.type_html(b) for b, _, _ in s.supertypes)
        labels = [l for l in self.labels(s) if l not in ("public",)]
        lab = "".join(f' <span class="mlabel">{esc(l)}</span>' for l in labels if l in ("private", "protected", "internal", "override", "suspend", "@Composable", "extension", "abstract"))
        brief = self.brief(s)
        rows = f'<tr><td class="memItemLeft">{left}</td><td class="memItemRight">{right}{lab}</td></tr>'
        if brief:
            rows += f'<tr><td class="mdescLeft">&#160;</td><td class="mdescRight">{self.link.inline(brief, s)} <a href="{s.url}">More...</a></td></tr>'
        rows += '<tr class="memSeparator"><td colspan="2"></td></tr>'
        return rows

    def member_table(self, title, anchor, syms):
        if not syms:
            return ""
        rows = "".join(self.decl_row(s) for s in syms)
        return f'<h2 class="groupheader"><a id="{anchor}"></a>{esc(title)}</h2>\n<table class="memberdecls">{rows}</table>\n'

    def proto(self, s):
        """The box at the top of a member's documentation, with its full declaration"""
        anns = [a for a in s.annotations if a != "@Composable"] + (["@Composable"] if s.is_composable else [])
        ann_html = f'<div class="annotations">{" ".join(esc(a) for a in anns)}</div>' if anns else ""
        mods = [m for m in s.modifiers if m not in ("public",)]
        if s.kind in ("fun", "constructor"):
            head = " ".join(mods + ["fun" if s.kind == "fun" else "constructor"])
            if s.anchor == "primary-constructor" and s.parent is not None:
                head = s.parent.name
            if s.type_params:
                head += " " + esc(s.type_params)
            name = (esc(s.receiver) + "." if s.receiver else "") + (esc(s.name) if s.kind == "fun" else "")
            ret = (": " + self.link.type_html(s.type)) if s.type else ""
            rows = []
            if not s.params:
                rows.append(f'<tr><td class="memname">{esc(head)} {name}</td><td>(</td><td class="paramname"></td><td>){ret}</td></tr>')
            else:
                for idx, prm in enumerate(s.params):
                    first = idx == 0
                    last = idx == len(s.params) - 1
                    pmods = " ".join(prm.modifiers + ([prm.prop] if prm.prop else []))
                    pname = (esc(pmods) + " " if pmods else "") + esc(prm.name)
                    ptype = (": " + self.link.type_html(prm.type)) if prm.type else ""
                    pdef = (" = " + esc(prm.default)) if prm.default else ""
                    tail = (")" + ret) if last else ","
                    rows.append(
                        f'<tr><td class="memname">{(esc(head) + " " + name) if first else ""}</td><td>{"(" if first else ""}</td>'
                        f'<td class="paramname">{pname}</td><td class="paramtype">{ptype}{pdef}{tail}</td></tr>'
                    )
            table = f'<table class="memname">{"".join(rows)}</table>'
        elif s.kind == "entry":
            table = f'<table class="memname"><tr><td class="memname">{esc(s.parent.name if s.parent else "")}.{esc(s.signature)}</td></tr></table>'
        else:
            sig = s.signature
            body = esc(sig)
            if s.kind in ("val", "var"):
                body = (esc(" ".join(mods + [s.kind])) + " " + (esc(s.receiver) + "." if s.receiver else "") + esc(s.name)
                        + ((": " + self.link.type_html(s.type)) if s.type else "")
                        + ((" = " + esc(shorten(s.initializer, 160))) if s.initializer else ""))
            elif s.kind == "typealias":
                body = esc(" ".join(mods + ["typealias"])) + " " + esc(s.name) + esc(s.type_params) + " = " + self.link.type_html(s.type)
            table = f'<table class="memname"><tr><td class="memname">{body}</td></tr></table>'
        labels = self.labels(s)
        right = "".join(f'<span class="mlabel">{esc(l)}</span>' for l in labels)
        return f'<div class="memproto">{ann_html}<table class="mlabels"><tr><td class="mlabels-left">{table}</td><td class="mlabels-right">{right}</td></tr></table></div>'

    def doc_body(self, s, include_refs=True):
        """Everything under a member's declaration: its documentation, parameters, definition, uses"""
        d = self.doc_of(s)
        out = []
        text = d.text
        if not text and s.ctor_prop is not None and s.ctor_prop.doc:
            text = Doc(s.ctor_prop.doc).text
        if text:
            out.append(f'<div class="textblock">{self.link.block(text, s)}</div>')
            if s.doc_from_comment:
                out.append('<div class="fromcomment">From the comment above the declaration.</div>')
        else:
            out.append('<p class="undoc">Not documented.</p>')
        if d.receiver:
            out.append(f'<dl class="section"><dt>Receiver</dt><dd>{self.link.inline(" ".join(d.receiver), s)}</dd></dl>')
        elif s.receiver:
            out.append(f'<dl class="section"><dt>Receiver</dt><dd>Extension on <code>{self.link.type_html(s.receiver)}</code></dd></dl>')
        if s.params and s.kind in ("fun", "constructor"):
            rows = []
            for prm in s.params:
                pd = d.params.get(prm.name)
                desc = self.link.inline(" ".join(pd), s) if pd else (self.link.inline(Doc(prm.doc).text, s) if prm.doc else "")
                default = f'<br><span class="definition">default <code>{esc(shorten(prm.default, 120))}</code></span>' if prm.default else ""
                rows.append(f'<tr><td class="paramname">{esc(prm.name)}</td><td><code>{self.link.type_html(prm.type)}</code>{default}</td><td>{desc}</td></tr>')
            out.append(f'<dl class="params"><dt>Parameters</dt><dd><table class="params">{"".join(rows)}</table></dd></dl>')
        if d.returns:
            out.append(f'<dl class="section return"><dt>Returns</dt><dd>{self.link.inline(" ".join(d.returns), s)}</dd></dl>')
        elif s.kind == "fun" and s.type and s.type != "Unit":
            out.append(f'<dl class="section return"><dt>Returns</dt><dd><code>{self.link.type_html(s.type)}</code></dd></dl>')
        for name, val in d.throws:
            out.append(f'<dl class="section"><dt>Throws</dt><dd><code>{esc(name)}</code> {self.link.inline(" ".join(val), s)}</dd></dl>')
        for tag, val in d.other:
            cls = "note" if tag in ("note",) else "todo" if tag in ("todo",) else "section"
            out.append(f'<dl class="{cls}"><dt>{esc(tag.capitalize())}</dt><dd>{self.link.inline(" ".join(val), s)}</dd></dl>')
        if d.see:
            out.append('<dl class="see"><dt>See also</dt><dd>' + ", ".join(self.link.inline(x if x.startswith("[") else f"[{x}]", s) for x in d.see) + "</dd></dl>")
        if s.kind in ("val", "var") and s.initializer and len(s.initializer) > 160:
            out.append(f'<dl class="section"><dt>Initial value</dt><dd><div class="fragment"><pre>{highlight_snippet([s.initializer])}</pre></div></dd></dl>')
        f = s.file
        lines = f"line {s.line}" if s.end_line <= s.line else f"lines {s.line}–{s.end_line}"
        out.append(f'<p class="definition">Definition at {lines} of file <a class="el" href="{f.src_page}#l{s.line:05d}">{esc(f.name)}</a>.</p>')
        if include_refs:
            out.append(self.refs_html(s))
        return "".join(out)

    def refs_html(self, s):
        out = []
        refs = sorted((r for r in s.refs if not r.is_classlike), key=lambda r: (r.display_scope, r.name))
        types = sorted((r for r in s.refs if r.is_classlike), key=lambda r: r.name)
        by = sorted(s.referenced_by, key=lambda r: (r.display_scope, r.name))
        if types:
            out.append('<p class="definition">Uses types ' + ", ".join(f'<a class="el" href="{r.url}">{esc(r.name)}</a>' for r in types) + ".</p>")
        if refs:
            out.append('<p class="definition">References ' + ", ".join(self.ref_label(r) for r in refs) + ".</p>")
        if by:
            out.append('<p class="definition">Referenced by ' + ", ".join(self.ref_label(r) for r in by) + ".</p>")
        return "".join(out)

    def ref_label(self, r):
        scope = r.display_scope
        label = f"{scope}.{r.name}" if r.parent is not None else r.name
        if r.kind == "fun":
            label += "()"
        return f'<a class="el" href="{r.url}">{esc(label)}</a>'

    def member_doc(self, s):
        title = esc(s.name) + ("()" if s.kind in ("fun", "constructor") else "")
        if s.receiver:
            title = esc(s.receiver) + "." + title
        return (
            f'<a id="{s.anchor}"></a>\n<h2 class="memtitle"><span class="permalink"><a href="#{s.anchor}">◆</a></span>{title}</h2>\n'
            f'<div class="memitem">{self.proto(s)}<div class="memdoc">{self.doc_body(s)}</div></div>\n'
        )

    # -- graphs

    def graph(self, center_label, center_url, left, right, left_caption, right_caption, ext=()):
        """Three columns: what points at the center on the left, what it points at on the right"""
        def width(label):
            return max(60, int(len(label) * 6.7) + 18)

        left = sorted(left, key=lambda x: x[0].lower())
        right = sorted(right, key=lambda x: x[0].lower())
        h = 24
        gap = 8
        rows = max(len(left), len(right), 1)
        height = rows * (h + gap) + 30
        lw = max([width(l) for l, _ in left] or [0])
        rw = max([width(l) for l, _ in right] or [0])
        cw = width(center_label)
        col_gap = 70
        x_left = 10
        x_center = x_left + (lw + col_gap if left else 0)
        x_right = x_center + cw + col_gap
        total_w = x_right + (rw if right else 0) + 10
        if not right:
            total_w = x_center + cw + 10
        cy = height / 2 - h / 2 + 8
        parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{int(total_w)}" height="{int(height)}" role="img">']
        parts.append('<defs><marker id="ah" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path class="arrow" d="M0,0L10,5L0,10z"/></marker></defs>')

        def column(items, x, w_col):
            n = len(items)
            top = height / 2 - (n * (h + gap) - gap) / 2 + 8
            coords = []
            for idx, (label, url) in enumerate(items):
                y = top + idx * (h + gap)
                coords.append((x, y, width(label), label, url))
            return coords

        lc = column(left, x_left, lw)
        rc = column(right, x_right, rw)
        for x, y, w, label, url in lc:
            x0 = x_left + lw - w
            parts.append(f'<path class="edge" marker-end="url(#ah)" d="M{x0 + w},{y + h / 2} C{x0 + w + col_gap / 2},{y + h / 2} {x_center - col_gap / 2},{cy + h / 2} {x_center},{cy + h / 2}"/>')
        for x, y, w, label, url in rc:
            parts.append(f'<path class="edge" marker-end="url(#ah)" d="M{x_center + cw},{cy + h / 2} C{x_center + cw + col_gap / 2},{cy + h / 2} {x - col_gap / 2},{y + h / 2} {x},{y + h / 2}"/>')

        def node(x, y, w, label, url, cls="node"):
            inner = f'<rect x="{x}" y="{y}" width="{w}" height="{h}"/><text x="{x + w / 2}" y="{y + 16}" text-anchor="middle">{esc(label)}</text>'
            if url:
                inner = f'<a href="{esc(url)}"><title>{esc(label)}</title>{inner}</a>'
            return f'<g class="{cls}">{inner}</g>'

        for x, y, w, label, url in lc:
            parts.append(node(x_left + lw - w, y, w, label, url, "node ext" if label in ext else "node"))
        parts.append(node(x_center, cy, cw, center_label, center_url, "node self"))
        for x, y, w, label, url in rc:
            parts.append(node(x, y, w, label, url, "node ext" if label in ext else "node"))
        parts.append("</svg>")
        cap = []
        if left:
            cap.append(left_caption)
        if right:
            cap.append(right_caption)
        legend = f'<div class="legend">{" ".join(cap)}</div>' if cap else ""
        return f'<div class="graph dyncontent">{"".join(parts)}{legend}</div>'

    # -- pages

    def class_page(self, c):
        d = self.doc_of(c)
        members = [m for m in c.children if not (m.is_classlike and not m.is_companion)]
        companion = [ch for ch in c.children if ch.is_companion]
        comp_members = [m for comp in companion for m in comp.children if not (m.is_classlike and not m.is_companion)]
        nested = [m for m in c.children if m.is_classlike and not m.is_companion] + [m for comp in companion for m in comp.children if m.is_classlike]

        def pick(syms, kinds, vis_private):
            return [s for s in syms if s.kind in kinds and ((s.visibility == "private") == vis_private)]

        entries = [m for m in members if m.kind == "entry"]
        ctors = [m for m in members if m.kind == "constructor"]
        pub_funs = pick(members, ("fun",), False)
        priv_funs = pick(members, ("fun",), True)
        pub_props = pick(members, ("val", "var"), False)
        priv_props = pick(members, ("val", "var"), True)
        comp_funs = [m for m in comp_members if m.kind == "fun"]
        comp_props = [m for m in comp_members if m.kind in ("val", "var")]
        aliases = [m for m in members if m.kind == "typealias"]

        summary_links = []
        body = []
        brief = d.brief
        if brief:
            body.append(f'<p>{self.link.inline(brief, c)} <a href="#details">More...</a></p>')
        # Inheritance
        bases = []
        for b, call, delegate in c.supertypes:
            hit = self.link.symbol(b.split("<")[0], c)
            hit = hit if hit is not None and hit.is_classlike else None
            bases.append((b, hit.page if hit else None))
        derived = [s for s in self.p.classes if any(self.link.symbol(b.split("<")[0], s) is c for b, _, _ in s.supertypes)]
        if bases or derived:
            g = self.graph(
                c.name, None,
                [(x.name, x.page) for x in derived], bases,
                f"Left: what inherits from {c.name}.", f"Right: what {c.name} inherits from.",
                ext={b for b, url in bases if url is None},
            )
            body.append(f'<details class="graphbox" open><summary>Inheritance diagram for {esc(c.name)}</summary>{g}</details>')
        if nested:
            summary_links.append('<a href="#nested-classes">Nested Classes</a>')
            body.append(self.member_table("Nested Classes", "nested-classes", nested))
        if entries:
            summary_links.append('<a href="#enum-values">Enum Values</a>')
            body.append(self.member_table("Enum Values", "enum-values", entries))
        if ctors:
            body.append(self.member_table("Secondary Constructors", "constructors", ctors))
        if pub_funs:
            summary_links.append('<a href="#pub-methods">Public Member Functions</a>')
            body.append(self.member_table("Public Member Functions", "pub-methods", pub_funs))
        if pub_props:
            summary_links.append('<a href="#pub-attribs">Properties</a>')
            body.append(self.member_table("Properties", "pub-attribs", pub_props))
        if aliases:
            body.append(self.member_table("Type Aliases", "typealiases", aliases))
        if comp_funs or comp_props:
            summary_links.append('<a href="#companion">Companion Object</a>')
            body.append(self.member_table("Companion Object Members", "companion", comp_funs + comp_props))
        if priv_funs:
            summary_links.append('<a href="#pri-methods">Private Member Functions</a>')
            body.append(self.member_table("Private Member Functions", "pri-methods", priv_funs))
        if priv_props:
            summary_links.append('<a href="#pri-attribs">Private Properties</a>')
            body.append(self.member_table("Private Properties", "pri-attribs", priv_props))

        # Detailed description
        body.append('<a id="details" name="details"></a><h2 class="groupheader">Detailed Description</h2>')
        decl = esc(c.signature) if c.signature else esc(c.keyword + " " + c.name)
        if c.params:
            # One constructor parameter per line, the way the code itself is written
            head = " ".join([m for m in c.modifiers if m != "public"] + [c.kind, c.name]) + esc(c.type_params)
            plist = ",\n".join(
                "    " + " ".join(prm.annotations + prm.modifiers + ([prm.prop] if prm.prop else []) + [prm.name + (": " + prm.type if prm.type else "")]) + (" = " + prm.default if prm.default else "")
                for prm in c.params
            )
            sup = (" : " + ", ".join(b + call + (" by " + d if d else "") for b, call, d in c.supertypes)) if c.supertypes else ""
            decl = esc(f"{head}(\n{plist},\n){sup}")
        anns = " ".join(esc(a) for a in c.annotations)
        body.append(f'<div class="fragment"><pre>{(anns + chr(10)) if anns else ""}{decl}</pre></div>')
        if d.text:
            body.append(f'<div class="textblock">{self.link.block(d.text, c)}</div>')
            if c.doc_from_comment:
                body.append('<div class="fromcomment">From the comment above the declaration.</div>')
        else:
            body.append('<p class="undoc">Not documented.</p>')
        for tag, val in d.other:
            body.append(f'<dl class="section"><dt>{esc(tag.capitalize())}</dt><dd>{self.link.inline(" ".join(val), c)}</dd></dl>')
        if d.see:
            body.append('<dl class="see"><dt>See also</dt><dd>' + ", ".join(self.link.inline(x if x.startswith("[") else f"[{x}]", c) for x in d.see) + "</dd></dl>")
        if c.supertypes:
            items = []
            for b, call, delegate in c.supertypes:
                t = self.link.type_html(b)
                if delegate:
                    t += f" by <code>{esc(delegate)}</code>"
                items.append(f"<li>{t}</li>")
            body.append(f'<dl class="section"><dt>Inherits</dt><dd><ul>{"".join(items)}</ul></dd></dl>')
        if derived:
            body.append('<dl class="section"><dt>Inherited by</dt><dd>' + ", ".join(f'<a class="el" href="{x.page}">{esc(x.name)}</a>' for x in derived) + "</dd></dl>")
        f = c.file
        lines = f"lines {c.line}–{c.end_line}" if c.end_line > c.line else f"line {c.line}"
        body.append(f'<p class="definition">Definition at {lines} of file <a class="el" href="{f.src_page}#l{c.line:05d}">{esc(f.name)}</a>.</p>')
        body.append(self.refs_html(c))

        # Constructor
        if c.params:
            ctor = Symbol("constructor", "constructor", c.file)
            ctor.params = c.params
            ctor.parent = c
            ctor.line = c.line
            ctor.end_line = c.line
            ctor.anchor = "primary-constructor"
            ctor.page = c.page
            ctor.modifiers = []
            prow = []
            for prm in c.params:
                pd = d.params.get(prm.name) or d.properties.get(prm.name)
                desc = self.link.inline(" ".join(pd), c) if pd else (self.link.block(Doc(prm.doc).text, c) if prm.doc else "")
                kind = prm.prop or "param"
                default = f'<code>{esc(shorten(prm.default, 80))}</code>' if prm.default else ""
                prow.append(f'<tr><td class="paramname"><code>{esc(prm.name)}</code></td><td><code>{esc(kind)}</code></td><td><code>{self.link.type_html(prm.type)}</code></td><td>{default}</td><td>{desc}</td></tr>')
            body.append('<h2 class="groupheader">Constructor Documentation</h2>')
            body.append(
                f'<a id="primary-constructor"></a>\n<h2 class="memtitle"><span class="permalink"><a href="#primary-constructor">◆</a></span>{esc(c.name)}()</h2>\n'
                f'<div class="memitem">{self.proto(ctor)}<div class="memdoc">'
                + (f'<div class="textblock">{self.link.inline(" ".join(d.constructor), c)}</div>' if d.constructor else "")
                + f'<table class="fieldtable"><tr><th>Name</th><th>Kind</th><th>Type</th><th>Default</th><th>Description</th></tr>{"".join(prow)}</table>'
                + f'<p class="definition">Definition at line {c.line} of file <a class="el" href="{f.src_page}#l{c.line:05d}">{esc(f.name)}</a>.</p></div></div>'
            )
        if entries:
            rows = "".join(
                f'<tr><td class="fieldname"><a id="{e.anchor}"></a><code>{esc(e.signature)}</code></td><td>{" ".join(esc(a) for a in e.annotations)}</td><td>{self.link.inline(self.doc_of(e).text, e)}</td></tr>'
                for e in entries
            )
            body.append(f'<h2 class="groupheader">Enumeration Values</h2><table class="fieldtable"><tr><th>Value</th><th>Annotations</th><th>Description</th></tr>{rows}</table>')
        for title, group in (("Secondary Constructor Documentation", ctors), ("Member Function Documentation", pub_funs + priv_funs), ("Property Documentation", pub_props + priv_props), ("Companion Object Member Documentation", comp_funs + comp_props), ("Type Alias Documentation", aliases)):
            if group:
                body.append(f'<h2 class="groupheader">{title}</h2>')
                body.extend(self.member_doc(m) for m in group)
        body.append(f'<hr><p>The documentation for this {esc(c.keyword)} was generated from the following file:</p><ul><li><a class="el" href="{f.page}">{esc(f.rel)}</a></li></ul>')

        title = f"{c.name} {'Interface' if c.kind == 'interface' else 'Object' if c.kind == 'object' else 'Enum' if 'enum' in c.modifiers else 'Class'} Reference"
        chain = []
        s = c.parent
        while s:
            chain.append(s)
            s = s.parent
        navpath = [(seg, None) for seg in []]
        navpath = [(c.file.package or "(default)", "pkg_" + safe(c.file.package) + ".html")]
        navpath += [(x.name, x.page) for x in reversed(chain)]
        navpath.append((c.name, c.page))
        labels = "".join(f'<span class="mlabel">{esc(l)}</span>' for l in [c.keyword] + [l for l in self.labels(c) if l not in c.keyword.split()])
        self.write(c.page, title, "\n".join(body), tab="classes", summary=" | ".join(summary_links), navpath=navpath, header_extra=f'<div class="ingroups">{labels} in <a href="{f.page}">{esc(f.name)}</a></div>')

    def file_page(self, f):
        tops = f.symbols
        classes = [s for s in tops if s.is_classlike]
        nested_classes = [s for s in f.all_symbols if s.is_classlike and s.parent is not None and not s.is_companion]
        funs = [s for s in tops if s.kind == "fun"]
        props = [s for s in tops if s.kind in ("val", "var")]
        aliases = [s for s in tops if s.kind == "typealias"]
        body = []
        body.append(f'<p><a href="{f.src_page}">Go to the source code of this file.</a></p>')
        stats = [
            (f.line_count, "lines"),
            (len([s for s in f.all_symbols if s.is_classlike]), "classes and objects"),
            (len([s for s in f.all_symbols if s.kind == "fun"]), "functions"),
            (len([s for s in f.all_symbols if s.is_composable]), "composables"),
        ]
        body.append('<div class="statgrid">' + "".join(f"<div><b>{v}</b><span>{esc(l)}</span></div>" for v, l in stats) + "</div>")
        if f.imports:
            items = []
            for path, alias, line in f.imports:
                hit = self.p.by_qualified.get(path)
                label = esc(path) + (f" as {esc(alias)}" if alias else "")
                if hit:
                    label = f'<a class="el" href="{hit.url}">{label}</a>'
                items.append(f'<code><span class="keyword">import</span> {label}</code>')
            body.append('<div class="textblock"><code><span class="keyword">package</span> ' + f'<a class="el" href="pkg_{safe(f.package)}.html">{esc(f.package)}</a></code></div>')
            # Folded away like Doxygen's include list, with the project's own imports counted
            own = sum(1 for path, _, _ in f.imports if path in self.p.by_qualified)
            body.append(f'<details class="graphbox"><summary>{len(f.imports)} imports, {own} from this project</summary><div class="textblock">' + "<br>".join(items) + "</div></details>")
        if f.file_annotations:
            body.append('<p class="annotations">' + " ".join(esc(a) for a in f.file_annotations) + "</p>")
        g = self.graph(
            f.name, None,
            [(x.name, x.page) for x in f.used_by], [(x.name, x.page) for x in f.deps],
            f"Left: the files that use something from {f.name}.", f"Right: the files whose code {f.name} uses.",
        )
        if f.deps or f.used_by:
            body.append(f'<details class="graphbox" open><summary>Dependency graph for {esc(f.name)}</summary>{g}</details>')
        body.append(self.member_table("Classes", "nested-classes", classes + nested_classes))
        body.append(self.member_table("Type Aliases", "typealiases", aliases))
        pub = [s for s in funs if s.visibility != "private"]
        pri = [s for s in funs if s.visibility == "private"]
        body.append(self.member_table("Functions", "func-members", pub))
        body.append(self.member_table("Private Functions", "pri-func-members", pri))
        body.append(self.member_table("Properties", "var-members", [s for s in props if s.visibility != "private"]))
        body.append(self.member_table("Private Properties", "pri-var-members", [s for s in props if s.visibility == "private"]))
        for title, group in (("Function Documentation", funs), ("Property Documentation", props), ("Type Alias Documentation", aliases)):
            if group:
                body.append(f'<h2 class="groupheader">{title}</h2>')
                body.extend(self.member_doc(m) for m in group)
        parts = f.rel.split("/")
        navpath = [(p, None) for p in parts[:-1]] + [(f.name, f.page)]
        summary = " | ".join(x for x, cond in (('<a href="#nested-classes">Classes</a>', classes), ('<a href="#func-members">Functions</a>', pub), ('<a href="#var-members">Properties</a>', [s for s in props if s.visibility != "private"])) if cond)
        self.write(f.page, f"{f.name} File Reference", "\n".join(body), tab="files", navpath=navpath, summary=summary, header_extra=f'<div class="ingroups">{esc(f.rel)}{" · unit tests" if f.is_test else ""}</div>')

    def source_page(self, f):
        hl = render_highlight(f.src, f.tokens, f.comments, f.link_at, f.decl_at, "")
        lines = hl.split("\n")
        # A span that runs over several lines (a comment, a string) is closed and opened again on each
        out = []
        open_tags = []
        for idx, line in enumerate(lines, start=1):
            prefix = "".join(open_tags)
            for m in re.finditer(r"<(/?)(span|a)\b[^>]*>", line):
                if m.group(1):
                    if open_tags:
                        open_tags.pop()
                else:
                    open_tags.append(m.group(0))
            suffix = "".join("</span>" if t.startswith("<span") else "</a>" for t in reversed(open_tags))
            out.append(f'<div class="line" id="l{idx:05d}"><span class="lineno"><a href="#l{idx:05d}">{idx}</a></span>{prefix}{line}{suffix}</div>')
        body = f'<p><a href="{f.page}">Go to the documentation of this file.</a> · <a href="{REPO_URL}/blob/main/{f.rel}">On GitHub</a></p><div class="srccode">{"".join(out)}</div>'
        parts = f.rel.split("/")
        navpath = [(p, None) for p in parts[:-1]] + [(f.name, f.page)]
        self.write(f.src_page, f.name, body, tab="files", navpath=navpath)

    def package_page(self, pkg, files):
        syms = [s for f in files for s in f.symbols]
        classes = sorted([s for f in files for s in f.all_symbols if s.is_classlike and not s.is_companion], key=lambda s: s.name.lower())
        funs = sorted([s for s in syms if s.kind == "fun" and s.visibility != "private"], key=lambda s: s.name.lower())
        props = sorted([s for s in syms if s.kind in ("val", "var") and s.visibility != "private"], key=lambda s: s.name.lower())
        body = []
        deps = defaultdict(int)
        users = defaultdict(int)
        for f in files:
            for d in f.deps:
                if d.package != pkg:
                    deps[d.package] += 1
            for u in f.used_by:
                if u.package != pkg:
                    users[u.package] += 1
        short = lambda p: p.replace("com.kastoriadevs.astikoktel.app", "app") or "(default)"
        if deps or users:
            g = self.graph(short(pkg), None, [(short(p), "pkg_" + safe(p) + ".html") for p in users], [(short(p), "pkg_" + safe(p) + ".html") for p in deps],
                           "Left: packages that use this one.", "Right: packages this one uses.")
            body.append(f'<details class="graphbox" open><summary>Package dependency graph</summary>{g}</details>')
        rows = "".join(f'<tr><td class="entry"><span class="icon f">K</span><a class="el" href="{f.page}">{esc(f.name)}</a></td><td class="desc">{f.line_count} lines{", tests" if f.is_test else ""}</td></tr>' for f in sorted(files, key=lambda f: f.name))
        body.append(f'<h2 class="groupheader">Files</h2><table class="directory">{rows}</table>')
        body.append(self.member_table("Classes", "nested-classes", classes))
        body.append(self.member_table("Functions", "func-members", funs))
        body.append(self.member_table("Properties", "var-members", props))
        self.write("pkg_" + safe(pkg) + ".html", f"{pkg or '(default)'} Package Reference", "\n".join(body), tab="packages", navpath=[("Packages", "packages.html"), (pkg, None)])

    # -- index pages

    def index_page(self):
        readme = (ROOT / "README.md").read_text(encoding="utf-8")
        files = self.p.files
        allsyms = self.p.symbols
        documentable = [s for s in allsyms if s.visibility != "private" and s.kind != "entry"]
        documented = [s for s in documentable if s.doc or (s.ctor_prop is not None and s.ctor_prop.doc)]
        cov = 100 * len(documented) / max(1, len(documentable))
        stats = [
            (len(files), "Kotlin files"),
            (sum(f.line_count for f in files), "lines of Kotlin"),
            (len(self.p.packages), "packages"),
            (len([s for s in allsyms if s.is_classlike]), "classes, objects, interfaces"),
            (len([s for s in allsyms if s.kind == "fun"]), "functions"),
            (len([s for s in allsyms if s.is_composable]), "composables"),
            (len([s for s in allsyms if s.is_test]), "unit tests"),
            (f"{cov:.0f}%", "of public declarations documented"),
        ]
        grid = '<div class="statgrid">' + "".join(f"<div><b>{v}</b><span>{esc(l)}</span></div>" for v, l in stats) + "</div>"
        intro = (
            "<p>This is the code documentation of the app, made from its source by <code>scripts/gen_docs.py</code> in the way Doxygen "
            "would if it read Kotlin. Start from the <a href=\"annotated.html\">class list</a>, the <a href=\"files.html\">files</a>, "
            "or the <a href=\"topics.html\">topics</a> that group the screens, view models, data models and tests; or search for any name above.</p>"
        )
        body = intro + grid + f'<h2 class="groupheader">README</h2><div class="textblock">{self.link.block(readme)}</div>'
        self.write("index.html", "Kastoria City Buses Documentation", body, tab="main")

    def packages_index(self):
        rows = []
        for pkg in sorted(self.p.packages):
            files = self.p.packages[pkg]
            n_cls = len([s for f in files for s in f.all_symbols if s.is_classlike and not s.is_companion])
            n_fun = len([s for f in files for s in f.all_symbols if s.kind == "fun"])
            tests = all(f.is_test for f in files)
            desc = f"{len(files)} files, {n_cls} classes, {n_fun} functions" + (" (unit tests)" if tests else "")
            rows.append(f'<tr><td class="entry"><span class="icon">N</span><a class="el" href="pkg_{safe(pkg)}.html">{esc(pkg or "(default)")}</a></td><td class="desc">{desc}</td></tr>')
        body = f'<p>Here are the packages with a brief description:</p><table class="directory">{"".join(rows)}</table>'
        self.write("packages.html", "Package List", body, tab="packages", subtab="packages.html")

    def annotated(self):
        rows = []

        def add(c, depth):
            pad = "&#160;" * (depth * 4)
            rows.append(f'<tr><td class="entry">{pad}{self.kind_icon(c)}<a class="el" href="{c.page}">{esc(c.name)}</a></td><td class="desc">{self.link.inline(self.brief(c), c)}</td></tr>')
            for ch in c.children:
                if ch.is_classlike and not ch.is_companion:
                    add(ch, depth + 1)
                if ch.is_companion:
                    for cc in ch.children:
                        if cc.is_classlike:
                            add(cc, depth + 1)

        for pkg in sorted(self.p.packages):
            tops = sorted([s for f in self.p.packages[pkg] for s in f.symbols if s.is_classlike], key=lambda s: s.name.lower())
            if not tops:
                continue
            rows.append(f'<tr><td class="entry"><span class="icon">N</span><a class="el" href="pkg_{safe(pkg)}.html">{esc(pkg)}</a></td><td class="desc"></td></tr>')
            for c in tops:
                add(c, 1)
        legend = '<p class="legend">' + " ".join(f'{i} {l}' for i, l in (('<span class="icon">C</span>', "class"), ('<span class="icon d">D</span>', "data class / object"), ('<span class="icon e">E</span>', "enum"), ('<span class="icon i">I</span>', "interface"), ('<span class="icon o">O</span>', "object"))) + "</p>"
        body = f'<p>Here are the classes, interfaces, objects and enums with brief descriptions:</p>{legend}<table class="directory">{"".join(rows)}</table>'
        self.write("annotated.html", "Class List", body, tab="classes", subtab="annotated.html")

    def class_index(self):
        classes = sorted([c for c in self.p.classes if not c.is_companion], key=lambda c: c.name.lower())
        letters = sorted({c.name[0].upper() for c in classes})
        body = ['<div class="qindex">' + " | ".join(f'<a href="#letter_{l}">{l}</a>' for l in letters) + "</div><div class=\"classindex\">"]
        for l in letters:
            body.append(f'<div class="ah" id="letter_{l}">{l}</div>')
            for c in [c for c in classes if c.name[0].upper() == l]:
                scope = c.file.package.replace("com.kastoriadevs.astikoktel.app", "app")
                if c.parent is not None:
                    scope += "." + c.parent.name
                body.append(f'<div class="item"><a class="el" href="{c.page}">{esc(c.name)}</a> <span class="scope">({esc(scope)})</span></div>')
        body.append("</div>")
        self.write("classes.html", "Class Index", "".join(body), tab="classes", subtab="classes.html")

    def hierarchy(self):
        children = defaultdict(list)
        external_roots = defaultdict(list)
        roots = []
        for c in self.p.classes:
            if c.is_companion:
                continue
            internal_parent = False
            for b, _, _ in c.supertypes:
                hit = self.link.symbol(b.split("<")[0], c)
                if hit is not None and hit.is_classlike:
                    children[hit].append(c)
                    internal_parent = True
                else:
                    external_roots[b.split("<")[0].split(".")[-1]].append(c)
            if not c.supertypes:
                roots.append(c)

        def tree(c, seen):
            if c in seen:
                return ""
            seen = seen | {c}
            kids = sorted(children.get(c, []), key=lambda x: x.name.lower())
            inner = "".join(tree(k, seen) for k in kids)
            return f'<li>{self.kind_icon(c)}<a class="el" href="{c.page}">{esc(c.name)}</a>' + (f"<ul>{inner}</ul>" if inner else "") + "</li>"

        items = []
        for c in sorted(roots, key=lambda x: x.name.lower()):
            items.append(tree(c, set()))
        for ext in sorted(external_roots, key=str.lower):
            kids = "".join(tree(k, set()) for k in sorted(external_roots[ext], key=lambda x: x.name.lower()))
            items.append(f'<li><span class="ext">{esc(ext)}</span> <span class="undoc">(outside this project)</span><ul>{kids}</ul></li>')
        body = f'<p>This inheritance list is sorted roughly, but not completely, alphabetically. Names in grey come from libraries.</p><div class="hierarchy"><ul>{"".join(items)}</ul></div>'
        self.write("hierarchy.html", "Class Hierarchy", body, tab="classes", subtab="hierarchy.html")

    def members_page(self, name, title, syms, intro, tab, subtab, member_tabs):
        syms = sorted(syms, key=lambda s: (s.name.lower().strip("`"), s.display_scope))
        letters = []
        for s in syms:
            l = s.name.strip("`")[:1].lower()
            if l not in letters:
                letters.append(l)
        body = ['<div class="qindex">' + " | ".join(f'<a href="#index_{safe(l) or "_"}">{esc(l)}</a>' for l in letters) + "</div>", f"<p>{intro}</p>"]
        current = None
        items = []
        for s in syms:
            l = s.name.strip("`")[:1].lower()
            if l != current:
                if items:
                    body.append('<ul class="memberlist">' + "".join(items) + "</ul>")
                    items = []
                body.append(f'<div class="letter" id="index_{safe(l) or "_"}">- {esc(l)} -</div>')
                current = l
            suffix = "()" if s.kind == "fun" else ""
            where = s.display_scope
            target = s.parent.page if s.parent is not None else s.file.page
            items.append(f'<li>{esc(s.name)}{suffix} : <a class="el" href="{s.url}">{esc(where)}</a></li>')
        if items:
            body.append('<ul class="memberlist">' + "".join(items) + "</ul>")
        self.write(name, title, "".join(body), tab=tab, subtab=subtab, member_tabs=member_tabs)

    def files_index(self):
        tree = {}
        for f in self.p.files:
            node = tree
            parts = f.rel.split("/")
            for part in parts[:-1]:
                node = node.setdefault(part + "/", {})
            node[parts[-1]] = f
        rows = []

        def walk(node, depth, prefix):
            # Folders with a single folder inside are shown as one, like com/kastoriadevs/astikoktel/app
            for key in sorted(node, key=lambda k: (not k.endswith("/"), k.lower())):
                val = node[key]
                pad = "&#160;" * (depth * 4)
                if isinstance(val, dict):
                    label = key
                    while len(val) == 1 and isinstance(next(iter(val.values())), dict):
                        k2 = next(iter(val))
                        label += k2
                        val = val[k2]
                    rows.append(f'<tr><td class="entry">{pad}<span class="icon p">▤</span>{esc(label.rstrip("/"))}</td><td class="desc"></td></tr>')
                    walk(val, depth + 1, prefix + label)
                else:
                    f = val
                    names = [s.name for s in f.symbols if s.visibility != "private"][:6]
                    desc = ", ".join(names) + ("…" if len([s for s in f.symbols if s.visibility != "private"]) > 6 else "")
                    rows.append(f'<tr><td class="entry">{pad}<span class="icon f">K</span><a class="el" href="{f.page}">{esc(f.name)}</a> <a href="{f.src_page}" title="Source">[code]</a></td><td class="desc">{f.line_count} lines · {esc(desc)}</td></tr>')

        walk(tree, 0, "")
        body = f'<p>Here is a list of all files with the main things declared in each:</p><table class="directory">{"".join(rows)}</table>'
        self.write("files.html", "File List", body, tab="files", subtab="files.html")

    def topics(self):
        groups = []
        composables = [s for s in self.p.symbols if s.is_composable]
        groups.append(("composables", "Composables", "The Jetpack Compose functions that draw the app, by package. Screens are the ones that end in Screen; the rest are the pieces they're built from.", composables, True))
        vms = [c for c in self.p.classes if any("ViewModel" in b for b, _, _ in c.supertypes)]
        groups.append(("viewmodels", "View models", "The classes that hold each screen's state and survive configuration changes.", vms, False))
        models = [c for c in self.p.classes if any(a.startswith("@Serializable") for a in c.annotations)]
        groups.append(("models", "Serializable models", "The data classes and enums read from the JSON files in assets and from the announcements feed, with kotlinx.serialization.", models, False))
        objects = [c for c in self.p.classes if c.kind == "object" and not c.is_companion and c.parent is None]
        groups.append(("objects", "Singletons and helpers", "Top-level objects: one instance for the whole app.", objects, False))
        tests = [s for s in self.p.symbols if s.is_test]
        groups.append(("tests", "Unit tests", "The tests under app/src/test, run on every pull request by the Build workflow.", tests, True))
        rows = []
        for key, title, desc, syms, _ in groups:
            rows.append(f'<tr><td class="entry"><span class="icon">T</span><a class="el" href="topic_{key}.html">{esc(title)}</a></td><td class="desc">{esc(desc)} ({len(syms)})</td></tr>')
            body = [f"<p>{esc(desc)}</p>"]
            by_pkg = defaultdict(list)
            for s in syms:
                by_pkg[s.file.package if key != "tests" else s.display_scope].append(s)
            for pkg in sorted(by_pkg):
                label = pkg.replace("com.kastoriadevs.astikoktel.app", "app")
                body.append(self.member_table(label, safe(pkg), sorted(by_pkg[pkg], key=lambda s: s.name.lower())))
            self.write(f"topic_{key}.html", title, "\n".join(body), tab="topics", navpath=[("Topics", "topics.html"), (title, None)])
        self.write("topics.html", "Topics", f'<p>Here is a list of all topics with brief descriptions:</p><table class="directory">{"".join(rows)}</table>', tab="topics")

    def related_pages(self):
        pages = [("todo.html", "Todo List", "Every TODO, FIXME and HACK comment in the code"), ("data.html", "Data files", "The JSON files in assets that hold the network, and the classes that read them"), ("undocumented.html", "Undocumented", "Public declarations that still have no documentation")]
        rows = "".join(f'<tr><td class="entry"><span class="icon">P</span><a class="el" href="{u}">{esc(t)}</a></td><td class="desc">{esc(d)}</td></tr>' for u, t, d in pages)
        self.write("pages.html", "Related Pages", f'<p>Here is a list of all related documentation pages:</p><table class="directory">{rows}</table>', tab="pages")
        # Todo list
        items = []
        for f in self.p.files:
            for c in f.comments:
                for m in re.finditer(r"\b(TODO|FIXME|HACK|XXX)\b[:\s]*(.*)", c.text):
                    line = c.line + c.text[: m.start()].count("\n")
                    items.append(f'<dt><a class="el" href="{f.src_page}#l{line:05d}">{esc(f.name)}:{line}</a> <span class="mlabel">{m.group(1)}</span></dt><dd>{esc(m.group(2).strip(" */"))}</dd>')
        body = "<dl class=\"todo\">" + "".join(items) + "</dl>" if items else '<p>There are no TODO, FIXME or HACK comments in the code.</p>'
        self.write("todo.html", "Todo List", body, tab="pages", navpath=[("Related Pages", "pages.html"), ("Todo List", None)])
        # Data files
        body = ['<p>The whole network, lines, stops and timetables, ships with the app as JSON in <code>app/src/main/assets</code>, '
                'read once at start-up into <a class="el" href="' + (self.p.by_name.get("TransitData", [None])[0].page if self.p.by_name.get("TransitData") else "#") + '">TransitData</a>.</p>']
        model_for = {"stops.json": "Stop", "lines.json": "Line", "timetables.json": "Timetable", "meta.json": "Meta"}
        for path in sorted(ASSETS.glob("*.json")):
            try:
                data = json.loads(path.read_text(encoding="utf-8"))
            except Exception:
                continue
            size = path.stat().st_size
            if isinstance(data, list):
                shape = f"An array of {len(data)} objects"
                keys = sorted({k for x in data if isinstance(x, dict) for k in x})
            elif isinstance(data, dict):
                shape = "An object"
                keys = list(data)
            else:
                shape, keys = type(data).__name__, []
            model = model_for.get(path.name)
            hit = [c for c in self.p.by_name.get(model, []) if c.is_classlike] if model else []
            link = f' read as <a class="el" href="{hit[0].page}">{esc(model)}</a>' if hit else ""
            sample = ""
            if isinstance(data, list) and data:
                sample = json.dumps(data[0], ensure_ascii=False, indent=2)
                if len(sample) > 1400:
                    sample = sample[:1400] + "\n  …"
            elif isinstance(data, dict):
                sample = json.dumps(data, ensure_ascii=False, indent=2)[:1400]
            body.append(f'<h2 class="groupheader">{esc(path.name)}</h2><p>{shape}, {size / 1024:.1f} KB{link}. Fields: ' + ", ".join(f"<code>{esc(k)}</code>" for k in keys) + ".</p>")
            if sample:
                body.append(f'<details class="graphbox"><summary>First entry</summary><div class="fragment"><pre>{esc(sample)}</pre></div></details>')
        self.write("data.html", "Data files", "".join(body), tab="pages", navpath=[("Related Pages", "pages.html"), ("Data files", None)])
        # Undocumented
        undoc = [s for s in self.p.symbols if s.visibility != "private" and s.kind != "entry" and not s.doc and not (s.ctor_prop is not None and s.ctor_prop.doc) and not s.is_test and not s.is_companion and not ("override" in s.modifiers)]
        by_file = defaultdict(list)
        for s in undoc:
            by_file[s.file].append(s)
        body = [f"<p>{len(undoc)} public declarations have neither KDoc nor a comment right above them. Overrides and tests are left out.</p>"]
        for f in sorted(by_file, key=lambda f: f.rel):
            body.append(f'<h2 class="groupheader"><a href="{f.page}">{esc(f.rel)}</a></h2><ul class="memberlist">' + "".join(
                f'<li>{esc(s.keyword)} <a class="el" href="{s.url}">{esc(s.display_scope + "." + s.name if s.parent else s.name)}</a> <span class="scope">line {s.line}</span></li>' for s in by_file[f]) + "</ul>")
        self.write("undocumented.html", "Undocumented", "".join(body), tab="pages", navpath=[("Related Pages", "pages.html"), ("Undocumented", None)])

    # -- navigation and search

    def navtree(self):
        def class_node(c):
            kids = [class_node(ch) for ch in c.children if ch.is_classlike and not ch.is_companion]
            for comp in [ch for ch in c.children if ch.is_companion]:
                kids += [class_node(x) for x in comp.children if x.is_classlike]
            return [c.name, c.page, kids]

        pkgs = []
        for pkg in sorted(self.p.packages):
            files = self.p.packages[pkg]
            classes = sorted([s for f in files for s in f.symbols if s.is_classlike], key=lambda s: s.name.lower())
            pkgs.append([pkg.replace("com.kastoriadevs.astikoktel.app", "app") or "(default)", "pkg_" + safe(pkg) + ".html", [class_node(c) for c in classes]])
        file_nodes = [[f.name, f.page, []] for f in sorted(self.p.files, key=lambda f: f.name.lower())]
        tree = [
            ["Main Page", "index.html", []],
            ["Topics", "topics.html", [["Composables", "topic_composables.html", []], ["View models", "topic_viewmodels.html", []], ["Serializable models", "topic_models.html", []], ["Singletons and helpers", "topic_objects.html", []], ["Unit tests", "topic_tests.html", []]]],
            ["Packages", "packages.html", pkgs + [["Package Members", "namespacemembers.html", []]]],
            ["Classes", "annotated.html", [["Class List", "annotated.html", []], ["Class Index", "classes.html", []], ["Class Hierarchy", "hierarchy.html", []], ["Class Members", "functions.html", []]]],
            ["Files", "files.html", [["File List", "files.html", file_nodes], ["File Members", "globals.html", []]]],
            ["Related Pages", "pages.html", [["Todo List", "todo.html", []], ["Data files", "data.html", []], ["Undocumented", "undocumented.html", []]]],
        ]
        (self.out / "navtreedata.js").write_text("window.NAVTREE = " + json.dumps(tree, ensure_ascii=False) + ";\n", encoding="utf-8", newline="\n")
        data = []
        for s in self.p.symbols:
            if s.is_companion:
                continue
            kind = s.keyword if s.kind != "fun" else ("composable" if s.is_composable else "test" if s.is_test else "function")
            data.append([s.name.strip("`"), kind, s.display_scope, s.url])
        for f in self.p.files:
            data.append([f.name, "file", f.rel.rsplit("/", 1)[0], f.page])
        for pkg in self.p.packages:
            data.append([pkg, "package", "", "pkg_" + safe(pkg) + ".html"])
        (self.out / "searchdata.js").write_text("window.SEARCHDATA = " + json.dumps(data, ensure_ascii=False) + ";\n", encoding="utf-8", newline="\n")

    # -- all

    def build(self):
        # Emptied rather than deleted, so a server or editor that has the folder open doesn't stop it
        self.out.mkdir(parents=True, exist_ok=True)
        for old in self.out.iterdir():
            shutil.rmtree(old) if old.is_dir() else old.unlink()
        (self.out / "doxygen.css").write_text(CSS.lstrip(), encoding="utf-8", newline="\n")
        (self.out / "docs.js").write_text(JS.lstrip(), encoding="utf-8", newline="\n")
        self.navtree()
        self.index_page()
        self.packages_index()
        self.annotated()
        self.class_index()
        self.hierarchy()
        self.files_index()
        self.topics()
        self.related_pages()
        members = [s for s in self.p.symbols if s.parent is not None and not s.is_classlike]
        intro = "Here is a list of all class members with links to the classes they belong to:"
        self.members_page("functions.html", "Class Members", members, intro, "classes", "functions.html", "functions")
        self.members_page("functions_func.html", "Class Members - Functions", [s for s in members if s.kind == "fun"], intro, "classes", "functions.html", "functions")
        self.members_page("functions_vars.html", "Class Members - Properties", [s for s in members if s.kind in ("val", "var")], intro, "classes", "functions.html", "functions")
        self.members_page("functions_eval.html", "Class Members - Enum values", [s for s in members if s.kind == "entry"], intro, "classes", "functions.html", "functions")
        globals_ = [s for s in self.p.symbols if s.parent is None and not s.is_classlike]
        gintro = "Here is a list of all top-level functions, properties and type aliases with links to the files they are declared in:"
        self.members_page("globals.html", "File Members", globals_, gintro, "files", "globals.html", "globals")
        self.members_page("globals_func.html", "File Members - Functions", [s for s in globals_ if s.kind == "fun"], gintro, "files", "globals.html", "globals")
        self.members_page("globals_vars.html", "File Members - Properties", [s for s in globals_ if s.kind in ("val", "var")], gintro, "files", "globals.html", "globals")
        self.members_page("globals_type.html", "File Members - Type aliases", [s for s in globals_ if s.kind == "typealias"], gintro, "files", "globals.html", "globals")
        self.members_page("namespacemembers.html", "Package Members", [s for s in globals_ if s.visibility != "private"], "Here is a list of all public top-level declarations, by package and file:", "packages", "namespacemembers.html", None)
        for pkg, files in self.p.packages.items():
            self.package_page(pkg, files)
        for c in self.p.classes:
            if not c.is_companion:
                self.class_page(c)
        for f in self.p.files:
            self.file_page(f)
            self.source_page(f)


def MEMBER_TABS_FOR(key):
    return MEMBER_SUBTABS[key]


def shorten(s, n):
    s = " ".join((s or "").split())
    return s if len(s) <= n else s[: n - 1] + "…"


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--out", type=Path, default=OUT, help="where to write the site (default docs/html)")
    args = ap.parse_args()
    project = Project()
    site = Site(project, args.out.resolve())
    site.build()
    n_sym = len(project.symbols)
    print(f"{len(project.files)} files, {len(project.classes)} classes, {n_sym} declarations -> {site.pages_written} pages in {args.out}")


if __name__ == "__main__":
    sys.exit(main())
