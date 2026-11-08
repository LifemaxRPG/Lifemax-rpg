/* The Life Director time and hash kernel's golden cases: what tools/gen_ld_kernel_golden.js records and the native kernel
   (android-wrapper/native/src/main/java/com/lifemax/rpg/core/lifedirector) must reproduce. Rules are LIFE_DIRECTOR_ENGINE_SPEC.md's.

   Every case is {id, family, rule, fn, args}; the recorder adds out. The definitions use no Intl and no time zone: every instant is
   built with Date.UTC from the table of 2026 and 2027 clock changes below, so the same cases come out of every runner, and the
   recorded outputs (not this file) say what the zone does. The test checks the table against the recorded offsets.
   A number the JSON text cannot hold (NaN, Infinity, -0) is written {$num: 'NaN' | 'Infinity' | '-Infinity' | '-0'}. */
'use strict';

/* The zone matrix (WP-LD1), then the plan's extra zones with half-hour shifts or unusual offsets. */
const MATRIX = ['UTC', 'America/New_York', 'America/Phoenix', 'America/Los_Angeles', 'America/Santiago', 'Europe/London', 'Asia/Kolkata',
  'Australia/Sydney', 'Pacific/Apia', 'Pacific/Kiritimati', 'Etc/GMT+12'];
const EXTRA = ['Australia/Lord_Howe', 'Asia/Kathmandu', 'Pacific/Auckland', 'America/Sao_Paulo'];
const ZONES = MATRIX.concat(EXTRA);
/* Zones without a clock change in 2025-2028: their offset in minutes. */
const FIXED = { 'UTC': 0, 'America/Phoenix': -420, 'Asia/Kolkata': 330, 'Pacific/Apia': 780, 'Pacific/Kiritimati': 840, 'Etc/GMT+12': -720,
  'Asia/Kathmandu': 345, 'America/Sao_Paulo': -180 };
/* Every clock change of 2026 and 2027 in the zones that have one: [zone, the instant (UTC), offset before, offset after] in minutes. */
const CHANGES = [
  ['America/New_York', '2026-03-08T07:00', -300, -240], ['America/New_York', '2026-11-01T06:00', -240, -300],
  ['America/New_York', '2027-03-14T07:00', -300, -240], ['America/New_York', '2027-11-07T06:00', -240, -300],
  ['America/Los_Angeles', '2026-03-08T10:00', -480, -420], ['America/Los_Angeles', '2026-11-01T09:00', -420, -480],
  ['America/Los_Angeles', '2027-03-14T10:00', -480, -420], ['America/Los_Angeles', '2027-11-07T09:00', -420, -480],
  ['America/Santiago', '2026-04-05T03:00', -180, -240], ['America/Santiago', '2026-09-06T04:00', -240, -180],
  ['America/Santiago', '2027-04-04T03:00', -180, -240], ['America/Santiago', '2027-09-05T04:00', -240, -180],
  ['Europe/London', '2026-03-29T01:00', 0, 60], ['Europe/London', '2026-10-25T01:00', 60, 0],
  ['Europe/London', '2027-03-28T01:00', 0, 60], ['Europe/London', '2027-10-31T01:00', 60, 0],
  ['Australia/Sydney', '2026-04-04T16:00', 660, 600], ['Australia/Sydney', '2026-10-03T16:00', 600, 660],
  ['Australia/Sydney', '2027-04-03T16:00', 660, 600], ['Australia/Sydney', '2027-10-02T16:00', 600, 660],
  ['Australia/Lord_Howe', '2026-04-04T15:00', 660, 630], ['Australia/Lord_Howe', '2026-10-03T15:30', 630, 660],
  ['Australia/Lord_Howe', '2027-04-03T15:00', 660, 630], ['Australia/Lord_Howe', '2027-10-02T15:30', 630, 660],
  ['Pacific/Auckland', '2026-04-04T14:00', 780, 720], ['Pacific/Auckland', '2026-09-26T14:00', 720, 780],
  ['Pacific/Auckland', '2027-04-03T14:00', 780, 720], ['Pacific/Auckland', '2027-09-25T14:00', 720, 780],
].map(([zone, at, before, after]) => ({ zone, at: Date.parse(at + ':00Z') / 1000, before, after }));

/* Names for the zone and alias families: every zone id of the JVM's tzdb and of the page's Intl, the tz database's links, and the
   legacy names Intl still accepts, then names that are not zones or are spelled wrongly. */
