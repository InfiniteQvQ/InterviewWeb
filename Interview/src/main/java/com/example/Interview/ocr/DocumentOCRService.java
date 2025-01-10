package com.example.Interview.ocr;

import com.google.cloud.vision.v1.*;
import com.google.protobuf.ByteString;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentOCRService {

    public DocumentOCRService() {
        // 设置 GOOGLE_APPLICATION_CREDENTIALS 环境变量
        String credentialsPath = "src/main/resources/solosync-15708ffbd26e.json";
        System.setProperty("GOOGLE_APPLICATION_CREDENTIALS", new File(credentialsPath).getAbsolutePath());

    }

    public String processFile(File file, String outputDir) throws IOException {
        if (file.getName().endsWith(".pdf")) {
            return processPdf(file, outputDir);
        } else if (file.getName().endsWith(".docx") || file.getName().endsWith(".doc")) {
            return processWord(file);
        } else {
            throw new IllegalArgumentException("Unsupported file format. Please provide a PDF or Word file.");
        }
    }

    private String processPdf(File pdfFile, String outputDir) throws IOException {
        List<File> imageFiles = convertPdfToImages(pdfFile, outputDir);
        StringBuilder fullText = new StringBuilder();

        for (File imageFile : imageFiles) {
            String text = recognizeText(imageFile);
            fullText.append(text).append("\n\n");
        }

        return fullText.toString();
    }

    private String processWord(File wordFile) throws IOException {
        try (FileInputStream fis = new FileInputStream(wordFile)) {
            XWPFDocument document = new XWPFDocument(fis);
            StringBuilder text = new StringBuilder();

            document.getParagraphs().forEach(paragraph -> text.append(paragraph.getText()).append("\n"));
            return text.toString();
        }
    }

    private List<File> convertPdfToImages(File pdfFile, String outputDir) throws IOException {
        List<File> imageFiles = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfFile)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            for (int page = 0; page < document.getNumberOfPages(); page++) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300);
                File outputFile = new File(outputDir, "page_" + page + ".png");
                ImageIO.write(image, "png", outputFile);
                imageFiles.add(outputFile);
            }
        }
        return imageFiles;
    }

    private String recognizeText(File imageFile) throws IOException {
        try (ImageAnnotatorClient vision = ImageAnnotatorClient.create()) {
            ByteString imgBytes = ByteString.readFrom(new FileInputStream(imageFile));

            Image img = Image.newBuilder().setContent(imgBytes).build();
            Feature feat = Feature.newBuilder().setType(Feature.Type.DOCUMENT_TEXT_DETECTION).build();
            AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
                    .addFeatures(feat)
                    .setImage(img)
                    .build();

            AnnotateImageResponse response = vision.batchAnnotateImages(List.of(request)).getResponses(0);

            if (response.hasError()) {
                throw new RuntimeException("Error during OCR: " + response.getError().getMessage());
            }

            return response.getFullTextAnnotation().getText();
        }
    }
}
