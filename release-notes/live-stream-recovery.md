### Live streams that recover

A live HLS window slides forward, so retrying a stalled live stream at its old position
asks for a segment that has already been purged. The retries now resume at the live edge,
and when they are exhausted the page is reloaded and the same stream is replayed from the
live edge — once per playback the user asked for.

A stall that is not live is never restarted from zero: only a live stream is considered
recoverable by a refresh.
