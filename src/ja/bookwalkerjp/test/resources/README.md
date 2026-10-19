Fixtures were obtained from these public BOOK☆WALKER endpoints on 2026-10-19:

- `standalone-details.json`: https://member.bookwalker.jp/api/books/updates?fileType=EPUB&48f2f317-3551-47b3-93a2-152211fafa60=0
- `series-details.json`: https://member.bookwalker.jp/api/books/updates?fileType=EPUB&c189c886-516a-4799-bd7c-b47eaae6fc78=0
- `standalone-buttons.html`: the trial button block from https://bookwalker.jp/de48f2f317-3551-47b3-93a2-152211fafa60/

The JSON fixtures retain the identity and series fields. Unused fields were removed and description/copyright fields were set to null. No authenticated account data is included. Purchased/free button cases in `StandaloneBookTest` use constructed HTML; authenticated-device validation is still required.