const NAMES = (
  'ACT AET AGT ART AST Africa/Abidjan Africa/Accra Africa/Addis_Ababa Africa/Algiers Africa/Asmara Africa/Asmera Africa/Bamako ' +
  'Africa/Bangui Africa/Banjul Africa/Bissau Africa/Blantyre Africa/Brazzaville Africa/Bujumbura Africa/Cairo Africa/Casablanca ' +
  'Africa/Ceuta Africa/Conakry Africa/Dakar Africa/Dar_es_Salaam Africa/Djibouti Africa/Douala Africa/El_Aaiun Africa/Freetown ' +
  'Africa/Gaborone Africa/Harare Africa/Johannesburg Africa/Juba Africa/Kampala Africa/Khartoum Africa/Kigali Africa/Kinshasa ' +
  'Africa/Lagos Africa/Libreville Africa/Lome Africa/Luanda Africa/Lubumbashi Africa/Lusaka Africa/Malabo Africa/Maputo ' +
  'Africa/Maseru Africa/Mbabane Africa/Mogadishu Africa/Monrovia Africa/Nairobi Africa/Ndjamena Africa/Niamey Africa/Nouakchott ' +
  'Africa/Ouagadougou Africa/Porto-Novo Africa/Sao_Tome Africa/Timbuktu Africa/Tripoli Africa/Tunis Africa/Windhoek America/Adak ' +
  'America/Anchorage America/Anguilla America/Antigua America/Araguaina America/Argentina/Buenos_Aires America/Argentina/Catamarca ' +
  'America/Argentina/ComodRivadavia America/Argentina/Cordoba America/Argentina/Jujuy America/Argentina/La_Rioja ' +
  'America/Argentina/Mendoza America/Argentina/Rio_Gallegos America/Argentina/Salta America/Argentina/San_Juan ' +
  'America/Argentina/San_Luis America/Argentina/Tucuman America/Argentina/Ushuaia America/Aruba America/Asuncion America/Atikokan ' +
  'America/Atka America/Bahia America/Bahia_Banderas America/Barbados America/Belem America/Belize America/Blanc-Sablon ' +
  'America/Boa_Vista America/Bogota America/Boise America/Buenos_Aires America/Cambridge_Bay America/Campo_Grande America/Cancun ' +
  'America/Caracas America/Catamarca America/Cayenne America/Cayman America/Chicago America/Chihuahua America/Ciudad_Juarez ' +
  'America/Coral_Harbour America/Cordoba America/Costa_Rica America/Coyhaique America/Creston America/Cuiaba America/Curacao ' +
  'America/Danmarkshavn America/Dawson America/Dawson_Creek America/Denver America/Detroit America/Dominica America/Edmonton ' +
  'America/Eirunepe America/El_Salvador America/Ensenada America/Fort_Nelson America/Fort_Wayne America/Fortaleza ' +
  'America/Glace_Bay America/Godthab America/Goose_Bay America/Grand_Turk America/Grenada America/Guadeloupe America/Guatemala ' +
  'America/Guayaquil America/Guyana America/Halifax America/Havana America/Hermosillo America/Indiana/Indianapolis ' +
  'America/Indiana/Knox America/Indiana/Marengo America/Indiana/Petersburg America/Indiana/Tell_City America/Indiana/Vevay ' +
  'America/Indiana/Vincennes America/Indiana/Winamac America/Indianapolis America/Inuvik America/Iqaluit America/Jamaica ' +
  'America/Jujuy America/Juneau America/Kentucky/Louisville America/Kentucky/Monticello America/Knox_IN America/Kralendijk ' +
  'America/La_Paz America/Lima America/Los_Angeles America/Louisville America/Lower_Princes America/Maceio America/Managua ' +
  'America/Manaus America/Marigot America/Martinique America/Matamoros America/Mazatlan America/Mendoza America/Menominee ' +
  'America/Merida America/Metlakatla America/Mexico_City America/Miquelon America/Moncton America/Monterrey America/Montevideo ' +
  'America/Montreal America/Montserrat America/Nassau America/New_York America/Nipigon America/Nome America/Noronha ' +
  'America/North_Dakota/Beulah America/North_Dakota/Center America/North_Dakota/New_Salem America/Nuuk America/Ojinaga ' +
  'America/Panama America/Pangnirtung America/Paramaribo America/Phoenix America/Port-au-Prince America/Port_of_Spain ' +
  'America/Porto_Acre America/Porto_Velho America/Puerto_Rico America/Punta_Arenas America/Rainy_River America/Rankin_Inlet ' +
  'America/Recife America/Regina America/Resolute America/Rio_Branco America/Rosario America/Santa_Isabel America/Santarem ' +
  'America/Santiago America/Santo_Domingo America/Sao_Paulo America/Scoresbysund America/Shiprock America/Sitka ' +
  'America/St_Barthelemy America/St_Johns America/St_Kitts America/St_Lucia America/St_Thomas America/St_Vincent ' +
  'America/Swift_Current America/Tegucigalpa America/Thule America/Thunder_Bay America/Tijuana America/Toronto America/Tortola ' +
  'America/Vancouver America/Virgin America/Whitehorse America/Winnipeg America/Yakutat America/Yellowknife Antarctica/Casey ' +
  'Antarctica/Davis Antarctica/DumontDUrville Antarctica/Macquarie Antarctica/Mawson Antarctica/McMurdo Antarctica/Palmer ' +
  'Antarctica/Rothera Antarctica/South_Pole Antarctica/Syowa Antarctica/Troll Antarctica/Vostok Arctic/Longyearbyen Asia/Aden ' +
  'Asia/Almaty Asia/Amman Asia/Anadyr Asia/Aqtau Asia/Aqtobe Asia/Ashgabat Asia/Ashkhabad Asia/Atyrau Asia/Baghdad Asia/Bahrain ' +
  'Asia/Baku Asia/Bangkok Asia/Barnaul Asia/Beirut Asia/Bishkek Asia/Brunei Asia/Calcutta Asia/Chita Asia/Choibalsan ' +
  'Asia/Chongqing Asia/Chungking Asia/Colombo Asia/Dacca Asia/Damascus Asia/Dhaka Asia/Dili Asia/Dubai Asia/Dushanbe ' +
  'Asia/Famagusta Asia/Gaza Asia/Harbin Asia/Hebron Asia/Ho_Chi_Minh Asia/Hong_Kong Asia/Hovd Asia/Irkutsk Asia/Istanbul ' +
  'Asia/Jakarta Asia/Jayapura Asia/Jerusalem Asia/Kabul Asia/Kamchatka Asia/Karachi Asia/Kashgar Asia/Kathmandu Asia/Katmandu ' +
  'Asia/Khandyga Asia/Kolkata Asia/Krasnoyarsk Asia/Kuala_Lumpur Asia/Kuching Asia/Kuwait Asia/Macao Asia/Macau Asia/Magadan ' +
  'Asia/Makassar Asia/Manila Asia/Muscat Asia/Nicosia Asia/Novokuznetsk Asia/Novosibirsk Asia/Omsk Asia/Oral Asia/Phnom_Penh ' +
  'Asia/Pontianak Asia/Pyongyang Asia/Qatar Asia/Qostanay Asia/Qyzylorda Asia/Rangoon Asia/Riyadh Asia/Saigon Asia/Sakhalin ' +
  'Asia/Samarkand Asia/Seoul Asia/Shanghai Asia/Singapore Asia/Srednekolymsk Asia/Taipei Asia/Tashkent Asia/Tbilisi Asia/Tehran ' +
  'Asia/Tel_Aviv Asia/Thimbu Asia/Thimphu Asia/Tokyo Asia/Tomsk Asia/Ujung_Pandang Asia/Ulaanbaatar Asia/Ulan_Bator Asia/Urumqi ' +
  'Asia/Ust-Nera Asia/Vientiane Asia/Vladivostok Asia/Yakutsk Asia/Yangon Asia/Yekaterinburg Asia/Yerevan Atlantic/Azores ' +
  'Atlantic/Bermuda Atlantic/Canary Atlantic/Cape_Verde Atlantic/Faeroe Atlantic/Faroe Atlantic/Jan_Mayen Atlantic/Madeira ' +
  'Atlantic/Reykjavik Atlantic/South_Georgia Atlantic/St_Helena Atlantic/Stanley Australia/ACT Australia/Adelaide ' +
  'Australia/Brisbane Australia/Broken_Hill Australia/Canberra Australia/Currie Australia/Darwin Australia/Eucla Australia/Hobart ' +
  'Australia/LHI Australia/Lindeman Australia/Lord_Howe Australia/Melbourne Australia/NSW Australia/North Australia/Perth ' +
  'Australia/Queensland Australia/South Australia/Sydney Australia/Tasmania Australia/Victoria Australia/West ' +
  'Australia/Yancowinna BET BST Brazil/Acre Brazil/DeNoronha Brazil/East Brazil/West CAT CET CNT CST CST6CDT CTT Canada/Atlantic ' +
  'Canada/Central Canada/East-Saskatchewan Canada/Eastern Canada/Mountain Canada/Newfoundland Canada/Pacific Canada/Saskatchewan ' +
  'Canada/Yukon Chile/Continental Chile/EasterIsland Cuba EAT ECT EET EST EST5EDT Egypt Eire Etc/GMT Etc/GMT+0 Etc/GMT+1 ' +
  'Etc/GMT+10 Etc/GMT+11 Etc/GMT+12 Etc/GMT+2 Etc/GMT+3 Etc/GMT+4 Etc/GMT+5 Etc/GMT+6 Etc/GMT+7 Etc/GMT+8 Etc/GMT+9 Etc/GMT-0 ' +
  'Etc/GMT-1 Etc/GMT-10 Etc/GMT-11 Etc/GMT-12 Etc/GMT-13 Etc/GMT-14 Etc/GMT-2 Etc/GMT-3 Etc/GMT-4 Etc/GMT-5 Etc/GMT-6 Etc/GMT-7 ' +
  'Etc/GMT-8 Etc/GMT-9 Etc/GMT0 Etc/Greenwich Etc/UCT Etc/UTC Etc/Universal Etc/Zulu Europe/Amsterdam Europe/Andorra ' +
  'Europe/Astrakhan Europe/Athens Europe/Belfast Europe/Belgrade Europe/Berlin Europe/Bratislava Europe/Brussels Europe/Bucharest ' +
  'Europe/Budapest Europe/Busingen Europe/Chisinau Europe/Copenhagen Europe/Dublin Europe/Gibraltar Europe/Guernsey ' +
  'Europe/Helsinki Europe/Isle_of_Man Europe/Istanbul Europe/Jersey Europe/Kaliningrad Europe/Kiev Europe/Kirov Europe/Kyiv ' +
  'Europe/Lisbon Europe/Ljubljana Europe/London Europe/Luxembourg Europe/Madrid Europe/Malta Europe/Mariehamn Europe/Minsk ' +
  'Europe/Monaco Europe/Moscow Europe/Nicosia Europe/Oslo Europe/Paris Europe/Podgorica Europe/Prague Europe/Riga Europe/Rome ' +
  'Europe/Samara Europe/San_Marino Europe/Sarajevo Europe/Saratov Europe/Simferopol Europe/Skopje Europe/Sofia Europe/Stockholm ' +
  'Europe/Tallinn Europe/Tirane Europe/Tiraspol Europe/Ulyanovsk Europe/Uzhgorod Europe/Vaduz Europe/Vatican Europe/Vienna ' +
  'Europe/Vilnius Europe/Volgograd Europe/Warsaw Europe/Zagreb Europe/Zaporozhye Europe/Zurich Factory GB GB-Eire GMT GMT+0 GMT-0 ' +
  'GMT0 Greenwich HST Hongkong IET IST Iceland Indian/Antananarivo Indian/Chagos Indian/Christmas Indian/Cocos Indian/Comoro ' +
  'Indian/Kerguelen Indian/Mahe Indian/Maldives Indian/Mauritius Indian/Mayotte Indian/Reunion Iran Israel JST Jamaica Japan ' +
  'Kwajalein Libya MET MIT MST MST7MDT Mexico/BajaNorte Mexico/BajaSur Mexico/General NET NST NZ NZ-CHAT Navajo PLT PNT PRC PRT ' +
  'PST PST8PDT Pacific/Apia Pacific/Auckland Pacific/Bougainville Pacific/Chatham Pacific/Chuuk Pacific/Easter Pacific/Efate ' +
  'Pacific/Enderbury Pacific/Fakaofo Pacific/Fiji Pacific/Funafuti Pacific/Galapagos Pacific/Gambier Pacific/Guadalcanal ' +
  'Pacific/Guam Pacific/Honolulu Pacific/Johnston Pacific/Kanton Pacific/Kiritimati Pacific/Kosrae Pacific/Kwajalein ' +
  'Pacific/Majuro Pacific/Marquesas Pacific/Midway Pacific/Nauru Pacific/Niue Pacific/Norfolk Pacific/Noumea Pacific/Pago_Pago ' +
  'Pacific/Palau Pacific/Pitcairn Pacific/Pohnpei Pacific/Ponape Pacific/Port_Moresby Pacific/Rarotonga Pacific/Saipan ' +
  'Pacific/Samoa Pacific/Tahiti Pacific/Tarawa Pacific/Tongatapu Pacific/Truk Pacific/Wake Pacific/Wallis Pacific/Yap Poland ' +
  'Portugal ROC ROK SST Singapore SystemV/AST4 SystemV/AST4ADT SystemV/CST6 SystemV/CST6CDT SystemV/EST5 SystemV/EST5EDT ' +
  'SystemV/HST10 SystemV/MST7 SystemV/MST7MDT SystemV/PST8 SystemV/PST8PDT SystemV/YST9 SystemV/YST9YDT Turkey UCT US/Alaska ' +
  'US/Aleutian US/Arizona US/Central US/East-Indiana US/Eastern US/Hawaii US/Indiana-Starke US/Michigan US/Mountain US/Pacific ' +
  'US/Pacific-New US/Samoa UTC Universal VST W-SU WET Zulu'
).split(' ').concat([
  'utc', 'america/new_york', 'AMERICA/NEW_YORK', 'Europe/LONDON', 'us/eastern', 'aSiA/kOlKaTa', 'etc/gmt+12', 'Pacific/apia', 'est', 'systemv/est5',
  '+05:30', '-03:00', '+00:00', '-00:00', '+14:00', '-12:00', '+23:59', '-23:59', '+24:00', '+0530', '+05', '-1', '05:30', '+5:30', '+05:30:00',
  '+05:60', '+5', '−05:00', '−', 'UTC+05:30', 'GMT+5', 'UTC+5', 'Etc/GMT+13', 'Etc/GMT-15', 'Z', 'z', '', ' ', 'Mars/Phobos',
  'America/New York', 'America/New_York ', ' UTC', 'America//New_York', 'America/', 'Etc/Unknown', 'localtime', 'posixrules', 'EST5', 'UTC0',
  'Asia/Riyadh87', 'ＵＴＣ', 'İstanbul', 'Europe/Kyiv\u0000', 'América/Bogotá', 'America/New_York/Extra'
]);

