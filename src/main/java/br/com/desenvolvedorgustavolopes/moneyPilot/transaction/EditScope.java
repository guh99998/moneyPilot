package br.com.desenvolvedorgustavolopes.moneyPilot.transaction;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Alcance da edição de uma parcela: só ela, ou ela e as seguintes do grupo.")
public enum EditScope {
    ONLY_THIS,
    THIS_AND_FOLLOWING
}
