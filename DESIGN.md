# Linzin Desk Clock UI contract

Preserve the existing native Compose watch faces, drawable assets, black canvas, rounded panels and palette: accent #81C7D4, secondary #5DAC81, surface #1E1E22, surface variant #2C2C34. New settings use the existing dialog's typography and spacing. No web framework or new brand style.

The split divider has a narrow visible line and a 48 dp touch target. The exit button has a 48 dp target in the upper right; an unobtrusive bottom handle reserves a 12 dp edge gesture region with a 2 dp indicator. Both remain usable under UI Lock. Media uses fitted original artwork with blurred mirrored margins, readable text and labelled supported controls. Respect font scale and constrain labels on narrow panels.

Hardware visual QA remains separate from logic tests. Check narrow 20% panels, 50/50, landscape-left/right, gesture conflicts, large text, OLED treatment and every existing watch face on a device.

## v2.0.1 user reference

Media presentation follows the supplied cover/player screenshot: artwork above a bottom frosted translucent colour panel, centred title and artist, elapsed/seek/duration row, large circular play/pause flanked by previous/next. The surrounding white dotted page in the reference is not application chrome. Missing-access state has a clear notification-access button; actual locked hosts show an unlock hint. Divider release anchors are 30/50/70 with ±5 percentage-point bands; dragging remains continuous. Auto-launch setup explicitly explains Android special access before opening system settings.

## v2.0.2 refinements

The media player remains fully visible and controllable on the lock screen; no metadata, artwork or progress hiding option remains. Only explicit UI Lock disables interactions. Controls occupy 35% of panel height, with a compact adaptive arrangement at short heights. Rounded internal corners must reveal the artwork-coloured background, never a black seam. Draw the seek line and thumb in one coordinate space so the thumb is vertically centred and its x position matches elapsed/duration. Keep a thin 12 dp bottom exit strip and 2 dp handle.

## User-selected clock side

The user chooses whether the clock stays on the physical left or right. Divider dragging, snapping and crossing 50% must preserve that choice even when the clock is the smaller panel. Changing the selected clock side swaps content without changing the divider ratio. Save the side and ratio independently. Keep 30/50/70 release anchors and existing UI Lock. Settings and tutorial labels describe clock position, never promise a larger clock panel.
