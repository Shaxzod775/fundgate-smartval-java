package uz.fundgate.chat.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import uz.fundgate.chat.dto.DocumentGenerateRequest;
import uz.fundgate.chat.dto.DocumentResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Service for generating Word, Excel, and PowerPoint documents using Apache POI.
 *
 * Ported from Python chatkit-backend: documents.py + claude_document_agent.py
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final String WORKSPACE_DIR = System.getProperty("java.io.tmpdir") + "/smartval-docs";

    /**
     * Generate a document based on the request format.
     *
     * @return byte array of the generated document
     */
    public byte[] generateDocument(DocumentGenerateRequest request) {
        log.info("[DOCUMENT] Generating {} document: {}", request.getFormat(), request.getTitle());

        return switch (request.getFormat().toLowerCase()) {
            case "docx" -> generateWordDocument(request);
            case "xlsx" -> generateExcelDocument(request);
            case "pptx" -> generatePowerPointDocument(request);
            default -> throw new IllegalArgumentException("Unsupported format: " + request.getFormat());
        };
    }

    /**
     * Generate document and save to temporary storage, returning file metadata.
     */
    public DocumentResponse generateAndSave(DocumentGenerateRequest request) {
        byte[] documentBytes = generateDocument(request);

        String fileId = UUID.randomUUID().toString().substring(0, 8);
        String fileName = sanitizeFileName(request.getTitle()) + "." + request.getFormat();
        String fullFileName = fileId + "_" + fileName;

        try {
            Path workspacePath = Path.of(WORKSPACE_DIR);
            Files.createDirectories(workspacePath);
            Path filePath = workspacePath.resolve(fullFileName);
            Files.write(filePath, documentBytes);

            log.info("[DOCUMENT] Saved document: {}, size: {} bytes", filePath, documentBytes.length);

            return DocumentResponse.builder()
                    .fileUrl("/api/download-document/" + fileId + "/" + fileName)
                    .fileName(fileName)
                    .format(request.getFormat())
                    .fileSize(documentBytes.length)
                    .build();

        } catch (IOException e) {
            log.error("[DOCUMENT] Failed to save document: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save generated document", e);
        }
    }

    /**
     * Get a saved document by file ID and file name.
     */
    public byte[] getDocument(String fileId, String fileName) {
        Path filePath = Path.of(WORKSPACE_DIR, fileId + "_" + fileName);

        if (!Files.exists(filePath)) {
            throw new RuntimeException("Document not found: " + fileName);
        }

        try {
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read document: " + e.getMessage(), e);
        }
    }

    // =========================================================================
    // Word Document Generation
    // =========================================================================

    private byte[] generateWordDocument(DocumentGenerateRequest request) {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            // Title
            XWPFParagraph titleParagraph = document.createParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            titleParagraph.setSpacingAfter(400);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText(request.getTitle());
            titleRun.setBold(true);
            titleRun.setFontSize(24);
            titleRun.setFontFamily("Arial");

            // Main content
            if (request.getContent() != null && !request.getContent().isBlank()) {
                addContentParagraphs(document, request.getContent());
            }

            // Sections
            List<DocumentGenerateRequest.DocumentSection> sections = request.getSections();
            if (sections != null) {
                for (DocumentGenerateRequest.DocumentSection section : sections) {
                    // Section heading
                    if (section.getHeading() != null && !section.getHeading().isBlank()) {
                        XWPFParagraph headingParagraph = document.createParagraph();
                        headingParagraph.setStyle("Heading2");
                        headingParagraph.setSpacingBefore(300);
                        headingParagraph.setSpacingAfter(200);
                        XWPFRun headingRun = headingParagraph.createRun();
                        headingRun.setText(section.getHeading());
                        headingRun.setBold(true);
                        headingRun.setFontSize(16);
                        headingRun.setFontFamily("Arial");
                    }

                    // Section body
                    if (section.getBody() != null && !section.getBody().isBlank()) {
                        addContentParagraphs(document, section.getBody());
                    }
                }
            }

            document.write(outputStream);
            log.info("[DOCUMENT] Generated Word document: {}", request.getTitle());
            return outputStream.toByteArray();

        } catch (IOException e) {
            log.error("[DOCUMENT] Error generating Word document: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate Word document", e);
        }
    }

    private void addContentParagraphs(XWPFDocument document, String content) {
        String[] paragraphs = content.split("\n\n");
        for (String paragraphText : paragraphs) {
            if (!paragraphText.isBlank()) {
                XWPFParagraph paragraph = document.createParagraph();
                paragraph.setSpacingAfter(200);
                XWPFRun run = paragraph.createRun();
                run.setText(paragraphText.trim());
                run.setFontSize(12);
                run.setFontFamily("Arial");
            }
        }
    }

    // =========================================================================
    // Excel Document Generation
    // =========================================================================

    private byte[] generateExcelDocument(DocumentGenerateRequest request) {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet(request.getTitle());

            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Create data style
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            // Parse content as rows (tab-separated or comma-separated)
            String[] lines = request.getContent().split("\n");
            int rowNum = 0;

            for (String line : lines) {
                if (line.isBlank()) continue;

                Row row = sheet.createRow(rowNum);
                String[] cells = line.contains("\t") ? line.split("\t") : line.split(",");

                for (int colNum = 0; colNum < cells.length; colNum++) {
                    Cell cell = row.createCell(colNum);
                    String value = cells[colNum].trim();

                    // Try to parse as number
                    try {
                        double numVal = Double.parseDouble(value);
                        cell.setCellValue(numVal);
                    } catch (NumberFormatException e) {
                        cell.setCellValue(value);
                    }

                    cell.setCellStyle(rowNum == 0 ? headerStyle : dataStyle);
                }
                rowNum++;
            }

            // Auto-size columns
            if (rowNum > 0) {
                Row firstRow = sheet.getRow(0);
                if (firstRow != null) {
                    for (int i = 0; i < firstRow.getLastCellNum(); i++) {
                        sheet.autoSizeColumn(i);
                    }
                }
            }

            // Add sections as additional sheets
            List<DocumentGenerateRequest.DocumentSection> sections = request.getSections();
            if (sections != null) {
                for (DocumentGenerateRequest.DocumentSection section : sections) {
                    if (section.getHeading() != null && section.getBody() != null) {
                        Sheet sectionSheet = workbook.createSheet(section.getHeading());
                        String[] sectionLines = section.getBody().split("\n");
                        int sectionRowNum = 0;
                        for (String sectionLine : sectionLines) {
                            if (sectionLine.isBlank()) continue;
                            Row sectionRow = sectionSheet.createRow(sectionRowNum);
                            String[] sectionCells = sectionLine.contains("\t")
                                    ? sectionLine.split("\t") : sectionLine.split(",");
                            for (int colNum = 0; colNum < sectionCells.length; colNum++) {
                                Cell cell = sectionRow.createCell(colNum);
                                cell.setCellValue(sectionCells[colNum].trim());
                                cell.setCellStyle(sectionRowNum == 0 ? headerStyle : dataStyle);
                            }
                            sectionRowNum++;
                        }
                    }
                }
            }

            workbook.write(outputStream);
            log.info("[DOCUMENT] Generated Excel document: {}", request.getTitle());
            return outputStream.toByteArray();

        } catch (IOException e) {
            log.error("[DOCUMENT] Error generating Excel document: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate Excel document", e);
        }
    }

    // =========================================================================
    // PowerPoint Document Generation
    // =========================================================================

    private byte[] generatePowerPointDocument(DocumentGenerateRequest request) {
        try (XMLSlideShow pptx = new XMLSlideShow();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            // Title slide
            XSLFSlideLayout titleLayout = pptx.getSlideMasters().get(0)
                    .getLayout(SlideLayout.TITLE);
            XSLFSlide titleSlide = pptx.createSlide(titleLayout);

            XSLFTextShape titleShape = titleSlide.getPlaceholder(0);
            if (titleShape != null) {
                titleShape.setText(request.getTitle());
            }

            XSLFTextShape subtitleShape = titleSlide.getPlaceholder(1);
            if (subtitleShape != null) {
                subtitleShape.setText("Generated by FundGate SmartVal");
            }

            // Content slide
            if (request.getContent() != null && !request.getContent().isBlank()) {
                XSLFSlideLayout contentLayout = pptx.getSlideMasters().get(0)
                        .getLayout(SlideLayout.TITLE_AND_CONTENT);
                XSLFSlide contentSlide = pptx.createSlide(contentLayout);

                XSLFTextShape contentTitle = contentSlide.getPlaceholder(0);
                if (contentTitle != null) {
                    contentTitle.setText("Overview");
                }

                XSLFTextShape contentBody = contentSlide.getPlaceholder(1);
                if (contentBody != null) {
                    contentBody.clearText();
                    String[] contentParagraphs = request.getContent().split("\n");
                    boolean first = true;
                    for (String paragraphText : contentParagraphs) {
                        if (!paragraphText.isBlank()) {
                            XSLFTextParagraph paragraph;
                            if (first) {
                                paragraph = contentBody.getTextParagraphs().get(0);
                                first = false;
                            } else {
                                paragraph = contentBody.addNewTextParagraph();
                            }
                            paragraph.addNewTextRun().setText(paragraphText.trim());
                        }
                    }
                }
            }

            // Section slides
            List<DocumentGenerateRequest.DocumentSection> sections = request.getSections();
            if (sections != null) {
                for (DocumentGenerateRequest.DocumentSection section : sections) {
                    XSLFSlideLayout sectionLayout = pptx.getSlideMasters().get(0)
                            .getLayout(SlideLayout.TITLE_AND_CONTENT);
                    XSLFSlide sectionSlide = pptx.createSlide(sectionLayout);

                    XSLFTextShape sectionTitle = sectionSlide.getPlaceholder(0);
                    if (sectionTitle != null && section.getHeading() != null) {
                        sectionTitle.setText(section.getHeading());
                    }

                    XSLFTextShape sectionBody = sectionSlide.getPlaceholder(1);
                    if (sectionBody != null && section.getBody() != null) {
                        sectionBody.clearText();
                        String[] bodyParagraphs = section.getBody().split("\n");
                        boolean first = true;
                        for (String bodyText : bodyParagraphs) {
                            if (!bodyText.isBlank()) {
                                XSLFTextParagraph paragraph;
                                if (first) {
                                    paragraph = sectionBody.getTextParagraphs().get(0);
                                    first = false;
                                } else {
                                    paragraph = sectionBody.addNewTextParagraph();
                                }
                                paragraph.addNewTextRun().setText(bodyText.trim());
                            }
                        }
                    }
                }
            }

            pptx.write(outputStream);
            log.info("[DOCUMENT] Generated PowerPoint document: {}", request.getTitle());
            return outputStream.toByteArray();

        } catch (IOException e) {
            log.error("[DOCUMENT] Error generating PowerPoint document: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PowerPoint document", e);
        }
    }

    // =========================================================================
    // Utility
    // =========================================================================

    /**
     * Get the MIME type for a document format.
     */
    public String getMimeType(String format) {
        return switch (format.toLowerCase()) {
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }

    private String sanitizeFileName(String name) {
        return name.replaceAll("[^a-zA-Z0-9\\-_ ]", "")
                .replaceAll("\\s+", "_")
                .toLowerCase();
    }
}
