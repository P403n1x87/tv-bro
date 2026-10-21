### Embedded players

The WebView no longer marks its own user agent, so the embedded players that read it
stopped answering 403. The marker is stripped from the user agent the page sees, and the
default user agent is looked up safely on the devices that do not have one.
