/* Library projection v1, the Media pane. The page stays the owner of the media list, the audio files and the save: the native
   screen draws this projection, filters it itself (taking the page's filter whenever the page sets one, as a voice search does)
   and hands every tap to the page's own handler. The kit closes the screen, the handler runs, and the screen comes back once
   nothing it does not draw is over the pane: the add/edit form, the audio player, a file button handed to the owner, a level-up.
   The page's toast, which the screen would hide, comes with the projection while it shows, for the screen to draw.
   With the native audio dock (50-native-audio.js) Play and a file chosen in Android's picker are commands, so the screen stays open.
   The switch's Journal is a destination of its own: the native Journal (50-native-journal.js, its own switch) or the page.
   A screen that says it draws the Media sheets (library.media.write.v1, plan phase 3) gets edit "native" and the form's words, and
   adds, edits, ticks and removes through commands on the page's own owners (mediaUpsert, mediaSetStatus, mediaRemove,
   mediaRemoveAudio) without closing; play and attach stay hand-overs until the audio dock.
   The tab lifecycle is NativeTabKit's. Inventory: docs/architecture/LIBRARY_INVENTORY.md. */
(function () {
  // what the page writes in views/media.html and in its list (renderMedia, mediaAudioButtons), word for word
  const WORDS = {
    panes: [
      { id: "media", label: "Media" },
      { id: "history", label: "Journal" },
    ],
    header: {
      eyebrow: "YOUR PERSONAL LIBRARY",
      title: "Media library",
      blurb: "Reading list, courses, and audiobooks. Attach .m4b/.m4a files to play in-app — stored on this device.",
      add: "＋ Add media",
    },
    search: { label: "Search library", placeholder: "Search titles, creators, notes…" },
    statusFilter: "Show",
    typeFilter: "Media type",
    empty: {
      icon: "▤",
      title: "Your library is ready",
      body: "Add books, articles, courses, podcasts, or videos to start your list.",
      add: "＋ Add your first item",
    },
    noMatch: { icon: "▤", title: "No matching items", body: "Try changing your search or filters." },
    buttons: {
      edit: "Edit",
      remove: "Remove",
      play: "▶ Play",
      replace: "Replace file",
      removeAudio: "Remove audio",
      attach: "🎧 Attach .m4b / audio",
      link: "Open link ↗",
    },
  };
  const FILTERS = "#mediaSearch, #mediaFilter, #mediaTypeFilter";
  const WRITE = "library.media.write.v1"; // the screen draws the add/edit sheet, the tick and the confirms itself
  const FILE_BUTTON = "#mediaList [data-media-file]"; // a row's Attach or Replace file
  let filterRevision = 1; // rises whenever the page's filter is set (typed on the page, or by a voice search), so the screen adopts it
  let acting = false; // a tap handed back to the page is still running: what it shows may come only after an await (the player)

  /** a link the screen may open: http and https only (the page draws any stored link; only its form checks the scheme) */
  function webLink(link) {
    const s = String(link || "").trim();
    return /^https?:\/\//i.test(s) ? s : "";
  }
  /** a select of the page's filter as options: the values its filter compares with, the labels it shows */
  function options(id) {
    return [...document.getElementById(id).options].map((o) => ({ id: o.value, label: o.text }));
  }
  function itemOf(x) {
    const type = String(x.type || ""),
      creator = String(x.creator || "");
    return {
      id: String(x.id),
      title: String(x.title || ""),
      type,
      creator,
      subtitle: (type || "Other") + (creator ? " · " + creator : ""),
      status: String(x.status || ""),
      statusLabel: MEDIA_STATUS[x.status] || MEDIA_STATUS.unread,
      finished: x.status === "finished",
      notes: String(x.notes || ""),
      link: webLink(x.link),
      // the row shows the progress whenever audio is recorded, and the file's buttons once it has a name (Attach otherwise)
      audio: x.audio
        ? {
            file: String(x.audio.name || ""),
            meta: x.audio.name ? mediaAudioMeta(x.audio) : "",
            progress: mediaAudioProgress(x.status, x.audio.position, x.audio.duration),
          }
        : null,
    };
  }
  /** the page's feature tip for this pane, while the page would show it */
  function tip() {
    const t = FEATURE_TIPS.media;
    return state.tutorialDone && !(state.tipsSeen || {}).media
      ? { icon: t.icon, title: t.title, body: t.body, dismiss: "Dismiss tip" }
      : null;
  }
  /** the audio player at the bottom of the page; idle whenever the screen covers the pane, since a loaded player keeps it away */
  function player() {
    const el = document.getElementById("audioEl");
    return {
      mediaId: audioCtx.mediaId || "",
      visible: document.getElementById("audioPlayer").classList.contains("show"),
      playing: !!el.src && !el.paused,
    };
  }

  function projection() {
    const items = Array.isArray(state.mediaItems) ? state.mediaItems : [],
      n = mediaStats(items);
    return {
      panes: WORDS.panes.map((p) => ({ ...p, current: p.id === "media" })),
      header: WORDS.header,
      tip: tip(),
      stats: [
        { label: "Total saved", value: String(n.total), foot: "Items in your library", bar: null },
        { label: "Finished", value: String(n.finished), foot: "Completed items", bar: null },
        { label: "In progress", value: String(n.progress), foot: "Currently working through", bar: null },
        { label: "Completion", value: n.pct + "%", foot: "", bar: n.pct },
      ],
      search: WORDS.search,
      statusFilter: { label: WORDS.statusFilter, options: options("mediaFilter") },
      typeFilter: { label: WORDS.typeFilter, options: options("mediaTypeFilter") },
      filter: Object.assign(mediaFilterOnPage(), { revision: filterRevision }),
      items: items.map(itemOf),
      empty: WORDS.empty,
      noMatch: WORDS.noMatch,
      buttons: WORDS.buttons,
      player: player(),
      // the Media sheets: drawn by the screen when it says so, else every edit is the page's own form
      edit: tab.has(WRITE) ? "native" : "page",
      form: tab.has(WRITE) ? form(items) : null,
      // notice: the kit's (cfg.notices), the page's toast while it shows
    };
  }

  /** a field of the page's form as the screen draws it: its label, placeholder and length, read from the form itself */
  function field(id, extra) {
    const el = document.getElementById(id);
    return Object.assign({ label: document.querySelector("label[for=" + id + "]").textContent, placeholder: el.placeholder, maxLength: el.maxLength }, extra);
  }
  function select(id) {
    return { label: document.querySelector("label[for=" + id + "]").textContent, options: options(id) };
  }
  /** the page's add/edit form and confirms, for a screen that draws them. links holds the stored link of an item whose link the
      rows do not show (not http or https), so an edit starts from what the page's form would show and does not drop it. */
  function form(items) {
    const buttons = document.querySelectorAll("#mediaForm button");
    const links = {};
    items.forEach((x) => {
      const stored = String(x.link || "");
      if (stored !== webLink(stored)) links[String(x.id)] = stored;
    });
    return {
      heading: { add: MEDIA_FORM.add, edit: MEDIA_FORM.edit },
      save: buttons[0].textContent,
      cancel: buttons[1].textContent,
      fields: {
        title: field("mediaTitle", { required: MEDIA_REFUSE.title }),
        type: select("mediaType"),
        creator: field("mediaCreator"),
        status: select("mediaStatus"),
        link: field("mediaLink"),
        notes: field("mediaNotes"),
      },
      defaults: { type: MEDIA_FORM.type, status: MEDIA_FORM.status },
      links,
      confirms: {
        remove: { text: MEDIA_ASK.remove, confirm: WORDS.buttons.remove },
        removeAudio: { text: MEDIA_ASK.removeAudio, confirm: WORDS.buttons.removeAudio },
        cancel: buttons[1].textContent,
      },
    };
  }

  const FIELDS = ["title", "type", "creator", "status", "link", "notes"];
  const MALFORMED = { ok: false, changed: false, message: "Something went wrong. Nothing was changed." };
  /** what an owner answered, as a reply; the page's toast says it, as the page's own tick and buttons do, unless [quiet] */
  function said(r, quiet) {
    if (!quiet) toast(r.message);
    return { ok: !!r.ok, changed: !!r.changed, message: String(r.message || "") };
  }
  /* The Media sheets' writes (plan §3.4, phase 3), for a screen that draws them. Each is repeat-safe: an add carries the screen's
     operation id, which mediaUpsert stores on the item in the same save, so the same add again answers the item it made; an edit
     and a status are sets; remove and remove audio do nothing to an id already gone. ok only once the save has stored it. */
  const COMMANDS = {
    upsert(p) {
      const f = p.fields, id = typeof p.id === "string" ? p.id : "", op = typeof p.op === "string" ? p.op : "";
      if (!f || typeof f !== "object" || !FIELDS.every((k) => typeof f[k] === "string") || (!id && !op)) return MALFORMED;
      // an edit of an item removed meanwhile would otherwise add it back under its old id
      if (id && !mediaById(id)) return { ok: false, changed: false, message: "That item is no longer in your library" };
      const r = mediaUpsert(Object.assign({ id, op }, ...FIELDS.map((k) => ({ [k]: f[k] }))));
      // a refusal is drawn on the sheet, by its field; a stored item is said as the page's form says it
      return Object.assign(said({ ok: r.ok, changed: r.ok && !r.duplicate, message: r.message }, !r.ok), { id: r.id || "", field: r.field || "" });
    },
    setStatus: (p) => said(mediaSetStatus(String(p.id || ""), String(p.status || ""))),
    remove: (p) => said(mediaRemove(String(p.id || ""))),
    removeAudio: async (p) => said(await mediaRemoveAudio(String(p.id || ""))),
  };
  /** a command runs only for a screen that draws the sheets: an older screen never sends one */
  const gated = Object.fromEntries(
    Object.entries(COMMANDS).map(([name, run]) => [name, (p) => (tab.has(WRITE) ? run(p) : { ok: false, changed: false, message: "Media cannot do that here." })]),
  );
  /* With the native audio dock (50-native-audio.js) Play and a file chosen in Android's picker are commands too, so the screen stays
     open and the book starts in the dock. The native side sends them only while the dock is on. */
  const commands = Object.assign(gated, {
    play: async ({ id }) => {
      if (!mediaById(String(id || ""))) return { ok: false, message: "That item is gone." };
      await playMediaAudio(String(id));
      return { ok: true };
    },
    // fire-and-forget: the reply is at once and the page's toasts report the rest, so a 300 MB read blocks no command
    attach: async (p) => window.NativeAudioDock.attachPicked(p),
    attachRefused: async (p) => {
      window.NativeAudioDock.refused(String(p.name || ""));
      return { ok: true };
    },
  });

  /** the row's own file button, in view, focused and named in a toast for the owner to tap: a file picker opens only from a tap
      on the page. A page filter that hides the row is cleared quietly, so the screen keeps its own filter. */
  function showFileButton(id) {
    const button = () => [...document.querySelectorAll(FILE_BUTTON)].find((b) => b.closest("[data-media-id]").dataset.mediaId === id);
    if (!button()) {
      document.getElementById("mediaSearch").value = "";
      document.getElementById("mediaFilter").value = "all";
      document.getElementById("mediaTypeFilter").value = "all";
      renderMedia();
    }
    const b = button();
    if (!b) return;
    b.scrollIntoView({ block: "center" });
    b.focus({ preventScroll: true });
    toast(`Tap "${b.textContent.trim()}" to choose the file`);
    NativeTabKit.claimNotice(); // it is about the page, so no screen shows it
  }
  /** open an item's link as its row's anchor does: a new window, no referrer */
  function openLink(link) {
    const href = webLink(link);
    if (href) Object.assign(document.createElement("a"), { href, target: "_blank", rel: "noopener noreferrer" }).click();
  }
  const PANE_ACTIONS = {
    add: () => showMediaForm(),
    tip: () => dismissFeatureTip("media"),
  };
  // a native action carries only its name and an id, so ticking and unticking are two actions
  const ITEM_ACTIONS = {
    edit: (x) => showMediaForm(x),
    finished: (x) => toggleMediaFinished(x.id, true),
    unfinished: (x) => toggleMediaFinished(x.id, false),
    remove: (x) => deleteMediaItem(x.id),
    play: (x) => playMediaAudio(x.id),
    attach: (x) => showFileButton(x.id),
    replace: (x) => showFileButton(x.id),
    removeAudio: (x) => removeMediaAudio(x.id),
    link: (x) => openLink(x.link),
  };
  /** a native action, after the screen has closed: the page's own handler, then the screen again unless the page now shows
      something over the pane. An item the page no longer has does nothing, as on the page. */
  async function onAction(e) {
    const item = mediaById(String(e.id || ""));
    acting = true;
    try {
      if (PANE_ACTIONS[e.action]) PANE_ACTIONS[e.action]();
      else if (item && ITEM_ACTIONS[e.action]) await ITEM_ACTIONS[e.action](item, e);
    } finally {
      acting = false;
    }
    tab.revisit();
  }

  const tab = NativeTabKit.create({
    id: "media",
    plugin: "NativeMedia",
    capability: "library.media.projection.v1",
    prime: true, // composed ahead at idle as a swipe neighbour (NativePrime)
    setting: "nativeLibrary",
    toggle: "nativeLibraryToggle",
    projection,
    onAction,
    commands,
    render: "renderMedia",
    // the page's toast goes to the screen, which hides it (the kit's shared notices); the one pointing the owner to a file
    // button is about the page, and claimed for none
    notices: true,
    // a player that is loading, loaded or showing keeps the pane on the page, so its controls never vanish under the screen
    // (audioBusy), unless the native dock is carrying it (50-native-audio.js)
    blocked: () =>
      acting ||
      document.getElementById("mediaFormCard").style.display !== "none" ||
      (audioBusy() && !window.NativeAudioDock?.ready()) ||
      !!document.activeElement?.matches(FILE_BUTTON) ||
      !!document.querySelector(".luov"),
    // The page covers the pane now and then (the form, the player, a level-up) and gives it back (the form closed, the player
    // closed, the file button left, a save after a play that failed, which redraws the list): the screen steps aside for it
    // and comes back after.
    watch(observe) {
      observe(document.getElementById("mediaFormCard"), { attributes: true, attributeFilter: ["style"] });
      observe(document.getElementById("audioPlayer"), { attributes: true, attributeFilter: ["class"] });
      observe(document.getElementById("mediaList"), { childList: true });
    },
    globals: { open: "openNativeLibrary", enable: "setNativeLibraryEnabled", projection: "NativeLibraryTabProjection" },
  });

  document.addEventListener(
    "focusout",
    (e) => {
      if (e.target.matches?.(FILE_BUTTON)) setTimeout(tab.follow); // once the focus has moved on
    },
    true,
  );
  document.addEventListener(
    "input",
    (e) => {
      if (e.target.matches?.(FILTERS)) filterRevision++;
    },
    true,
  );
})();