/* 32-bit, 53-bit and text edge strings for the hash: empty, ASCII, every UTF-16 shape (non-BMP pairs, lone and reversed surrogates,
   U+FFFF, NUL), long texts, and the shapes the engine hashes (event and task keys). */
const kb = n => Array.from({ length: n }, (_, i) => String.fromCharCode(32 + (i * 7) % 95)).join('');
const STRINGS = ['', 'a', 'b', 'ab', 'ba', 'abc', 'hello world', ' ', '\u0000', '\u0000\u0000', '0', '1', '-1', 'undefined', 'null', 'é', 'é',
  'ÿ', 'Ā', '中文', 'Ω', '\u{1F600}', '\u{1F468}‍\u{1F469}‍\u{1F467}', '\u{1F1EC}\u{1F1E7}', '\u{10FFFF}', '\u{10000}',
  '\uD83D', '\uDE00', '\uDE00\uD83D', 'a\uD800b', '￿', '￾', '﻿', '\r\n\t', '"\\', '  ', kb(64), kb(1024), kb(4096),
  'x'.repeat(1000), '\u{1F600}'.repeat(300), 'DONE:player:i_00000000000aaaaaaaaaaa', 'ADHOC:lm_abc123def456:f1e2d3c4-b5a6-4789-8abc-def012345678',
  'residual:i_0123456789a0123456789a', 'QUEST_BOUNTY:q-12', 'MISS_PENALTY:i_x', 'SKIPPED:i_x:1793430000:3', '2026-10-28|N', 'goal:g-1:strength:0',
  'goal:g-1:strength:10', 'goal:g-1:strength:100', 'Life Director ’s · ★ ×'];

