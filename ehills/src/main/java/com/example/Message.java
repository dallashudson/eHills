package com.example;

class Message {
  String reason;
  String entry;
  int number;

  protected Message() {
    this.reason = "";
    this.entry = "";
    this.number = 0;
    System.out.println("client-side message created");
  }

  protected Message(String type, String input, int number) {
    this.reason = type;
    this.entry = input;
    this.number = number;
    System.out.println("client-side message created");
  }
}