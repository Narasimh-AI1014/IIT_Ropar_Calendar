package com.iitrpr.calendar.student;

import com.iitrpr.calendar.domain.ScheduleRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/student")
public class StudentScheduleController {

    @Autowired
    private ScheduleRepository scheduleRepository;

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    @GetMapping("/me/schedule-flat")
    public ResponseEntity<List<StudentScheduleEvent>> getMySchedule(@RequestParam Long studentId) {
        List<StudentScheduleEvent> completeSchedule = new ArrayList<>();

        // 1. Process Weekly Classes
        List<Object[]> rawClasses = scheduleRepository.findStudentClasses(studentId);
        for (Object[] row : rawClasses) {
            completeSchedule.add(new StudentScheduleEvent(
                (String) row[0], (String) row[1], (String) row[2],
                "Weekday: " + row[3].toString(), // Formats recurrent day mapping index
                formatTime(row[4]), formatTime(row[5]),
                (String) row[6], (String) row[7]
            ));
        }

        // 2. Process Exams
        List<Object[]> rawExams = scheduleRepository.findStudentExams(studentId);
        for (Object[] row : rawExams) {
            completeSchedule.add(new StudentScheduleEvent(
                (String) row[0], (String) row[1], (String) row[2],
                row[3].toString(), formatTime(row[4]), formatTime(row[5]),
                (String) row[6], (String) row[7]
            ));
        }

        // 3. Process Dynamic Custom Events
        List<Object[]> rawEvents = scheduleRepository.findStudentEvents(studentId);
        for (Object[] row : rawEvents) {
            completeSchedule.add(new StudentScheduleEvent(
                (String) row[0], (String) row[1] != null ? (String) row[1] : "GEN", (String) row[2],
                row[3].toString(), formatTime(row[4]), formatTime(row[5]),
                (String) row[6], (String) row[7]
            ));
        }

        return ResponseEntity.ok(completeSchedule);
    }

    private String formatTime(Object timeObj) {
        if (timeObj == null) return "00:00";
        if (timeObj instanceof java.sql.Time) {
            return ((java.sql.Time) timeObj).toLocalTime().format(timeFormatter);
        }
        if (timeObj instanceof LocalTime) {
            return ((LocalTime) timeObj).format(timeFormatter);
        }
        return timeObj.toString().substring(0, 5); // Truncates HH:mm:ss down to HH:mm safely
    }
}
