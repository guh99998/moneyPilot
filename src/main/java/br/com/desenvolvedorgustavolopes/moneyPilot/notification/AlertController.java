package br.com.desenvolvedorgustavolopes.moneyPilot.notification;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService service;

    @GetMapping
    public AlertsResponse listAlerts() {
        return service.listAlerts();
    }

    @PostMapping("/dismiss")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void dismiss(@Valid @RequestBody DismissAlertRequest request) {
        service.dismiss(request.key());
    }
}
