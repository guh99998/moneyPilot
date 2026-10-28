package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class SupportEmailRequiredException extends RuntimeException {
    public SupportEmailRequiredException() {
        super("Informe um e-mail para recebermos a resposta.");
    }
}
