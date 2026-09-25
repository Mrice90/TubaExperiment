package com.infiniteconquest.gui;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Loads, tints and caches the CC0 Kenney particle sprites used by cards and battle effects. */
final class VisualEffects {
    enum Sprite { MAGIC, ORBIT, SMOKE, FLAME, SPARK, SLASH, RING, TRACE, LIGHT,
        TWIRL, DEBRIS_A, DEBRIS_B, DEBRIS_C }

    /** Resource roots for bundled VFX sprite art (see THIRD_PARTY_ASSETS.md). */
    private static final String KENNEY_ROOT = "/vfx/kenney-particle-pack/";
    private static final String FREE_VFX_ROOT = "/vfx/free-vfx-pack/";

    /**
     * Sprite key -&gt; full classpath resource path. Keys are stable; the backing
     * pack may change per key.
     */
    private static final Map<Sprite, String> FILES = Map.ofEntries(
            Map.entry(Sprite.MAGIC, KENNEY_ROOT + "magic_01.png"), Map.entry(Sprite.ORBIT, KENNEY_ROOT + "magic_04.png"),
            Map.entry(Sprite.SMOKE, KENNEY_ROOT + "smoke_03.png"), Map.entry(Sprite.FLAME, KENNEY_ROOT + "flame_04.png"),
            Map.entry(Sprite.SPARK, KENNEY_ROOT + "spark_07.png"), Map.entry(Sprite.SLASH, KENNEY_ROOT + "slash_02.png"),
            Map.entry(Sprite.RING, KENNEY_ROOT + "circle_03.png"), Map.entry(Sprite.TRACE, KENNEY_ROOT + "trace_06.png"),
            Map.entry(Sprite.LIGHT, KENNEY_ROOT + "light_03.png"), Map.entry(Sprite.TWIRL, FREE_VFX_ROOT + "vortex_swirl.png"),
            Map.entry(Sprite.DEBRIS_A, FREE_VFX_ROOT + "ember_debris_a.png"), Map.entry(Sprite.DEBRIS_B, FREE_VFX_ROOT + "ember_debris_b.png"),
            Map.entry(Sprite.DEBRIS_C, FREE_VFX_ROOT + "ember_debris_c.png"));
    private static final Map<Sprite, BufferedImage> SOURCE = new HashMap<>();
    private static final Map<Key, BufferedImage> CACHE = new HashMap<>();

    private VisualEffects() { }

    static void draw(Graphics2D g, Sprite sprite, int centerX, int centerY, int size,
                     Color color, float alpha, double rotation) {
        if (size <= 0 || alpha <= 0f) return;
        BufferedImage image = tinted(sprite, size, color);
        if (image == null) return;
        Graphics2D copy = (Graphics2D) g.create();
        copy.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        copy.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, alpha))));
        copy.rotate(rotation, centerX, centerY);
        // Cached textures use size buckets; draw at the requested size so the center
        // does not jump whenever an expanding effect crosses a bucket boundary.
        copy.drawImage(image, centerX - size / 2, centerY - size / 2, size, size, null);
        copy.dispose();
    }

    static boolean available() { return source(Sprite.SPARK) != null; }

    /**
     * Sprite draw that reuses a caller-owned scratch graphics instead of
     * creating and disposing a copy per call. The scratch's composite and
     * transform are saved and restored, so the caller's state is untouched;
     * the rendered pixels are identical to {@link #draw}. Intended for hot
     * per-frame loops (e.g. the combat overlay's destroy debris) that would
     * otherwise churn a Graphics2D copy per sprite per frame.
     */
    static void drawInto(Graphics2D scratch, Sprite sprite, int centerX, int centerY, int size,
                         Color color, float alpha, double rotation) {
        if (size <= 0 || alpha <= 0f) return;
        BufferedImage image = tinted(sprite, size, color);
        if (image == null) return;
        Composite savedComposite = scratch.getComposite();
        AffineTransform savedTransform = scratch.getTransform();
        scratch.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        scratch.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, alpha))));
        scratch.rotate(rotation, centerX, centerY);
        // Cached textures use size buckets; draw at the requested size so the center
        // does not jump whenever an expanding effect crosses a bucket boundary.
        scratch.drawImage(image, centerX - size / 2, centerY - size / 2, size, size, null);
        scratch.setTransform(savedTransform);
        scratch.setComposite(savedComposite);
    }

    private static BufferedImage tinted(Sprite sprite, int size, Color color) {
        int bucket = Math.max(16, ((size + 7) / 8) * 8);
        Key key = new Key(sprite, bucket, color.getRGB());
        synchronized (CACHE) {
            return CACHE.computeIfAbsent(key, unused -> tint(source(sprite), bucket, color));
        }
    }

    private static BufferedImage source(Sprite sprite) {
        synchronized (SOURCE) {
            if (SOURCE.containsKey(sprite)) return SOURCE.get(sprite);
            try {
                BufferedImage image = ImageIO.read(Objects.requireNonNull(
                        VisualEffects.class.getResourceAsStream(FILES.get(sprite))));
                SOURCE.put(sprite, image);
                return image;
            } catch (IOException | NullPointerException exception) {
                SOURCE.put(sprite, null);
                return null;
            }
        }
    }

    private static BufferedImage tint(BufferedImage source, int size, Color color) {
        if (source == null) return null;
        BufferedImage result = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = result.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(source, 0, 0, size, size, null);
        g.setComposite(AlphaComposite.SrcIn);
        g.setColor(color); g.fillRect(0, 0, size, size);
        g.dispose();
        return result;
    }

    private record Key(Sprite sprite, int size, int color) { }
}
