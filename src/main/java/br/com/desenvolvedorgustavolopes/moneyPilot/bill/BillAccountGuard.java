package br.com.desenvolvedorgustavolopes.moneyPilot.bill;

import br.com.desenvolvedorgustavolopes.moneyPilot.account.Account;
import br.com.desenvolvedorgustavolopes.moneyPilot.account.AccountType;
import br.com.desenvolvedorgustavolopes.moneyPilot.exception.InvalidInvoiceDaysException;

/**
 * Spec 016, regra 38: título e recorrência de título não apontam para cartão com fechamento e
 * vencimento. A cobrança no cartão é a compra recorrente; baixar um título no cartão continua valendo.
 */
final class BillAccountGuard {

    private BillAccountGuard() {
    }

    static void rejectInvoiceCard(Account account) {
        if (account.getType() == AccountType.CREDIT_CARD && account.getClosingDay() != null) {
            throw new InvalidInvoiceDaysException("accountId",
                    "Cartão com fatura não recebe títulos: para uma assinatura use Compra recorrente em Faturas");
        }
    }
}
