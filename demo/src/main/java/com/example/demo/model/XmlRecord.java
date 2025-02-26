package com.example.demo.model;
import jakarta.persistence.*;
import jakarta.xml.bind.annotation.XmlElement;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter
@Setter
@Table(name = "xml_record")  // Updated table name
public class XmlRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @XmlElement
    private String name;

    @XmlElement
    private String value;
}