/* ---------- small helpers (no Intl) ---------- */
const utc = (y, mo, d, h = 0, mi = 0, s = 0) => Date.UTC(y, mo - 1, d, h, mi, s) / 1000;
const iso = t => new Date(t * 1000).toISOString().slice(0, 19).replace(/[-:]/g, '');
/* The offset in minutes at instant t by the table (the first change's "before" before it, the last's "after" after it). */
function offsetAt(zone, t) {
  if (zone in FIXED) return FIXED[zone];
  let off = null;
  for (const c of CHANGES.filter(c => c.zone === zone)) { if (off === null) off = c.before; if (t >= c.at) off = c.after; }
  return off;
}
/* The instant of local wall time (y, mo, d, minute) by the table; only used to choose interesting instants. */
const localInstant = (zone, y, mo, d, min = 0) => { const w = utc(y, mo, d, 0, min); return w - offsetAt(zone, w - offsetAt(zone, w) * 60) * 60; };
const ymdOf = t => { const x = new Date(t * 1000); return [x.getUTCFullYear(), x.getUTCMonth() + 1, x.getUTCDate()]; };
/* Seeded generator for the fuzz family (xorshift32; deliberately not the engine's mulberry32). */
function fuzzRandom(seed) {
  let x = seed >>> 0 || 1;
  return () => { x ^= x << 13; x >>>= 0; x ^= x >>> 17; x ^= x << 5; x >>>= 0; return x / 4294967296; };
}
const num = v => (Number.isNaN(v) ? { $num: 'NaN' } : v === Infinity ? { $num: 'Infinity' } : v === -Infinity ? { $num: '-Infinity' } : Object.is(v, -0) ? { $num: '-0' } : v);
/* The kind of a wall time on a change's day: inside the spring gap, inside the autumn fold, or neither. */
function wallKind(zone, w) {
  for (const c of CHANGES.filter(c => c.zone === zone)) {
    const a = c.at + c.before * 60, b = c.at + c.after * 60;
    if (c.before < c.after && w >= a && w < b) return 'gap';
    if (c.before > c.after && w >= b && w < a) return 'fold';
  }
  return 'plain';
}
const WALL_RULE = { plain: 'LDT-6 LDT-7', gap: 'LDT-6 LDT-8', fold: 'LDT-6 LDT-9' };

