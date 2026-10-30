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
  /** what the award would be, worded the way the web form's preview words it */
  function previewOf(d) {
    const x = activityPreview(d);
    const split =
      x.secondary && x.secondary !== x.primary
        ? [
            { attr: x.primary, xp: Math.round(x.total * 0.7) },
            { attr: x.secondary, xp: x.total - Math.round(x.total * 0.7) },
          ]
        : [{ attr: x.primary, xp: x.total }];
    return { total: x.total, skill: x.skill, durFactor: x.durFactor, split, enough: x.total > 0 };
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
      maxMinutes: MAX_MINUTES,
      draft: d,
      draftRevision: revision,
      preview: previewOf(d),
    };
  }

  window.NativeLogProjection = {
    snapshot: projection,
    previewOf,
    clean,
    draftFor,
    setDraft(d) {
      draft = clean(d);
      revision++;
    },
  };
})();
