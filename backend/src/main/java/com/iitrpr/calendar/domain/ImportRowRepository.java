package com.iitrpr.calendar.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ImportRowRepository extends JpaRepository<ImportRow, Long> {
}
