/* ChartDesk Pine v6 subset: lexer, parser and per-bar evaluator. No DOM. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.ChartDeskPine = factory();
})(typeof self !== 'undefined' ? self : this, function () {
  'use strict';

  var KEYWORDS = ['var', 'if', 'else', 'for', 'to', 'by', 'and', 'or', 'not', 'true', 'false'];

  function PineError(message, line, column) {
    this.name = 'PineError';
    this.message = message;
    this.line = line || 1;
    this.column = column || 1;
  }
  PineError.prototype = Object.create(Error.prototype);

  // ---- lexer --------------------------------------------------------------
  function tokenize(source) {
    var tokens = [];
    var i = 0;
    var line = 1;
    var col = 1;
    var indentStack = [0];
    var atLineStart = true;

    function push(type, value) {
      tokens.push({ type: type, value: value, line: line, column: col });
    }

    function countIndent() {
      var start = i;
      while (source[i] === ' ') { i++; col++; }
      return i - start;
    }

    while (i < source.length) {
      if (atLineStart) {
        var indent = countIndent();
        if (source[i] === '\n' || source[i] === '\r' || i >= source.length) {
          // blank line
        } else {
          var current = indentStack[indentStack.length - 1];
          if (indent > current) {
            indentStack.push(indent);
            push('INDENT', indent);
          } else {
            while (indent < indentStack[indentStack.length - 1]) {
              indentStack.pop();
              push('DEDENT', null);
            }
            if (indent !== indentStack[indentStack.length - 1]) {
              throw new PineError('Inconsistent indentation', line, col);
            }
          }
        }
        atLineStart = false;
      }

      var ch = source[i];
      if (ch === undefined) break;
      if (ch === '\n' || ch === '\r') {
        push('NEWLINE', null);
        if (ch === '\r' && source[i + 1] === '\n') i++;
        i++;
        line++;
        col = 1;
        atLineStart = true;
        continue;
      }
      if (ch === ' ' || ch === '\t') { i++; col++; continue; }
      if (ch === '/' && source[i + 1] === '/') {
        while (i < source.length && source[i] !== '\n') { i++; col++; }
        continue;
      }
      if (ch === '"' || ch === "'") {
        var quote = ch;
        var startCol = col;
        i++; col++;
        var str = '';
        while (i < source.length && source[i] !== quote) {
          if (source[i] === '\\') { i++; col++; }
          str += source[i];
          i++; col++;
        }
        if (source[i] !== quote) throw new PineError('Unterminated string', line, startCol);
        i++; col++;
        push('STRING', str);
        continue;
      }
      if (/[0-9]/.test(ch) || (ch === '.' && /[0-9]/.test(source[i + 1] || ''))) {
        var start = i;
        while (/[0-9.]/.test(source[i] || '')) { i++; col++; }
        if (/[eE]/.test(source[i] || '')) {
          i++; col++;
          if (/[+-]/.test(source[i] || '')) { i++; col++; }
          while (/[0-9]/.test(source[i] || '')) { i++; col++; }
        }
        push('NUMBER', parseFloat(source.slice(start, i)));
        continue;
      }
      if (/[A-Za-z_]/.test(ch)) {
        var idStart = i;
        while (/[A-Za-z0-9_.]/.test(source[i] || '')) { i++; col++; }
        var word = source.slice(idStart, i);
        if (KEYWORDS.indexOf(word) >= 0) push('KEYWORD', word);
        else push('IDENT', word);
        continue;
      }
      var two = source.slice(i, i + 2);
      if (two === ':=' || two === '==' || two === '!=' || two === '<=' || two === '>=') {
        push('OP', two);
        i += 2;
        col += 2;
        continue;
      }
      if ('+-*/%<>?:=(),[].'.indexOf(ch) >= 0) {
        push('OP', ch);
        i++;
        col++;
        continue;
      }
      throw new PineError('Unexpected character "' + ch + '"', line, col);
    }
    while (indentStack.length > 1) {
      indentStack.pop();
      push('DEDENT', null);
    }
    push('EOF', null);
    return tokens;
  }

  // ---- parser -------------------------------------------------------------
  function Parser(tokens) {
    this.tokens = tokens;
    this.pos = 0;
  }

  Parser.prototype.peek = function () { return this.tokens[this.pos]; };
  Parser.prototype.next = function () { return this.tokens[this.pos++]; };
  Parser.prototype.match = function (type, value) {
    var t = this.peek();
    if (t.type === type && (value === undefined || value === null || t.value === value)) { this.pos++; return t; }
    return null;
  };
  Parser.prototype.expect = function (type, value, message) {
    var t = this.match(type, value);
    if (!t) {
      var cur = this.peek();
      throw new PineError(message || ('Expected ' + type + (value ? ' ' + value : '')), cur.line, cur.column);
    }
    return t;
  };
  Parser.prototype.skipNewlines = function () {
    while (this.peek().type === 'NEWLINE') this.pos++;
  };

  Parser.prototype.parseProgram = function () {
    var body = [];
    this.skipNewlines();
    while (this.peek().type !== 'EOF') {
      body.push(this.parseStatement());
      this.skipNewlines();
    }
    return { type: 'Program', body: body };
  };

  Parser.prototype.parseBlock = function () {
    if (this.match('NEWLINE')) {
      this.expect('INDENT', null, 'Expected an indented block');
      var body = [];
      this.skipNewlines();
      while (this.peek().type !== 'DEDENT' && this.peek().type !== 'EOF') {
        body.push(this.parseStatement());
        this.skipNewlines();
      }
      this.match('DEDENT');
      return { type: 'Block', body: body };
    }
    return { type: 'Block', body: [this.parseStatement()] };
  };

  Parser.prototype.parseStatement = function () {
    var t = this.peek();
    if (t.type === 'KEYWORD' && t.value === 'var') {
      this.next();
      var name = this.expect('IDENT', null, 'Expected a variable name').value;
      this.expect('OP', '=', 'Expected "=" after var name');
      var value = this.parseExpression();
      return { type: 'VarDecl', name: name, value: value, line: t.line };
    }
    if (t.type === 'KEYWORD' && t.value === 'if') {
      this.next();
      var cond = this.parseExpression();
      var thenBlock = this.parseBlock();
      this.skipNewlines();
      var elseBlock = null;
      if (this.peek().type === 'KEYWORD' && this.peek().value === 'else') {
        this.next();
        elseBlock = this.parseBlock();
      }
      return { type: 'If', cond: cond, then: thenBlock, otherwise: elseBlock, line: t.line };
    }
    if (t.type === 'KEYWORD' && t.value === 'for') {
      this.next();
      var loopVar = this.expect('IDENT', null, 'Expected loop variable').value;
      this.expect('OP', '=', 'Expected "=" in for loop');
      var from = this.parseExpression();
      this.expect('KEYWORD', 'to', 'Expected "to" in for loop');
      var to = this.parseExpression();
      var by = null;
      if (this.match('KEYWORD', 'by')) by = this.parseExpression();
      var body = this.parseBlock();
      return { type: 'For', variable: loopVar, from: from, to: to, by: by, body: body, line: t.line };
    }
    if (t.type === 'IDENT') {
      var after = this.tokens[this.pos + 1];
      if (after && after.type === 'OP' && (after.value === '=' || after.value === ':=')) {
        this.next();
        this.next();
        return { type: 'Assign', name: t.value, op: after.value, value: this.parseExpression(), line: t.line };
      }
    }
    return { type: 'ExprStmt', expr: this.parseExpression(), line: t.line };
  };

  Parser.prototype.parseExpression = function () { return this.parseTernary(); };

  Parser.prototype.parseTernary = function () {
    var cond = this.parseBinary(0);
    if (this.match('OP', '?')) {
      var a = this.parseTernary();
      this.expect('OP', ':', 'Expected ":" in ternary');
      var b = this.parseTernary();
      return { type: 'Ternary', cond: cond, a: a, b: b };
    }
    return cond;
  };

  var PRECEDENCE = [
    ['or'],
    ['and'],
    ['==', '!='],
    ['<', '>', '<=', '>='],
    ['+', '-'],
    ['*', '/', '%'],
  ];

  Parser.prototype.parseBinary = function (level) {
    if (level >= PRECEDENCE.length) return this.parseUnary();
    var left = this.parseBinary(level + 1);
    while (true) {
      var t = this.peek();
      var op = t.type === 'KEYWORD' ? t.value : (t.type === 'OP' ? t.value : null);
      if (!op || PRECEDENCE[level].indexOf(op) === -1) break;
      this.next();
      var right = this.parseBinary(level + 1);
      left = { type: 'Binary', op: op, left: left, right: right, line: t.line };
    }
    return left;
  };

  Parser.prototype.parseUnary = function () {
    var t = this.peek();
    if ((t.type === 'OP' && (t.value === '-' || t.value === '+')) || (t.type === 'KEYWORD' && t.value === 'not')) {
      this.next();
      return { type: 'Unary', op: t.value, value: this.parseUnary(), line: t.line };
    }
    return this.parsePostfix();
  };

  Parser.prototype.parsePostfix = function () {
    var node = this.parsePrimary();
    while (true) {
      if (this.match('OP', '(')) {
        var args = [];
        if (!this.match('OP', ')')) {
          do {
            var t = this.peek();
            var after = this.tokens[this.pos + 1];
            if (t.type === 'IDENT' && after && after.type === 'OP' && after.value === '=') {
              this.next();
              this.next();
              args.push({ type: 'NamedArg', name: t.value, value: this.parseExpression() });
            } else {
              args.push(this.parseExpression());
            }
          } while (this.match('OP', ','));
          this.expect('OP', ')', 'Expected ")"');
        }
        node = { type: 'Call', callee: node, args: args };
      } else if (this.match('OP', '.')) {
        var prop = this.expect('IDENT', null, 'Expected a property name');
        node = { type: 'Member', object: node, property: prop.value };
      } else if (this.match('OP', '[')) {
        var index = this.parseExpression();
        this.expect('OP', ']', 'Expected "]"');
        node = { type: 'Index', object: node, index: index };
      } else {
        break;
      }
    }
    return node;
  };

  Parser.prototype.parsePrimary = function () {
    var t = this.next();
    if (t.type === 'NUMBER') return { type: 'Literal', value: t.value };
    if (t.type === 'STRING') return { type: 'Literal', value: t.value };
    if (t.type === 'KEYWORD' && (t.value === 'true' || t.value === 'false')) {
      return { type: 'Literal', value: t.value === 'true' };
    }
    if (t.type === 'KEYWORD' && t.value === 'na') return { type: 'Literal', value: null };
    if (t.type === 'IDENT') return { type: 'Ident', name: t.value, line: t.line };
    if (t.type === 'OP' && t.value === '(') {
      var expr = this.parseExpression();
      this.expect('OP', ')', 'Expected ")"');
      return expr;
    }
    throw new PineError('Unexpected token ' + (t.value === null ? t.type : t.value), t.line, t.column);
  };

  // ---- series -------------------------------------------------------------
  function Series(values) {
    this.values = values;
  }
  Series.prototype.get = function (i) {
    if (i < 0) return null;
    var v = this.values[i];
    return v === undefined ? null : v;
  };

  function number(value) {
    if (value === null || value === undefined) return null;
    var n = typeof value === 'number' ? value : Number(value);
    return isFinite(n) ? n : null;
  }

  function arithmetic(op, a, b) {
    if (a === null || b === null) return null;
    switch (op) {
      case '+': return a + b;
      case '-': return a - b;
      case '*': return a * b;
      case '/': return b === 0 ? null : a / b;
      case '%': return b === 0 ? null : a % b;
      default: return null;
    }
  }

  function compare(op, a, b) {
    switch (op) {
      case '==': return a === b;
      case '!=': return a !== b;
      case '<': return a !== null && b !== null && a < b;
      case '>': return a !== null && b !== null && a > b;
      case '<=': return a !== null && b !== null && a <= b;
      case '>=': return a !== null && b !== null && a >= b;
      default: return false;
    }
  }

  // ---- runtime ------------------------------------------------------------
  function createRuntime(candles, warnings) {
    var env = {
      candles: candles,
      bar: 0,
      vars: {},
      builtins: {
        open: new Series(candles.map(function (c) { return c.open; })),
        high: new Series(candles.map(function (c) { return c.high; })),
        low: new Series(candles.map(function (c) { return c.low; })),
        close: new Series(candles.map(function (c) { return c.close; })),
        volume: new Series(candles.map(function (c) { return c.volume; })),
        time: new Series(candles.map(function (c) { return c.time; })),
      },
      plots: [],
      shapes: [],
      hlines: [],
      bgcolors: [],
      warnings: warnings || [],
      loopGuard: 0,
    };
    return env;
  }

  function resolveColor(value) {
    if (typeof value !== 'string') return 'accent';
    var map = {
      green: 'up', red: 'down', blue: 'blue', orange: 'warning', yellow: 'warning',
      gray: 'dim', grey: 'dim', white: 'text', black: 'text', purple: 'purple', teal: 'accent',
    };
    return map[value.toLowerCase()] || 'accent';
  }

  function seriesOf(value) {
    if (value instanceof Series) return value;
    return new Series([value]);
  }

  /** Series-aware argument evaluation: keeps builtin/user series intact for ta.* calls. */
  function evalSeriesArg(node, env) {
    if (node.type === 'Ident') {
      if (node.name in env.builtins) return env.builtins[node.name];
      if (node.name in env.vars && env.vars[node.name] instanceof Series) return env.vars[node.name];
    }
    return new Series([evalNode(node, env)]);
  }

  function windowValues(env, series, length) {
    var out = [];
    for (var i = env.bar - length + 1; i <= env.bar; i++) {
      out.push(series.get(i));
    }
    return out;
  }

  function sma(env, src, length) {
    var vals = windowValues(env, src, length);
    if (vals.length < length || vals.some(function (v) { return v === null; })) return null;
    var sum = 0;
    for (var i = 0; i < vals.length; i++) sum += vals[i];
    return sum / length;
  }

  function ema(env, src, length) {
    var k = 2 / (length + 1);
    var prev = null;
    for (var i = 0; i <= env.bar; i++) {
      var v = src.get(i);
      if (v === null) continue;
      if (prev === null) {
        if (i >= length - 1) {
          var seed = 0;
          var count = 0;
          for (var j = i - length + 1; j <= i; j++) {
            var sv = src.get(j);
            if (sv !== null) { seed += sv; count++; }
          }
          prev = count === length ? seed / length : v;
        }
      } else {
        prev = v * k + prev * (1 - k);
      }
    }
    return prev;
  }

  function rsi(env, src, length) {
    var gains = 0;
    var losses = 0;
    if (env.bar < length) return null;
    for (var i = env.bar - length + 1; i <= env.bar; i++) {
      var prev = src.get(i - 1);
      var cur = src.get(i);
      if (prev === null || cur === null) return null;
      var d = cur - prev;
      if (d >= 0) gains += d;
      else losses -= d;
    }
    if (losses === 0) return 100;
    var rs = gains / losses;
    return 100 - 100 / (1 + rs);
  }

  function atr(env, length) {
    var sum = 0;
    if (env.bar < length) return null;
    for (var i = env.bar - length + 1; i <= env.bar; i++) {
      var c = env.candles[i];
      var prev = env.candles[i - 1];
      var tr = prev ? Math.max(c.high - c.low, Math.abs(c.high - prev.close), Math.abs(c.low - prev.close)) : c.high - c.low;
      sum += tr;
    }
    return sum / length;
  }

  function highestLowest(env, src, length, wantHigh) {
    var vals = windowValues(env, src, length).filter(function (v) { return v !== null; });
    if (!vals.length) return null;
    return wantHigh ? Math.max.apply(null, vals) : Math.min.apply(null, vals);
  }

  function callBuiltin(env, name, args, named) {
    named = named || {};
    function arg(i, fallback, key) {
      if (key && named[key] !== undefined) return named[key];
      return args.length > i && args[i] !== undefined ? args[i] : fallback;
    }
    switch (name) {
      case 'ta.sma': return sma(env, seriesOf(arg(0, null, 'source')), arg(1, 14, 'length'));
      case 'ta.ema': return ema(env, seriesOf(arg(0, null, 'source')), arg(1, 14, 'length'));
      case 'ta.rsi': return rsi(env, seriesOf(arg(0, null, 'source')), arg(1, 14, 'length'));
      case 'ta.atr': return atr(env, arg(0, 14, 'length'));
      case 'ta.highest': return highestLowest(env, seriesOf(arg(0, null, 'source')), arg(1, 14, 'length'), true);
      case 'ta.lowest': return highestLowest(env, seriesOf(arg(0, null, 'source')), arg(1, 14, 'length'), false);
      case 'ta.change': {
        var s = seriesOf(arg(0, null, 'source'));
        var cur = s.get(env.bar);
        var prev = s.get(env.bar - 1);
        return cur === null || prev === null ? null : cur - prev;
      }
      case 'ta.crossover': {
        var a = seriesOf(arg(0, null, 'source1'));
        var b = seriesOf(arg(1, null, 'source2'));
        var a0 = a.get(env.bar - 1), a1 = a.get(env.bar), b0 = b.get(env.bar - 1), b1 = b.get(env.bar);
        return a0 !== null && b0 !== null && a1 !== null && b1 !== null && a0 <= b0 && a1 > b1;
      }
      case 'ta.crossunder': {
        var a = seriesOf(arg(0, null, 'source1'));
        var b = seriesOf(arg(1, null, 'source2'));
        var a0 = a.get(env.bar - 1), a1 = a.get(env.bar), b0 = b.get(env.bar - 1), b1 = b.get(env.bar);
        return a0 !== null && b0 !== null && a1 !== null && b1 !== null && a0 >= b0 && a1 < b1;
      }
      case 'math.abs': return Math.abs(arg(0, 0));
      case 'math.sqrt': return Math.sqrt(arg(0, 0));
      case 'math.pow': return Math.pow(arg(0, 1), arg(1, 1));
      case 'math.round': return Math.round(arg(0, 0));
      case 'math.floor': return Math.floor(arg(0, 0));
      case 'math.ceil': return Math.ceil(arg(0, 0));
      case 'math.log': return Math.log(arg(0, 1));
      case 'math.max': return Math.max(arg(0, 0), arg(1, 0));
      case 'math.min': return Math.min(arg(0, 0), arg(1, 0));
      case 'na': return arg(0) === null || arg(0) === undefined || (typeof arg(0) === 'number' && isNaN(arg(0)));
      case 'nz': return arg(0) === null ? arg(1, 0) : arg(0);
      case 'input.int': return number(arg(0, 0, 'defval')) || 0;
      case 'input.float': return number(arg(0, 0, 'defval')) || 0;
      case 'input.bool': return !!arg(0, false, 'defval');
      case 'input.string': return String(arg(0, '', 'defval'));
      case 'input.source': return arg(0, null, 'defval');
      case 'request.security': {
        if (env.warnings.indexOf('request.security evaluates on the chart series') === -1) {
          env.warnings.push('request.security evaluates on the chart series');
        }
        return arg(2, null, 'expression');
      }
      case 'array.new_float': {
        var size = number(arg(0, 0, 'size')) || 0;
        var fill = arg(1, 0, 'initial_value');
        return new Array(Math.max(0, size)).fill(fill);
      }
      case 'array.new_int': return callBuiltin(env, 'array.new_float', args, named);
      case 'array.push': if (Array.isArray(args[0])) args[0].push(args[1]); return args[0];
      case 'array.get': return Array.isArray(args[0]) ? args[0][number(args[1]) || 0] : null;
      case 'array.size': return Array.isArray(args[0]) ? args[0].length : 0;
      case 'array.set': if (Array.isArray(args[0])) args[0][number(args[1]) || 0] = args[2]; return args[0];
      case 'plot': {
        var title = arg(1, 'plot', 'title');
        var plot = env.plots.filter(function (p) { return p.title === title; })[0];
        if (!plot) {
          plot = { title: title, color: resolveColor(arg(2, 'blue', 'color')), values: [] };
          env.plots.push(plot);
        }
        plot.values[env.bar] = number(arg(0));
        return arg(0);
      }
      case 'plotshape': {
        var shapeTitle = arg(1, 'shape', 'title');
        var shape = env.shapes.filter(function (s) { return s.title === shapeTitle; })[0];
        if (!shape) {
          shape = { title: shapeTitle, color: resolveColor(arg(2, 'green', 'color')), values: [] };
          env.shapes.push(shape);
        }
        shape.values[env.bar] = !!arg(0);
        return arg(0);
      }
      case 'hline': {
        env.hlines.push({
          price: number(arg(0, 0, 'price')) || 0,
          color: resolveColor(arg(1, 'gray', 'color')),
        });
        return arg(0);
      }
      case 'bgcolor': {
        env.bgcolors[env.bar] = !!arg(0);
        return arg(0);
      }
      case 'indicator':
      case 'study':
        return null;
      default:
        throw new PineError('Unsupported function "' + name + '"');
    }
  }

  function colorMember(object, property) {
    if (object === 'color') return property;
    return null;
  }

  function evalNode(node, env) {
    switch (node.type) {
      case 'Literal': return node.value;
      case 'Ident': {
        if (node.name.indexOf('.') > 0) {
          var parts = node.name.split('.');
          if (parts[0] === 'color') return parts[1];
          if (parts[0] === 'input') return { inputType: parts[1] };
        }
        if (node.name === 'na') return null;
        if (node.name in env.builtins) return env.builtins[node.name].get(env.bar);
        if (node.name in env.vars) {
          var v = env.vars[node.name];
          return v instanceof Series ? v.get(env.bar) : v;
        }
        if (node.name === 'syminfo' || node.name === 'timeframe' || node.name === 'bar_index') {
          if (node.name === 'bar_index') return env.bar;
          return null;
        }
        throw new PineError('Unknown identifier "' + node.name + '"', node.line);
      }
      case 'Member': {
        var obj = node.object.type === 'Ident' ? node.object.name : evalNode(node.object, env);
        if (obj === 'color') return node.property;
        if (obj === 'math' || obj === 'ta' || obj === 'array') return { namespace: obj, member: node.property };
        return null;
      }
      case 'Unary': {
        var value = evalNode(node.value, env);
        if (node.op === 'not') return !value;
        var n = number(value);
        if (n === null) return null;
        return node.op === '-' ? -n : n;
      }
      case 'Binary': {
        if (node.op === 'and') return !!evalNode(node.left, env) && !!evalNode(node.right, env);
        if (node.op === 'or') return !!evalNode(node.left, env) || !!evalNode(node.right, env);
        var a = evalNode(node.left, env);
        var b = evalNode(node.right, env);
        if (['+', '-', '*', '/', '%'].indexOf(node.op) >= 0) {
          return arithmetic(node.op, number(a), number(b));
        }
        return compare(node.op, a, b);
      }
      case 'Ternary': return evalNode(node.cond, env) ? evalNode(node.a, env) : evalNode(node.b, env);
      case 'Index': {
        var target = node.object;
        if (target.type === 'Ident' && target.name in env.vars) {
          var stored = env.vars[target.name];
          var idx = number(evalNode(node.index, env)) || 0;
          if (stored instanceof Series) return stored.get(env.bar - idx);
          return stored;
        }
        if (target.type === 'Ident' && target.name in env.builtins) {
          var hist = env.builtins[target.name];
          var back = number(evalNode(node.index, env)) || 0;
          return hist.get(env.bar - back);
        }
        var arr = evalNode(target, env);
        var at = number(evalNode(node.index, env)) || 0;
        return Array.isArray(arr) ? arr[at] : null;
      }
      case 'Call': {
        var name;
        if (node.callee.type === 'Ident') name = node.callee.name;
        else if (node.callee.type === 'Member') {
          var o = node.callee.object;
          var objName = o.type === 'Ident' ? o.name : evalNode(o, env);
          if (typeof objName === 'object' && objName && objName.namespace) name = objName.namespace + '.' + objName.member;
          else name = String(objName) + '.' + node.callee.property;
        } else {
          throw new PineError('Unsupported call target', node.line);
        }
        if (name.indexOf('strategy.') === 0 || name.indexOf('table.') === 0 ||
            name.indexOf('label.') === 0 || name.indexOf('line.') === 0 || name.indexOf('box.') === 0) {
          throw new PineError('Unsupported function "' + name + '"', node.line);
        }
        var seriesAware = ['ta.sma', 'ta.ema', 'ta.rsi', 'ta.highest', 'ta.lowest', 'ta.change',
          'ta.crossover', 'ta.crossunder'].indexOf(name) >= 0;
        var positional = [];
        var named = {};
        node.args.forEach(function (a, index) {
          if (a.type === 'NamedArg') {
            var isSeriesName = a.name === 'source' || a.name === 'source1' || a.name === 'source2';
            named[a.name] = seriesAware && isSeriesName ? evalSeriesArg(a.value, env) : evalNode(a.value, env);
          } else {
            var wantSeries = seriesAware && (index === 0 || (index === 1 && name.indexOf('ta.cross') === 0));
            positional.push(wantSeries ? evalSeriesArg(a, env) : evalNode(a, env));
          }
        });
        return callBuiltin(env, name, positional, named);
      }
      default:
        throw new PineError('Cannot evaluate ' + node.type);
    }
  }

  function runStatement(stmt, env) {
    switch (stmt.type) {
      case 'VarDecl': {
        if (!(stmt.name in env.vars)) {
          var init = evalNode(stmt.value, env);
          env.vars[stmt.name] = new Series(new Array(env.candles.length).fill(null));
          env.vars[stmt.name].values[env.bar] = init;
        } else {
          var series = env.vars[stmt.name];
          var last = series.get(env.bar - 1);
          series.values[env.bar] = last === null ? evalNode(stmt.value, env) : last;
        }
        return;
      }
      case 'Assign': {
        var value = evalNode(stmt.value, env);
        if (stmt.op === ':=') {
          if (!(stmt.name in env.vars)) throw new PineError('Cannot reassign undeclared variable "' + stmt.name + '"', stmt.line);
          var existing = env.vars[stmt.name];
          if (existing instanceof Series) existing.values[env.bar] = value;
          else env.vars[stmt.name] = value;
        } else {
          if (!(stmt.name in env.vars)) {
            env.vars[stmt.name] = new Series(new Array(env.candles.length).fill(null));
          }
          var target = env.vars[stmt.name];
          if (target instanceof Series) target.values[env.bar] = value;
          else env.vars[stmt.name] = value;
        }
        return;
      }
      case 'ExprStmt': evalNode(stmt.expr, env); return;
      case 'If': {
        if (evalNode(stmt.cond, env)) runBlock(stmt.then, env);
        else if (stmt.otherwise) runBlock(stmt.otherwise, env);
        return;
      }
      case 'For': {
        var from = number(evalNode(stmt.from, env)) || 0;
        var to = number(evalNode(stmt.to, env)) || 0;
        var by = stmt.by ? (number(evalNode(stmt.by, env)) || 1) : 1;
        if (by === 0) throw new PineError('for loop step cannot be zero', stmt.line);
        var iterations = 0;
        for (var i = from; by > 0 ? i <= to : i >= to; i += by) {
          if (++iterations > 5000) throw new PineError('for loop exceeded 5000 iterations', stmt.line);
          env.vars[stmt.variable] = i;
          runBlock(stmt.body, env);
        }
        return;
      }
      default:
        throw new PineError('Unsupported statement ' + stmt.type);
    }
  }

  function runBlock(block, env) {
    block.body.forEach(function (stmt) { runStatement(stmt, env); });
  }

  function compile(source) {
    var tokens = tokenize(source);
    var parser = new Parser(tokens);
    return parser.parseProgram();
  }

  function run(source, candles) {
    var warnings = [];
    var ast;
    try {
      ast = compile(source);
    } catch (e) {
      return { ok: false, errors: [{ message: e.message, line: e.line || 1, column: e.column || 1 }], plots: [], shapes: [], hlines: [], warnings: warnings };
    }
    var env = createRuntime(candles || [], warnings);
    try {
      for (var bar = 0; bar < (candles || []).length; bar++) {
        env.bar = bar;
        env.loopGuard = 0;
        runBlock(ast, env);
      }
    } catch (e) {
      return {
        ok: false,
        errors: [{ message: e.message, line: e.line || 1, column: e.column || 1 }],
        plots: [], shapes: [], hlines: [], warnings: warnings,
      };
    }

    var plots = env.plots.map(function (p) {
      var data = [];
      for (var i = 0; i < candles.length; i++) {
        var v = p.values[i];
        if (v !== null && v !== undefined && isFinite(v)) data.push({ time: candles[i].time, value: v });
      }
      return { title: p.title, color: p.color, data: data };
    });
    var shapes = env.shapes.map(function (s) {
      var data = [];
      for (var i = 0; i < candles.length; i++) {
        if (s.values[i]) data.push({ time: candles[i].time, value: candles[i].low, color: s.color });
      }
      return { title: s.title, color: s.color, data: data };
    });
    return {
      ok: true,
      errors: [],
      plots: plots,
      shapes: shapes,
      hlines: env.hlines,
      bgcolors: env.bgcolors.filter(Boolean).length,
      warnings: warnings,
    };
  }

  return {
    run: run,
    compile: compile,
    tokenize: tokenize,
    PineError: PineError,
    __test: { createRuntime: createRuntime, evalNode: evalNode },
  };
});
