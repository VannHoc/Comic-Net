package com.comicnet.desktop.client.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Separate transport: no session cookie, bearer token, global cookie handler, or redirects. */
public final class MediaTransport implements AutoCloseable {
  private final Set<String> allowedHosts;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public MediaTransport(Set<String> allowedHosts) {
    this.allowedHosts = Set.copyOf(allowedHosts);
  }

  public CompletableFuture<HttpResponse<Void>> check(URI uri) {
    validate(uri);
    return http.sendAsync(
        HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(8))
            .method("HEAD", HttpRequest.BodyPublishers.noBody())
            .build(),
        HttpResponse.BodyHandlers.discarding());
  }

  private void validate(URI uri) {
    if (uri == null || uri.getHost() == null)
      throw new IllegalArgumentException("Media host is required.");
    boolean local = Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
    if (!allowedHosts.contains(uri.getHost())
        || uri.getUserInfo() != null
        || uri.getFragment() != null
        || !("https".equals(uri.getScheme()) || local && "http".equals(uri.getScheme()))) {
      throw new IllegalArgumentException("Media destination is not allowed.");
    }
  }

  public CompletableFuture<java.awt.image.BufferedImage> image(URI uri, int desiredWidth) {
    validate(uri);
    return http.sendAsync(
            HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "image/jpeg, image/png")
                .GET()
                .build(),
            ignored -> new LimitedBody())
        .thenApplyAsync(
            response -> {
              try {
                String mime =
                    response.headers().firstValue("Content-Type").orElse("").split(";")[0];
                if (response.statusCode() != 200
                    || !Set.of("image/jpeg", "image/png").contains(mime))
                  throw new java.io.IOException("Image response unavailable.");
                byte[] bytes = response.body();
                try (var input =
                    javax.imageio.ImageIO.createImageInputStream(
                        new java.io.ByteArrayInputStream(bytes))) {
                  var readers = javax.imageio.ImageIO.getImageReaders(input);
                  if (!readers.hasNext())
                    throw new java.io.IOException("No supported image decoder.");
                  var reader = readers.next();
                  try {
                    reader.setInput(input, true, true);
                    String format = reader.getFormatName().toUpperCase(java.util.Locale.ROOT);
                    if (!(mime.equals("image/png") && format.equals("PNG")
                        || mime.equals("image/jpeg")
                            && (format.equals("JPEG") || format.equals("JPG"))))
                      throw new java.io.IOException("Image content type mismatch.");
                    int width = reader.getWidth(0), height = reader.getHeight(0);
                    if (width < 1
                        || height < 1
                        || width > 40000
                        || height > 40000
                        || (long) width * height > 40_000_000L)
                      throw new java.io.IOException("Image dimensions exceed limit.");
                    var parameters = reader.getDefaultReadParam();
                    int subsample =
                        Math.max(
                            1,
                            Math.max(
                                (int)
                                    Math.ceil(
                                        (double) width / Math.max(1, Math.min(1760, desiredWidth))),
                                (int) Math.ceil(Math.sqrt((double) width * height / 4_000_000))));
                    parameters.setSourceSubsampling(subsample, subsample, 0, 0);
                    var decoded = reader.read(0, parameters);
                    if (decoded == null) throw new java.io.IOException("Image decode failed.");
                    return decoded;
                  } finally {
                    reader.dispose();
                  }
                }
              } catch (java.io.IOException e) {
                throw new java.util.concurrent.CompletionException(e);
              }
            });
  }

  public CompletableFuture<java.awt.image.BufferedImage> thumbnail(URI uri, int width, int height) {
    return image(uri, width)
        .thenApplyAsync(
            image -> {
              var result =
                  new java.awt.image.BufferedImage(
                      width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
              var canvas = result.createGraphics();
              try {
                canvas.setRenderingHint(
                    java.awt.RenderingHints.KEY_ANTIALIASING,
                    java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                canvas.setRenderingHint(
                    java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                canvas.setClip(
                    new java.awt.geom.RoundRectangle2D.Double(0, 0, width, height, 16, 16));
                double scale =
                    Math.max(
                        (double) width / image.getWidth(), (double) height / image.getHeight());
                int w = (int) Math.ceil(image.getWidth() * scale),
                    h = (int) Math.ceil(image.getHeight() * scale);
                canvas.drawImage(image, (width - w) / 2, (height - h) / 2, w, h, null);
              } finally {
                canvas.dispose();
                image.flush();
              }
              return result;
            });
  }

  private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final CompletableFuture<byte[]> result = new CompletableFuture<>();
    private final java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
    private java.util.concurrent.Flow.Subscription subscription;

    @Override
    public java.util.concurrent.CompletionStage<byte[]> getBody() {
      return result;
    }

    @Override
    public void onSubscribe(java.util.concurrent.Flow.Subscription value) {
      subscription = value;
      value.request(1);
    }

    @Override
    public void onNext(java.util.List<java.nio.ByteBuffer> buffers) {
      for (var buffer : buffers) {
        if ((long) bytes.size() + buffer.remaining() > 64L * 1024 * 1024) {
          subscription.cancel();
          result.completeExceptionally(new java.io.IOException("Image response exceeds limit."));
          return;
        }
        byte[] part = new byte[buffer.remaining()];
        buffer.get(part);
        bytes.writeBytes(part);
      }
      subscription.request(1);
    }

    @Override
    public void onError(Throwable error) {
      result.completeExceptionally(error);
    }

    @Override
    public void onComplete() {
      result.complete(bytes.toByteArray());
    }
  }

  @Override
  public void close() {
    http.shutdownNow();
  }
}
