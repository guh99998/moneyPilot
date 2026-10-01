package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bill-recurrences")
public class BillRecurrenceController {

    private final BillRecurrenceService service;

    @GetMapping("")
    @ResponseStatus(HttpStatus.OK)
    public Page<BillRecurrenceResponse> getAllBillRecurrences(Pageable pageable) {
        return service.getAllBillRecurrences(pageable);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public BillRecurrenceResponse getBillRecurrenceById(@PathVariable Long id) {
        return service.getBillRecurrenceById(id);
    }

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public BillRecurrenceResponse createBillRecurrence(@Valid @RequestBody BillRecurrenceRequest request) {
        return service.createBillRecurrence(request);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public BillRecurrenceResponse updateBillRecurrence(@PathVariable Long id, @Valid @RequestBody BillRecurrenceRequest request) {
        return service.updateBillRecurrence(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBillRecurrence(@PathVariable Long id) {
        service.deleteBillRecurrence(id);
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.OK)
    public BillRecurrenceResponse deactivateBillRecurrence(@PathVariable Long id) {
        return service.deactivate(id);
    }
}