function defineCases() {
  const cases = [], ids = new Set();
  const add = (given, rule, fn, args, tag) => {
    // a case whose answer rests on a tz-fragile zone's data goes to its own family (see TZ_FRAGILE), its id keeping where it came from
    const fragile = !ZONE_FREE.includes(given) && TZ_FRAGILE.includes([...args].reverse().find(a => typeof a === 'string'));
    const family = fragile ? 'tzfragile' : given;
    let id = (fragile ? 'tzfragile/' + given : given) + '/' + fn + '/' + tag;
    for (let k = 2; ids.has(id); k++) id = family + '/' + fn + '/' + tag + '#' + k;
    ids.add(id);
    cases.push({ id, family, rule, fn, args });
  };
  const hashTag = (s, i) => i + ':' + (s.length > 24 ? s.length + 'u' : JSON.stringify(s).slice(1, -1).replace(/[^\x20-\x7e]/g, '?'));

  /* hash: cyrb53 itself, at the engine's seeds and at the 32-bit edges of the seed */
  STRINGS.forEach((s, i) => { for (const seed of [0, 1, 7]) add('hash', 'LDH-1 LDH-2 LDN-6', 'cyrb53', [s, seed], hashTag(s, i) + '@' + seed); });
  for (const seed of [-1, 2147483647, -2147483648, 2147483648, 4294967295, 4294967303, 0xdeadbeef, 0x41c6ce57, 1.9, -1.9, 9007199254740991])
    for (const s of ['', 'a', '\u{1F600}', kb(64)]) add('hash', 'LDH-1 LDN-5', 'cyrb53', [s, seed], hashTag(s, 0) + '@' + seed);
  /* stable: the ~106-bit ids */
  STRINGS.forEach((s, i) => add('stable', 'LDH-3 LDN-6', 'stableId', ['t', s], hashTag(s, i)));
  for (const p of ['', 'ev', 'chg', 'in', 'p', 'bounty', 'pen', 'i', 'goal', '\u{1F600}', 'a_b'])
    for (const s of ['', 'x', 'DONE:player:i_1', '\uD83D']) add('stable', 'LDH-3', 'stableId', [p, s], p + '|' + hashTag(s, 0));
  /* event: completion event ids (scheduled, revisions, ad-hoc, and the refusal) */
  for (const pid of ['player', 'lm_0123456789ab', '', '\u{1F600}'])
    for (const inst of ['i_00000000000aaaaaaaaaaa', 'manual-op-1', '0'])
      for (const rev of [null, 0, 1, 2, 10]) add('event', 'LDH-5 LDH-3', 'completionEventId', [pid, inst, null, rev], pid + '|' + inst + '|' + rev);
  for (const pid of ['player', 'lm_0123456789ab'])
    for (const [inst, client] of [[null, 'c-1'], ['', 'c-1'], [null, 'f1e2d3c4-b5a6-4789-8abc-def012345678'], [null, '\u{1F600}'], [null, null], ['', ''], [null, '']])
      add('event', 'LDH-5', 'completionEventId', [pid, inst, client, null], pid + '|' + inst + '|' + client);
  /* rng: mulberry32's first outputs */
  for (const seed of [0, 1, 2, 3, 7, 13, 20, 42, 1000, 123456789, 2147483647, 2147483648, 4294967295, 4294967296, 4294967301, -1, -123, 0xdeadbeef, 1.5, -1.5])
    add('rng', 'LDH-7 LDN-5', 'mulberry32', [seed, 8], String(seed));
  for (const seed of [1, 7]) add('rng', 'LDH-7', 'mulberry32', [seed, 200], 'long-' + seed);

  /* num: ceilTo15, the 15-minute duration rounding */
  for (const m of [-30, -15, -1, -0.5, 0, 0.0001, 1, 7.5, 14, 14.999, 15, 15.0000001, 16, 29, 30, 31, 44.9, 45, 59, 60, 61, 89, 90, 91, 119.5, 120,
    479, 480, 481, 1439, 1440, 1441, 2879, 100000, 1e15, 1 / 3, 2 / 3 * 45])
    add('num', 'LDT-20', 'ceilTo15', [m], String(m));
  for (const m of [NaN, Infinity, -Infinity, -0]) add('num', 'LDT-20 LDN-1', 'ceilTo15', [num(m)], String(m));

  /* zone: which names are time zones; alias: what offset each name gives in January and July 2026 */
  NAMES.forEach((n, i) => add('zone', 'LDT-2 LDT-3', 'isValidTimeZone', [n], i + ':' + hashTag(n, 0).slice(2)));
  NAMES.forEach((n, i) => {
    add('alias', 'LDT-3 LDT-5', 'tzOffsetSec', [utc(2026, 1, 15, 12), n], i + ':' + hashTag(n, 0).slice(2) + '@jan');
    add('alias', 'LDT-3 LDT-5', 'tzOffsetSec', [utc(2026, 7, 15, 12), n], i + ':' + hashTag(n, 0).slice(2) + '@jul');
  });

  /* parts and offset: around every clock change (30-minute steps over a day, every minute for 10 minutes, the second either side) */
  for (const c of CHANGES) {
    const full = c.at < utc(2027, 1, 1), at = [];
    if (full) {
      for (let k = -26; k <= 26; k++) at.push(c.at + k * 1800);
      for (let k = -10; k <= 10; k++) if (k % 30) at.push(c.at + k * 60);
    } else for (let k = -6; k <= 6; k++) at.push(c.at + k * 3600);
    at.push(c.at - 1, c.at + 1);
    for (const t of [...new Set(at)].sort((x, y) => x - y)) {
      add('parts', 'LDT-4', 'localParts', [t, c.zone], c.zone + '@' + iso(t));
      add('offset', 'LDT-5', 'tzOffsetSec', [t, c.zone], c.zone + '@' + iso(t));
    }
  }
  /* parts and offset: local midnights of month, year and leap-day boundaries in every zone, the second either side */
  const BOUNDS = [[2025, 12, 31], [2026, 1, 1], [2026, 1, 31], [2026, 2, 1], [2026, 2, 28], [2026, 3, 1], [2026, 6, 30], [2026, 7, 1], [2026, 12, 31],
    [2027, 1, 1], [2027, 12, 31], [2028, 1, 1], [2024, 2, 29], [2028, 2, 29], [2028, 3, 1]];
  for (const zone of ZONES) for (const [y, mo, d] of BOUNDS) {
    const m = localInstant(zone, y, mo, d);
    for (const t of [m - 1, m, m + 1, m + 43200]) {
      add('parts', 'LDT-4 LDT-21', 'localParts', [t, zone], zone + '@' + iso(t));
      if (t !== m + 1) add('offset', 'LDT-5', 'tzOffsetSec', [t, zone], zone + '@' + iso(t));
    }
  }
  for (const [t, why] of [[0, 'epoch'], [-1, 'before-epoch'], [946684800, 'y2k'], [2147483647, '2038'], [2147483648, '2038+1'], [4102444800, '2100'],
    [1774000000.5, 'half-second'], [1774000000.999, 'ms']])
    for (const zone of MATRIX) {
      add('parts', 'LDT-4 LDN-2', 'localParts', [t, zone], zone + '@' + why);
      add('offset', 'LDT-5 LDN-2', 'tzOffsetSec', [t, zone], zone + '@' + why);
    }

  /* wall: zonedToEpoch on the dates around every clock change; every 15 minutes, every minute of the gap or fold, overnight minutes */
  for (const c of CHANGES) {
    const full = c.at < utc(2027, 1, 1);
    const dates = new Set([c.at + c.before * 60 - 60, c.at + c.after * 60, c.at + c.before * 60, c.at + c.after * 60 - 60].map(t => ymdOf(t).join('-')));
    for (const key of dates) {
      const [y, mo, d] = key.split('-').map(Number), mins = new Set();
      for (let m = 0; m <= 1440; m += full ? 15 : 60) mins.add(m);
      const a = c.at + c.before * 60, b = c.at + c.after * 60;
      for (let w = Math.min(a, b) - 120; w <= Math.max(a, b) + 120; w += full ? 60 : 900) { const m = (w - utc(y, mo, d)) / 60; if (m >= 0 && m <= 2880) mins.add(m); }
      if (full) for (let m = 1440; m <= 2880; m += 60) mins.add(m);
      for (const m of [...mins].sort((x, z) => x - z)) {
        const kind = wallKind(c.zone, utc(y, mo, d, 0, m));
        add('wall', WALL_RULE[kind], 'zonedToEpoch', [y, mo, d, m, c.zone], c.zone + '@' + key + '+' + m + (kind === 'plain' ? '' : '-' + kind));
      }
    }
  }
  for (const zone of ZONES) for (const [y, mo, d] of BOUNDS) for (const m of [0, 1, 719, 1439, 1440, 1441, 2879])
    add('wall', 'LDT-6 LDT-7', 'zonedToEpoch', [y, mo, d, m, zone], zone + '@' + y + '-' + mo + '-' + d + '+' + m);
  /* wall edges: the date rolled by Date.UTC (day 0, day 32, February 30, month 13), negative and fractional minutes, two-digit years */
  for (const zone of ['UTC', 'America/New_York', 'Australia/Sydney', 'Asia/Kolkata'])
    for (const [y, mo, d, m] of [[2026, 3, 0, 600], [2026, 1, 32, 600], [2026, 2, 30, 600], [2026, 13, 1, 600], [2026, 0, 1, 600], [2026, 3, 8, -60],
      [2026, 3, 8, -1440], [2026, 3, 8, 90.5], [2026, 3, 8, 150.9], [2026, 3, 8, -0.5], [2026, 3, 8, 4320], [26, 3, 8, 600], [99, 12, 31, 600], [100, 1, 1, 600],
      [0, 1, 1, 0], [2026.7, 3, 8, 600], [2026, 3.5, 8, 600], [2026, 3, 8.9, 600]])
      add('wall', 'LDT-6 LDN-3', 'zonedToEpoch', [y, mo, d, m, zone], zone + '@' + [y, mo, d, m].join('/'));

  /* cal: calendar arithmetic without a zone */
  const YMDS = [[2026, 1, 1], [2026, 1, 31], [2026, 2, 28], [2024, 2, 28], [2024, 2, 29], [2028, 2, 29], [2100, 2, 28], [2000, 2, 28], [2026, 12, 31],
    [2027, 1, 1], [2026, 10, 26], [1970, 1, 1], [1969, 12, 31], [2026, 3, 8], [2026, 9, 6]];
  for (const [y, mo, d] of YMDS) for (const n of [-400, -366, -365, -31, -1, 0, 1, 6, 7, 28, 30, 31, 365, 366, 1461])
    add('cal', 'LDT-11', 'ymdAdd', [{ y, mo, d }, n], y + '-' + mo + '-' + d + (n < 0 ? '' : '+') + n);
  for (const [y, mo, d] of [[2026, 1, 0], [2026, 2, 30], [2026, 13, 1], [2026, 0, 15], [2026, -1, 15], [2026, 1, -5], [26, 1, 1], [99, 12, 31], [100, 1, 1], [0, 1, 1],
    [-1, 1, 1], [10000, 1, 1], [275760, 9, 13], [275760, 9, 14]])
    for (const n of [0, 1, -1]) add('cal', 'LDT-11 LDN-3', 'ymdAdd', [{ y, mo, d }, n], 'odd:' + y + '-' + mo + '-' + d + '+' + n);
  for (const v of [[2026, 1, 1], [2026, 10, 9], [2026, 12, 31], [999, 1, 1], [5, 1, 2], [12345, 6, 7], [-44, 3, 15], [2026, 100, 100], [0, 0, 0]])
    add('cal', 'LDT-12', 'ymdKey', [{ y: v[0], mo: v[1], d: v[2] }], v.join('-'));
  add('cal', 'LDT-12 LDN-1', 'ymdKey', [{ y: num(NaN), mo: num(NaN), d: num(NaN) }], 'NaN');
  for (const k of ['2026-01-01', '2026-10-26', '2024-02-29', '2026-02-29', '2026-02-30', '2026-13-01', '2026-00-10', '2026-1-5', '0026-01-05', '0099-12-31',
    '0100-01-01', '2026--5', '2026-01', '2026', '', '-', '--', 'x', '2026-01-05T10:00', ' 2026-01-05', '2026 -01-05', '2026-01-05 ', '2026-0x0A-05',
    '2026-1e1-05', '2026-+3-05', '2026-01-05-07', '2026-01-5.5', '2026-01-.5', '2026-01-Infinity', ' 2026　-01-05', '+002026-01-05',
    '1970-01-01', '1969-12-31', '275760-09-13', '275760-09-14'])
    add('cal', 'LDT-13 LDN-4', 'parseYmd', [k], JSON.stringify(k).slice(1, -1).replace(/[^\x20-\x7e]/g, '?') || 'empty');
  const PAIRS = [[[2026, 1, 1], [2026, 1, 1]], [[2026, 1, 1], [2026, 1, 2]], [[2026, 1, 2], [2026, 1, 1]], [[2026, 3, 7], [2026, 3, 9]], [[2026, 10, 31], [2026, 11, 2]],
    [[2024, 2, 28], [2024, 3, 1]], [[2026, 2, 28], [2026, 3, 1]], [[2026, 1, 1], [2027, 1, 1]], [[2024, 1, 1], [2025, 1, 1]], [[1970, 1, 1], [2026, 10, 26]],
    [[2026, 10, 26], [1970, 1, 1]], [[2026, 1, 32], [2026, 2, 1]], [[2026, 13, 1], [2027, 1, 1]], [[26, 1, 1], [1926, 1, 1]], [[2026, 10, 26], [2026, 10, 5]],
    [[2100, 2, 28], [2100, 3, 1]], [[2000, 2, 28], [2000, 3, 1]]];
  for (const [a, b] of PAIRS) add('cal', 'LDT-14', 'daysBetween', [{ y: a[0], mo: a[1], d: a[2] }, { y: b[0], mo: b[1], d: b[2] }], a.join('-') + '..' + b.join('-'));
  for (const [anchor, n] of [['2026-10-26', 0], ['2026-10-26', 3], ['2025-12-31', 400], ['2026-03-08', -10], ['2026-02-30', 1]])
    add('cal', 'LDT-14 LDT-13', 'daysBetween', [{ $parse: anchor }, { y: 2026, mo: 10, d: 26 + n }], 'parsed:' + anchor + '+' + n);

  /* horizon: the plan's days in every zone, from several starts, across every 2026 clock change */
  for (const zone of ZONES) {
    for (const [what, t, days] of [['monday-0600', localInstant(zone, 2026, 10, 26, 360), 7], ['midnight', localInstant(zone, 2026, 10, 26), 7],
      ['before-midnight', localInstant(zone, 2026, 10, 26) - 1, 2], ['one-day', localInstant(zone, 2026, 6, 15, 600), 1], ['four-weeks', localInstant(zone, 2026, 2, 20, 720), 28],
      ['year-end', localInstant(zone, 2026, 12, 29, 1200), 7], ['zero-days', localInstant(zone, 2026, 6, 15, 600), 0]])
      add('horizon', 'LDT-15', 'buildHorizon', [t, days, zone], zone + '@' + what);
  }
  for (const c of CHANGES) if (c.at < utc(2027, 1, 1)) {
    const t = c.at - 30 * 3600;
    add('horizon', 'LDT-15 LDT-8 LDT-9', 'buildHorizon', [t, 3, c.zone], c.zone + '@' + iso(c.at));
    add('grid', 'LDT-16', 'grid', [t, 3, c.zone], c.zone + '@' + iso(c.at));
  }
  for (const zone of ZONES.filter(z => z in FIXED)) add('grid', 'LDT-16', 'grid', [localInstant(zone, 2026, 10, 26, 360), 2, zone], zone + '@plain');
  /* slot: the grid's slot of an epoch, rounded down and up, and the epoch of a slot */
  for (const H0 of [1793422800, 1793422800 + 1800, 0])
    for (const dt of [-1801, -900, -1, 0, 1, 450, 899, 900, 901, 1799, 86400, 86401, 0.5, -0.5]) {
      add('slot', 'LDT-17 LDT-1', 'slotOf', [H0, H0 + dt], H0 + '+' + dt);
      add('slot', 'LDT-17 LDT-1', 'slotCeil', [H0, H0 + dt], H0 + '+' + dt);
    }
  for (const H0 of [1793422800, 0]) for (const s of [-1, 0, 1, 4, 96, 672, 2688, 0.5]) add('slot', 'LDT-17 LDT-1', 'epochOf', [H0, s], H0 + '#' + s);
  add('slot', 'LDT-1', 'SLOT_SEC', [], 'value');
  add('slot', 'LDT-1', 'SLOTS_PER_HOUR', [], 'value');

  /* week: the next local midnight, the local Monday's key, and the host's week bounds, around midnights, Sundays and clock changes */
  for (const zone of ZONES) {
    const pts = [];
    for (const [y, mo, d] of [[2026, 10, 25], [2026, 10, 26], [2026, 10, 28], [2026, 10, 31], [2026, 12, 31], [2027, 1, 3], [2027, 1, 4]]) {
      const m = localInstant(zone, y, mo, d);
      pts.push([m - 1, y + '-' + mo + '-' + d + '-1s'], [m, y + '-' + mo + '-' + d], [m + 43200, y + '-' + mo + '-' + d + '+12h']);
    }
    for (const c of CHANGES.filter(c => c.zone === zone && c.at < utc(2027, 1, 1))) pts.push([c.at - 1, iso(c.at) + '-1s'], [c.at, iso(c.at)], [c.at + 3 * 86400, iso(c.at) + '+3d']);
    for (const [t, what] of pts) {
      add('week', 'LDT-18', 'dayEndAfter', [t, zone], zone + '@' + what);
      add('week', 'LDT-19 LDT-21', 'weekKey', [t, zone], zone + '@' + what);
      add('week', 'LDT-19 LDT-21', 'weekBounds', [t, zone], zone + '@' + what);
    }
  }

  /* fuzz: seeded instants, wall times and zones, and seeded strings for the hash */
  const rnd = fuzzRandom(20261108), pick = a => a[Math.floor(rnd() * a.length)];
  const FUZZ_ZONES = ZONES.concat(['America/Chicago', 'America/Denver', 'America/Anchorage', 'Pacific/Honolulu', 'America/St_Johns', 'America/Halifax',
    'America/Havana', 'America/Mexico_City', 'America/Bogota', 'America/Asuncion', 'America/Nuuk', 'America/Scoresbysund', 'Atlantic/Azores',
    'Europe/Dublin', 'Europe/Berlin', 'Europe/Paris', 'Europe/Moscow', 'Europe/Istanbul', 'Africa/Cairo', 'Africa/Casablanca', 'Africa/Johannesburg',
    'Asia/Tehran', 'Asia/Dubai', 'Asia/Karachi', 'Asia/Dhaka', 'Asia/Yangon', 'Asia/Shanghai', 'Asia/Tokyo', 'Asia/Jerusalem', 'Asia/Beirut',
    'Asia/Almaty', 'Asia/Pyongyang', 'Australia/Adelaide', 'Australia/Eucla', 'Australia/Perth', 'Australia/Darwin', 'Pacific/Chatham',
    'Pacific/Norfolk', 'Pacific/Fiji', 'Pacific/Tongatapu', 'Pacific/Marquesas', 'Pacific/Pago_Pago', 'Antarctica/Troll', 'Antarctica/Macquarie',
    'America/Argentina/Buenos_Aires', 'Etc/GMT-14', 'US/Eastern', 'Asia/Calcutta', 'EST5EDT', 'PST']);
  const lo = utc(2026, 1, 1), hi = utc(2028, 1, 1);
  for (let i = 0; i < 1600; i++) {
    const zone = pick(FUZZ_ZONES), kind = pick(['localParts', 'tzOffsetSec', 'zonedToEpoch', 'zonedToEpoch', 'dayEndAfter', 'weekKey', 'buildHorizon']);
    const near = rnd() < 0.4 ? pick(CHANGES.filter(c => c.zone === zone).map(c => c.at).concat([lo + Math.floor(rnd() * (hi - lo))])) : null;
    const t = near !== null ? near + Math.floor((rnd() * 2 - 1) * 2 * 3600) : lo + Math.floor(rnd() * (hi - lo));
    if (kind === 'zonedToEpoch') {
      const [y, mo, d] = ymdOf(t - 86400), m = rnd() < 0.5 ? Math.floor(rnd() * 2880) : Math.floor(rnd() * 192) * 15;
      add('fuzz', 'LDT-6', 'zonedToEpoch', [y, mo, d, m, zone], i + ':' + zone);
    } else if (kind === 'buildHorizon') add('fuzz', 'LDT-15', 'buildHorizon', [t, 1 + Math.floor(rnd() * 3), zone], i + ':' + zone);
    else add('fuzz', { localParts: 'LDT-4', tzOffsetSec: 'LDT-5', dayEndAfter: 'LDT-18', weekKey: 'LDT-19' }[kind], kind, [t, zone], i + ':' + zone);
  }
  const unit = () => {
    const r = rnd();
    if (r < 0.4) return String.fromCharCode(32 + Math.floor(rnd() * 95));
    if (r < 0.55) return String.fromCharCode(Math.floor(rnd() * 0x800));
    if (r < 0.7) return String.fromCharCode(0x800 + Math.floor(rnd() * 0xD000));
    if (r < 0.85) return String.fromCodePoint(0x10000 + Math.floor(rnd() * 0xFFFFF));
    if (r < 0.95) return String.fromCharCode(0xD800 + Math.floor(rnd() * 0x800)); // a lone surrogate
    return String.fromCharCode(0xE000 + Math.floor(rnd() * 0x2000));
  };
  for (let i = 0; i < 300; i++) {
    let s = ''; const n = Math.floor(rnd() * (rnd() < 0.1 ? 600 : 40));
    for (let k = 0; k < n; k++) s += unit();
    if (i % 2) add('fuzz', 'LDH-1 LDN-6', 'cyrb53', [s, pick([0, 1, 7, Math.floor(rnd() * 4294967296) - 2147483648])], 'h' + i);
    else add('fuzz', 'LDH-3 LDN-6', 'stableId', [pick(['t', 'ev', 'chg']), s], 's' + i);
  }
  return cases;
}

