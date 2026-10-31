#!/usr/bin/env python3
"""Write src/runtime/module-map.json and src/runtime/symbol-index.json from the runtime segments, in the order
src/index.template.html includes them, which is the order the page runs them in. check_source.py fails while either is stale.

A segment's statements are the top-level statements that start in it (some of the original segments begin part-way through
one). Its symbols are the names declared or assigned at the top level (function, class, const/let/var, `name =`, `a.b.c =`)
or by a `window.name =` anywhere, which is how a segment wrapped in a function exports; each is listed under the segment where
the name ends. Tags are kept by hand in module-map.json; a new segment starts with the words of its file name.
    python3 tools/gen_module_map.py
"""
from bisect import bisect_right
from pathlib import Path
import json, re

root = Path(__file__).resolve().parents[1]
MODULE_MAP = root / 'src/runtime/module-map.json'
SYMBOL_INDEX = root / 'src/runtime/symbol-index.json'
NOTE = 'Ordered script fragments, NOT ES modules. Preserve sequence and single <script> wrapper.'

TOKEN = re.compile(r'''(?P<space>\s+|//[^\n]*|/\*.*?\*/)
  | (?P<string>'(?:[^'\\\n]|\\.)*'|"(?:[^"\\\n]|\\.)*")
  | (?P<name>\#?(?:[^\W\d]|\$)[\w$]*)
  | (?P<number>0[xXbBoO][\da-fA-F_]+n?|(?:\d[\d_]*\.?[\d_]*|\.\d[\d_]*)(?:[eE][+-]?\d+)?n?)
  | (?P<punct>>>>=|\.\.\.|===|!==|\*\*=|<<=|>>=|>>>|&&=|\|\|=|\?\?=|=>|==|!=|<=|>=|&&|\|\||\?\?|\?\.
              |\+\+|--|[-+*/%&|^]=|\*\*|<<|>>|[{}()\[\];,<>+\-*/%&|^!~?:=.@])''', re.S | re.X)
REGEX = re.compile(r'/(?:[^/\\\[\n]|\\.|\[(?:[^\]\\\n]|\\.)*\])+/[a-z]*')
TEMPLATE_PART = re.compile(r'(?:[^`\\$]|\\.|\$(?!\{))*(?:`|\$\{)', re.S)
OPERATOR_WORDS = {'return', 'typeof', 'case', 'do', 'else', 'in', 'of', 'instanceof', 'new', 'delete', 'void', 'throw', 'yield',
                  'await'}
CONTINUES = {'in', 'of', 'instanceof', 'extends', 'else', 'catch', 'finally'}
BLOCKS = {'function', 'class', 'if', 'for', 'while', 'do', 'try', 'switch', '{'}
OPENS, CLOSES = '([{', ')]}'


def tokens(js):
    """(kind, text, offset, depth, after a line break) for each token; depth counts the brackets open around it."""
    stack, pos, prev, newline = [], 0, None, False
    while pos < len(js):
        c = js[pos]
        if c == '`' or c == '}' and stack and stack[-1] == '${':  # a template literal, or its next part after a ${...}
            if c == '}':
                stack.pop()
            m = TEMPLATE_PART.match(js, pos + 1)
            if not m:
                raise SyntaxError(f'unclosed template literal at offset {pos}')
            tok, pos = ('template', js[pos:m.end()], pos, len(stack), newline), m.end()
            if tok[1].endswith('${'):
                stack.append('${')
        elif c == '/' and not js.startswith(('//', '/*'), pos) and (
                prev is None or prev[0] == 'punct' and prev[1] not in ')]' or prev[1] in OPERATOR_WORDS):  # a regex, not a division
            m = REGEX.match(js, pos)
            if not m:
                raise SyntaxError(f'unreadable regular expression at offset {pos}')
            tok, pos = ('regex', m.group(), pos, len(stack), newline), m.end()
        else:
            m = TOKEN.match(js, pos)
            if not m:
                raise SyntaxError(f'unreadable script at offset {pos}: {js[pos:pos + 40]!r}')
            kind, text, pos = m.lastgroup, m.group(), m.end()
            if kind == 'space':
                newline = newline or '\n' in text
                continue
            if text in CLOSES and (not stack or stack.pop() != OPENS[CLOSES.index(text)]):
                raise SyntaxError(f'unbalanced {text!r} at offset {m.start()}')
            tok = (kind, text, m.start(), len(stack), newline)
            if text in OPENS:
                stack.append(text)
        yield tok
        prev, newline = tok, False
    if stack:
        raise SyntaxError(f'{len(stack)} bracket(s) still open at the end')


