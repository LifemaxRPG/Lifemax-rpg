# ADR 0027: Economy shadow mode stays inside CONTRACT A10

While the owner's switch "Native economy check (beta)" is on, the page sends native, for each outermost economy action, the
economy's save keys just before and just after (docs/specs/ECONOMY_SHADOW_PLAN.md). This record says how that stays a shadow.

- **The page remains the only writer and the only thing the owner sees.** Native never writes the save, sends no command to the
  page, calls `notifyListeners` for nothing, draws nothing it computed and uses no network. The plugin (`NativeShadow`) only hands
  text to a background worker and answers with counts. A10: "Shadow outputs cannot award rewards or persist a second
  authoritative save." Nothing here does either.
- **No second save.** The copy of the economy keys lives in memory only and is lost with the process; the next page session sends a
  new base. The only file is the ring (`noBackupFilesDir/economy-shadow/ring.json`): counts, action kinds and field paths, with every
  owner-chosen name as a salted hash and never a value, at most 200 entries and 128 KB. It is outside device backups and the backup
  folder (ADR 0004 and 0010 unaffected). The coach's reflection text crosses to native in memory only (decision O-3).
- **Transparent.** Every wrapper runs the page's function exactly once with its own `this`, arguments, return value and exception;
  every shadow step is caught; envelopes are posted after the action and never awaited. A test records the economy, quests and board
  golden cases again with the check on and native answering, rejecting every call or throwing, and they come out unchanged.
- **Off means nothing.** Without the switch, the plugin or its capability, in a demo, or when the page's content fingerprint is not
  the one native was built from, no wrapper is installed.
- **It stops itself.** Three page faults or five slow actions in a row turn the page side off for the session; five native errors in a
  day, a resync loop, a slow worker or a save over 4 MB disable native until the owner turns the switch off and on (or installs a new
  version). Battery Saver pauses it (O-5).
- **No native-only fixes during shadow** (O-6): a difference is fixed on the page first, with a golden case, or ruled.

WP6a builds the transport and records only which fields each action touches. WP6b compares the economy actions: native runs
each on its copy of the before-state and counts, in the ring, whether it got the page's outcome; what native computed is never
shown, written or sent anywhere, and a difference is kept as field paths and economy numbers only. The allowlist of accepted
differences is explicit (`core/shadow/KnownDifferences.kt`); an entry beyond spec 21's is proposed in a pull request and signed
off by the owner.
WP6c adds the quest and Daily Hand actions with the page's composition of every save. WP6d adds the Health Connect sync and the
day roll's decay pass: native replays the sync on the plugin answers the page itself received, as the page's recording proxy kept
them, and makes no Health Connect read of its own; workout titles and apps cross in memory only, like every other envelope.
