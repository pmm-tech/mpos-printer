package id.modefashion.printer.dto;

public class ReceiptLineData {
  public static final String TYPE_TXT = "string";
  public static final String TYPE_IMG = "img/png";
  public static final String TYPE_BARCODE = "barcode";

  private String type;
  private String content;

  public ReceiptLineData(String type, String content) {
    this.type = type;
    this.content = content;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }
}
