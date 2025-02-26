package com.example.demo.service;

import com.example.demo.dto.RecordsWrapper;
import com.example.demo.model.XmlRecord;
import com.example.demo.repository.XmlRecordRepository;

import jakarta.transaction.Transactional;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.xml.stream.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLInputFactory;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@Transactional
public class XmlProcessorService {

    @Autowired
    private XmlRecordRepository xmlRecordRepository;
    private static final int BATCH_SIZE = 100000;
    private static final int THREAD_COUNT = 14;

    public XmlProcessorService(XmlRecordRepository xmlRecordRepository) {
        this.xmlRecordRepository = xmlRecordRepository;
    }
    //<?xml version="1.0" encoding="UTF-8"?>

    public void saveRecordsUsingBatch(InputStream inputStream) {
        long startTime = System.currentTimeMillis();
        List<XmlRecord> batch = new ArrayList<>();
        XMLStreamReader reader = null;

        try {
            reader = XMLInputFactory.newInstance().createXMLStreamReader(inputStream);

            JAXBContext context = JAXBContext.newInstance(RecordsWrapper.RecordDto.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();

            int recordCount = 0;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamReader.START_ELEMENT && "record".equals(reader.getLocalName())) {
                    JAXBElement<RecordsWrapper.RecordDto> recordElement = unmarshaller.unmarshal(reader, RecordsWrapper.RecordDto.class);
                    RecordsWrapper.RecordDto dto = recordElement.getValue();

                    XmlRecord record = new XmlRecord();
                    record.setName(dto.getName());
                    record.setValue(dto.getValue());

                    batch.add(record);
                    recordCount++;

                    // Save batch if it reaches the defined size
                    if (batch.size() >= BATCH_SIZE) {
                        xmlRecordRepository.saveAll(batch);
                        System.out.println("Processed " + recordCount + " records...");
                        batch.clear();
                    }
                }
            }

            // Save remaining records if any
            if (!batch.isEmpty()) {
                xmlRecordRepository.saveAll(batch);
                System.out.println("Processed final batch of " + batch.size() + " records.");
            }

            long endTime = System.currentTimeMillis();
            System.out.println("Batch Insertion completed. Total records: " + recordCount + ", Time: " + (endTime - startTime) + " ms");

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to process XML: " + e.getMessage());
        } finally {
            // Close the reader manually in finally block
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void saveRecordsUsingStreaming(InputStream inputStream) {
        long startTime = System.currentTimeMillis();
        List<XmlRecord> batch = new ArrayList<>();
        XMLStreamReader reader = null;

        try {
            // Initialize XMLStreamReader manually
            reader = XMLInputFactory.newInstance().createXMLStreamReader(inputStream);

            // Initialize JAXBContext and Unmarshaller for RecordDto
            JAXBContext context = JAXBContext.newInstance(RecordsWrapper.RecordDto.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();

            int recordCount = 0;

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamReader.START_ELEMENT && "record".equals(reader.getLocalName())) {
                    // Unmarshal record element
                    JAXBElement<RecordsWrapper.RecordDto> recordElement = unmarshaller.unmarshal(reader, RecordsWrapper.RecordDto.class);
                    RecordsWrapper.RecordDto dto = recordElement.getValue();

                    // Create XmlRecord and add to batch
                    XmlRecord record = new XmlRecord();
                    record.setName(dto.getName());
                    record.setValue(dto.getValue());

                    batch.add(record);
                    recordCount++;

                    // Save the record directly for streaming (no batching)
                    xmlRecordRepository.save(record);

                    // Progress logging every 1000 records processed
                    if (recordCount % 100000 == 0) {
                        System.out.println(recordCount + " records processed...");
                    }
                }
            }

            long endTime = System.currentTimeMillis();
            System.out.println("Streaming Insertion completed. Total records: " + recordCount + ", Time: " + (endTime - startTime) + " ms");

        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to process XML: " + e.getMessage());
        } finally {
            // Close the reader manually in finally block
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void saveRecordsUsingMultithreading(InputStream inputStream) {
        long startTime = System.currentTimeMillis();

        // Thread pool and task coordination
        ExecutorService executorService = Executors.newFixedThreadPool(THREAD_COUNT);
        BlockingQueue<RecordsWrapper.RecordDto> recordQueue = new LinkedBlockingQueue<>(1000);
        CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
        AtomicBoolean parsingCompleted = new AtomicBoolean(false);  // End-of-stream flag

        try {
            // XML Parser thread to feed records into the queue
            executorService.submit(() -> parseXmlAndFeedQueue(inputStream, recordQueue, parsingCompleted));

            // Worker threads to consume and save records
            for (int i = 0; i < THREAD_COUNT; i++) {
                executorService.submit(() -> {
                    try {
                        JAXBContext context = JAXBContext.newInstance(RecordsWrapper.RecordDto.class);
                        Unmarshaller unmarshaller = context.createUnmarshaller();

                        while (!parsingCompleted.get() || !recordQueue.isEmpty()) {
                            RecordsWrapper.RecordDto record = recordQueue.poll(5, TimeUnit.SECONDS);  // Avoid indefinite blocking
                            if (record != null && record.getName() != null && record.getValue() != null) {
                                // Ensure valid record before saving
                                XmlRecord xmlRecord = new XmlRecord();
                                xmlRecord.setName(record.getName());
                                xmlRecord.setValue(record.getValue());

                                xmlRecordRepository.save(xmlRecord);  // Database insertion
                                System.out.println(Thread.currentThread().getName() + " saved record: " + record.getName());
                            }
                        }
                    } catch (Exception e) {
                        System.err.println(Thread.currentThread().getName() + " encountered an error: " + e.getMessage());
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // Wait for all worker threads to finish
            latch.await();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            executorService.shutdown();
        }

        long endTime = System.currentTimeMillis();
        System.out.println("Multithreading insertion completed. Total time: " + (endTime - startTime) + " ms");
    }

    // Updated XML parser method
    private void parseXmlAndFeedQueue(InputStream inputStream, BlockingQueue<RecordsWrapper.RecordDto> queue, AtomicBoolean parsingCompleted) {
        try (BufferedInputStream bufferedStream = new BufferedInputStream(inputStream)) {
            JAXBContext context = JAXBContext.newInstance(RecordsWrapper.RecordDto.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();

            XMLInputFactory factory = XMLInputFactory.newInstance();
            XMLStreamReader reader = factory.createXMLStreamReader(bufferedStream);

            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamReader.START_ELEMENT && "record".equals(reader.getLocalName())) {
                    try {
                        RecordsWrapper.RecordDto record = (RecordsWrapper.RecordDto) unmarshaller.unmarshal(reader);

                        if (record != null && record.getName() != null && record.getValue() != null) {
                            queue.put(record);  // Add valid record to queue
                        } else {
                            System.err.println("Skipping invalid or null record.");
                        }

                    } catch (JAXBException e) {
                        System.err.println("Failed to unmarshal record: " + e.getMessage());
                    }
                }
            }

            System.out.println("XML parsing completed.");
            parsingCompleted.set(true);  // Signal end of parsing
            reader.close();

        } catch (Exception e) {
            System.err.println("Error parsing XML: " + e.getMessage());
            e.printStackTrace();
        }
    }


//    public void saveRecordsUsingChunking(List<XmlRecord> records) {
//        long startTime = System.currentTimeMillis();
//        int chunkSize = 1000;
//        int totalRecords = records.size();
//        int totalChunks = (int) Math.ceil((double) totalRecords / chunkSize);
//
//        System.out.println("Starting Chunking Insertion with chunk size of " + chunkSize + " records per chunk.");
//        for (int i = 0; i < totalRecords; i += chunkSize) {
//            int toIndex = Math.min(i + chunkSize, totalRecords);
//            List<XmlRecord> chunk = records.subList(i, toIndex);
//            xmlRecordRepository.saveAll(chunk);
//            System.out.println("Chunk " + (i / chunkSize + 1) + " of " + totalChunks + " processed. Size: " + chunk.size());
//        }
//
//        long endTime = System.currentTimeMillis();
//        System.out.println("Chunking Insertion completed. Total time: " + (endTime - startTime) + " ms");
//    }
}