package com.iitrpr.calendar.student;

import com.iitrpr.calendar.admin.CampusController;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** The campus calendar as students see it (read only). */
@RestController
@RequestMapping("/api/student")
public class StudentCampusController {

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/campus")
    public List<Map<String, Object>> campus() {
        return CampusController.listAll(em);
    }
}
