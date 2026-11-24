# Pine support matrix

ChartDesk runs a **Pine v6 subset** in the chart's JS layer. Scripts are compiled once and evaluated bar by bar
against the loaded candles. It is intentionally not a full Pine implementation: no exchange order execution, no
multi-timeframe data fetching, no `strategy.*` backtester.

## Supported

| Area | Syntax |
| --- | --- |
| Declaration | `indicator("name", overlay=true)`, `study(...)` (ignored visually) |
| Variables | `x = close`, `var counter = 0`, reassignment `counter := counter + 1`, history `x[1]` |
| Control flow | `if / else` with indentation, `for i = 1 to 10 by 2` (max 5000 iterations) |
| Operators | `+ - * / %`, comparisons, `and or not`, ternary `cond ? a : b` |
| Builtins | `open high low close volume time bar_index` |
| `ta.*` | `sma ema rsi atr highest lowest change crossover crossunder` |
| `math.*` | `abs sqrt pow round floor ceil log max min` |
| Helpers | `na()`, `nz()` |
| Inputs | `input.int`, `input.float`, `input.bool`, `input.string`, `input.source` (defaults only) |
| Plotting | `plot(series, title=, color=)`, `plotshape(cond, title=, color=)`, `hline(price, color=)`, `bgcolor(cond)` (validated, not painted) |
| Arrays | `array.new_float`, `array.new_int`, `array.push`, `array.get`, `array.set`, `array.size` |
| Multi-symbol | `request.security(sym, tf, expr)` evaluates on the chart's own series and emits a warning |
| Colors | `color.green/red/blue/orange/yellow/gray/teal/purple` |

## Not supported (fails with a readable error, never a crash)

- `strategy.*` (orders, equity, backtests)
- `table.*`, `label.*`, `line.*`, `box.*` drawings
- User-defined functions `f(x) => ...`
- `switch`, `while`, `import`
- `request.*` other than a basic `request.security`
- `ta.*` functions outside the list above (`ta.supertrend`, `ta.vwap`, `ta.macd`, ...) — use built-in indicators instead
- Multi-timeframe candle fetching, `syminfo.*` metadata, sessions/timezones
- `bgcolor` visuals (conditions are evaluated but the background is not painted yet)

## Error model

- Lexer/parser errors carry `line` and `column`; the editor shows `line N: message`.
- Runtime errors (unknown identifier, unsupported function, loop guard) are caught per run and returned as a failed
  `pineResult`; the chart keeps its previous state.
- Empty scripts are valid and produce no plots.

## Where scripts run

The editor lives in the Scripts tab. Pressing Run sends the code to the chart WebView. If the Chart tab is not
mounted, the command is queued and applied when the chart is next opened. The last script re-runs automatically
when the symbol or timeframe changes.
