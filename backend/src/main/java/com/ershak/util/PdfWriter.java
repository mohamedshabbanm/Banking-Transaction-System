package com.ershak.util;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * A tiny PDF writer: A4 pages, the two built-in fonts (Helvetica and Helvetica-Bold), text, lines and
 * filled rectangles. No library. A PDF is a list of numbered objects followed by a table that says where
 * each one starts, so we write the objects, remember their byte offsets, then write that table.
 * <p>
 * The built-in fonts only cover Latin text; any other character is printed as "?".
 */
public final class PdfWriter {

    public static final double WIDTH = 595;
    public static final double HEIGHT = 842;

    private final List<StringBuilder> pages = new ArrayList<>();
    private StringBuilder page;

    public PdfWriter() {
        newPage();
    }

    public void newPage() {
        page = new StringBuilder();
        pages.add(page);
    }

    public int pageCount() {
        return pages.size();
    }

    /** Selects the page that later drawing calls write to (1-based). */
    public void usePage(int number) {
        page = pages.get(number - 1);
    }

    /** y is measured from the TOP of the page, like in a web page. */
    public void text(double x, double y, double size, boolean bold, String text, int[] rgb) {
        page.append("BT /").append(bold ? "F2" : "F1").append(' ').append(num(size)).append(" Tf ")
                .append(color(rgb)).append(" rg ")
                .append(num(x)).append(' ').append(num(HEIGHT - y)).append(" Td (")
                .append(escape(text)).append(") Tj ET\n");
    }

    public void textRight(double xRight, double y, double size, boolean bold, String text, int[] rgb) {
        text(xRight - width(text, size, bold), y, size, bold, text, rgb);
    }

    public void rect(double x, double y, double w, double h, int[] rgb) {
        page.append(color(rgb)).append(" rg ").append(num(x)).append(' ').append(num(HEIGHT - y - h)).append(' ')
                .append(num(w)).append(' ').append(num(h)).append(" re f\n");
    }

    public void line(double x1, double y1, double x2, double y2, double thickness, int[] rgb) {
        page.append(color(rgb)).append(" RG ").append(num(thickness)).append(" w ")
                .append(num(x1)).append(' ').append(num(HEIGHT - y1)).append(" m ")
                .append(num(x2)).append(' ').append(num(HEIGHT - y2)).append(" l S\n");
    }

    /**
     * Text width in points. Digits and common money characters use the real Helvetica widths, so amounts
     * can be right-aligned exactly; other letters use an average that is close enough for layout.
     */
    public static double width(String text, double size, boolean bold) {
        double units = 0;
        for (char ch : text.toCharArray()) {
            units += switch (ch) {
                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> 556;
                case '.', ',', ' ', ':', '/', '|' -> 278;
                case '-', '(', ')' -> 333;
                case 'i', 'j', 'l' -> 222;
                case 'm', 'M', 'W' -> 833;
                case 'f', 't', 'r' -> 300;
                default -> Character.isUpperCase(ch) ? 667 : 530;
            };
            if (bold && !Character.isDigit(ch)) {
                units += 20;
            }
        }
        return units * size / 1000.0;
    }

    /** Cuts a text with "..." so it fits the width. */
    public static String fit(String text, double maxWidth, double size, boolean bold) {
        if (width(text, size, bold) <= maxWidth) {
            return text;
        }
        String s = text;
        while (s.length() > 1 && width(s + "...", size, bold) > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + "...";
    }

    public byte[] toBytes() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        write(out, "%PDF-1.4\n");

        // Object layout: 1 catalog, 2 page tree, 3 regular font, 4 bold font, then (page, content) pairs.
        int firstPage = 5;
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < pages.size(); i++) {
            kids.append(firstPage + i * 2).append(" 0 R ");
        }
        object(out, offsets, "<< /Type /Catalog /Pages 2 0 R >>");
        object(out, offsets, "<< /Type /Pages /Kids [" + kids + "] /Count " + pages.size() + " >>");
        object(out, offsets, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        object(out, offsets, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        for (int i = 0; i < pages.size(); i++) {
            int content = firstPage + i * 2 + 1;
            object(out, offsets, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + (int) WIDTH + " " + (int) HEIGHT
                    + "] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents " + content + " 0 R >>");
            byte[] stream = pages.get(i).toString().getBytes(StandardCharsets.ISO_8859_1);
            offsets.add(out.size());
            write(out, (offsets.size()) + " 0 obj\n<< /Length " + stream.length + " >>\nstream\n");
            out.writeBytes(stream);
            write(out, "\nendstream\nendobj\n");
        }

        int xref = out.size();
        write(out, "xref\n0 " + (offsets.size() + 1) + "\n0000000000 65535 f \n");
        for (int offset : offsets) {
            write(out, String.format("%010d 00000 n \n", offset));
        }
        write(out, "trailer\n<< /Size " + (offsets.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static void object(ByteArrayOutputStream out, List<Integer> offsets, String body) {
        offsets.add(out.size());
        write(out, offsets.size() + " 0 obj\n" + body + "\nendobj\n");
    }

    private static void write(ByteArrayOutputStream out, String s) {
        out.writeBytes(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String escape(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (ch == '(' || ch == ')' || ch == '\\') {
                b.append('\\').append(ch);
            } else if (ch >= 32 && ch < 127) {
                b.append(ch);
            } else if (ch == '·') {
                b.append("\\267");
            } else if (ch == '•') {
                b.append("\\225");
            } else if (ch == '—' || ch == '–') {
                b.append('-');
            } else {
                b.append('?');
            }
        }
        return b.toString();
    }

    private static String color(int[] rgb) {
        return num(rgb[0] / 255.0) + " " + num(rgb[1] / 255.0) + " " + num(rgb[2] / 255.0);
    }

    private static String num(double v) {
        String s = String.format(java.util.Locale.ROOT, "%.2f", v);
        return s.contains(".") ? s.replaceAll("0+$", "").replaceAll("\\.$", "") : s;
    }
}
