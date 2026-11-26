package com.recipefinder.frameworks_drivers.images;

import com.recipefinder.entities.Recipe;
import com.recipefinder.entities.RecipeKey;
import com.recipefinder.interface_adapters.UiDispatcher;
import com.recipefinder.interfaces.RecipeGateway;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Loads recipe names and thumbnails for the UI.
 * Network and image processing run on the supplied background executor.
 * Results reach the UI through UiDispatcher.
 */
public final class RecipeDisplayLoader implements AutoCloseable {

    private static final int MAX_CACHE_ENTRIES = 32;

    private static final long SUCCESS_CACHE_NANOS =
            TimeUnit.MINUTES.toNanos(5);

    private static final long RETRY_CACHE_NANOS =
            TimeUnit.SECONDS.toNanos(30);

    private final RecipeGateway recipeGateway;
    private final UiDispatcher uiDispatcher;
    private final Executor backgroundExecutor;

    private final Map<RecipeKey, CacheEntry> cache =
            new LinkedHashMap<>(16, 0.75f, true);

    private final Map<RecipeKey, List<Consumer<DisplayData>>> pending =
            new HashMap<>();

    private volatile boolean closed;

    public RecipeDisplayLoader(
            RecipeGateway recipeGateway,
            UiDispatcher uiDispatcher,
            Executor backgroundExecutor) {

        this.recipeGateway = Objects.requireNonNull(
                recipeGateway, "recipeGateway");

        this.uiDispatcher = Objects.requireNonNull(
                uiDispatcher, "uiDispatcher");

        this.backgroundExecutor = Objects.requireNonNull(
                backgroundExecutor, "backgroundExecutor");
    }

    /**
     * Requests display information without blocking the caller.
     * Requests for the same recipe share an existing download.
     */
    public void load(
            RecipeKey key,
            Consumer<DisplayData> onLoaded) {

        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(onLoaded, "onLoaded");

        DisplayData cachedData = null;

        synchronized (this) {
            if (closed) {
                return;
            }

            CacheEntry entry = cache.get(key);

            if (entry != null
                    && System.nanoTime() - entry.expiresAtNanos() < 0) {
                cachedData = entry.data();
            } else {
                cache.remove(key);

                List<Consumer<DisplayData>> waiting =
                        pending.get(key);

                if (waiting != null) {
                    waiting.add(onLoaded);
                    return;
                }

                pending.put(
                        key,
                        new ArrayList<>(List.of(onLoaded)));
            }
        }

        if (cachedData != null) {
            deliver(onLoaded, cachedData);
            return;
        }

        try {
            backgroundExecutor.execute(
                    () -> publish(key, fetch(key)));
        } catch (RejectedExecutionException exception) {
            publish(key, new DisplayData(key.toString(), null));
        }
    }

    private DisplayData fetch(RecipeKey key) {
        try {
            Recipe recipe = recipeGateway.getRecipeDetails(key);

            String title = recipe.getName().isBlank()
                    ? key.toString()
                    : recipe.getName();

            ImageIcon thumbnail =
                    loadThumbnail(recipe.getThumbnailUrl());

            return new DisplayData(title, thumbnail);
        } catch (IOException | RuntimeException exception) {
            return new DisplayData(key.toString(), null);
        }
    }

    /**
     * A failed photo download returns null while preserving the
     * recipe name obtained from the API.
     */
    private ImageIcon loadThumbnail(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }

        try {
            URLConnection connection =
                    URI.create(imageUrl).toURL().openConnection();

            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);

            BufferedImage original;

            try (InputStream input = connection.getInputStream()) {
                original = ImageIO.read(input);
            }

            if (original == null) {
                return null;
            }

            double scale = Math.min(
                    1.0,
                    Math.min(
                            128.0 / original.getWidth(),
                            80.0 / original.getHeight()));

            int width = Math.max(
                    1,
                    (int) Math.round(original.getWidth() * scale));

            int height = Math.max(
                    1,
                    (int) Math.round(original.getHeight() * scale));

            BufferedImage resized = new BufferedImage(
                    width,
                    height,
                    BufferedImage.TYPE_INT_ARGB);

            Graphics2D graphics = resized.createGraphics();

            try {
                graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);

                graphics.drawImage(
                        original, 0, 0, width, height, null);
            } finally {
                graphics.dispose();
            }

            return new ImageIcon(resized);
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private void publish(RecipeKey key, DisplayData data) {
        List<Consumer<DisplayData>> listeners;

        synchronized (this) {
            if (closed) {
                return;
            }

            long lifetime = data.thumbnail() == null
                    ? RETRY_CACHE_NANOS
                    : SUCCESS_CACHE_NANOS;

            cache.put(
                    key,
                    new CacheEntry(
                            data,
                            System.nanoTime() + lifetime));

            if (cache.size() > MAX_CACHE_ENTRIES) {
                RecipeKey oldest =
                        cache.keySet().iterator().next();

                cache.remove(oldest);
            }

            listeners = pending.remove(key);
        }

        if (listeners != null) {
            for (Consumer<DisplayData> listener : listeners) {
                deliver(listener, data);
            }
        }
    }

    private void deliver(
            Consumer<DisplayData> listener,
            DisplayData data) {

        uiDispatcher.dispatch(() -> {
            if (!closed) {
                listener.accept(data);
            }
        });
    }

    @Override
    public synchronized void close() {
        closed = true;
        cache.clear();
        pending.clear();
    }

    /** Presentation information used only by the outer UI layer. */
    public record DisplayData(
            String title,
            ImageIcon thumbnail) {
    }

    private record CacheEntry(
            DisplayData data,
            long expiresAtNanos) {
    }
}