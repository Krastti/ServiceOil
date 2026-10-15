# Third-party frontend resources

The ServiceOil interface adapts [SB Admin 2 v4.1.4](https://github.com/StartBootstrap/startbootstrap-sb-admin-2/tree/v4.1.4).
Distributed assets were copied from the pinned v4.1.4 source archive. Original minified files and their copyright notices are preserved.

| Resource | Version | Local files | License |
| --- | --- | --- | --- |
| SB Admin 2 | 4.1.4 | `sb-admin-2/css/`, `sb-admin-2/js/` | `sb-admin-2/LICENSE` (MIT) |
| Bootstrap, including Popper in the JS bundle | 4.6.0 | CSS included in SB Admin 2; JS in `bootstrap/` | `bootstrap/LICENSE` (MIT); Popper copyright notice is preserved in the bundle |
| jQuery | 3.6.0 | `jquery/` | `jquery/LICENSE.txt` (MIT) |
| jQuery Easing | 1.4.1 | `jquery-easing/` | `jquery-easing/LICENSE` (BSD) |
| Font Awesome Free | 5.15.3 | `fontawesome-free/css/`, `fontawesome-free/webfonts/` | `fontawesome-free/LICENSE.txt` (CC BY 4.0, SIL OFL 1.1, MIT) |

ServiceOil-specific markup, styles and data rendering are in the adjacent `index.html`, `app.css` and `app.js`.
The SVG chart uses the existing ServiceOil observation series; the template's sample chart data and Chart.js demo scripts are not used.
Fonts fall back to system fonts (Segoe UI / Arial); Google Fonts and CDNs are not requested.

To update the theme, review the matching upstream release, replace its assets together, retain their licenses and check the ServiceOil desktop and mobile flows. Bootstrap 5 and Bootstrap 6 are not drop-in replacements for this Bootstrap 4 theme.
