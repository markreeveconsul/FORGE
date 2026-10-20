package edu.course.shop.service;

import edu.course.shop.model.Product;
import java.util.*;

/** Checks only explicit hardware facts recorded by the store; missing facts stay unverified. */
public final class CompatibilityAdvisor {
    public enum Status { MATCH, CONFLICT, UNVERIFIED }
    public record Check(String label, Status status, String evidence) { }
    private CompatibilityAdvisor() { }

    public static List<Check> analyze(List<Product> products) {
        List<Check> checks = new ArrayList<>();
        List<Product> cpus = products.stream().filter(p -> hasRole(p, "cpu", "processor", "ryzen", "core i3", "core i5", "core i7", "core i9")).toList();
        List<Product> boards = products.stream().filter(p -> hasRole(p, "motherboard", "mainboard")).toList();
        for (Product cpu : cpus) for (Product board : boards) {
            String a = spec(cpu, "socket", "socket type", "cpu socket", "cpu socket type", "processor socket", "processor socket type");
            String b = spec(board, "socket", "socket type", "cpu socket", "cpu socket type", "processor socket", "processor socket type");
            checks.add(compare("CPU / motherboard socket", a, b, cpu.getName(), board.getName()));
        }

        List<Product> memory = products.stream().filter(p -> hasRole(p, "ram", "memory", "ddr4", "ddr5")).toList();
        for (Product item : memory) for (Product board : boards) {
            String a = spec(item, "memory type", "ram type", "memory generation", "ram generation", "type");
            String b = spec(board, "memory type", "ram type", "supported memory", "supported ram", "memory generation", "ram generation");
            checks.add(compare("Memory / motherboard type", a, b, item.getName(), board.getName()));
        }

        List<Product> gpus = products.stream().filter(p -> hasRole(p, "gpu", "graphics card", "geforce", "radeon", "rtx", "rx ")).toList();
        List<Product> cases = products.stream().filter(p -> hasRole(p, "case", "chassis", "pc case")).toList();
        for (Product gpu : gpus) for (Product pcCase : cases) {
            String a = spec(gpu, "length", "gpu length", "graphics card length", "card length");
            String b = spec(pcCase, "maximum gpu length", "max gpu length", "gpu clearance", "maximum graphics card length", "graphics card clearance");
            checks.add(lengthCheck(a, b, gpu.getName(), pcCase.getName()));
        }

        List<Product> psus = products.stream().filter(p -> hasRole(p, "power supply", "psu")).toList();
        for (Product gpu : gpus) for (Product psu : psus) {
            String a = spec(gpu, "recommended psu", "recommended power supply", "minimum psu", "minimum power supply");
            String b = spec(psu, "wattage", "power", "capacity");
            checks.add(wattageCheck(a, b, gpu.getName(), psu.getName()));
        }

        if (checks.isEmpty()) checks.add(new Check("Build compatibility", Status.UNVERIFIED,
            "No matching CPU, motherboard, memory, GPU, case or power-supply specifications were available to compare."));
        return List.copyOf(checks);
    }

    public static String report(List<Product> products) {
        List<Check> checks = analyze(products);
        StringBuilder out = new StringBuilder("Compatibility check (based only on saved product specs):");
        for (Check check : checks) out.append("\n• ").append(check.status()).append(" — ").append(check.label()).append(": ").append(check.evidence());
        if (checks.stream().anyMatch(c -> c.status() == Status.CONFLICT)) out.append("\nAt least one recorded conflict needs resolving before purchase.");
        else if (checks.stream().anyMatch(c -> c.status() == Status.UNVERIFIED)) out.append("\nThis is not a complete compatibility certification; add the missing specifications to the product records to verify the build.");
        return out.toString();
    }

    private static Check compare(String label, String a, String b, String nameA, String nameB) {
        if (a == null || b == null) return new Check(label, Status.UNVERIFIED,
            "Missing socket data for " + (a == null ? nameA : nameB) + ".");
        String valueA = normalizeComparable(label, a), valueB = normalizeComparable(label, b);
        boolean same = valueA.equals(valueB);
        return new Check(label, same ? Status.MATCH : Status.CONFLICT,
            nameA + " lists " + a + "; " + nameB + " lists " + b + ".");
    }

    private static Check lengthCheck(String gpuLength, String clearance, String gpu, String pcCase) {
        Double length = millimeters(gpuLength), max = millimeters(clearance);
        if (length == null || max == null) return new Check("GPU / case clearance", Status.UNVERIFIED,
            "Missing GPU length or case clearance for " + (length == null ? gpu : pcCase) + ".");
        return new Check("GPU / case clearance", length <= max ? Status.MATCH : Status.CONFLICT,
            gpu + " is " + gpuLength + "; " + pcCase + " allows " + clearance + ".");
    }

    private static Check wattageCheck(String recommended, String capacity, String gpu, String psu) {
        Double need = watts(recommended), have = watts(capacity);
        if (need == null || have == null) return new Check("GPU / power supply", Status.UNVERIFIED,
            "Missing recommended PSU wattage or PSU capacity for " + (need == null ? gpu : psu) + ".");
        return new Check("GPU / power supply", have >= need ? Status.MATCH : Status.CONFLICT,
            gpu + " recommends " + recommended + "; " + psu + " lists " + capacity + ".");
    }

    private static boolean hasRole(Product p, String... words) {
        String text = (p.getName() + " " + p.getCategory().label()).toLowerCase(Locale.ROOT);
        return Arrays.stream(words).anyMatch(text::contains);
    }
    private static String spec(Product product, String... keys) {
        Set<String> wanted = new HashSet<>(); for (String key : keys) wanted.add(normalize(key));
        for (String line : product.getSpecifications().lines().toList()) {
            int colon = line.indexOf(':'); if (colon < 1) continue;
            if (wanted.contains(normalize(line.substring(0, colon)))) {
                String value = line.substring(colon + 1).trim(); if (!value.isBlank()) return value;
            }
        }
        return null;
    }
    private static String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
    private static String normalizeComparable(String label,String value) {
        String normalized=normalize(value);
        if(label.equals("CPU / motherboard socket"))return normalized.replace("sockettype", "").replace("socket", "");
        if(label.equals("Memory / motherboard type")) {
            var ddr=java.util.regex.Pattern.compile("ddr\\s*([345])",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value);
            if(ddr.find())return "ddr"+ddr.group(1);
        }
        return normalized;
    }
    private static Double millimeters(String value) {
        if (value == null) return null;
        var match = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(mm|millimeters?|cm|centimeters?|inches|inch|in|\\\")",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value.replace(",", ""));
        if(!match.find())return null;
        double number=Double.parseDouble(match.group(1));String unit=match.group(2).toLowerCase(Locale.ROOT);
        return unit.equals("mm")||unit.startsWith("millimeter")?number:unit.equals("cm")||unit.startsWith("centimeter")?number*10:number*25.4;
    }
    private static Double watts(String value) {
        if (value == null) return null;
        var match=java.util.regex.Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(kw|kilowatts?|w|watts?)\\b",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(value.replace(",", ""));
        if(!match.find())return null;
        double number=Double.parseDouble(match.group(1));return match.group(2).toLowerCase(Locale.ROOT).startsWith("k")?number*1000:number;
    }
}
