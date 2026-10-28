package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

/** Quem não é administrador vê as rotas do painel como um recurso que não existe. */
public class AdminOnlyException extends RuntimeException {
    public AdminOnlyException() {
        super("Resource not found");
    }
}
