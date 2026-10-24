# FORGE submission checklist

This checklist follows OOP-PROJECT.pdf sections VIII to XV. The dated implementation snapshot is 24 October 2026. A completed artifact is evidence, not a guarantee of an instructor's grade or a student's understanding.

| Requirement | Evidence | Status |
|---|---|---|
| Official project title | Report section 1 | Prepared; instructor approval must be confirmed by group |
| Project description | Section 2 | Prepared |
| Problem statement | Section 3 | Prepared |
| Objectives | Section 4 | Prepared |
| At least five meaningful features | Section 5 and actual customer/admin workflows | Implemented and covered by tests |
| Encapsulation | Section 6, Product private state and Cart controlled updates | Implemented and explained |
| Inheritance | Section 6, User with Customer/Admin/Guest | Implemented and explained |
| Polymorphism | Section 6, CheckoutService invokes PaymentMethod.pay | Implemented and explained |
| Abstraction | Section 6, abstract User and PaymentMethod interface | Implemented and explained |
| UML names, attributes, operations, visibility and relationships | Section 7, 15 SVG/PDF sheets and classes.puml | Prepared from current source |
| Complete source member inventory | uml/CLASS-REFERENCE.md and source-inventory.json | 104 source files and 165 named types |
| GUI screenshots | Section 8 and screenshots/submission | 12 current JavaFX scenes with provenance |
| Input validation and error handling | Sections 6 and 9; JUnit and UI evidence | Implemented and tested |
| Testing results | Section 9 and evidence/2026-10-24 | 175 JUnit tests and 121 UI assertions passed |
| Conclusion | Section 10 | Prepared; group must review in their own voice |
| Java source and dependencies | src/main/java, pom.xml and build.ps1 | Present; presentation-machine run pending |
| Final presentation | DEFENSE.md | Rehearsal and personal explanation pending |

## Files to review and submit

- [Project report PDF](submission/FORGE-PROJECT-REPORT.pdf)
- [UML PDF](submission/FORGE-UML.pdf)
- [Editable report](PROJECT-GUIDE.md)
- [Diagram source](classes.puml) and [sheet index](uml/README.md)
- [Current test evidence](TESTING.md)

Include source code and pom.xml in the instructor's requested format. Do not submit local keys, .env files, the encrypted key file, production data/shop.db or an arbitrary copy of the .git directory. Public demo credentials in README are intentional; a private provider key is not a demo credential.

## Personal preparation

- Confirm the exact title is approved and names/course/instructor spelling is correct.
- Review the conclusion and ensure each member can explain it in their own words.
- Trace Product validation, User role overrides, PaymentMethod dispatch and the checkout transaction.
- Explain local assistant retrieval versus online generation, source-checked specification proposals and the administrator review boundary.
- Rehearse invalid input, a declined payment, rollback and a rejected unauthorized action.
- Test the native file chooser, sizing, keyboard access, reduced motion and a live AI call on the presentation computer when quota allows.
- Follow the instructor's actual presentation duration and submission format. A previous short demo script does not replace any longer required defense.

The assignment permits external resources and AI assistance only with the student's own understanding. Each member remains responsible for explaining, modifying and defending the submitted implementation.
