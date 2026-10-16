# Transaction review, alerts and calendar UX

Actual emulator captures using invented QA merchants and amounts in the isolated
`com.mohamedgamal.masareef.reviewalertsqa` application. No phone data is included.

| Capture | Behavior |
| --- | --- |
| [Classification list, light](classification-light.png) | The banner opens all five uncategorized expenses in one filtered list. |
| [Alert controls, dark, font 1.3](alerts-dark-large.png) | Switches show saved preferences and explain a channel blocked by Android. |
| [Calendar day, light](calendar-light.png) | Each commitment has an explicit details action and chevron. |
| [Commitment details, dark, font 1.3](details-dark-large.png) | The day dialog has closed before navigation to details. |

`ReviewAlertsUiTest` passed 34 checks per configuration: light, dark and dark with
font scale 1.3 (102 total). It checks list navigation and classification refresh,
independent switch preferences and channel blocking, actual calendar-day selection,
dialog dismissal/window focus, Back navigation and preservation of financial rows.
Transient system toasts from preceding switch interactions appear in some captures;
they are separate from the calendar dialog whose dismissal is asserted.

The test is dispatched by `ReconciliationTest` with `feature_audit` values
`review_alerts_light`, `review_alerts_dark` and `review_alerts_large`. It refuses
to run outside an empty, isolated `.reviewalertsqa` package on an emulator.
