#!/usr/bin/env python3
"""Condense the USDA FoodData Central "Branded" download into the file LifeMax's Food database imports.

usage: build_usda_condensed.py ZIP OUT.jsonl.gz [MANIFEST.json] [--source-url URL]

ZIP is FoodData_Central_branded_food_json_<date>.zip from https://fdc.nal.usda.gov/download-datasets (one 3 GB JSON file
inside; it is read as a stream, never loaded whole). OUT is a gzip of UTF-8 text, one JSON object per food per line, the
shape the page's importer (importUsdaFoods in runtime segment 08) and tests/fixtures/usda-mini.jsonl.gz use:

  id "fdc<fdcId>", n name, brand, cat, kcal p c f fib sug na (per 100 g, or per 100 ml for liquids), serving + servingUnit
  ("g" or "ml") and household ("2 tbsp").

What it changes in USDA's text, and nothing else (no nutrient value is altered):
  * names and brands: ALL CAPS become Title Case; the doubled quotes USDA's export leaves ("" for ") become one;
  * "grm" and "gm" servings are grams ("g"), "mlt" is "ml"; servings in mg, IU or mcg are dropped (the food stays);
  * household servings: USDA's codes become words (ONZ oz, OZA fl oz, GRM g, MLT ml, LBR lb) and SHOUTED words are lowered;
  * the placeholder brand "Not a Branded Item" is dropped;
  * a food with no energy and no protein, carbs and fat is left out (it cannot be logged).
Energy is nutrient 1008 (kcal), else the Atwater general (2047) or specific (2048) factor value.
The output is deterministic: the same ZIP gives the same bytes (gzip header time fixed to 0).
"""
import collections
import gzip
import hashlib
import json
import re
import sys
import zipfile

SMALL = {"a", "an", "and", "as", "at", "but", "by", "for", "in", "of", "on", "or", "the", "to", "with", "w/", "n"}
GRAMS = {"g", "grm", "gm"}
MLS = {"ml", "mlt"}
CODES = {"ONZ": "oz", "OZA": "fl oz", "GRM": "g", "MLT": "ml", "LBR": "lb"}
PLACEHOLDER_BRANDS = {"not a branded item", "not branded", "n/a", "none"}


def title(text):
    out = []
    for i, word in enumerate(re.split(r"(\s+)", text.strip())):
        if not word or word.isspace():
            out.append(word)
            continue
        lower = word.lower()
        if i > 0 and lower in SMALL:
            out.append(lower)
            continue
        out.append(re.sub(r"(^|[-/(\"])([a-z])", lambda m: m.group(1) + m.group(2).upper(), lower))
    return "".join(out)


def household(text):
    def word(m):
        t = m.group(0)
        if t in CODES:
            return CODES[t]
        return t.lower() if t.isupper() and len(t) >= 3 else t

    return re.sub(r"[A-Za-z]+", word, text)


def rounded(value):
    try:
        return round(float(value), 1)
    except (TypeError, ValueError):
        return None


def condense(food, stats):
    name = (food.get("description") or "").strip().replace('""', '"')
    if not name:
        stats["skipped_no_name"] += 1
        return None
    nutrients = {}
    for item in food.get("foodNutrients", []):
        nid = (item.get("nutrient") or {}).get("id")
        amount = item.get("amount")
        if nid is not None and amount is not None and nid not in nutrients:
            nutrients[nid] = amount
    kcal = nutrients.get(1008, nutrients.get(2047, nutrients.get(2048)))
    protein, carbs, fat = nutrients.get(1003), nutrients.get(1005), nutrients.get(1004)
    if kcal is None and protein is None and carbs is None and fat is None:
        stats["skipped_no_nutrition"] += 1
        return None
    rec = {"id": "fdc%d" % food["fdcId"], "n": title(name)[:90]}
    brand = (food.get("brandName") or food.get("brandOwner") or "").strip().replace('""', '"')
    if brand and brand.lower() not in PLACEHOLDER_BRANDS:
        rec["brand"] = title(brand)[:60]
    cat = (food.get("brandedFoodCategory") or "").strip().replace('""', '"')
    if cat:
        rec["cat"] = cat
    rec["kcal"] = rounded(kcal) or 0
    rec["p"], rec["c"], rec["f"] = rounded(protein) or 0, rounded(carbs) or 0, rounded(fat) or 0
    for key, nid in (("fib", 1079), ("sug", 2000), ("na", 1093)):
        value = rounded(nutrients.get(nid))
        if value is not None:
            rec[key] = value
    size = food.get("servingSize")
    unit = (food.get("servingSizeUnit") or "").lower()
    if size and size > 0:
        if unit in GRAMS:
            rec["serving"], rec["servingUnit"] = round(size, 1), "g"
            stats["serving_g"] += 1
        elif unit in MLS:
            rec["serving"], rec["servingUnit"] = round(size, 1), "ml"
            stats["serving_ml"] += 1
        else:
            stats["serving_dropped_other_unit"] += 1
    else:
        stats["no_serving"] += 1
    text = (food.get("householdServingFullText") or "").strip().replace('""', '"')
    if text:
        rec["household"] = household(text)[:40]
    return rec


def main(argv):
    args = [a for a in argv[1:] if not a.startswith("--")]
    source = ""
    if "--source-url" in argv:
        source = argv[argv.index("--source-url") + 1]
        args = [a for a in args if a != source]
    if len(args) < 2:
        print(__doc__)
        return 2
    zip_path, out_path = args[0], args[1]
    manifest_path = args[2] if len(args) > 2 else None
    stats = collections.Counter()
    with zipfile.ZipFile(zip_path) as z:
        member = z.namelist()[0]
        with z.open(member) as src, open(out_path, "wb") as raw:
            with gzip.GzipFile(fileobj=raw, mode="wb", compresslevel=9, mtime=0) as out:
                for line in src:
                    if not line.startswith(b'{"foodClass'):
                        continue
                    try:
                        food = json.loads(line.rstrip().rstrip(b","))
                    except ValueError:
                        stats["bad_json"] += 1
                        continue
                    stats["records"] += 1
                    rec = condense(food, stats)
                    if rec is None:
                        continue
                    out.write((json.dumps(rec, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8"))
                    stats["foods"] += 1
    digest = hashlib.sha256(open(out_path, "rb").read()).hexdigest()
    manifest = {
        "file": out_path.rsplit("/", 1)[-1],
        "foods": stats["foods"],
        "bytes": len(open(out_path, "rb").read()),
        "sha256": digest,
        "usdaFile": member,
        "usdaUrl": source,
        "stats": dict(stats),
    }
    if manifest_path:
        with open(manifest_path, "w", encoding="utf-8") as f:
            json.dump(manifest, f, indent=1, sort_keys=True)
            f.write("\n")
    print(json.dumps(manifest, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
