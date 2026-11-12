package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Alcance do estorno de uma compra parcelada: só a parcela, ela e as seguintes, ou a compra inteira.")
public enum RefundScope {
    ONLY_THIS,
    THIS_AND_FOLLOWING,
    WHOLE_PURCHASE
}
