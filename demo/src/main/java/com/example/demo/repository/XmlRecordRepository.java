package com.example.demo.repository;

import com.example.demo.model.XmlRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface XmlRecordRepository extends JpaRepository<XmlRecord, Long> {
}
