package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/bills")
public class BillController {

    private final BillService service;

    @GetMapping("")
    @ResponseStatus(HttpStatus.OK)
    public Page<BillResponse> getAllBills(@RequestParam(required = false) Long accountId,
                                          @RequestParam(required = false) Long categoryId,
                                          @RequestParam(required = false) BillType type,
                                          @RequestParam(required = false) BillStatusFilter status,
                                          @RequestParam(required = false) LocalDate dueDateFrom,
                                          @RequestParam(required = false) LocalDate dueDateTo,
                                          @PageableDefault(size = 20, sort = "dueDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.getAllBills(accountId, categoryId, type, status, dueDateFrom, dueDateTo, pageable);
    }

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public BillResponse createBill(@RequestBody @Valid BillRequest request) {
        return service.createBill(request);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public BillResponse getBillById(@PathVariable Long id) {
        return service.getBillById(id);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public BillResponse updateBill(@PathVariable Long id, @RequestBody @Valid BillRequest request) {
        return service.updateBill(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBill(@PathVariable Long id) {
        service.deleteBill(id);
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.OK)
    public BillResponse cancelBill(@PathVariable Long id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/settle")
    @ResponseStatus(HttpStatus.OK)
    public BillResponse settleBill(@PathVariable Long id, @RequestBody @Valid SettleRequest request) {
        return service.settle(id, request);
    }

    @PostMapping("/{id}/unsettle")
    @ResponseStatus(HttpStatus.OK)
    public BillResponse unsettleBill(@PathVariable Long id) {
        return service.unsettle(id);
    }

    @PostMapping("/installments")
    @ResponseStatus(HttpStatus.CREATED)
    public List<BillResponse> createInstallments(@RequestBody @Valid InstallmentPlanRequest request) {
        return service.createInstallmentPlan(request);
    }

    @DeleteMapping("/installments/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInstallmentGroup(@PathVariable UUID groupId) {
        service.deleteInstallmentGroup(groupId);
    }

    @PostMapping("/settle")
    @ResponseStatus(HttpStatus.OK)
    public List<BillResponse> bulkSettle(@RequestBody @Valid BulkSettleRequest request) {
        return service.bulkSettle(request);
    }
}
