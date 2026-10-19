"""Live API regression check (stdlib only; makes public requests to both sites).

Run from the repository root:
    python3 lib-multisrc/gigaviewer/test/verify_readable_products.py

Compares complete default/100-item pagination responses, including order and
purchase/availability metadata. This is not an Android/Kotlin integration test.
Run away from scheduled chapter releases to avoid changes between requests.
"""

import json
import time
import urllib.parse
import urllib.request

CASES = (
    ("https://shonenjumpplus.com", "10834108156648240732"),  # SPY×FAMILY
    ("https://shonenjumpplus.com", "17107094915405001773"),  # 打ち切られ漫画家と同人女
    ("https://tonarinoyj.jp", "3269754496306260262"),  # 超人X
    ("https://tonarinoyj.jp", "2550689798352530651"),  # 毎日ふあんちゃん
    (
        "https://tonarinoyj.jp",
        "2550912964815319852",
    ),  # 円環のエモーションズ (50 episodes)
)


def get_counts(base_url, series_id):
    query = """
        query($id: String!) {
          series(databaseId: $id) {
            episodes: readableProducts(types: [EPISODE], first: 0) { totalCount }
            volumes: readableProducts(types: [VOLUME], first: 0) { totalCount }
          }
        }
    """
    request = urllib.request.Request(
        base_url + "/graphql",
        data=json.dumps({"query": query, "variables": {"id": series_id}}).encode(),
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        result = json.load(response)
    assert not result.get("errors"), result
    return result["data"]["series"]


def fetch_products(base_url, series_id, kind, count, episode_page_size):
    products = []
    requests = 0
    payload_bytes = 0
    started = time.perf_counter()
    while len(products) < count:
        params = {
            "type": kind,
            "aggregate_id": series_id,
            "sort_order": "desc",
            "offset": len(products),
        }
        if kind == "episode" and episode_page_size is not None:
            params["limit"] = episode_page_size
        url = base_url + "/api/viewer/pagination_readable_products?"
        with urllib.request.urlopen(
            url + urllib.parse.urlencode(params), timeout=30
        ) as response:
            payload = response.read()
        requests += 1
        payload_bytes += len(payload)
        batch = json.loads(payload)
        if not batch:
            break
        products.extend(batch)
    elapsed = time.perf_counter() - started
    print(
        f"  {kind} limit={episode_page_size}: {len(products)} items, "
        f"{requests} HTTP, {payload_bytes} decoded bytes, {elapsed:.3f}s"
    )
    return products, requests


def main():
    for base_url, series_id in CASES:
        print(base_url, series_id, flush=True)
        counts = get_counts(base_url, series_id)
        for kind, key in (("episode", "episodes"), ("volume", "volumes")):
            count = counts[key]["totalCount"]
            before, old_requests = fetch_products(
                base_url, series_id, kind, count, None
            )
            after, new_requests = fetch_products(base_url, series_id, kind, count, 100)
            assert before == after, f"{series_id}/{kind}: payload or order changed"
            assert len(after) == count, f"{series_id}/{kind}: incomplete list"
            ids = [product["readable_product_id"] for product in after]
            assert len(ids) == len(set(ids)), f"{series_id}/{kind}: duplicate IDs"
            assert new_requests <= old_requests, "request count increased"
            if kind == "volume":
                assert new_requests == old_requests, "volume pagination changed"
            print("  PASS: complete payload/order unchanged", flush=True)


if __name__ == "__main__":
    main()
