package org.signal.registration.sender.way_sms;

public class WaySmsRes {

  private int status;
  private String message;
  private String data;

  public int getStatus() {
    return status;
  }

  public void setStatus(final int status) {
    this.status = status;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(final String message) {
    this.message = message;
  }

  public String getData() {
    return data;
  }

  public void setData(final String data) {
    this.data = data;
  }
}
