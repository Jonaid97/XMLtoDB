package com.example.demo.controller;

import com.example.demo.dto.RecordsWrapper;
import com.example.demo.model.XmlRecord;
import com.example.demo.service.XmlProcessorService;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.io.InputStream;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/xml")
public class XmlController {

    @Autowired
    private XmlProcessorService xmlProcessorService;

    @PostMapping("/upload")
    public ResponseEntity<String> uploadXml(@RequestParam("file") MultipartFile file,
                                            @RequestParam(value = "technique", defaultValue = "batch") String method) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File is empty!");
        }

        System.out.println("Received file: " + file.getOriginalFilename() + " (Size: " + file.getSize() + " bytes)");

        try (InputStream inputStream = file.getInputStream()) {
            switch (method.toLowerCase()) {
                case "batch":
                    xmlProcessorService.saveRecordsUsingBatch(inputStream);
                    break;
                case "streaming":
                    xmlProcessorService.saveRecordsUsingStreaming(inputStream);
                    break;
//                case "multithreading":
////                    xmlProcessorService.saveRecordsUsingMultithreading(inputStream);
////                    break;
//                case "chunking":
//                    xmlProcessorService.saveRecordsUsingChunking(inputStream);
//                    break;
                default:
                    return ResponseEntity.badRequest().body("Invalid method specified");
            }

            return ResponseEntity.ok("XML processed successfully.");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Failed to process XML: " + e.getMessage());
        }
    }
}



