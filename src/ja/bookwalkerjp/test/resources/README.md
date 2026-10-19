Fixtures were obtained from these public BOOK☆WALKER endpoints on 2026-10-19:

- `standalone-details.json`: https://member.bookwalker.jp/api/books/updates?fileType=EPUB&48f2f317-3551-47b3-93a2-152211fafa60=0
- `series-details.json`: https://member.bookwalker.jp/api/books/updates?fileType=EPUB&c189c886-516a-4799-bd7c-b47eaae6fc78=0
- `standalone-buttons.html`: the trial button block from https://bookwalker.jp/de48f2f317-3551-47b3-93a2-152211fafa60/

The JSON fixtures retain the identity and series fields. Unused fields were removed and description/copyright fields were set to null. No authenticated account data is included. Purchased/free button cases in `StandaloneBookTest` use constructed HTML; authenticated-device validation is still required.

## Listing cover measurements (2026-10-19)

Version 1.6.11 temporarily used the site's small `data-original` thumbnails. Version 1.6.12 restores full-size `coverImage_` URLs for numeric `rimg` IDs and legacy `thumbnailImage_` URLs in both public and purchased listings. The original URL is retained as a fragment for the existing interceptor fallback; unrecognized IDs are left unchanged. Details still use `DetailsResponse.coverImageUrl`.

The following measurements document the earlier native-thumbnail experiment. Restoring full-size covers prioritizes image quality over these bandwidth savings.

Five numeric `rimg` covers from the first popular page were fetched anonymously alongside their previously reconstructed URLs. All ten GETs returned HTTP 200. Sizes below are response-body bytes, excluding HTTP/TLS overhead:

| `rimg` ID | Previous full-size bytes | Native thumbnail bytes | Previous dimensions | Native dimensions |
| --- | ---: | ---: | --- | --- |
| `8866361` | 479,043 | 6,679 | 880×1200 | 110×150 |
| `9938264` | 861,766 | 6,863 | 1899×2700 | 106×150 |
| `0948512` | 518,650 | 7,135 | 940×1200 | 117×150 |
| `2121493` | 1,118,891 | 7,314 | 1802×2560 | 106×150 |
| `3169194` | 666,864 | 6,715 | 1800×2560 | 105×150 |
| **Total** | **3,645,214** | **34,706** | | |

The native-thumbnail experiment achieved a **99.05% reduction for these five covers**, not for the entire listing. Example native URL: `https://rimg.bookwalker.jp/8866361/eUnObgIVNjRTJtVUNQrbaQ__.jpg`; previous URL: `https://c.bookwalker.jp/coverImage_1636687.jpg`.

Public listing snapshots used the extension's desktop User-Agent and configured cookies (`holdBook-series=1`, `safeSearch=111`, `mySetting/showCoverR15=1`). Pages 1 and 2 were fetched for:

- `/category/2/?order=rank&np=0&page=N`: 60 / 60 parsed entries.
- `/category/2/?order=release&np=0&page=N`: 60 / 60.
- `/search/?word=異世界&order=rank&wa=0&np=0&page=N`: 76 / 60 (including all entries matched by the existing selector).
- `/search/?word=異世界&order=rank&wa=1&np=0&page=N`: 100 / 100.

During the native-thumbnail experiment, the compiled thumbnail helper was checked against every matched tile in these snapshots: it returned the original resolved image URL and did not mutate the document, titles, links, or order. All eight pages retained their next-page links; no IDs overlapped between pages 1 and 2 within each case. Listing requests, ID/title extraction, page sizes, sorting, and next-page detection are unchanged, so old page N still corresponds to new page N. The server ignored exploratory `qcnt=20` / `qcnt=30` on the popular listing (still 60 entries); no page-size parameter was added.

`ListingTest` uses constructed desktop/mobile HTML to check high-resolution URL conversion, original-URL fallback fragments, unchanged full-size URLs, unrecognized IDs, and absent covers. `LibraryTest` checks the same mapping for purchased listings without calling the extension API's stubbed `SManga.create()`. Authenticated-library and Android-rendering validation is still required. Existing cached covers may remain until refreshed.
