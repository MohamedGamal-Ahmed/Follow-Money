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
