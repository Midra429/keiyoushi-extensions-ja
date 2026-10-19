# Cover background

`String?.withCoverBackground()` marks a thumbnail URL for processing by
`CoverBackgroundInterceptor`. Install the interceptor **before** reader decryption and
cover-fallback interceptors. Its versioned URL fragment is removed before proceeding;
any original fragment is restored for downstream interceptors.

Only marked covers are processed. Never mark chapter page images.

The output is a losslessly encoded 2:3 PNG, at most 600 × 900 pixels. The original
image is fitted without cropping or changing its aspect ratio. Its position is aligned
to integer pixels so unscaled covers are not softened by half-pixel interpolation.
Small opaque foregrounds retain their decoded pixels; large covers are still resized
to the output limit. PNG may use more cover-cache storage than JPEG. Padding is filled with a
center-cropped copy, blurred at low resolution, adaptively dimmed according to its
brightness with a 22–52% black overlay, and finished with a subtle vignette. Large source images are downsampled
before decoding to bound memory usage. No extra network requests or external image
processing service are used.

This matches Komikku's normal book-cover grid. Square lists and other aspect ratios
may still crop the composed image. The app also uses the composed cover for details,
full-screen viewing, saving, and sharing; an extension cannot distinguish those uses.
Existing library entries need a metadata refresh to acquire the marked thumbnail URL.
The marker changes the cover cache key; change its version when changing the rendering.
Version 2 slightly lightens the background. Version 3 preserves foreground quality
with pixel-aligned placement and PNG encoding. Version 1 and 2 URLs are still processed,
and marking them again upgrades them without losing existing fragments.

Validation:

```sh
./gradlew :lib:coverbackground:testDebugUnitTest :lib:coverbackground:lintRelease
```

Bitmap tests use Robolectric native graphics; production code adds no third-party runtime
dependency.
