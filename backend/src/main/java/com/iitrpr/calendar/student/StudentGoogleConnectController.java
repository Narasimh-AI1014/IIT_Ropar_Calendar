package com.iitrpr.calendar.student;

import com.iitrpr.calendar.domain.AppUser;
import com.iitrpr.calendar.domain.AppUserRepository;
import com.iitrpr.calendar.errors.BadRequestException;
import com.iitrpr.calendar.security.EncryptionUtil;
import com.iitrpr.calendar.sync.SyncTaskDispatcher;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/student/google")
public class StudentGoogleConnectController {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private SyncTaskDispatcher syncTaskDispatcher;

    @PostMapping("/connect")
    @Transactional
    public ResponseEntity<Map<String, String>> connectGoogleCalendar(
            @RequestParam("studentId") Long studentId,
            @Valid @RequestBody GoogleConnectRequest request) {

        // 1. Validate if the student profile exists in our records
        AppUser student = appUserRepository.findById(studentId)
                .orElseThrow(() -> new BadRequestException("Student profile identity key '" + studentId + "' not found."));

        // 2. Simulate exchanging the authCode for the actual Google OAuth Refresh Token
        // In your production build, this block makes a secure network call hitting Google's token endpoint
        String mockRefreshToken = "ref_tok_" + UUID.randomUUID().toString().substring(0, 12);

        try {
            // 3. Cryptographically encrypt the token before writing it to the database field
            String securedToken = EncryptionUtil.encrypt(mockRefreshToken);
            student.setRefreshToken(securedToken);
            appUserRepository.save(student);
        } catch (Exception e) {
            throw new BadRequestException("Cryptographic subsystem failure: Failed to secure access tokens. " + e.getMessage());
        }

        // 4. Instantly trigger a throttled full calendar backfill event using your sync task worker queue
        syncTaskDispatcher.dispatchSingleStudentSync(studentId);

        // 5. Structure confirmation payload response package
        Map<String, String> response = new HashMap<>();
        response.put("status", "CONNECTED");
        response.put("message", "Google Calendar connected successfully. Background backfill synchronization has been initialized.");

        return ResponseEntity.ok(response);
    }
}
