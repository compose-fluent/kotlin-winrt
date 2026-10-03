# Per-example source

These files follow Microsoft WinUI Gallery's `ControlExample.SampleDefinition`
section format (`--- header`, `--- xaml`, and `--- kotlin` in place of `--- c#`).
Each file describes one example. KSP indexes the header within its route and
prepares the code viewer's tokens; it does not compile or execute these snippets.
The adjacent page XAML and Kotlin remain the executable source.

Keep each snippet aligned with its corresponding controls and handlers. Exclude
the Gallery shell, other examples, and source-viewer wiring. Omit Kotlin when an
example only needs markup. Changes to these files are tracked as KSP inputs.

The page wrapper currently expands the common example layout into native controls.
Preserving upstream `ControlExample.Example`, `Options`, `Output`, and
`Substitutions` markup requires authored custom-type/member support and compiled
binding support in the XAML pipeline. These remain upstream prerequisites;
independent source definitions do not claim that wrapper parity is complete.
