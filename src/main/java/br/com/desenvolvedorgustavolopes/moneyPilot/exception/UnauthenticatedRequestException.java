package br.com.desenvolvedorgustavolopes.moneyPilot.exception;

public class UnauthenticatedRequestException extends RuntimeException {
  public UnauthenticatedRequestException() {
    super("User is not authenticated");
  }
}
