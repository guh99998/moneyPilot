package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

/** Sem chave Pix configurada o apoio não existe: a rota responde como um recurso que não existe. */
public class ContributionNotConfiguredException extends RuntimeException {
    public ContributionNotConfiguredException() {
        super("Resource not found");
    }
}
