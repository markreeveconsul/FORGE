package edu.course.shop.service;

import edu.course.shop.model.Product;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;

/** Local, offline image similarity search using color histograms. Images never leave the device. */
public final class VisualProductSearch {
    private static final int BINS = 8;

    public List<Product> rank(byte[] queryImage, Collection<Product> products) {
        double[] query = histogram(decode(queryImage));
        List<Scored> matches = new ArrayList<>();
        for (Product product : products) {
            double best = Double.NEGATIVE_INFINITY;
            byte[] main = product.getPhoto();
            if (main != null) best = Math.max(best, similarity(query, histogram(decode(main))));
            for (byte[] angle : product.getAdditionalPhotos())
                best = Math.max(best, similarity(query, histogram(decode(angle))));
            if (best > Double.NEGATIVE_INFINITY) matches.add(new Scored(product, best));
        }
        matches.sort(Comparator.comparingDouble(Scored::score).reversed()
            .thenComparing(s -> s.product().getName(), String.CASE_INSENSITIVE_ORDER));
        return matches.stream().map(Scored::product).toList();
    }

    private static BufferedImage decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("Choose a readable image file.");
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) throw new IllegalArgumentException("Choose a PNG or JPEG image.");
            return image;
        } catch (IOException e) {
            throw new IllegalArgumentException("The selected image could not be read.", e);
        }
    }

    private static double[] histogram(BufferedImage image) {
        double[] bins = new double[BINS * BINS * BINS];
        long count = 0;
        int stepX = Math.max(1, image.getWidth() / 160), stepY = Math.max(1, image.getHeight() / 160);
        for (int y = 0; y < image.getHeight(); y += stepY) {
            for (int x = 0; x < image.getWidth(); x += stepX) {
                int argb = image.getRGB(x, y);
                if ((argb >>> 24) < 32) continue;
                int r = (argb >>> 16) & 0xff, g = (argb >>> 8) & 0xff, b = argb & 0xff;
                int index = (r * BINS / 256) * BINS * BINS + (g * BINS / 256) * BINS + b * BINS / 256;
                bins[index]++; count++;
            }
        }
        if (count == 0) throw new IllegalArgumentException("The selected image is fully transparent.");
        for (int i = 0; i < bins.length; i++) bins[i] /= count;
        return bins;
    }

    private static double similarity(double[] a, double[] b) {
        double intersection = 0;
        for (int i = 0; i < a.length; i++) intersection += Math.min(a[i], b[i]);
        return intersection;
    }

    private record Scored(Product product, double score) { }
}
