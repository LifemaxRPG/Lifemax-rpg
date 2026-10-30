/* Log activity projection v1. The page stays the owner of the XP rules and the save: the native screen shows this projection,
   asks the page for a preview whenever the draft changes, and submits through recordActivity. The tab lifecycle is NativeTabKit's. */
(function () {
  const QUALITIES = [
    { value: "0.8", label: "Light", hint: "0.8×" },
    { value: "1", label: "Normal", hint: "1.0×" },
    { value: "1.1", label: "Challenging", hint: "1.1×" },
    { value: "1.2", label: "High quality", hint: "1.2×" },
  ];
  const MAX_MINUTES = 1440;
  const CELEBRATE_AFTER_MS = 450; // after the hand-over to Overview has drawn
  let draft = null;
  let revision = 1; // raised whenever the page replaces the draft (after a save, or a prefill), so the native screen adopts it

  function preset(id) {
    return rules[id] || rules.custom;
  }
  /** a fresh draft for a preset, with the preset's own skill and attributes, like choosing it in the web form */
  function draftFor(type, from) {
    const r = preset(type);
    return {
      type: rules[type] ? type : "custom",
      customName: from ? String(from.customName || "") : "",
      duration: from ? String(from.duration) : "30",
      quality: from ? String(from.quality) : "1",
      skill: r.skill,
      primary: r.primary,
      secondary: r.secondary || "",
      notes: from ? String(from.notes || "") : "",
    };
  }
  /** the draft as the page will read it: known preset, duration clamped to the form's limits, attributes that exist */
  function clean(d) {
    const ids = ATTRS.map((a) => a.id);
    const base = draftFor(d && d.type in rules ? d.type : "reading");
    const x = Object.assign(base, d || {});
    const minutes = Math.max(0, Math.min(MAX_MINUTES, Math.round(Number(x.duration) || 0)));
    return {
      type: x.type in rules ? x.type : "custom",
      customName: String(x.customName || "").slice(0, 80),
      duration: String(minutes),
      quality: QUALITIES.some((q) => q.value === String(x.quality)) ? String(x.quality) : "1",
      skill: String(x.skill || "").slice(0, 60),
      primary: ids.includes(x.primary) ? x.primary : preset(x.type).primary,
      secondary: x.secondary === "" || ids.includes(x.secondary) ? x.secondary : "",
      notes: String(x.notes || "").slice(0, 2000),
    };
  }
  /** what Save would pay for this draft, from the function the save pays with (a peek that changes nothing), and why it is not the raw total */
  function previewOf(d) {
    const x = activityPreview(d);
    const a = activityAward(d, true);
    return { total: x.total, award: a.xp, reason: a.msg, skill: x.skill, durFactor: x.durFactor, split: a.split };
  }
  function projection() {
    const d = draft || (draft = draftFor("reading"));
    return {
      presets: Object.keys(rules).map((id) => {
        const r = rules[id];
        return { id, name: r.name, icon: r.icon, base: r.base, skill: r.skill, primary: r.primary, secondary: r.secondary || "" };
      }),
      attributes: ATTRS.map((a) => ({ id: a.id, name: a.name, short: a.short, icon: a.icon, color: NativeTabKit.color(ATTR_COL[a.id]) })),
      qualities: QUALITIES,
      minMinutes: ECO.minMin,
      maxMinutes: MAX_MINUTES,
      durationRules: ECO_DURATION_RULES,
      draft: d,
      draftRevision: revision,
      preview: previewOf(d),
    };
  }

  /** take over whatever the web form holds (a prefill from Life Director, a link or the form itself), as a fresh draft */
  function fromForm() {
    try {
      draft = clean(activityDraftFromForm());
    } catch (_) {
      draft = draftFor("reading");
    }
    revision++;
  }
  const commands = {
    /** the screen changed the draft: keep it (without replacing what is being typed) so the next projection previews it */
    preview(p) {
      draft = clean(p.draft);
      return { ok: true };
    },
    /** save through the same owner function as the web form; one operation id pays out once */
    submit(p) {
      const d = clean(p.draft);
      const opId = String(p.operationId || "").slice(0, 80);
      if (!opId) return { ok: false, message: "Could not save: missing operation id" };
      // The XP celebration normally plays inside the save; here it would play under the native screen and on the same frames as the
      // switch to Overview, so it is held back and played once Overview is showing, from where the save button was.
      const { result: res, held } = holdCelebrations(() => recordActivity(Object.assign({}, d, { opId })));
      if (res.ok && held.length)
        setTimeout(() => {
          lastTap = { x: innerWidth / 2, y: innerHeight - 120 };
          held.forEach((h) => celebrateXP(h[0], h[1]));
        }, CELEBRATE_AFTER_MS);
      if (!res.ok) {
        draft = d;
        return { ok: false, message: res.message };
      }
      draft = draftFor(d.type);
      revision++;
      toast(res.message);
      return { ok: true, message: res.message, xp: res.xp, navigate: "dashboard" };
    },
  };

  NativeTabKit.create({
    id: "log",
    plugin: "NativeLog",
    capability: "log.projection.v1",
    setting: "nativeLog",
    toggle: "nativeLogToggle",
    projection,
    onAction() {},
    commands,
    beforeOpen: fromForm,
    globals: { open: "openNativeLog", enable: "setNativeLogEnabled", projection: "NativeLogTabProjection" },
  });

  window.NativeLogProjection = {
    snapshot: projection,
    previewOf,
    clean,
    draftFor,
    commands,
    setDraft(d) {
      draft = clean(d);
      revision++;
    },
  };
})();
