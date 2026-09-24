package com.infiniteconquest.gui;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Loads, tints and caches the CC0 Kenney particle sprites used by cards and battle effects. */
final class VisualEffects {
    enum Sprite { MAGIC, ORBIT, SMOKE, FLAME, SPARK, SLASH, RING, TRACE, LIGHT }

    private static final String ROOT = "/vfx/kenney-particle-pack/";
    private static final Map<Sprite, String> FILES = Map.of(
            Sprite.MAGIC, "magic_01.png", Sprite.ORBIT, "magic_04.png",
            Sprite.SMOKE, "smoke_03.png", Sprite.FLAME, "flame_04.png",
            Sprite.SPARK, "spark_07.png", Sprite.SLASH, "slash_02.png",
            Sprite.RING, "circle_03.png", Sprite.TRACE, "trace_06.png",
            Sprite.LIGHT, "light_03.png");
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
                        VisualEffects.class.getResourceAsStream(ROOT + FILES.get(sprite))));
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
