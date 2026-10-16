# Card repayment and cancellation screenshots

Actual app screens from a disposable emulator, captured by CardJourneyUiTest. Names, accounts, purchases and messages are synthetic QA fixtures, not the owner's phone records.

The flow shares the reimbursement bottom-sheet window and existing theme controls. Cancellation is an audit event outside totals; its badge uses both an icon and text.

| Screen | Light | Dark |
| --- | --- | --- |
| Choose accounts/cards | [Image](accounts-light.png) | [Image](accounts-dark.png) |
| Choose purchases | [Image](choose-light.png) | [Image](choose-dark.png) |
| Review repayment | [Image](review-light.png) | [Image](review-dark.png) |
| Confirmed result | [Image](result-light.png) | [Image](result-dark.png) |
| Canceled attempt | [Image](canceled-light.png) | [Image](canceled-dark.png) |

[Dark review with font scale 1.3](review-dark-large.png). Body content scrolls independently of the fixed action buttons. Run `tools/run-card-journeys.ps1 -Device emulator-5556` to reproduce the functional checks and captures in the isolated `.cardreview.journeyqa` package.


## Shared menus and original bank messages

Actual screenshots from `SelectionUiTest`, using the same synthetic QA fixtures on a separate read-only Pixel emulator (720 x 1600, density 280). These cover the shared component used by form selectors, action menus and multi-select dialogs. The final account-action icon uses the app's outlined transfer symbol rather than tinting the decorative chip into a solid block.

| Screen | Light | Dark |
| --- | --- | --- |
| Searchable category selection | [Image](category-light.png) | [Image](category-dark.png) |
| Account actions | [Image](actions-light.png) | [Image](actions-dark.png) |
| Multi-select search with retained input | [Image](multiselect-light.png) | [Image](multiselect-dark.png) |
| Original bank messages | [Image](messages-light.png) | [Image](messages-dark.png) |

Font scale 1.3: [category selection](category-dark-large.png), [original bank messages](messages-dark-large.png). Message text stays selectable and unchanged; close/actions remain accessible. All accounts, handles and message content shown here are synthetic.
