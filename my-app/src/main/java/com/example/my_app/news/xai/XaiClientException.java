package com.example.my_app.news.xai;

public class XaiClientException extends RuntimeException {

  public XaiClientException(String message) {
    super(message);
  }

  public XaiClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
