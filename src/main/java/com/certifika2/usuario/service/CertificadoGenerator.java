package com.certifika2.usuario.service;

import com.certifika2.usuario.model.FormatoCertificado;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Genera el archivo del certificado como PDF (OpenPDF) o como imagen PNG (Java2D). */
@Component
public class CertificadoGenerator {

    private static final Color AZUL = new Color(0x1F, 0x3A, 0x68);
    private static final Color DORADO = new Color(0xB8, 0x8A, 0x2E);
    private static final Color GRIS = new Color(0x55, 0x55, 0x55);
    private static final DateTimeFormatter FECHA = DateTimeFormatter
            .ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-CL"))
            .withZone(ZoneId.of("America/Santiago"));

    public record DatosCertificado(String nombreEstudiante, String nombreCurso, String nivelCurso,
                                   Instant fechaCompletado, String codigoVerificacion) {
    }

    public byte[] generar(FormatoCertificado formato, DatosCertificado datos) {
        return switch (formato) {
            case PDF -> generarPdf(datos);
            case PNG -> generarPng(datos);
        };
    }

    private byte[] generarPdf(DatosCertificado d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 70, 70, 70, 50);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            doc.addTitle("Certificado " + d.nombreCurso());
            doc.addAuthor("Certifika2");
            doc.open();

            Rectangle pagina = doc.getPageSize();
            PdfContentByte cb = writer.getDirectContentUnder();
            cb.setColorStroke(AZUL);
            cb.setLineWidth(6f);
            cb.rectangle(20, 20, pagina.getWidth() - 40, pagina.getHeight() - 40);
            cb.stroke();
            cb.setColorStroke(DORADO);
            cb.setLineWidth(1.5f);
            cb.rectangle(32, 32, pagina.getWidth() - 64, pagina.getHeight() - 64);
            cb.stroke();

            doc.add(parrafo("CERTIFIKA2", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, DORADO), 25));
            doc.add(parrafo("CERTIFICADO DE APROBACIÓN", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 30, AZUL), 30));
            doc.add(parrafo("Se certifica que", FontFactory.getFont(FontFactory.HELVETICA, 14, GRIS), 12));
            doc.add(parrafo(d.nombreEstudiante(), FontFactory.getFont(FontFactory.TIMES_BOLDITALIC, 34, Color.BLACK), 18));
            doc.add(parrafo("ha completado satisfactoriamente el curso", FontFactory.getFont(FontFactory.HELVETICA, 14, GRIS), 12));
            doc.add(parrafo(d.nombreCurso(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 22, AZUL), 8));
            if (d.nivelCurso() != null && !d.nivelCurso().isBlank()) {
                doc.add(parrafo("Nivel: " + d.nivelCurso(), FontFactory.getFont(FontFactory.HELVETICA, 12, GRIS), 8));
            }
            doc.add(parrafo("Fecha de aprobación: " + FECHA.format(d.fechaCompletado()),
                    FontFactory.getFont(FontFactory.HELVETICA, 12, GRIS), 40));
            doc.add(parrafo("Código de verificación: " + d.codigoVerificacion(),
                    FontFactory.getFont(FontFactory.COURIER, 9, GRIS), 0));
        } catch (DocumentException e) {
            throw new IllegalStateException("No se pudo generar el PDF del certificado", e);
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return out.toByteArray();
    }

    private Paragraph parrafo(String texto, Font fuente, float espacioDespues) {
        Paragraph p = new Paragraph(texto, fuente);
        p.setAlignment(Element.ALIGN_CENTER);
        p.setSpacingAfter(espacioDespues);
        return p;
    }

    private byte[] generarPng(DatosCertificado d) {
        int w = 1754;  // A4 apaisado a 150 dpi
        int h = 1240;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, w, h);

            g.setColor(AZUL);
            g.setStroke(new BasicStroke(14));
            g.drawRect(40, 40, w - 80, h - 80);
            g.setColor(DORADO);
            g.setStroke(new BasicStroke(3));
            g.drawRect(66, 66, w - 132, h - 132);

            centrado(g, "CERTIFIKA2", fuente(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 34), DORADO, w, 200);
            centrado(g, "CERTIFICADO DE APROBACIÓN", fuente(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 64), AZUL, w, 310);
            centrado(g, "Se certifica que", fuente(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 32), GRIS, w, 420);
            centrado(g, d.nombreEstudiante(), fuente(java.awt.Font.SERIF, java.awt.Font.BOLD | java.awt.Font.ITALIC, 76), Color.BLACK, w, 540);
            centrado(g, "ha completado satisfactoriamente el curso", fuente(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 32), GRIS, w, 640);
            centrado(g, d.nombreCurso(), fuente(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 50), AZUL, w, 740);
            if (d.nivelCurso() != null && !d.nivelCurso().isBlank()) {
                centrado(g, "Nivel: " + d.nivelCurso(), fuente(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 28), GRIS, w, 810);
            }
            centrado(g, "Fecha de aprobación: " + FECHA.format(d.fechaCompletado()),
                    fuente(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 28), GRIS, w, 930);
            centrado(g, "Código de verificación: " + d.codigoVerificacion(),
                    fuente(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 22), GRIS, w, 1080);
        } finally {
            g.dispose();
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo generar el PNG del certificado", e);
        }
    }

    private java.awt.Font fuente(String familia, int estilo, int tamano) {
        return new java.awt.Font(familia, estilo, tamano);
    }

    private void centrado(Graphics2D g, String texto, java.awt.Font fuente, Color color, int ancho, int y) {
        g.setFont(fuente);
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(texto, (ancho - fm.stringWidth(texto)) / 2, y);
    }
}