/* Zones whose answers depend on the tz database release rather than on a rule. Their cases form the family `tzfragile`: the web test
   still requires them byte for byte from Chromium, but a runner with other tz data (another node, the CI runner's JDK, a phone) may
   answer differently there, so node's check allows it when its tz version differs from the recording's, and the native replay only
   reports them and checks its own rules on them (KernelTzFragileTest). Evidence: offset transitions 2024-2029 of all 604 zone ids
   compared across five tz data sets (node 21: tz 2024a; node 22: 2025b; Chromium 153; the JVM: 2026b; the system's zoneinfo: 2026e),
   plus the CI runner's JDK (Java 21.0.13), which disagreed with Chromium on Casablanca. Every zone not listed had identical
   transitions in all five.
     Africa/Casablanca, Africa/El_Aaiun  Morocco: daylight saving suspended around Ramadan and predicted afresh by every release; 2026e
                                         ends the +01 offset in September 2026; the CI JDK differs too.
     America/Asuncion                    Paraguay's permanent -03 from tz 2024b (node 21 differs).
     America/Coyhaique                   a zone of its own from tz 2025a (node 21 lacks it).
     America/Vancouver, Canada/Pacific   British Columbia's permanent daylight time in the JVM's 2026b and 2026e, not in Chromium's data.
     Europe/Chisinau, Europe/Tiraspol    Moldova's change instants one hour apart between releases.
     Asia/Gaza, Asia/Hebron              not seen to differ, but their rules follow Ramadan and are revised in most releases.
     Factory                             the placeholder zone; some ICU builds carry it, some do not. */
const TZ_FRAGILE = ['Africa/Casablanca', 'Africa/El_Aaiun', 'America/Asuncion', 'America/Coyhaique', 'America/Vancouver', 'Canada/Pacific',
  'Europe/Chisinau', 'Europe/Tiraspol', 'Asia/Gaza', 'Asia/Hebron', 'Factory'];
/* Families whose answers need no zone. */
const ZONE_FREE = ['hash', 'stable', 'event', 'rng', 'num', 'cal', 'slot'];

/* The zone a case's answer depends on, or null for the zone-free families. */
const zoneOf = c => (ZONE_FREE.includes(c.family) ? null
  : [...c.args].reverse().find(a => typeof a === 'string') || null);

const FAMILIES = ['hash', 'stable', 'event', 'rng', 'num', 'zone', 'alias', 'parts', 'offset', 'wall', 'cal', 'horizon', 'grid', 'slot', 'week', 'fuzz', 'tzfragile'];

module.exports = { defineCases, FAMILIES, MATRIX, EXTRA, ZONES, FIXED, CHANGES, NAMES, TZ_FRAGILE, zoneOf };
