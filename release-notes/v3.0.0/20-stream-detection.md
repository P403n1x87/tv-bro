### Stream detection
* The stream list shows one row per stream with a leading icon, a short label
  and, for HLS/DASH manifests, resolution, bandwidth, languages and duration
  read in the background while the list is already usable.
* Manifests hidden behind extensionless gateways (e.g. a `.m3u8` in a signed
  query string) are now detected on the WebView engine too.
* Rotating signatures on the same stream no longer add a new row each time.
* The current page URL is no longer listed as if it were a detected stream.
