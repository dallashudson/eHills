/* Message.java
 *  EE422C Final Project submission by
 *  Mina Abbassian
 *  mea2947
 *  16170
 *  Fall 2020
 */

package final_project;

class Message {
  String type;
  String input;
  int number;

  protected Message() {
    this.type = "";
    this.input = "";
    this.number = 0;
    System.out.println("client-side message created");
  }

  protected Message(String type, String input, int number) {
    this.type = type;
    this.input = input;
    this.number = number;
    System.out.println("client-side message created");
  }
}