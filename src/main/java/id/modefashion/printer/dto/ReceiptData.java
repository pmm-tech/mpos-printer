package id.modefashion.printer.dto;

import java.util.List;

public class ReceiptData {
  private int total;
  private List<ReceiptLineData> data;
  private String printer;
  private boolean formFeed;

  public int getTotal() {
    return total;
  }

  public void setTotal(int total) {
    this.total = total;
  }

  public List<ReceiptLineData> getData() {
    return data;
  }

  public void setData(List<ReceiptLineData> data) {
    this.data = data;
  }

  public String getPrinter() {
    return printer;
  }

  public void setPrinter(String printer) {
    this.printer = printer;
  }

  public boolean isFormFeed() {
    return formFeed;
  }

  public void setFormFeed(boolean formFeed) {
    this.formFeed = formFeed;
  }
}
