package com.iitrpr.calendar.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.iitrpr.calendar.domain.AppUser;
import com.iitrpr.calendar.domain.AppUserRepository;
import com.iitrpr.calendar.errors.BadRequestException;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private GoogleTokenVerifier googleTokenVerifier;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private JwtService jwtService;

    @PostMapping("/google")
    public ResponseEntity<Map<String, Object>> loginWithGoogle(@Valid @RequestBody LoginRequest loginRequest) {
        
        // 1. Cryptographically verify the Google Token signature and institutional domain constraints
        GoogleIdToken.Payload verifiedPayload = googleTokenVerifier.verify(loginRequest.getIdToken());
        String email = verifiedPayload.getEmail().toLowerCase().trim();
        String name = (String) verifiedPayload.get("name");

        // 2. Validate if the user profile exists inside our system database records
        Optional<AppUser> userOptional = appUserRepository.findByEmail(email);
        
        if (userOptional.isEmpty()) {
            throw new BadRequestException("User record matching '" + email + "' is not registered in the system database. Contact Admin.");
        }

        AppUser user = userOptional.get();
        
        // 3. Reject deactivated user account profiles
        if (!user.isActive()) {
            throw new BadRequestException("This user account profile has been deactivated.");
        }

        // 4. Enforce strict student-site and staff-site destination validation rules
        String requestedSite = loginRequest.getCallingSite(); // e.g. "http://localhost:5173"
        String assignedRole = user.getRole(); // 'STUDENT', 'FACULTY', 'ADMIN'

        validateCallingSitePermissions(assignedRole, requestedSite);

        // 5. Generate a short-lived custom signed stateless app JWT token
        String systemToken = jwtService.generateToken(email, assignedRole);

        // 6. Structure payload response package bundle back to the React frontends
        Map<String, Object> response = new HashMap<>();
        response.put("token", systemToken);
        response.put("role", assignedRole);
        response.put("email", email);
        response.put("name", name);

        return ResponseEntity.ok(response);
    }

    private void validateCallingSitePermissions(String role, String callingSite) {
        if ("STUDENT".equals(role) && !callingSite.contains("5173")) {
            throw new BadRequestException("Access Denied: Student accounts are restricted to the Student Timetable dashboard (Port 5173).");
        }
        if ("ADMIN".equals(role) && !callingSite.contains("5175")) {
            throw new BadRequestException("Access Denied: Admin credentials must login through the dedicated Admin Management space (Port 5175).");
        }
        if ("FACULTY".equals(role) && !callingSite.contains("5176")) {
            throw new BadRequestException("Access Denied: Faculty credentials must login through the dedicated Faculty Portal (Port 5176).");
        }
    }
}
