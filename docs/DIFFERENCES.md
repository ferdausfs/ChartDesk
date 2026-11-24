# How ChartDesk differs from the reference experience

ChartDesk takes the *layout language* of a professional trading terminal (top toolbar, left drawing rail,
right watchlist, bottom timeframe strip) because that is functional convention, not decoration. Everything
visual below is ours.

| Axis | Reference (vela.luxalgo.com) | ChartDesk |
| --- | --- | --- |
| Accent | magenta/pink gradient | flat teal `#14C7A5`, no gradients on chrome |
| Icon style | filled, rounded, glow | 1.5dp stroke, square-ish caps, no glow |
| Corner radius | pill / 20dp+ | 6dp controls, 10dp panels, 2dp tags |
| Panels | floating glass cards | opaque layered surfaces, 1dp hairline borders |
| Top bar | single dense row | two-zone bar: symbol block left, tool cluster right, 44dp targets |
| Drawing rail | icons only, expands on hover | 48dp strip, expands on long-press, labels in a second column |
| Type | geometric sans + light weights | system sans for UI, monospace tabular for every price |
| Motion | springy, 300ms+ | 150–200ms ease-out, no overshoot |
| Colour-blind mode | not offered | first-class palette switch (blue/orange candles) |
| Charts | proprietary renderer | open-source lightweight-charts, vendored locally |

No logo, name, copy text, font file, icon asset, CSS or JavaScript from the reference is used or fetched.
ChartDesk is not affiliated with LuxAlgo or TradingView.
