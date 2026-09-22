package br.com.desenvolvedorgustavolopes.moneyPilot.account;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService service;

    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(@Valid @RequestBody AccountRequest request) {
        return service.createAccount(request);
    }

    @GetMapping("")
    @ResponseStatus(HttpStatus.OK)
    public Page<AccountResponse> getAllAccounts(Pageable pageable) {
        return service.getAllAccounts(pageable);
    }

    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public AccountResponse getAccountById(@PathVariable Long id) {
        return service.getAccountById(id);
    }

    @PutMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    public AccountResponse updateAccountById(@PathVariable Long id, @RequestBody @Valid AccountRequest request) {
        return service.updateAccountById(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccountById(@PathVariable Long id) {
        service.deleteAccountById(id);
    }

    @GetMapping("/{id}/balance")
    @ResponseStatus(HttpStatus.OK)
    public BalanceResponse getAccountBalance(@PathVariable Long id) {
        return service.getAccountBalance(id);
    }

}
