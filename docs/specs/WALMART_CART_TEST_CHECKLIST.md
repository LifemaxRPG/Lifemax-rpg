# Walmart cart: Android manual test checklist

The "Add All to Walmart Cart" button on native Food's Shopping pane opens one Walmart link that is meant to add several products to the cart. It is a link-based mechanism the owner confirmed on their phone with two products. It is **not an official API**. This checklist is what to run on a real Android phone before trusting it with a real shopping list. Nothing here has been run yet by the build agent: every box is open.

How the link is made (one place only, `feature/shopping/domain/WalmartCartFormat.kt`, golden test `WalmartCartLinkBuilderTest`):

```
https://affil.walmart.com/cart/addToCart?items=<encodeURIComponent("<itemId>|<qty>,<itemId>|<qty>,...")>
```

## Before you start

- Use inexpensive products. Nothing is bought: the link only changes the cart, and checkout is always yours.
- Note what is in your Walmart cart before each test, and look at it again after. The app never claims the cart was updated; it only says "Check your Walmart cart".
- Settings, Appearance: Native Food on. Food, Shopping pane: the list shows "Add All to Walmart Cart". If the pane looks like the old one, the app or page is older than this feature.
- For each ingredient you test, tap "Add Walmart link", paste a product link from the Walmart app (Share, Copy link) or website, and Save product. Accepted: `https://www.walmart.com/ip/<number>` and `https://www.walmart.com/ip/<name>/<number>`, with or without a query, or just the item number (5 to 15 digits).

## The owner's own two-product case (documented test)

The prototype's exact link, built from `https://www.walmart.com/ip/145051970` and `https://www.walmart.com/ip/165233284`, one of each:

```
https://affil.walmart.com/cart/addToCart?items=145051970%7C1%2C165233284%7C1
```

- [ ] Match two ingredients to those two products, quantity 1 each. The summary reads "2 items added, ... ". Tap "Add All to Walmart Cart": the Walmart app (or the browser) opens and both products are in the cart.
- [ ] Compare the link with the prototype (paste it into a browser's address bar from a note): they are identical.

## Cases to run

Tick each box and write down what you saw. "Expected" is what the design intends; a different result is a finding, not a failure of the checklist.

| # | Case | Expected | Result |
|---|---|---|---|
| 1 | 2 products, quantity 1 each | one link, both in the cart | [ ] |
| 2 | 10 products | one link, all 10 in the cart | [ ] |
| 3 | 20 products | one link (20 is the largest single link), all 20 | [ ] |
| 4 | 21 to 25 products | the card says "split into 2 carts. Next: Cart 1 of 2"; open Cart 1, check the cart, then Cart 2 | [ ] |
| 5 | 45 products | 3 carts of 20, 20 and 5 | [ ] |
| 6 | Multiple quantities (2, 5 and 12 of one product) | each product in the cart with that quantity | [ ] |
| 7 | A quantity above 12 (edit to 15) | the card says it was cut to 12 and to add the rest in Walmart; 12 are added | [ ] |
| 8 | The same product matched to two ingredients | one line with the quantities added (cut to 12 if above) | [ ] |
| 9 | Repeated launch: tap the button twice within a few seconds | the second tap asks "You opened this cart a moment ago..." with "Open again anyway" and Cancel; nothing opens until you say so | [ ] |
| 10 | "Open again anyway", then look at the cart | note whether quantities doubled (expected: they may). This is why the app asks | [ ] |
| 11 | Repeat after more than 10 minutes | opens without asking (the cart may still increase) | [ ] |
| 12 | Walmart app installed, signed in | the link opens in the app (Android's app-link handling) | [ ] |
| 13 | Walmart app installed, signed out | note what happens (sign-in prompt, empty cart, browser) | [ ] |
| 14 | Walmart app not installed | the browser opens; note whether items are added while signed out or signed in | [ ] |
| 15 | No browser and no Walmart app (a locked-down profile) | "No app on this phone could open the link." and nothing else | [ ] |
| 16 | An out-of-stock product in the list | note whether the others are still added, and whether Walmart tells you (the app cannot tell) | [ ] |
| 17 | A discontinued or wrong item number | note what Walmart does with it, and the others in the same link | [ ] |
| 18 | Different selected stores (pickup store, delivery address, another city) | note whether the cart items and their availability follow the selected store | [ ] |
| 19 | Per-ingredient "Search on Walmart" | the Walmart search for the ingredient opens; the cart is untouched | [ ] |
| 20 | Pasting bad links: a non-Walmart site, a search page, a link with no number, a number of 4 digits | each is refused with a reason and nothing is saved | [ ] |
| 21 | Change a product (Change product), Forget a product, close and reopen the app | the choice is kept; Forget removes it | [ ] |
| 22 | Copy list, Share list | the text has aisles, quantities and the chosen Walmart item numbers; owned items are left out | [ ] |
| 23 | Large text, dark and light themes, TalkBack | everything readable; each button says what it does; the summary is announced when it changes | [ ] |
| 24 | Food native switch off | the classic Shopping pane, unchanged | [ ] |

## Limitations

- Not an official Walmart API. Walmart can change or remove the link behaviour at any time; if the button stops working, `WalmartCartFormat.kt` and its golden test are the only places to change.
- The app cannot see the cart. It can only hand the link to the phone, and it never says the cart was updated.
- Availability, substitutions and partial additions cannot be detected: an out-of-stock or retired product may be skipped without the app knowing.
- No scraping, no Walmart login, no checkout, no price. Products are only ever the ones you paste: the app never searches for, guesses or substitutes a product from an ingredient name.
- Ingredient amounts come from the Smart Diet week list (already scaled by your portions) and from recipe lines as written. Recipe lines with no readable amount (for example "Pinch of salt") are shown under "Check these by hand". Different ingredients written differently (for example "chicken breast" in a recipe and in the week list) are not merged.
- The number of packages is the amount over the pack size the list knows, rounded up; where no pack size is known it starts at 1 and is yours to edit.
- Quantities, "Already own", removed rows and the recipes you add are kept only while the Food screen is open. Only the chosen Walmart products are saved (in your save, and in backups).

## Unverified assumptions

These are what the design relies on that nobody has proved yet. Each is a reason to run the table above.

1. **Quantity encoding.** `<id>|<qty>` with `qty` up to 12 was tried with quantity 1 only; larger quantities, and quantity 12, are assumed to work the same way (cases 6 and 7).
2. **Many lines in one link.** The prototype was tried with two products. 20 lines per link is a conservative guess, not a known limit; the link also stays under 1800 characters. Larger carts are split into several links (cases 3 to 5).
3. **The same item twice in one link.** Not tested; the app never sends the same item twice, it adds the quantities itself.
4. **Repeating a link.** A repeated launch may add the quantities again; that is why a second launch within 10 minutes needs "Open again anyway". The 10 minutes is a guess (cases 9 to 11).
5. **App or browser.** Android's link handling decides; the Walmart app is not guaranteed to take an `affil.walmart.com` link, and the browser may open with a different cart or sign-in (cases 12 to 14).
6. **Signed-out and store behaviour.** Which cart the items land in when you are signed out, and whether they follow the selected store, is unknown (cases 13, 14, 18).
7. **Out-of-stock, retired or wrong items.** What Walmart does with them, and with the other lines of the same link, is unknown (cases 16, 17).
8. **Item numbers.** The number in a `walmart.com/ip/` link is assumed to be the number the cart link needs, as it was for the two prototype products.
