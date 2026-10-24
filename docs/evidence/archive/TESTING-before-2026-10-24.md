# Admin workspace and UI fidelity follow-up — October 15, 2026

**43 backend tests, 112 GuiSmoke assertions and 99 NextUiSmoke assertions passed.** Admin overview, order history and customer summaries are database-backed read-only reports; customer access is rejected by the admin reporting service. The UI smoke exports overview, orders and customers screenshots in addition to its existing gallery. The latest desktop run also checked the centered search panel, filtered catalog spacing, compact viewer height, and responsive inventory columns. Existing shop-all hero, top section, category campaign artwork and motion were left unchanged.

# Next UI verification — October 14, 2026

**40 backend tests, 124 GUI assertions (89 existing + 35 new) and 30 motion assertions passed.** New service checks cover saved-item persistence/privacy, role checks, address ownership/defaults/limits/rollback, framing preservation, reviewed current-price repeat plans and atomic cart changes. NextUiSmoke exercises the new screens on a shown window and exports nine screenshots; existing GuiSmoke exports 26 shopping screenshots. Tests use fresh synthetic SQLite databases and gallery fixtures, never the user's store data. Source comparison confirms unchanged EditorialHeader, campaign assets and original CSS rules.

An initial quantity-control GUI check exposed a read-loading race: related products temporarily disabled purchase controls. Secondary recommendations now load without disabling the page, and the final pass covers the stepper. Initial test-harness compile/startup mistakes were corrected before these final results. Native chooser, drag gestures and presentation-machine display/GPU checks remain manual. See [Next UI](NEXT-UI.md) for commands and scope.

# Approved wireframe verification — October 14, 2026

Latest refinement: **31 backend tests, 89 shopping GUI assertions and 30 motion assertions passed**. The backend suite covers additive migration, gallery persistence, unauthorized/invalid updates, defensive photo copies and rollback of product/gallery writes. The shown-window GUI checks cover larger PC framing, real gallery selection, specifications, cancel/removal, Added feedback, actionable toast, drawer focus, stock limits, retained checkout inputs and compact stacked layouts. The motion pass verifies the unchanged storefront hero and proportional oversized account crop.

A focused scratch JavaFX check used a real 960×680 scene with header/footer space and verified that cart checkout and place-order actions are fully reachable after scrolling to the end. Native file chooser and laptop display/GPU checks remain manual. Fixtures are synthetic fresh SQLite databases; the user's store database was not accessed.

Gallery screenshots use visibly synthetic test images; actual additional product angles and specifications are supplied through the admin editor.

## Earlier verification history

# Animated banner verification — October 14, 2026

Java 21 / Maven 3.9.9 / JavaFX 21.0.6 on Linux: **66 shopping GUI assertions** and **25 motion assertions passed**. The shown-window motion harness uses JavaFX Robot to exercise hover and parallax, checks reduced motion and window/scene/viewport lifecycle, verifies compact sizing, and checks scrolling inside the actual storefront. It exported 60 scene frames for the preview. The earlier **26-test backend pass** was reused after confirming identical backend/test/pom input hashes. Compilation and whitespace checks passed. Synthetic window resizing under Xvfb produced GTK resize warnings; behavioral and dimension assertions passed. Presentation-machine GPU performance, display scaling and the native file picker remain manual checks.

See [Animations](ANIMATIONS.md) for the controls and lifecycle behavior. Earlier verification follows.

# Current design elevation verification — October 14, 2026

Linux / Temurin 21.0.12.1 / Maven 3.9.9 / JavaFX 21.0.6: **26 backend tests passed** and **61 GUI assertions passed**, with 24 scene snapshots. The smoke harness uses Xvfb, software rendering and a fresh isolated SQLite database. New coverage includes campaign dimensions/actions, visible search, offline illustrations, collection shortcuts (including clearing a search in the same category), and compact collection tiles. An existing stale payment-validation error was found and fixed. Screens were inspected at 1280×860 and 960×680. Native file-picker and presentation-machine keyboard/display checks remain manual.

See [Design elevation](DESIGN-ELEVATION.md) for the current direction. The following evidence describes the previous Windows verification.

# Testing evidence

Updated and verified on October 13, 2026 (Asia/Singapore), using Windows and JDK 26.0.1.

## Automated backend tests
**25 tests passed; zero failures, errors, or skipped tests.**

ShopTest.java contains these executable JUnit cases:

