# NaSCon Arena — Midnight Stadium

Source: Stitch project `8772982244908829561`, extracted 2026-10-18.

## Approved mobile screens

| Screen | Stitch screen ID | Source size |
|---|---|---|
| NaSConSports Home | `17a2717317e54b72bb73db34873c5ce1` | 780 × 2902 |
| Fixtures & Matches | `ac31fafd8e93453b820a2c472b3bfa0f` | 780 × 3960 |
| Match Center | `4b462e50bde54837b6cedade63861f6c` | 780 × 2744 |
| League Standings | `5819a19e8d1e4b31bf3d7bfb7ebc7325` | 780 × 2418 |
| Tournament Store | `05dec75fe7324e4c9a0c4601280aea0d` | 780 × 2722 |

The downloaded HTML is in `html/`; reference captures are in `screens/`. Image assets imported from the approved HTML are stored in Android `res/drawable-nodpi`.

## Color tokens

- Base void: `#070B13`
- Background / surface floor: `#0D1320`
- Surface lowest: `#080E1B`
- Surface low / card: `#161B29`
- Surface: `#1A1F2D`
- Surface high: `#242A38`
- Surface highest: `#2F3543`
- Primary royal blue: `#2563EB`
- Secondary electric cyan: `#00D2FF`
- Tertiary sky: `#38BDF8`
- Outline: `#334155`
- Text primary: `#F8FAFC`
- Text secondary: `#94A3B8`
- Text muted: `#64748B`
- Success: `#10B981`
- Warning: `#F59E0B`
- Live / danger: `#EF4444`

## Typography

- Display and labels: Space Grotesk, 600–700 weight.
- Body and titles: Plus Jakarta Sans, 400–600 weight.
- Score display: 36sp / 40sp line height, bold, tabular alignment.
- Large headline: 26sp / 32sp, bold.
- Medium headline: 20sp / 26sp, semibold.
- Small headline: 16sp / 22sp, semibold.
- Body: 15sp, 13sp and 11sp.
- Labels: 13sp, 11sp and 10sp, uppercase with tracking.

## Shape and spacing

- 4dp/8dp incremental grid; 16dp outer margins and 12dp gutters.
- 8dp controls, 12dp buttons, 16dp cards, full-radius status pills.
- 56dp top bar; 64dp floating bottom dock; minimum 44dp touch target.
- Cards use dark tonal layering, a low-opacity outline, and blue/cyan ambient accents rather than heavy opaque shadows.

## Components

- Primary buttons: 48dp, royal blue, white Space Grotesk label, cyan edge.
- Fixture scorecards: competition/status rail, two team crests, centered score box, and metadata rail.
- Standings: elevated header, compact rows, cyan qualification marker, cyan points.
- Merchandise: two-column cards, illuminated image stage, category tag, product title and cyan price.
- Missing pages inherit these same cards, controls, typography, tokens and navigation dock.
