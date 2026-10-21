package edu.course.shop.ui;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/** Small original outline icons, sharing one stroke and size. */
public final class StoreIcon {
    private StoreIcon() { }
    /** Fit header icons to one visual size while preserving each outline's proportions. */
    public static Group of(String name, double size) {
        Group artwork = of(name);
        var bounds = artwork.getLayoutBounds();
        double scale = size / Math.max(bounds.getWidth(), bounds.getHeight());
        artwork.getTransforms().add(new javafx.scene.transform.Scale(scale, scale));
        Group fitted = new Group(artwork);
        fitted.setMouseTransparent(true);
        return fitted;
    }
    public static Group of(String name) {
        String path=switch(name) {
            case "close" -> "M7 7 L17 17 M17 7 L7 17";
            case "bag" -> "M5 7 H19 L20 21 H4 Z M9 8 V6 A3 3 0 0 1 15 6 V8";
            case "account" -> "M16 7 A4 4 0 1 1 8 7 A4 4 0 1 1 16 7 M4 21 V19 A8 8 0 0 1 20 19 V21";
            case "heart" -> "M12 21 L3 12 C-2 6 5 -1 12 6 C19 -1 26 6 21 12 Z";
            case "search" -> "M16 10 A6 6 0 1 1 4 10 A6 6 0 1 1 16 10 M15 15 L21 21";
            case "dashboard" -> "M3 3 H10 V10 H3 Z M14 3 H21 V8 H14 Z M14 12 H21 V21 H14 Z M3 14 H10 V21 H3 Z";
            case "package" -> "M3 6 L12 2 L21 6 V18 L12 22 L3 18 Z M3 6 L12 10 L21 6 M12 10 V22 M7 4 L16 8";
            case "users" -> "M14 7 A4 4 0 1 1 6 7 A4 4 0 1 1 14 7 M2 21 V19 A8 8 0 0 1 18 19 V21 M18 3 A4 4 0 0 1 18 11 M21 21 V18 A5 5 0 0 0 18 14";
            case "sparkles" -> "M12 2 L15 9 L22 12 L15 15 L12 22 L9 15 L2 12 L9 9 Z M21 1 V5 M19 3 H23";
            case "moon" -> "M20 15 A9 9 0 1 1 9 3 A7 7 0 0 0 20 15 Z";
            case "sun" -> "M16 12 A4 4 0 1 1 8 12 A4 4 0 1 1 16 12 M12 1 V4 M12 20 V23 M1 12 H4 M20 12 H23 M4 4 L6 6 M18 18 L20 20 M4 20 L6 18 M18 6 L20 4";
            case "command" -> "M8 8 H16 V16 H8 Z M8 8 H5 A3 3 0 1 1 8 5 V8 M16 8 V5 A3 3 0 1 1 19 8 H16 M16 16 H19 A3 3 0 1 1 16 19 V16 M8 16 V19 A3 3 0 1 1 5 16 H8";
            case "logout" -> "M10 3 H4 V21 H10 M9 12 H22 M17 7 L22 12 L17 17";
            case "chart" -> "M3 3 V21 H23 M7 16 L12 10 L16 13 L22 5";
            case "warning" -> "M12 2 L23 21 H1 Z M12 8 V14 M12 17 V18";
            case "dollar" -> "M12 1 V23 M19 5 H9 A4 4 0 0 0 9 13 H15 A4 4 0 0 1 15 21 H5";
            default -> throw new IllegalArgumentException("Unknown icon: "+name);
        };
        SVGPath icon=new SVGPath();icon.setContent(path);icon.setFill(null);icon.setStroke(Color.web("#18181B"));
        icon.setStrokeWidth(1.7);icon.getStyleClass().add("store-icon");
        Group group=new Group(icon);group.setMouseTransparent(true);return group;
    }
}
