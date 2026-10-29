# Character native slice inventory
Baseline: bcb3daee08adf291c73bddd6ed49ff293d13b048 (build 172).

Authoritative calculations: chCompute/chDecay/chFocus/chArchetype/chCap (14-runtime), attrLevel/characterLevel/rebuildAttrXP (04-runtime), hero/attribute/skill presentation (15-runtime), renderAvatar/physical formatting (07-runtime). Do not recalculate gameplay in Kotlin.

Native presentation: hero/avatar/level/title/XP/next title; streak/total/week/luck; physical weight/BMI; radar with current/previous levels and focus markers; selected attribute detail, seven-day spark bars, status; three attribute sort modes; eight attribute cards; skills and show-all; benchmark display. Selection, comparison, sorting and show-all are local presentation state. Native inputs use projected values only.

Delegated actions in this read-only slice: log attribute/openAttr sheet, Luck/openLuck, benchmark addition/removal through the existing Classic Character form. Native does not send arbitrary XP/stat updates or call save(). These web edit dependencies prevent claiming complete native interaction parity. Default remains Classic; a persisted Native Character preview switch opts in.

Stores: core gameplay remains lifemax_rpg_prototype_v1; toggle in lifemax_android_prefs_v1. Projection uses volatile webSessionId + monotonic sequence, no durable save metadata. Source render invalidation plus on-resume refresh and a bounded visible-only periodic refresh cover legacy/time-based changes. Activity/WebView references remain in the plugin; ViewModel holds only repository/state.

A native view is attached in MainActivity, not a new Activity. Opening/fallback/closing leaves the retained web host and original save intact. Fallback restores original navigation/header and source handlers. Mutations remain disabled in the transport catalog.
