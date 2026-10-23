package com.capstone.agenticrag.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Component;

import com.capstone.agenticrag.common.Texts;

/**
 * Extracts text from supported files into sections that keep page numbers (PDF) or headings (Markdown/DOCX) so that
 * citations can reference them. No metadata is invented: TXT has neither pages nor sections.
 */
@Component
public class TextExtractor {

    static final int MAX_PDF_PAGES = 2000;
    private static final Pattern MD_HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");

    static {
        // Defend against zip bombs in DOCX files.
        ZipSecureFile.setMinInflateRatio(0.01);
        ZipSecureFile.setMaxEntrySize(200L * 1024 * 1024);
    }

    public record Section(Integer pageNumber, String heading, String text) {}

    public record Extracted(List<Section> sections, Integer pageCount) {
        public int totalChars() {
            return sections.stream().mapToInt(s -> s.text().length()).sum();
        }
    }

    /** Thrown with a message that is safe to show to the user. */
    public static class ExtractionException extends Exception {
        public ExtractionException(String message) {
            super(message);
        }
    }

    public Extracted extract(FileType type, byte[] data) throws ExtractionException {
        try {
            return switch (type) {
                case PDF -> pdf(data);
                case DOCX -> docx(data);
                case MD -> markdown(new String(data, StandardCharsets.UTF_8));
                case TXT -> new Extracted(List.of(new Section(null, null, Texts.clean(new String(data, StandardCharsets.UTF_8)))), null);
            };
        } catch (ExtractionException e) {
            throw e;
        } catch (Exception e) {
            throw new ExtractionException("The file could not be read. It may be corrupted or in an unsupported format.");
        }
    }

    private Extracted pdf(byte[] data) throws IOException, ExtractionException {
        try (PDDocument doc = Loader.loadPDF(data)) {
            if (doc.isEncrypted() && !doc.getCurrentAccessPermission().canExtractContent()) {
                throw new ExtractionException("This PDF is protected and does not allow text extraction.");
            }
            int pages = doc.getNumberOfPages();
            if (pages > MAX_PDF_PAGES) {
                throw new ExtractionException("PDFs with more than " + MAX_PDF_PAGES + " pages are not supported.");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<Section> sections = new ArrayList<>();
            for (int page = 1; page <= pages; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = Texts.clean(dehyphenate(stripper.getText(doc)));
                if (!text.isBlank()) {
                    sections.add(new Section(page, null, text));
                }
            }
            Extracted extracted = new Extracted(sections, pages);
            // Scanned/image-only PDFs have pages but (almost) no text layer. OCR is intentionally not performed.
            if (pages > 0 && extracted.totalChars() < Math.max(20, pages * 10)) {
                throw new ExtractionException("No extractable text was found. The PDF appears to be scanned or "
                        + "image-only; OCR is not supported. Please upload a text-based PDF.");
            }
            return extracted;
        } catch (InvalidPasswordException e) {
            throw new ExtractionException("Password-protected PDFs are not supported.");
        }
    }

    private Extracted docx(byte[] data) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(data))) {
            List<Section> sections = new ArrayList<>();
            String heading = null;
            StringBuilder current = new StringBuilder();
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph p) {
                    String text = p.getText();
                    String style = p.getStyle() == null ? "" : p.getStyle().toLowerCase();
                    boolean isHeading = style.startsWith("heading") || style.equals("title");
                    if (isHeading && !text.isBlank()) {
                        flush(sections, heading, current);
                        heading = Texts.truncate(text.strip(), 300);
                    }
                    current.append(text).append('\n');
                } else if (element instanceof XWPFTable t) {
                    t.getRows().forEach(row -> {
                        row.getTableCells().forEach(cell -> current.append(cell.getText()).append(" | "));
                        current.append('\n');
                    });
                }
            }
            flush(sections, heading, current);
            return new Extracted(sections, null);
        }
    }

    private Extracted markdown(String text) {
        List<Section> sections = new ArrayList<>();
        String heading = null;
        StringBuilder current = new StringBuilder();
        boolean inCodeFence = false;
        for (String line : text.split("\\R")) {
            if (line.strip().startsWith("```")) {
                inCodeFence = !inCodeFence;
            }
            Matcher m = MD_HEADING.matcher(line);
            if (!inCodeFence && m.matches()) {
                flush(sections, heading, current);
                heading = Texts.truncate(m.group(2).strip(), 300);
            }
            current.append(line).append('\n');
        }
        flush(sections, heading, current);
        return new Extracted(sections, null);
    }

    private static void flush(List<Section> sections, String heading, StringBuilder current) {
        String text = Texts.clean(current.toString());
        if (!text.isBlank()) {
            sections.add(new Section(null, heading, text));
        }
        current.setLength(0);
    }

    /** Joins words split across lines by a trailing hyphen ("informa-\ntion" becomes "information"). */
    private static String dehyphenate(String text) {
        return text.replaceAll("(\\p{L})-\\n(\\p{Ll})", "$1$2");
    }
}
