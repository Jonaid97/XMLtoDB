package com.example.demo.dto;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@XmlRootElement(name = "records")
@XmlAccessorType(XmlAccessType.FIELD)  // Better for field-based serialization
public class RecordsWrapper {

    @XmlElement(name = "record")  // Map each <record> element to the list
//    private List<RecordDto> records;
    private List<RecordDto> records = new ArrayList<>();

    @Getter
    @Setter
    @XmlAccessorType(XmlAccessType.FIELD)  // Ensures JAXB works on fields, not getters
    public static class RecordDto {
        private String name;
        private String value;
    }
}