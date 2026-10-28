#!/usr/bin/env python3
"""Build src/data/smartDiet.html (the Smart Diet data embedded in the app) from the validated Gemini files.
   python3 tools/make_smartdiet.py   (run gemini/smart-diet/validate.py first)"""
import json, sys
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]; D=ROOT/'gemini/smart-diet/athlete'
DISP={"chicken_breast_raw":"Chicken breast","rice_white_cooked":"White rice","olive_oil":"Olive oil","broccoli_raw":"Broccoli","eggs_whole_raw":"Eggs","oats_rolled_dry":"Rolled oats","whey_protein":"Whey protein","protein_powder_plant":"Plant protein","greek_yogurt_nonfat":"Greek yogurt","banana_raw":"Banana","almonds_raw":"Almonds","potato_sweet_raw":"Sweet potato","beef_ground_90_raw":"Ground beef","pasta_whole_wheat_dry":"Whole wheat pasta","spinach_raw":"Spinach","bread_whole_wheat":"Whole wheat bread","apple_raw":"Apple","peanut_butter":"Peanut butter","milk_2":"2% milk","tuna_canned_water":"Canned tuna","sauce_marinara":"Marinara sauce","avocado_raw":"Avocado","rice_microwave_cooked":"Microwave rice","chicken_rotisserie_cooked":"Rotisserie chicken","vegetables_mixed_frozen":"Frozen vegetables","oats_instant_dry":"Instant oats","turkey_deli_sliced_low_sodium":"Deli turkey (low sodium)","beans_black_canned":"Black beans","soy_yogurt_high_protein":"Soy yogurt (high protein)","lentils_cooked":"Lentils","tofu_firm":"Firm tofu","milk_soy_unsweetened":"Soy milk","turkey_ground_raw":"Ground turkey","shrimp_raw":"Shrimp","pork_tenderloin_raw":"Pork tenderloin","cottage_cheese_lowfat":"Cottage cheese","salmon_canned_water":"Canned salmon"}
cat=json.loads((D/'athlete-catalog.json').read_text(encoding='utf-8'))
out={"v":1,"cat":{},"plans":{}}
for i in cat['ingredients']:
    k=i['id'][4:]
    if k not in DISP: sys.exit('missing display name for '+k)
    out['cat'][i['id']]={"n":DISP[k],"st":i['state'],"p":i['per100g'],"s":i['shop']}
for key in ('standard','simple'):
    p=json.loads((D/f'athlete-{key}.json').read_text(encoding='utf-8'))
    out['plans'][key]={"name":p['name'],"summary":p['summary'],"ref":p['reference'],"meals":{m['id']:m for m in p['meals']},"week":p['week'],"prep":p['prep'],"notes":p['shopping_notes']}
s=json.dumps(out,ensure_ascii=False,separators=(',',':'))
assert '</script' not in s
(ROOT/'src/data/smartDiet.html').write_text('<script type="application/json" id="smartDiet">'+s+'</script>\n',encoding='utf-8')
print('smartDiet.html',len(s),'bytes')
