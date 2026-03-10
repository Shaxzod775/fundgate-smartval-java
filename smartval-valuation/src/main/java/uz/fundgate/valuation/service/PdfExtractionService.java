package uz.fundgate.valuation.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
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
 * PDF text and image extraction service.
 * Downloads PDFs from URLs and extracts text content and page images.
 * Mirrors the Python pdf_service.py functionality.
 */
@Slf4j
@Service
public class PdfExtractionService {

    private final WebClient webClient;

    @Value("${valuation.pdf.max-pages:20}")
    private int maxPages;

    @Value("${valuation.pdf.image-dpi:150}")
    private int imageDpi;

    public PdfExtractionService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * Extract text content from a PDF at the given URL.
     *
     * @param pdfUrl URL of the PDF document
     * @return extracted text content
     */
    public String extractTextFromUrl(String pdfUrl) {
        try {
            log.info("Downloading PDF from: {}", pdfUrl);
            byte[] pdfBytes = downloadPdf(pdfUrl);

            if (pdfBytes == null || pdfBytes.length == 0) {
                log.warn("Empty PDF downloaded from: {}", pdfUrl);
                return "";
            }

            return extractTextFromBytes(pdfBytes);
        } catch (Exception e) {
            log.error("Error extracting text from PDF {}: {}", pdfUrl, e.getMessage(), e);
            return "";
        }
    }

    /**
     * Extract text content from PDF bytes.
     */
    public String extractTextFromBytes(byte[] pdfBytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int pageCount = Math.min(document.getNumberOfPages(), maxPages);
            log.info("PDF has {} pages, extracting up to {}", document.getNumberOfPages(), pageCount);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(pageCount);

            String text = stripper.getText(document);
            log.info("Extracted {} characters from PDF", text.length());
            return text;
        }
    }

    /**
     * Extract page images from a PDF at the given URL.
     * Returns JPEG byte arrays for each page (for use with Claude Vision).
     *
     * @param pdfUrl URL of the PDF document
     * @return list of JPEG image bytes, one per page
     */
    public List<byte[]> extractImagesFromUrl(String pdfUrl) {
        try {
            byte[] pdfBytes = downloadPdf(pdfUrl);
            if (pdfBytes == null || pdfBytes.length == 0) {
                return List.of();
            }
            return extractImagesFromBytes(pdfBytes);
        } catch (Exception e) {
            log.error("Error extracting images from PDF {}: {}", pdfUrl, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Extract page images from PDF bytes.
     */
    public List<byte[]> extractImagesFromBytes(byte[] pdfBytes) throws IOException {
        List<byte[]> images = new ArrayList<>();

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = Math.min(document.getNumberOfPages(), maxPages);

            for (int i = 0; i < pageCount; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, imageDpi);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(image, "JPEG", baos);
                images.add(baos.toByteArray());
            }
        }

        log.info("Extracted {} page images from PDF", images.size());
        return images;
    }

    /**
     * Download PDF bytes from URL.
     */
    private byte[] downloadPdf(String pdfUrl) {
        try {
            return webClient.get()
                    .uri(pdfUrl)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
        } catch (Exception e) {
            log.error("Failed to download PDF from {}: {}", pdfUrl, e.getMessage());
            return null;
        }
    }
}
