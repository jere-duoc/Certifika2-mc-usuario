package com.certifika2.usuario.certificados;

import org.springframework.stereotype.Component;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

@Component
public class CertificadoRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu").withZone(ZoneOffset.UTC);
    public byte[] render(Certificado c) {
        BufferedImage image = image(c);
        return "PNG".equals(c.getFormato()) ? encode(image, "png") : pdf(image);
    }
    private List<String> lines(Certificado c) {
        return List.of("CERTIFIKA2", "CERTIFICADO DE FINALIZACIÓN", "Se certifica que", c.getNombreTitular(),
                "ha completado el curso", c.getNombreCurso(),
                "Nivel: " + (c.getNivelCurso() == null ? "No especificado" : c.getNivelCurso()),
                "Finalización: " + DATE.format(c.getFechaCompletado()), "Emisión: " + DATE.format(c.getFechaEmision()),
                "Verificación: " + c.getCodigoVerificacion());
    }
    private BufferedImage image(Certificado c) {
        var image = new BufferedImage(1600, 1120, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE); g.fillRect(0, 0, 1600, 1120);
            g.setColor(new Color(17, 44, 74)); g.fillRect(0, 0, 1600, 20);
            g.setColor(new Color(211, 222, 232)); g.drawRect(55, 55, 1490, 1010);
            var rows = new ArrayList<Row>(); int totalHeight = 0;
            var lines = lines(c);
            for (int i = 0; i < lines.size(); i++) {
                int size = i == 3 ? 48 : i == 1 ? 34 : i == 0 ? 28 : 23;
                int style = i == 3 || i == 1 ? Font.BOLD : Font.PLAIN;
                var font = new Font(Font.SANS_SERIF, style, size);
                for (String line : wrap(lines.get(i), g.getFontMetrics(font), 1320)) {
                    rows.add(new Row(line, size, style)); totalHeight += size + 12;
                }
                rows.add(new Row("", 0, Font.PLAIN)); totalHeight += 12;
            }
            double scale = Math.min(1.0, 875.0 / totalHeight); double y = 140;
            for (Row row : rows) {
                if (row.size() > 0) {
                    g.setFont(new Font(Font.SANS_SERIF, row.style(), (int)Math.round(row.size() * scale)));
                    g.setColor(row.style() == Font.BOLD ? new Color(17, 44, 74) : new Color(71, 85, 105));
                    g.drawString(row.text(), 130, (int)y);
                }
                y += (row.size() + 12) * scale;
            }
            return image;
        } finally { g.dispose(); }
    }
    private byte[] encode(BufferedImage image, String format) {
        try {
            var output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, output)) throw new IllegalStateException("Formato de imagen no disponible");
            return output.toByteArray();
        } catch (java.io.IOException ex) { throw new IllegalStateException("No fue posible generar el certificado", ex); }
    }
    private byte[] pdf(BufferedImage image) {
        // Image-backed PDF keeps the same typography and Unicode rendering as PNG without external fonts in the PDF reader.
        byte[] jpeg = encode(image, "jpeg");
        byte[] content = ascii("q 842 0 0 589.4 0 2.8 cm /Im0 Do Q\n");
        var objects = List.of(ascii("<< /Type /Catalog /Pages 2 0 R >>"),
                ascii("<< /Type /Pages /Kids [3 0 R] /Count 1 >>"),
                ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 842 595] /Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>"),
                concat(ascii("<< /Type /XObject /Subtype /Image /Width 1600 /Height 1120 /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length " + jpeg.length + " >>\nstream\n"), jpeg, ascii("\nendstream")),
                concat(ascii("<< /Length " + content.length + " >>\nstream\n"), content, ascii("endstream")));
        var output = new ByteArrayOutputStream(); output.writeBytes(ascii("%PDF-1.4\n"));
        var offsets = new ArrayList<Integer>(); offsets.add(0);
        for (int i = 0; i < objects.size(); i++) {
            offsets.add(output.size()); output.writeBytes(ascii((i + 1) + " 0 obj\n"));
            output.writeBytes(objects.get(i)); output.writeBytes(ascii("\nendobj\n"));
        }
        int xref = output.size(); output.writeBytes(ascii("xref\n0 6\n0000000000 65535 f \n"));
        for (int i = 1; i < offsets.size(); i++) output.writeBytes(ascii(String.format(java.util.Locale.ROOT, "%010d 00000 n \n", offsets.get(i))));
        output.writeBytes(ascii("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n"));
        return output.toByteArray();
    }
    private byte[] ascii(String text) { return text.getBytes(StandardCharsets.US_ASCII); }
    private byte[] concat(byte[]... arrays) { var out = new ByteArrayOutputStream(); for (byte[] a : arrays) out.writeBytes(a); return out.toByteArray(); }
    private List<String> wrap(String text, FontMetrics metrics, int width) {
        var result = new ArrayList<String>(); String clean = text.replaceAll("[\\p{Cntrl}]", " ");
        while (metrics.stringWidth(clean) > width) {
            int fit = clean.length(); while (fit > 1 && metrics.stringWidth(clean.substring(0, fit)) > width) fit--;
            int split = clean.lastIndexOf(' ', fit); if (split < fit / 2) split = fit;
            result.add(clean.substring(0, split)); clean = clean.substring(split).stripLeading();
        }
        result.add(clean); return result;
    }
    private record Row(String text, int size, int style) {}
}