| Test | Inputs/scenario | Expected result |
|---|---|---|
| loginChecksCredentialsAndResolvesRoles | Demo accounts; wrong/blank/SQL-like input | Correct subclass; invalid login rejected |
| invalidProductsCannotBeConstructed | Blank name, negative stock, nonnumeric/negative/overprecision price | ValidationException |
| customersCannotEditProductsEvenOutsideGui | Customer calls product save | Access denied |
| adminCreatesEditsAndDeactivatesProducts | New item -> edit -> deactivate | Stored correctly, hidden from customer |
| searchCombinesNameAndCategory | Uppercase name search and mismatched category | Case-insensitive match; correct filtering |
| cartCombinesItemsAndProtectsItsCollection | Add 1 then 2; update/remove; attempt list mutation | One line with 3; correct totals; collection protected |
| cartRejectsInvalidQuantitiesAndWrongOwner | Zero/excess quantity; wrong user | Rejected without valid cart changes |
| cashOnDeliveryCommitsOrderAndInventory | Buy 2 items | PENDING order; stock reduced by 2; cart cleared |
| approvedWalletUsesDifferentPaymentBehavior | Approved wallet | PAID order |
| declinedPaymentRollsBackStockAndKeepsCart | Declined wallet | No order; original stock/cart |
| insufficientSecondItemRollsBackFirstDeduction | Second item's stock reduced after cart addition | First deduction undone; no order |
| databaseWriteFailureRollsBackEntireCheckout | SQLite trigger aborts order insertion | StorageException; original stock/cart |
| checkoutRejectsEmptyCartAddressAndMissingPayment | Empty cart, blank address, null payment | ValidationException; no order |
| stalePriceRequiresCustomerReview | Price edited after cart addition | Checkout rejected; quantity update refreshes price |
| deactivatedCartItemCannotBePurchased | Product deactivated after cart addition | Checkout rejected; stock unchanged |
| orderSnapshotsSurviveProductEditsAndReopeningDatabase | Buy, change name/price, reopen file | Receipt retains original values; changes persist |
| historyDoesNotExposeOtherCustomersOrders | Different customer ID; admin | Other customer's orders absent; admin denied |
| photoAndDescriptionSurviveReopenReplacementAndRemoval | Upload PNG, delete original, reopen DB, replace with JPEG, remove | Persistent bytes/description; latest photo or null |
| invalidPhotosCannotReplaceSavedData | Corrupt, empty, oversize, excessive dimensions, GIF, fake JPEG, missing file | Reject; existing saved product remains intact |
| photoBytesAreEncapsulatedAndBasicEditPreservesPhoto | Mutate source/getter arrays; save stock-only edit | Defensive copies; photo and description retained |
| photosAndDescriptionsRequireAdminAccessAndValidDescription | Customer/guest save; overlong description | Service validation denies changes |
| existingSchemaMigratesWithoutLosingProducts | Old product table, migrate twice | Existing product intact; new columns available |
| guestCanBrowseAndBuildCartButCannotCheckoutOrReadOrders | Guest adds items, transfers to customer, attempts restricted operations | Cart transferred; checkout/history require customer |
| guestTransferNeverSilentlyChangesPriceAndCheckoutRevalidates | Admin changes price before guest sign-in | Original price retained; review warning; checkout requires update |
| deliveryDetailsAreValidatedOutsideTheGui | Missing/invalid fields, excessive combined length | DeliveryAddress rejects independently of GUI |

Run: `./build.ps1 Test`. Detailed Maven reports are generated under target/surefire-reports.

## GUI smoke test
**Passed: 35 assertions.** Fires actual JavaFX controls for inline login validation, catalog cards, sold-out actions, no-result searches, clear filters, price sorting, empty cart, cart counts, quantity edits, invalid quantity, removal, address validation/retention, declined and approved wallet checkout, confirmation, order history, and admin create/edit/filter/deactivation.

Additional checks cover guest browsing, product details, quantity preservation through sign-in, required delivery fields, photo import/preview/persistence, reopening the editor, canceling removal, corrupt uploads, and saving removal. The upload test calls the same import method used after file selection; the native chooser itself remains a manual check.

Run: `./build.ps1 Smoke`. Uses a unique test database under target and does not touch data/shop.db.

Seventeen actual JavaFX scene snapshots were generated. Main screens, upload editing and compact layouts were visually reviewed:

- 01-login.png
- 02-catalog.png
- 03-cart.png
- 04-checkout.png
- 05-orders.png
- 06-admin.png
- 07-confirmation.png
- 08-catalog-compact.png
- 09-cart-compact.png
- 10-checkout-compact.png
- 11-admin-editor.png
- 12-admin-compact.png
- 13-guest-storefront.png
- 14-product-detail.png
- 15-product-detail-compact.png
- 16-photo-upload.png
- 17-admin-editor-compact.png

These are application scene renders, not desktop captures or mockups. The smoke test does not replace an interactive usability review on the presentation machine.

## Build
Maven package completed successfully and produced target/marketplace-1.0.0.jar. This is a standard dependency-requiring JAR, not a standalone executable distribution.

## Manual checks still to perform personally
- [ ] Open the native photo picker; select PNG/JPEG, cancel selection, replace and remove a photo.
- [ ] Run build.ps1 Run on the presentation machine.
- [ ] Use Tab/Enter to log in and navigate form controls.
- [ ] Resize the window and verify tables/forms at your display scaling.
- [ ] Enter letters, fractions, huge numbers, and blanks in numeric inputs.
- [ ] Observe error dialogs for excess stock and declined demo payment.
- [ ] Log out with a nonempty cart; test both confirmation choices.
- [ ] Close and reopen the actual app; inspect saved orders and stock.
- [ ] Explain one test's setup, input, expected result, and assertion.

Do not mark these manual checks completed merely because the automated suite passes.

Account refinement coverage: GuiSmoke verifies supplied PC loading, password visibility/edit retention, login reduced motion and square catalog geometry alongside existing shopping/auth/upload flows. BannerMotionSmoke checks account animation/reduction and compact artwork fit, plus generated opaque and alpha-padded photo fixtures for fill/contain geometry. All fixtures are synthetic and use disposable databases.
