/* Library projection v1, the Media pane. The page stays the owner of the media list, the audio files and the save: the native
   screen draws this projection, filters it itself (taking the page's filter whenever the page sets one, as a voice search does)
   and hands every tap to the page's own handler. The kit closes the screen, the handler runs, and the screen comes back once
   nothing it does not draw is over the pane: the add/edit form, the audio player, a file button handed to the owner, a level-up.
   The Journal stays on the page; the switch's Journal is a web destination, so the Journal lock works as before.
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
  const FILE_BUTTON = '#mediaList .media-audio-row button[onclick^="pickAudioForMedia"]'; // a row's Attach or Replace file
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
    };
  }

  /** the row's own file button, in view and focused for the owner to tap: a file picker opens only from a tap on the page.
      A page filter that hides the row is cleared quietly, so the screen keeps its own filter. */
  function showFileButton(id) {
    const button = () => [...document.querySelectorAll(FILE_BUTTON)].find((b) => b.closest("[data-media-id]").dataset.mediaId === id);
    if (!button()) {
      document.getElementById("mediaSearch").value = "";
      document.getElementById("mediaFilter").value = "all";
      document.getElementById("mediaTypeFilter").value = "all";
      renderMedia();
    }
    button()?.scrollIntoView({ block: "center" });
    button()?.focus({ preventScroll: true });
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
  const ITEM_ACTIONS = {
    edit: (x) => showMediaForm(x),
    finished: (x, e) => toggleMediaFinished(x.id, e.checked === true),
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
    setting: "nativeLibrary",
    toggle: "nativeLibraryToggle",
    projection,
    onAction,
    render: "renderMedia",
    // until a native mini-player exists, a player that is loading, loaded or showing keeps the pane on the page, so its
    // controls never vanish under the screen
    blocked: () =>
      acting ||
      document.getElementById("mediaFormCard").style.display !== "none" ||
      !!audioCtx.loading ||
      !!audioCtx.mediaId ||
      document.getElementById("audioPlayer").classList.contains("show") ||
      !!document.activeElement?.matches(FILE_BUTTON) ||
      !!document.querySelector(".luov"),
    globals: { open: "openNativeLibrary", enable: "setNativeLibraryEnabled", projection: "NativeLibraryTabProjection" },
  });

  /* The page covers the pane now and then (the form, the player, a level-up) and gives it back (the form closed, the player
     closed, the file button left): step aside for it, and come back after. */
  function follow() {
    if (tab.blocked()) tab.send();
    else tab.revisit();
  }
  const watch = new MutationObserver(follow);
  watch.observe(document.getElementById("mediaFormCard"), { attributes: true, attributeFilter: ["style"] });
  watch.observe(document.getElementById("audioPlayer"), { attributes: true, attributeFilter: ["class"] });
  watch.observe(document.body, { childList: true });
  document.addEventListener(
    "focusout",
    (e) => {
      if (e.target.matches?.(FILE_BUTTON)) setTimeout(follow); // once the focus has moved on
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
