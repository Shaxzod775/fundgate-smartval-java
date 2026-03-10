package uz.fundgate.fundgate.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for PDF to image conversion and pitch deck analysis.
 *
 * Pipeline:
 * 1. Download PDF from URL (Firebase Storage, etc.)
 * 2. Convert each page to JPEG image using Apache PDFBox
 * 3. Return images for Claude Vision analysis
 */
@Slf4j
@Service
public class PdfVisionService {

    private final WebClient webClient;

    @Value("${fundgate.pdf.max-pages:20}")
    private int maxPages;

    @Value("${fundgate.pdf.image-dpi:150}")
    private int imageDpi;

    @Value("${fundgate.pdf.image-quality:85}")
    private int imageQuality;

    public PdfVisionService() {
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                .build();
    }

    /**
     * Download PDF from URL and convert pages to JPEG images.
     *
     * @param url URL to PDF file (Firebase Storage, etc.)
     * @return list of JPEG image bytes, one per page
     */
    public List<byte[]> downloadAndConvertPdf(String url) {
        log.info("Downloading PDF from: {}...", url.substring(0, Math.min(100, url.length())));

        byte[] pdfBytes = downloadPdf(url);
        return convertPdfToImages(pdfBytes);
    }

    /**
     * Download PDF from URL.
     */
    public byte[] downloadPdf(String url) {
        try {
            byte[] bytes = webClient.get()
                    .uri(url)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (bytes == null || bytes.length == 0) {
                throw new RuntimeException("Empty PDF response from URL: " + url);
            }

            log.info("Downloaded PDF: {} bytes", bytes.length);
            return bytes;

        } catch (Exception e) {
            log.error("Error downloading PDF: {}", e.getMessage());
            throw new RuntimeException("Failed to download PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Convert PDF bytes to list of JPEG images.
     *
     * @param pdfBytes PDF file content
     * @return list of JPEG image bytes
     */
    public List<byte[]> convertPdfToImages(byte[] pdfBytes) {
        List<byte[]> images = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = Math.min(document.getNumberOfPages(), maxPages);

            log.info("Converting {} pages to images (DPI: {})", pageCount, imageDpi);

            for (int pageNum = 0; pageNum < pageCount; pageNum++) {
                BufferedImage image = renderer.renderImageWithDPI(pageNum, imageDpi, ImageType.RGB);

                // Convert to JPEG bytes
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "JPEG", baos);
                byte[] jpegBytes = baos.toByteArray();

                images.add(jpegBytes);
                log.debug("Converted page {}: {} bytes", pageNum + 1, jpegBytes.length);
            }

            log.info("Successfully converted {} pages to JPEG images", images.size());

        } catch (IOException e) {
            log.error("Error converting PDF to images: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert PDF to images: " + e.getMessage(), e);
        }

        return images;
    }

    /**
     * Extract text content from PDF.
     * Useful as a fallback when vision analysis is not available.
     *
     * @param pdfBytes PDF file content
     * @return extracted text
     */
    public String extractText(byte[] pdfBytes) {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            org.apache.pdfbox.text.PDFTextStripper stripper = new org.apache.pdfbox.text.PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(document.getNumberOfPages(), maxPages));
            String text = stripper.getText(document);
            log.info("Extracted {} characters of text from PDF", text.length());
            return text;
        } catch (IOException e) {
            log.error("Error extracting text from PDF: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract text from PDF: " + e.getMessage(), e);
        }
    }
}