def statements(toks):
    """The top-level statements among a script's tokens, each as its list of tokens at that level."""
    out, cur, closed = [], [], False
    for tok in toks:
        kind, text, _, depth, newline = tok
        if depth:
            continue
        ends = cur and (cur[-1][0] in ('name', 'number', 'string', 'template', 'regex') and cur[-1][1] not in OPERATOR_WORDS
                        or cur[-1][1] in (')', ']', '}', '++', '--'))
        starts = kind in ('name', 'number', 'string') and text not in CONTINUES or text in ('!', '~', '++', '--')
        if cur and (closed and text not in CONTINUES and not (text == 'while' and cur[0][1] == 'do')
                    or not closed and newline and ends and starts):  # after a block, or a line break that ends it
            out.append(cur)
            cur = []
        if not cur and text in CONTINUES and out:
            cur = out.pop()  # else, catch or finally after a statement that ended with ;
        cur.append(tok)
        head = [t[1] for t in cur[:2]]
        closed = text == '}' and (head[0] in BLOCKS or head == ['async', 'function'])
        if text == ';':
            out.append(cur)
            cur, closed = [], False
    return out + [cur] if cur else out


def end(tok):
    return tok[2] + len(tok[1]) - 1


def symbols(statement):
    """(offset where it ends, name) for each name a top-level statement declares or assigns."""
    toks = statement[1:] if [t[1] for t in statement[:2]] == ['async', 'function'] else statement
    words = [t[1] for t in toks]
    if words[0] in ('function', 'class'):
        named = [toks[2 if words[1] == '*' else 1]]
    elif words[0] in ('const', 'let', 'var'):
        named = [toks[i + 1] for i in range(len(toks) - 1) if i == 0 or words[i] == ',']
    else:
        i = 1
        while i + 1 < len(toks) and words[i] == '.' and toks[i + 1][0] == 'name':
            i += 2
        return [(end(toks[i - 1]), ''.join(words[:i]))] if toks[0][0] == 'name' and words[i:i + 1] == ['='] else []
    return [(end(t), t[1]) for t in named if t[0] == 'name']


def window_names(toks):
    """(offset where it ends, 'window.name') for each window.name = at any depth."""
    for i in range(len(toks) - 3):
        if [t[1] for t in toks[i:i + 2]] == ['window', '.'] and toks[i + 2][0] == 'name' and toks[i + 3][1] == '=' and (
                i == 0 or toks[i - 1][1] not in ('.', '?.')):
            yield end(toks[i + 2]), 'window.' + toks[i + 2][1]


def segment_files():
    """The runtime segments in the order src/index.template.html includes them."""
    return re.findall(r'@@INCLUDE:(src/runtime/segments/[^@]+)@@', (root / 'src/index.template.html').read_text(encoding='utf-8'))


def maps():
    """The module map and the symbol index the template and the segments call for, keeping the tags already in the map."""
    files = segment_files()
    texts = [(root / f).read_text(encoding='utf-8') for f in files]
    tags = {s['file']: s['tags'] for s in json.loads(MODULE_MAP.read_text(encoding='utf-8'))['segments']}
    segments = [{'file': f, 'bytes': len(t.encode('utf-8')), 'statement_count': 0,
                 'tags': tags.get(f, [w for w in re.sub(r'^\d+-', '', Path(f).stem).split('-') if w != 'runtime']), 'symbols': []}
                for f, t in zip(files, texts)]
    starts = [0]
    for t in texts:
        starts.append(starts[-1] + len(t))

    def segment(offset):
        return segments[bisect_right(starts, offset) - 1]

    toks, named = list(tokens(''.join(texts))), []
    for statement in statements(toks):
        segment(statement[0][2])['statement_count'] += 1
        named += symbols(statement)
    for offset, s in sorted(named + list(window_names(toks)), key=lambda n: n[0]):
        if s not in segment(offset)['symbols']:
            segment(offset)['symbols'].append(s)
    index = {}
    for seg in segments:
        for s in seg['symbols']:
            index.setdefault(s, []).append(seg['file'])
    return {'note': NOTE, 'segments': segments}, dict(sorted(index.items()))


def dump(data):
    return json.dumps(data, indent=1, ensure_ascii=False) + '\n'


if __name__ == '__main__':
    module_map, symbol_index = maps()
    MODULE_MAP.write_text(dump(module_map), encoding='utf-8')
    SYMBOL_INDEX.write_text(dump(symbol_index), encoding='utf-8')
    print(f'Wrote {MODULE_MAP.relative_to(root)} ({len(module_map["segments"])} segments)'
          f' and {SYMBOL_INDEX.relative_to(root)} ({len(symbol_index)} symbols)')
