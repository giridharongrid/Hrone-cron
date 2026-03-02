package com.hrone;

import com.auth0.jwt.JWT;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class App {

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);

        RestTemplate rest = new RestTemplate();

        String user = System.getenv("HRONE_USERNAME");
        String pass = System.getenv("HRONE_PASSWORD");
        String domain = System.getenv("HRONE_DOMAIN_CODE");

        if (user == null || pass == null || domain == null) {
            System.out.println("❌ Missing secrets: HRONE_USERNAME / HRONE_PASSWORD / HRONE_DOMAIN_CODE");
            System.exit(1);
        }

        // Prepare login form as x-www-form-urlencoded
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", user);
        form.add("password", pass);
        form.add("grant_type", "password");
        form.add("loginType", "1");
        form.add("companyDomainCode", domain);
        form.add("isUpdated", "0");
        form.add("validSource", "Y");
        form.add("deviceName", "github-runner");


        HttpHeaders loginHeaders = new HttpHeaders();
        loginHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> loginEntity = new HttpEntity<>(form, loginHeaders);

        Map loginResp = rest.postForObject(
                "https://gateway.app.hrone.cloud/oauth2/token",
                loginEntity,
                Map.class
        );

        System.out.println("Login: " + loginResp);

        String token = (String) loginResp.get("access_token");
        String employeeId = JWT.decode(token).getClaim("LogOnId").asString();

        // Punch request
        Map<String, Object> punch = new HashMap<>();
        punch.put("requestType", "A");
        punch.put("applyRequestSource", 10);
        punch.put("employeeId", employeeId);

        // API expects something like: 2026-01-21T21:46 (IST, no seconds, no offset)
        DateTimeFormatter istMinuteFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String punchTime = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).format(istMinuteFmt);
        punch.put("punchTime", punchTime);

        punch.put("attendanceSource", "W");
        punch.put("attendanceType", "Online");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        headers.add("domainCode", domain);
        headers.add("AccessMode", "W");
        headers.add("X-Requested-With", "https://app.hrone.cloud");

        HttpEntity<?> entity = new HttpEntity<>(punch, headers);

        ResponseEntity<String> punchResp = rest.postForEntity(
                "https://app.hrone.cloud/api/timeoffice/mobile/checkin/Attendance/Request",
                entity,
                String.class
        );

        System.out.println("Punch: " + punchResp.getBody());
        System.exit(0);
    }
}
